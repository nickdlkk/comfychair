# Gallery layout/position delivery — beta.7 / code61

## Verified artifact
- APK: <http://192.168.4.168:9010/comfychair-v0.8.26-beta.7-release-gallery-space-20261105-0858.apk>
- Bytes:8372321; SHA256 `30eef6e72816dbd7a76c1dcb42b47020f53a1137f5802d6ae53f51c67a1befbb`.
- Actual complete no-proxy LAN HTTP GET200 matches local APK hash and size. Existing9010 server still serves `/root/share/comfychair`; no service/network changes.
- aapt: package `sh.hnet.comfychair`, `v0.8.26-beta.7`, code61. Previous distributed beta6 is code60. Both apksigner-valid; same Android Debug certificate SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`. Not production signing. Next distributed versionCode must exceed61.

## Root causes / changes
- SettingsNavHost always rendered BottomAppBar/FAB and scaffold padding, including gallery route. Route policy now suppresses chrome and inherited insets for gallery; gallery owns safeDrawing union IME once.
- Gallery added title/back/key row before tabs; shared panel added duplicate source/count/header row. Tabs now first, no explicit back/close control in standalone gallery or viewer. Search/sort/refresh/info one row; counts/help/capability/key/LAN setup in optional bounded scrollable panel. Original picker remains modal with source/slot header. Multiselect replaces gallery search/info row; actions48dp/localized, confirmation/results retained. Grid uses remaining weighted space; duplicate inner grid padding removed only for standalone.
- Delete success called onRefresh(), replacing multiple loaded pages with first page (even without a changed session). Now shared panel captures stable visible descriptor and pixel offset, removes only successes and restores first surviving visible anchor/nearest neighbor; numeric fallback subtracts only successful preceding deletions and clamps. Empty list resets0; failed items remain. Controller removes in place retaining pages/query/sort/session/cursor, invalidates in-flight list revision without resetting session, cancels refresh/append and rejects noncooperative late responses. Legacy Input local boundary adjusts to avoid skipping items. Both gallery and generation popup wired; existing targeted disk invalidation unchanged. No re-download of historical pages to restore.
- Preview conditional removed ServerImagePickerPanel from composition; on return new rememberLazyGridState and initial LaunchedEffect(session) scrollToItem(0) ran. Panel now stays composed under opaque viewer; hidden preview requests/pagination pause and hidden semantics clear. System back closes viewer without picker.open/refresh; gallery exit still pops to Settings, source/query changes reset session/top, one-shot use-as-source remains unchanged. No internal pager added.

## Execution / limits
- Java17, SDK `/root/Android/Sdk`, actual Gradle8.13. Final exact command: `./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=gallery-space`.
- BUILD SUCCESSFUL in6m27s. R8 minify, resource shrink/optimize and release packaging actually executed. No main/test edits during final build. Mapping files archived as gzip.
- XML164 tests,0 failures/errors/skipped,26 suites. Added13 tests:4 layout/navigation/security source/state contracts and9 anchor/controller/preview policy/wiring cases. Existing151 retained. Covers success-only offset, failed retention, anchor/neighbor/fallback, empty/end deletion, loaded pages/cursors, stale refresh and append, owner/session/source/query guards, legacy Input continuation and preview retained composition/policy. Source/policy tests are not Compose gestures.
- Initial red compile expected missing SettingsChromePolicy. First full build161 tests/1 failed source contract because hidden-view visibility expression changed; corrected by separating visibility from request pausing, then expanded tests and reran entire requested final command. Logs retained.
- `adb devices -l` empty. No rendered/device proof, narrow/large-font/IME gestures, Android Keystore, install or post-R8 runtime validation. No live remote API requests/deletions/generation, no server files/configuration/restarts; installed server extension remains independent.
- Disk initially13MB. Losslessly gzip-compressed prior code57/58/59/60 mapping.txt, verified round-trip bytes before removing uncompressed duplicates; historical APKs/source/stash untouched. Final build completed.
- No commit/push/tag/Release; prior dirty/untracked project skills and stash preserved. Final git diff --check clean.

## Files for this follow-up
- `app/build.gradle.kts`
- `app/src/main/java/sh/hnet/comfychair/ui/navigation/SettingsNavHost.kt`
- NEW `app/src/main/java/sh/hnet/comfychair/ui/navigation/SettingsChromePolicy.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/screens/ServerGalleryScreen.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/screens/ImageToImageScreen.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/components/ServerDeletionControls.kt`
- `app/src/main/java/sh/hnet/comfychair/viewmodel/ServerGalleryViewModel.kt`
- `app/src/main/java/sh/hnet/comfychair/viewmodel/ImageToImageViewModel.kt`
- `app/src/main/java/sh/hnet/comfychair/viewmodel/ServerImagePicker.kt`
- NEW `app/src/main/java/sh/hnet/comfychair/viewmodel/ServerGalleryAnchorPolicy.kt`
- `app/src/main/res/values/strings.xml`, `values-zh/strings.xml`
- `app/src/test/java/sh/hnet/comfychair/ServerImagePickerUiContractTest.kt` (descriptor-filtered list wiring)
- NEW `app/src/test/java/sh/hnet/comfychair/ServerGalleryLayoutContractTest.kt`
- NEW `app/src/test/java/sh/hnet/comfychair/ServerGalleryPositionTest.kt`
- `README.md`, `README.zh-CN.md`, `task_plan.md`, `docs/requirements/lan-deletion-handoff.md` (historical pointer)
- NEW `docs/requirements/gallery-layout-v0.8.26-beta.7.md`, this handoff.

Evidence `/root/comfychair-artifacts/code61/`: APK, before snapshots (initial UI/doc targets), output-metadata.json, aapt/beta6-aapt, certificate/beta6-certificate, build.log/build-final.log/test-red.log, test-results XML, mapping/*.gz, verification.json, final-git-status.txt/final-git-stash.txt, changed-files.json.
