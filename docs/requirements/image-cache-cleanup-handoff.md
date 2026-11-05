# beta.3 / code57 cleanup + statistics handoff

Subsequent local beta.4/code58 fixes ordinary picker scrolling; see [picker handoff](picker-scroll-handoff.md). This file remains historical beta3 evidence.

## Delivered
- Download <http://192.168.4.168:9010/comfychair-v0.8.26-beta.3-release-cache-settings-20261104-1940.apk>. Full no-proxy LAN GET200, **8293645 bytes**, SHA256 `dc53c6ef55fc2849a911439f18752630d549415a7052ab86b8cd48225fb5442d`; matched built/shared APK. Reused existing server9010 PID3938536 (`--directory /root/share/comfychair`), did not restart/kill it.
- APK metadata+aapt: `sh.hnet.comfychair`, `v0.8.26-beta.3`, code57. Same Android Debug certificate as beta2, SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c`; not production signing. Incremented once56→57.
- Default automaticON/64MiB/7days; validated1–1024MB/1–365days; live preference apply. Creation-age OR byte cap evicts oldest-created, deterministic name tie; autoOFF only independent hard1GiB/8192-entry safety cap, 4MiB per-entry maximum. Freshness24h lists/thumbs and7d descriptors still checked but stale TTL alone not deleted by managed cache. Opportunistic get/put/apply, no timer.
- Manual inline3-second two-click confirmation, busy/progress/result/error; all feature namespaces only, key preserved, no server deletion. Clears current upload memory and picker previews/list; generation prevents pre-clear IO repopulation without changing Direct Input epoch/provenance or selected sources. No bitmap recycle. Does not call old broad clearCache.
- Added actual IO checksum-valid cache size/count scan; formatted+raw bytes, count valid entry files, includes44-byte entry headers, excludes secret/temp/unrelated/corrupt; freshness-stale retained valid entries count. Enter card/apply/clear refresh; refresh button for later automatic changes; loading/error feedback.

## Verification
- `COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36 ./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=cache-settings`: **BUILD SUCCESSFUL in9m5s**, R8 and shrink/optimize resources executed. XML **131tests/0failures/0errors/0skipped**,12 new cleanup tests (previous119 retained). Real temp dirs: policy adapter persistence/default/corruption, validation, age boundary/size OR, creation versus read order/ties, live edits, off mode, manual scope/key, generation writes, concurrent clear/read/write, actual byte/count exclusion and zero after clear; MockWebServer proves upload memory/in-flight invalidation. Android SharedPreferences commit itself not instrumented.
- Live readonly XML: INPUT50+50/4651 assets/search474; OUTPUT50+46/2221 assets/search142; preview/content GET passed. No live POST/DELETE/generation. Prior Direct Input HEAD/fallback/cancellation tests retained/passed.
- Evidence `/root/comfychair-artifacts/code57/`: `build.log`, initial failed build log, matching `mapping/`, `test-results/`, `output-metadata.json`, `verification.json`. Initial build test compilation saw adapter methods added after main compile began; reran after all code edits, no main/test writes during final successful build.
- `adb devices -l` empty: no device install/Compose gesture/layout/Android codecs/R8 runtime verification. Root reports100% nonroot available but roughly4GiB privileged free; no destructive cleanup needed.

## Files changed in this task
Under `app/src/main/java/sh/hnet/comfychair/`: cache/PrivateImageDiskCache.kt, cache/ServerImageListDiskCache.kt, ComfyUIClient.kt, connection/ImageUploadReuse.kt, storage/AppSettings.kt, viewmodel/SettingsViewModel.kt, viewmodel/ImageToImageViewModel.kt, viewmodel/ServerImagePicker.kt, ui/screens/ApplicationSettingsScreen.kt.
New main files: cache/ImageCachePolicy.kt, cache/PersistentImageCache.kt, ui/components/ImageCacheSettings.kt.
Also app/build.gradle.kts, both values/values-zh strings.xml, both READMEs; new test app/src/test/java/sh/hnet/comfychair/ImageCacheCleanupTest.kt; new docs image-cache-cleanup-v0.8.26-beta.3.md and this handoff; appended pointer to previous persistent-input-cache-handoff.md.

All previous dirty/untracked implementation preserved, including six .agents/skills dirs and original stash@{0}. No git commit/push/tag/Release. Previous handoffs remain historical beta2 evidence.
