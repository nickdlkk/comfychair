# v0.8.25 Release shrinking / validation handoff

## Scope and version

User authorized commit/push/Release after first measuring shrinking. This implementation/build worker performed **no commit, push, tag or GitHub Release**; the publishing agent handles those steps. Existing unrelated `.agents/skills/`, stash and all earlier uncommitted features were preserved. `.github/workflows/build.yml` is owned by the publishing agent, not edited here.

Read actual beta version first: `v0.8.25-beta.1` / code53. Completed the first R8 experiment at that unchanged version, then bumped once to **v0.8.25 /code54**, application ID `sh.hnet.comfychair`. Release enables `isMinifyEnabled=true` and `isShrinkResources=true`; debug remains unminified. Existing debug signing configuration intentionally remains for update compatibility; no production certificate was invented.

## First actual experiment (before stable version bump)

- Command: `COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36 ./gradlew testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=release-shrunk-experiment`.
- `BUILD SUCCESSFUL in 5m50s`, 72 tasks. Unit tests were UP-TO-DATE here; the final stable build below explicitly cleans/reexecutes them.
- Experiment APK `app/build/outputs/apk/release/comfychair-v0.8.25-beta.1-release-release-shrunk-experiment-20261103-1449.apk`: **8,266,569 bytes**, SHA256 `2835ff0fd57a471d6ba49dc4dca9d8304e6c21cf2461472029a7ac45f6d4f008`.
- Log `/root/.hermes/profiles/dev/cache/scratch/comfychair-shrink-experiment-build.log`.

## Stable artifact and actual size result

| Metric | Distributed beta debug baseline | Stable shrunk Release |
|---|---:|---:|
| APK bytes | 86,933,230 | 8,266,557 |
| DEX files | 18 | 1 |
| DEX bytes (stored, uncompressed) | 84,110,828 | 6,456,040 |
| res/ + resources.arsc ZIP stored bytes | 2,465,234 | 1,545,084 |
| native library ZIP stored bytes | 37,392 | 37,392 |

**78,666,673 bytes smaller /90.49% reduction** relative to the explicitly requested distributed debug baseline. This is a **debug → optimized Release** comparison, including debug-only tooling removal and release optimizations, not an isolated R8-only A/B measurement against an unminified Release. The dependency includes material-icons-extended; actual R8 usage lists 11,404 removed `androidx.compose.material.icons.*` classes. No ABI split, language removal or compression trick was added.

- Baseline `/root/share/comfychair/comfychair-v0.8.25-beta.1-debug-compact-assets-picker-20261103-1425.apk`, hash verified `352c870dbdc1625341809c3efd2a8d78da699a20909e26af1126afb90528d5e8`.
- Stable APK `app/build/outputs/apk/release/comfychair-v0.8.25-release-shrunk-20261103-1455.apk`.
- Verified copy `/root/share/comfychair/comfychair-v0.8.25-release-shrunk-20261103-1455.apk`.
- **SHA256 `286414233c46e5a0a35b101e2ec0bb359a02a30f0b5be63f3b11b78f1cd42730`**.
- Download: <http://192.168.4.168:9010/comfychair-v0.8.25-release-shrunk-20261103-1455.apk>.
- Existing PID1433377 /9010 server reused unchanged, document root `/root/share/comfychair`; no 9011 changes. Complete no-proxy LAN GET returned **HTTP200 /8,266,557 bytes /same SHA256**. Local LAN success does not prove phone reachability.
- Metadata and SDK aapt agree: `sh.hnet.comfychair`, `v0.8.25`, code54. apksigner verify passes; same existing Android Debug certificate SHA256 `28a35c08467adf6be33e0ae772879c37680d4a5c5b3656be49d61d7bee74778c` as beta. Release is not marked debuggable by aapt. Upgrade compatibility metadata/certificate verified, not actual device installation/data retention.

## R8 boundary audit

Actual application uses explicit org.json parsing, not Gson/Moshi/reflective serializer models; no application JNI sources, class-name reflection or dynamic resource lookup found. Compose, Lifecycle, WorkManager, Media3 and OkHttp consumer rules are included in merged R8 configuration; default JNI method protection remains. Precise app rules retain ViewModel no-arg constructors and the persisted/reflectively instantiated GenerationCheckWorker class/constructor. Activity and connection/WebSocket subtype names remain readable for diagnostics. Source line/inner-class metadata and the diagnostic application/reporter boundary are preserved. No blanket keep-all, ignorewarnings or logging-stripping rules were added.

Final DEX contains `picker_open`, `RealtimeDiagnostics`, `/events`, `application/json`; mapping confirms diagnostic Application/reporter and worker retained, DebugLogger remains (renamed). The existing Logcat-disabled constant is unchanged; in-app opt-in logging and manual ZIP/realtime functions remain. R8 mapping/usage/resources/config/seeds exist under `app/build/outputs/mapping/release/` and were copied to dev scratch as `comfychair-release-{mapping,usage,resources,configuration,seeds}.txt`. Keep the matching mapping for retracing optimized fatal stacks (83,679,791 bytes mapping); do not assume another build's mapping matches.

## Final build and live readonly validation

Command (test task explicitly invalidated to avoid a false UP-TO-DATE live result):

```sh
COMFYCHAIR_LIVE_READONLY_HOST=192.168.4.36 ./gradlew cleanTestDebugUnitTest testDebugUnitTest assembleRelease --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' -Pkotlin.compiler.execution.strategy=in-process -PapkSuffix=shrunk
```

- **BUILD SUCCESSFUL in5m57s**, 73 tasks /30 executed /43 up-to-date. `testDebugUnitTest` executed, not cached. XML total **94 tests /0 failures /0 errors /0 skipped**.
- Actual current Kotlin client readonly GET tests at `192.168.4.36:8189`: Input first50 images /4638 assets /next50 /edit470; Output first46 images /2177 assets /next50 /edit125. Both sources exercised all4 sorts, search, after cursor, preview and asset-ID original GET. No generation or server mutation. Live endpoint has no authentication; MockWebServer covers auth headers, not a real authenticated deployment.
- Log `/root/.hermes/profiles/dev/cache/scratch/comfychair-v0.8.25-release-final-build.log`.
- Machine-readable verification `/root/.hermes/profiles/dev/cache/scratch/comfychair-release-verification.json`; aapt `comfychair-release-aapt.txt`; apksigner `comfychair-release-certificates.txt`; reusable verification script `comfychair-verify-release.py` in same scratch.
- XML `app/build/test-results/testDebugUnitTest/`; HTML `app/build/reports/tests/testDebugUnitTest/index.html`.
- Existing Kotlin condition/TabRow deprecation warnings only; first release experiment also reports unable to strip dependency `libandroidx.graphics.path.so` (packaged unchanged), not an R8 error. Release lintVital completed.

## Honest unverified boundaries

`adb devices -l` returned no attached devices. **Optimized Release runtime is unverified on Android**: startup, all generation modes, reflection/worker scheduling, touch/rendering, source decoding/disk publication, image/video viewer, settings persistence and covering beta with retained data need device smoke tests. Debug JVM tests and live unoptimized client HTTP tests cannot prove R8 runtime safety. No complete generation was requested/performed; no claim of fixing every historical login return. Full download was verified only from this host. Current assets/compact picker/diagnostics behavior is documented in both READMEs; older requirements sections are historical, superseded by this stable release record.
