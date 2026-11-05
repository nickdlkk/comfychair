# v0.8.26 / versionCode 63 — stable release

## Scope / 完整功能链

- beta.1/code55: SHA-256 immutable encoded upload names, overwrite=false, authenticated HEAD before reuse, bounded descriptors and single-flight cancellation.
- beta.2/code56: installation-HMAC-scoped private persistent first-page lists, sampled thumbnails and upload descriptors; unchanged Input direct reuse only for Editing with every consumer proven native LoadImage.image. Provenance, bitmap identity/revision and authenticated epoch are required. Compression/masks/custom consumers/replacements/restored provenance use encoded upload. Output direct reuse is not enabled.
- beta.3/code57: saved automatic cleanup policy and statistics, default 64 MiB/7 days, oldest-created eviction; feature-scoped manual clear preserves selected media/workflows/key and generation-guards late writes. Freshness TTL is separate from retention; independent safety ceiling remains with auto cleanup off.
- beta.4/code58: productive pagination has no five-page session cap; only consecutive no-progress pages pause automatic advance, with manual continuation. Appended-layout synchronization and single-flight guards preserve existing grid.
- beta.5/code59: independent Settings Input/Output gallery, original preview/save/material/source actions, guarded optional physical deletion extension. Dedicated key, capability check, queue/path safeguards, bounded prepare token, explicit permanent-delete confirmation and per-item results; no asset DB mutation.
- beta.6/code60: explicit per-origin trusted-LAN HTTP consent with one dedicated deletion key; optional proxy mode remains. HTTP exposes credentials/tokens in plaintext; never expose LAN mode publicly.
- beta.7/code61: fullscreen compact gallery, optional security panel, anchored scroll after deletion and preview return.
- beta.8/code62: existing MediaViewer graphical spinner reused in gallery/picker loading overlays with accessible cancel icon; no duplicate loading text or fake progress. Error/retry/confirmation text retained.
- Stable code63 exceeds all distributed betas; applicationId and local Android Debug certificate retained for update compatibility. This is not production signing. CI creates a separately named APK with its own runner certificate; do not substitute it for the primary upgrade APK.

## Deployment / 部署

The attached companion ZIP is built from repository source, README and isolated tests only. It contains no runtime settings, journal, credentials or operator bootstrap. Generic initializer defaults to proxy mode and remains disabled without valid configuration. See [administrator installation/security guide](../../server/comfychair_media/README.md). Existing installations may have private local initialization; preserve their private settings/journal and adapt upgrades administratively. This release does not restart services, mutate media or publish deployment secrets.

## Known limits / 已知边界

Only first-page lists and sampled thumbnails persist; later pages require network and fresh cursors. Preview network bodies may remain full-resolution webp, not server-resized thumbnails. HEAD only404 permits upload fallback; auth/network/server failures stop. Direct Input reuse intentionally uses original-server-file semantics, not Android re-encoding equivalence.

Deletion is Linux-only, conservative against any queued/running job, not universally race-free against external writers or plugins bypassing queue locks. Administrators must exclude concurrent external mutation. Durable tombstones hide exact descriptors in ComfyChair but do not clean other frontends/assets/history; recreated names remain hidden until administrative reconciliation. Preserve journal across upgrades.

JVM/source-contract and isolated Python tests do not establish Android installation, Compose gestures/rendering, Keystore/codec behavior or post-R8 runtime safety. No attached adb device: these remain unverified. No live media deletion is part of release verification.

## Verification / 构建与验收

Executed cleanTestDebugUnitTest testDebugUnitTest assembleRelease with R8 and resource shrinking: BUILD SUCCESSFUL in 7m33s. JVM XML: 170 tests, 0 failures/errors/skipped (27 suites). Isolated pytest server/tests XML: 84 tests, 0 failures/errors/skipped. No live endpoint was opted into for this run. Exact test XML, build log, APK metadata/signature/hash, matching compressed mapping and clean plugin archive are preserved under `/root/comfychair-artifacts/code63/`.

Primary APK: `comfychair-v0.8.26-release-stable-20261105-1006.apk`, 8,372,309 bytes; SHA256 `d1c7d864aefa0b2e2947c82cba031d1d8676b961e71b962f0e29795ccea735c1`.

Companion ZIP: `comfychair-media-v0.8.26-code63.zip`; SHA256 `470255525c374290b378fe70657ba0f4ea96003fa1749b6cfa050e6c91b15113`.

Local Android Debug certificate SHA256: `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`, identical to distributed beta.8. APK metadata confirms applicationId `sh.hnet.comfychair`, versionName `v0.8.26`, versionCode63. Matching R8 mapping archive is a separate local-build attachment, never interchangeable with CI mapping. Published tag points to the tested source commit. The final publication evidence records independently downloaded asset hashes, remote refs and terminal tag-CI outcome; historical beta handoffs describe their original deliveries only.
