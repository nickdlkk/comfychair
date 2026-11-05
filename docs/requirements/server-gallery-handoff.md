# beta.5 / code59 server gallery handoff (historical)

**Superseded security design:** beta.6/code60 adds explicit one-key trusted-LAN HTTP mode without proxy/TLS mandate; see [current handoff](lan-deletion-handoff.md). beta5 plugin was later installed but remains disabled; this beta6 task makes no remote changes. Historical verification below is unchanged.

## Delivered and verified

- APK <http://192.168.4.168:9010/comfychair-v0.8.26-beta.5-release-server-gallery-20261104-2237.apk>
  - 8,353,545 bytes; SHA256 `0f09367ecd79ef7a23203ff9086071c9b7a6bdd1af489dfe0ce27e2c6fc1ecb2`.
- Companion plugin + source tests + installation/security guide ZIP <http://192.168.4.168:9010/comfychair-media-v1-code59.zip>
  - 12,292 bytes; SHA256 `28e30f659fc1c02393db56803446dc65dc07f321ee7e9b10c398ecc1f3a70719`.
- Both full LAN no-proxy HTTP GET200 downloads match local bytes/SHA256. Existing persistent server9010 PID3938536/root `/root/share/comfychair` reused untouched.
- aapt: `sh.hnet.comfychair`, `v0.8.26-beta.5`, code59. Bumped58→59 once. apksigner valid, beta4 certificate matches: Android Debug SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c` (NOT production signing). Next distributed build must exceed59.

## Features and implementation

Settings → Application → Server gallery is an independent `server_gallery` route. Existing quick picker remains. Both reuse `ServerImagePickerPanel` three-column grid, shared bounded previews/disk caches and `ServerImagePicker` pagination/sparse-page policy. Gallery Input/Output tabs, assets search/sort (legacy scope unchanged), refresh/infinite scroll; full download-on-tap viewer with zoom/pan, filename, decoded dimensions, actual downloaded encoded bytes, phone PNG save (MediaStore pending+rollback), material export (existing library JPEG), one-shot identity-bound source slot1 transfer and navigation to Image-to-image. Source acceptance resets compression/masks/direct provenance and checks owner again after IO preparation; no image Intent or destructive activity restart.

Both gallery and picker now long-press multi-select only when extension authorizes the dedicated credential. Count/select-loaded(max50)/clear/cancel; irreversible source/count confirmation; per-item localized outcomes, partial successes retained, no automatic write retries/redirects. Credential stored in separate Keystore EncryptedSharedPreferences fail-closed, excluded OS backup/device transfer; nonloopback HTTP cannot carry deletion secret. Existing auth/TLS/self-signed policy remains a deployment caveat.

Extension `custom_nodes/comfychair_media` is route-only, zero workflow nodes. GET capabilities/missing and POST read-only prepare/delete-token.32KiB streamed-body bound,max50. Random256-bit tokens bound to authenticated principal, descriptor and dev/ino/mtime_ns/ctime_ns/size; TTL120s, idempotent recorded results10min, max256 operations. Every root/subdirectory component opened through dirfd/O_NOFOLLOW; regular image-extension files only, nlink1, no traversal/symlink/recursion. Actual prompt-queue mutex held across all queue checks and filesystem operation; ANY pending/running job blocks. Late-bound queue adapter handles ComfyUI import timing. Durable intent tombstones before unlink; no guessed assets DB updates, no DELETE/assets, no history/shared-preview removal. Source-scoped list revision and exact thumbs invalidated, stale generation writes rejected, upload cache/current bitmap/snapshots retained; existing DirectInput HEAD404 fallback remains.

## Security/consistency limitations and deployment scope

**Not deployed or restarted. No live destructive request was made.** Live GET capability returns404, so deletion remains disabled there. Must separately approve installation/restart, dedicated secrets and a genuinely authenticated/authorized trusted reverse proxy. Exact peer IP, proxy-only secret, overwritten authenticated principal, delete-admin authorization, private backend firewall and HTTPS are prerequisites; setting a boolean or trusting arbitrary forwarded headers is not sufficient.

**Not universally race-free:** queue mutex excludes only cooperating queue operations. External readers/writers/uncoordinated uploads/plugins and final stat→unlink replacement window need admin exclusive maintenance access. Persistent tombstones hide paths only in ComfyChair: other UI/history/assets can show stale records. Crash/unlink failure after intent may hide a still-existing file; reused same-name paths remain hidden until administrator reconciliation/reindex.5000 tombstone bound, no automatic purge. Cached lists can briefly show before revalidation. Detailed installation and limits: `server/comfychair_media/README.md`.

## Actual validation

- Full final invocation: `COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36 ./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=server-gallery` → **BUILD SUCCESSFUL in6m25s**; R8 and resource shrinking/optimization executed. No main/test source changes during final build.
- Android JUnit XML **147tests,0failures,0errors,0skipped** (138prior+9new). Protocol/body/result/header/redirect checks, multi-select gating/max50, targeted cache/revision/late-write preservation, tombstone filtering, HTTP secret refusal, source/route/storage wiring; prior paging/directInput/cache/upload tests retained. Source contracts are not actual gestures.
- Python pytest isolated tempdirs + aiohttp mocked HTTP routes **37passed**, XML0failures/errors/skipped. Covers traversal/links/root nofollow/hardlinks, identity replacement/modification/missing, default denial/auth/peer, body/batch/chunked limits, expiry/principal binding/idempotent same-name recreation, queue guard/lock, late-bound queue, partial/journal failure, output nested/duplicates. Only test fixtures were physically deleted. Nonblocking pytest plugin deprecation warnings retained in log.
- Live readonly XML: INPUT50+50 /2950assets /search301; OUTPUT50+50 /2010assets /search124; preview/content GET passed. Capability GET404. No live POST/DELETE/generation.
- `adb devices -l` empty: no install, real Compose gestures/layout, Keystore, image-codec or post-R8 runtime validation. Existing warnings plus deprecated gallery TabRow; no errors.
- Initial compile passed; first full test run found2 source-contract assertions (old exact ModalBottomSheet syntax and a comment containing rememberSaveable). Fixed contracts and strengthened final source acceptance; final clean test execution and release build passed. Earlier build log retained rather than hidden.
- Evidence `/root/comfychair-artifacts/code59/`: APK/ZIP, build-final.log, build.log, compile.log, pytest.log/xml, python-deps.log, test-results/, mapping/, output-metadata.json, aapt.txt, certificate.txt, beta4-certificate.txt, live-capability.txt, live-system-stats.json, verification.json.

## Exact repository files changed by this task (prior dirty content preserved)

Existing files modified:
- `app/build.gradle.kts`
- `app/src/main/java/sh/hnet/comfychair/ComfyUIClient.kt`
- `app/src/main/java/sh/hnet/comfychair/MainContainerActivity.kt`
- `app/src/main/java/sh/hnet/comfychair/cache/PrivateImageDiskCache.kt`
- `app/src/main/java/sh/hnet/comfychair/cache/ServerImageListDiskCache.kt`
- `app/src/main/java/sh/hnet/comfychair/viewmodel/ImageToImageViewModel.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/screens/ApplicationSettingsScreen.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/navigation/MainNavHost.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/navigation/SettingsNavHost.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh/strings.xml`
- `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/java/sh/hnet/comfychair/ServerAssetsTest.kt`
- `app/src/test/java/sh/hnet/comfychair/ServerImagePickerUiContractTest.kt`
- `README.md`, `README.zh-CN.md`, `task_plan.md`, `docs/requirements/picker-scroll-handoff.md`

New files:
- `app/src/main/java/sh/hnet/comfychair/connection/ServerMediaDelete.kt`
- `app/src/main/java/sh/hnet/comfychair/connection/ServerGallerySourceTransfer.kt`
- `app/src/main/java/sh/hnet/comfychair/storage/DeletionCredentialStorage.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/components/ServerDeletionControls.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/screens/ServerGalleryScreen.kt`
- `app/src/main/java/sh/hnet/comfychair/viewmodel/ServerGalleryViewModel.kt`
- `app/src/test/java/sh/hnet/comfychair/ServerGalleryDeleteTest.kt`
- `server/comfychair_media/__init__.py`, `service.py`, `README.md`
- `server/tests/test_media.py`, `server/tests/test_extra.py`
- `docs/requirements/server-gallery-v0.8.26-beta.5.md`, this handoff.

All six preexisting untracked project skill directories and stash@{0} preserved; no commit/push/tag/Release, no remote deployment. Active dev-profile UI skill updated with general lessons only; no other profile modified.
