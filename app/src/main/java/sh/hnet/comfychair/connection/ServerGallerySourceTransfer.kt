package sh.hnet.comfychair.connection

import sh.hnet.comfychair.viewmodel.ServerGalleryViewModel
import sh.hnet.comfychair.viewmodel.currentMediaIdentity

/** One-shot process-local handoff; Intent carries no bitmap/path or recoverable capability. */
object ServerGallerySourceTransfer {
    private var pending: Pair<String, ServerGalleryViewModel.FullImage>? = null
    @Synchronized fun offer(value: ServerGalleryViewModel.FullImage): String {
        check(value.owner == currentMediaIdentity())
        return java.util.UUID.randomUUID().toString().also { pending = it to value }
    }
    @Synchronized fun take(ticket: String): ServerGalleryViewModel.FullImage? {
        val value = pending; pending = null
        return value?.takeIf { it.first == ticket && it.second.owner == currentMediaIdentity() }?.second
    }
}
