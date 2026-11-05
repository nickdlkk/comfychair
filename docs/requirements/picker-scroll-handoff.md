# beta.4 / code58 picker scroll handoff

Superseded locally by [beta.5 / code59 server gallery handoff](server-gallery-handoff.md); beta4 evidence below remains historical.

## Delivered
- APK <http://192.168.4.168:9010/comfychair-v0.8.26-beta.4-release-picker-scroll-20261104-2118.apk>
- LAN no-proxy full GET200, 8293641 bytes, SHA256 `d69bc338169972b2fc001412e304caaf7bd576cde600b1b555ae47d9556b9abe`, equal to built/shared file. Existing persistent HTTP9010 PID3938536 document root `/root/share/comfychair` reused, no restart.
- aapt: `sh.hnet.comfychair`, `v0.8.26-beta.4`, code58 (57→58 once). apksigner valid, certificate matches downloaded beta3 local artifact: Android Debug SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`. Not production signing. Next delivery must exceed58.

## Root cause and changes
- Old panel's `autoPages < 5` counted ALL automatic pages per session; sparse pages could consume that while leaving one apparent page. Executed red source regression against old code: 1test/1failure. This is deterministic source evidence, not a reproduction of the unseen user's gesture session.
- New `ServerPickerScrollPolicy` and session-scoped snapshotFlow observe layout+state/cursor; normal browsing has no session quota. Require measured grid count==descriptor count to avoid advancing again from pre-append near-bottom layout. Empty/duplicate append pages alone count toward a consecutive-five sparse budget; real progress resets, localized manual retry renews. Controller loadingMore coalesces viewport triggers and existing seen-cursor validation prevents loops.
- Refresh resets loadingMore and errors, uses listRevision to reject non-cooperative older pages while retaining popup session, previews and scroll. Preview completion/errors never gate list loading. Existing visible-only three-worker previews, sampled disk cache and explicit tile retry remain unchanged; no unlimited retry or full-image prefetch.
- No further-page cursor cache redesign: first-page lists remain cached and revalidated, later pages still require network. Freshness/server/query scoped cursor safety preferred over stale traversal. Network preview may still be full-resolution webp60 capped4MiB; local256px sampling does not reduce network bytes. User preview failures cannot be proven without device evidence.

## Verification
- `COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36 ./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=picker-scroll`: BUILD SUCCESSFUL in7m31s; R8 and resource shrinking/optimization executed. No main/test edits during final build.
- XML: **138tests /0failures /0errors /0skipped** (131existing+7new). New executable policy/controller tests: both sources12additional50-image pages, no stale-layout library drain, unchanged viewport cursor updates, empty/duplicate sparse cutoff/manual recovery, errors retry/no cursor, request/download guards, thumbnail error independence, non-cooperative refresh callback. Existing server/client/slot/query changes, cancellation, direct Input HEAD/noPOST, cache cleanup and upload reuse tests retained. Source contracts updated, not represented as real touches.
- Actual live readonly XML: INPUT50+50 /3094assets /search310; OUTPUT50+50 /2010assets /search124; authenticated preview/content GET passed. Only existing readonly live test, no live POST/DELETE/generation.
- Evidence `/root/comfychair-artifacts/code58/`: build.log, targeted.log, regression-red.log, test-results/, matching mapping/, output-metadata.json, aapt.txt, certificate.txt, beta3-certificate.txt, verification.json.
- `adb devices -l` empty: no installation/real Compose gestures/layout/Android codec/R8 runtime verification. Existing compiler warnings unchanged. Filesystem reports zero nonroot-available blocks but >4GB privileged free; build completed with no cleanup/deletion.

## Exact task-changed repository files
- app/build.gradle.kts
- app/src/main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt
- app/src/main/java/sh/hnet/comfychair/viewmodel/ServerImagePicker.kt
- app/src/main/java/sh/hnet/comfychair/viewmodel/ServerPickerScrollPolicy.kt (new)
- app/src/test/java/sh/hnet/comfychair/ServerPickerScrollPolicyTest.kt (new)
- app/src/test/java/sh/hnet/comfychair/ServerPickerScrollRegressionTest.kt (new)
- app/src/test/java/sh/hnet/comfychair/ServerAssetsTest.kt
- app/src/test/java/sh/hnet/comfychair/ServerImagePickerUiContractTest.kt
- README.md, README.zh-CN.md
- docs/requirements/picker-scroll-v0.8.26-beta.4.md (new)
- docs/requirements/picker-scroll-handoff.md (new; this file)
- docs/requirements/image-cache-cleanup-handoff.md (historical pointer only)

All prior dirty/untracked code, six project skill directories and stash@{0} preserved; no commit/push/tag/Release or ComfyUI mutation. Active-profile UI modification skill gained general paging regression guidance; no other profile touched.
