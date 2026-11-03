package sh.hnet.comfychair.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import sh.hnet.comfychair.model.ServerImageQuery
import sh.hnet.comfychair.model.ServerImageSort
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import sh.hnet.comfychair.model.ServerImage
import sh.hnet.comfychair.model.ServerImagePage
import sh.hnet.comfychair.model.ServerImageSource

/** Identity includes the actual authenticated client, not just a reusable server ID. */
data class ServerImageIdentity(val serverId: String, val client: Any)
data class ServerImagePickerState(
    val slot: Int? = null,
    val source: ServerImageSource = ServerImageSource.INPUT,
    val images: List<ServerImage> = emptyList(),
    val loading: Boolean = false,
    val downloading: Boolean = false,
    val error: Boolean = false,
    val loadingMore: Boolean = false,
    val moreError: Boolean = false,
    val nextOffset: Int? = null,
    val nextCursor: String? = null,
    val assets: Boolean? = null,
    val totalAssets: Long? = null,
    val query: String = "",
    val sort: ServerImageSort = ServerImageSort.NEWEST,
    val selected: ServerImage? = null,
    val downloadError: Boolean = false,
    val session: Long = 0
)

/** All state transitions and acceptance run on the owning (UI) scope. IO work is injected. */
class ServerImagePicker<T>(
    private val scope: CoroutineScope,
    private val identity: () -> ServerImageIdentity?,
    private val validSlot: (Int) -> Boolean,
    private val list: suspend (ServerImageIdentity, ServerImageSource, Int?) -> ServerImagePage,
    private val download: suspend (ServerImageIdentity, ServerImage) -> T,
    private val accept: (ServerImageIdentity, Int, T) -> Unit,
    private val dispose: (T) -> Unit = {},
    private val browse: (suspend (ServerImageIdentity, ServerImageSource, ServerImageQuery) -> ServerImagePage)? = null
) {
    private val mutableState = MutableStateFlow(ServerImagePickerState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    private var origin: ServerImageIdentity? = null
    private var previews: Job? = null
    private val previewPermits = Semaphore(3)
    private var inputChoices: List<ServerImage> = emptyList()
    private val seenCursors = mutableSetOf<String>()

    /** Child of popup lifetime AND caller tile lifetime; never leaves detached downloads. */
    suspend fun <V> preview(image: ServerImage, block: suspend (ServerImageIdentity) -> V): V {
        val owner = origin ?: throw CancellationException("Closed")
        val token = revision
        val slot = mutableState.value.slot ?: throw CancellationException("Closed")
        val parent = previews ?: throw CancellationException("Closed")
        if (!current(token, owner, slot) || image !in mutableState.value.images) throw CancellationException("Stale")
        val task = CoroutineScope(scope.coroutineContext + parent).async {
            previewPermits.withPermit {
                // Parent cancellation can release permits before queued child cancellation propagates.
                if (!parent.isActive || mutableState.value.downloading || !current(token, owner, slot)) throw CancellationException("Closed")
                block(owner)
            }
        }
        try {
            val value = task.await()
            if (!current(token, owner, slot)) throw CancellationException("Stale")
            return value
        } finally { task.cancel() }
    }

    fun close() {
        revision++
        job?.cancel()
        job = null
        previews?.cancel()
        previews = null
        inputChoices = emptyList()
        seenCursors.clear()
        origin = null
        mutableState.value = ServerImagePickerState()
    }

    fun connectionChanged() {
        if (origin != null && (origin != identity() || mutableState.value.slot?.let { !validSlot(it) } == true)) close()
    }

    private fun current(token: Long, owner: ServerImageIdentity, slot: Int) =
        token == revision && owner == identity() && validSlot(slot)

    fun open(slot: Int, source: ServerImageSource) {
        close()
        if (!validSlot(slot)) return
        val owner = identity()
        mutableState.value = ServerImagePickerState(slot = slot, source = source, loading = owner != null, error = owner == null, session = revision)
        if (owner == null) return
        origin = owner
        previews = SupervisorJob(scope.coroutineContext[Job])
        load(owner, slot, source, null)
    }

    fun changeQuery(name: String, sort: ServerImageSort) {
        val old = mutableState.value
        val slot = old.slot ?: return
        if (old.downloading) return
        val owner = origin ?: return
        revision++
        job?.cancel(); previews?.cancel()
        previews = SupervisorJob(scope.coroutineContext[Job])
        inputChoices = emptyList()
        seenCursors.clear()
        mutableState.value = ServerImagePickerState(slot = slot, source = old.source, query = name,
            sort = sort, assets = old.assets, loading = true, session = revision)
        load(owner, slot, old.source, null, debounce = true)
    }

    fun refresh() {
        val s = mutableState.value
        changeQuery(s.query, s.sort)
    }

    fun cancelDownload() {
        if (!mutableState.value.downloading) return
        revision++; job?.cancel()
        previews = SupervisorJob(scope.coroutineContext[Job])
        mutableState.value = mutableState.value.copy(downloading = false, selected = null, downloadError = false)
    }

    fun retryDownload() { mutableState.value.selected?.let { select(it) } }

    fun loadMore(retry: Boolean = false) {
        val state = mutableState.value
        val slot = state.slot ?: return
        val owner = origin ?: return
        val offset = state.nextOffset
        if (offset == null && state.nextCursor == null) return
        if (state.loading || state.loadingMore || state.downloading ||
            (state.moreError && !retry)) return
        if (!current(revision, owner, slot)) { close(); return }
        if (state.source == ServerImageSource.INPUT && state.assets != true) {
            val end = ((offset ?: 0) + 50).coerceAtMost(inputChoices.size)
            mutableState.value = state.copy(images = inputChoices.take(end), nextOffset = end.takeIf { it < inputChoices.size })
            return
        }
        mutableState.value = state.copy(loadingMore = true, moreError = false)
        load(owner, slot, state.source, offset)
    }

    private fun load(owner: ServerImageIdentity, slot: Int, source: ServerImageSource, offset: Int?, debounce: Boolean = false) {
        val token = revision
        job = scope.launch {
            try {
                if (debounce) delay(300)
                val request = mutableState.value
                val page = browse?.invoke(owner, source, ServerImageQuery(request.query, request.sort, request.nextCursor, offset))
                    ?: list(owner, source, offset)
                val append = offset != null || request.nextCursor != null
                if (page.assets) require(page.nextCursor == null || page.nextCursor !in seenCursors)
                if (request.assets == true && append) require(page.assets)
                // A malformed/non-progressing cursor is an error, not an endless scroll loop.
                require(page.nextOffset == null || (page.nextOffset > 0 && (offset == null || page.nextOffset < offset)))
                if (current(token, owner, slot)) {
                    val state = mutableState.value
                    page.nextCursor?.let { seenCursors.add(it) }
                    if (source == ServerImageSource.INPUT && !page.assets) inputChoices = page.images.distinct()
                    mutableState.value = state.copy(
                        images = if (source == ServerImageSource.INPUT && !page.assets) inputChoices.take(50)
                            else (if (!append) page.images else state.images + page.images).distinct(),
                        loading = false, loadingMore = false, error = false, moreError = false,
                        nextOffset = if (source == ServerImageSource.INPUT && !page.assets) 50.takeIf { inputChoices.size > 50 } else page.nextOffset,
                        nextCursor = page.nextCursor, assets = page.assets, totalAssets = page.totalAssets
                    )
                } else if (token == revision) close()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                sh.hnet.comfychair.DiagnosticsApplication.event("picker_error " + e.javaClass.simpleName)
                if (current(token, owner, slot)) mutableState.value = mutableState.value.copy(
                    loading = false, loadingMore = false, error = offset == null && mutableState.value.nextCursor == null, moreError = offset != null || mutableState.value.nextCursor != null)
                else if (token == revision) close()
            }
        }
    }

    fun select(image: ServerImage) {
        val state = mutableState.value
        val slot = state.slot ?: return
        val owner = origin ?: return
        if (state.loading || state.downloading || image !in state.images) return
        if (!current(revision, owner, slot)) { close(); return }
        // Selection supersedes in-flight pagination, including non-cooperative callbacks.
        revision++
        job?.cancel()
        previews?.cancel()
        val token = revision
        mutableState.value = state.copy(downloading = true, selected = image, downloadError = false, loadingMore = false, error = false)
        job = scope.launch {
            try {
                val value = download(owner, image)
                // No suspension between this final identity check and source/cache acceptance.
                try { if (current(token, owner, slot)) accept(owner, slot, value) }
                finally { dispose(value) }
                if (token == revision) close()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                sh.hnet.comfychair.DiagnosticsApplication.event("picker_error " + e.javaClass.simpleName)
                if (current(token, owner, slot)) {
                    previews = SupervisorJob(scope.coroutineContext[Job])
                    mutableState.value = mutableState.value.copy(downloading = false, error = true, downloadError = true)
                }
                else if (token == revision) close()
            }
        }
    }
}
