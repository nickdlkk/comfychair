# Standalone server gallery + safe deletion — beta.5 / code59

## Approved scope

Implement Settings-independent server Input/Output gallery, retain quick picker and shared caching/pagination. Add real physical file deletion via a separately installable ComfyUI `custom_nodes` route plugin, not a workflow node. Local implementation/build/share only; NO Git commit/push/tag/Release, deployment/restart, real server mutations or deletion of user media.

## Acceptance implementation

- Settings Application → Server gallery → separate `server_gallery` navigation route. Input/Output tabs; existing server assets search/sort/refresh, legacy scope explicitly retained; 3-column shared panel/grid and infinite pagination/sparse recovery. Original downloads only on tap, encoded32MiB / decoded16MP guards; zoomable full viewer filename/dimensions/actual encoded-byte count.
- Phone save via MediaStore pending/rollback PNG; material export through existing library; source slot1 via one-shot process-local ticket and final exact server/client/auth epoch checks before atomic prepared-bitmap publication. Clears compression/masks/direct-source provenance, navigates existing main host to Image-to-image without restarting/wiping state. No claim of direct original reuse for this import.
- Existing picker preserved; both use same long-press multi-selection controls, only enabled when companion capability authorizes deletion. Max50 explicit; selected count/select loaded/clear/cancel; irreversible source/count confirmation; per-item outcomes; no automatic mutation retry or redirect.
- Dedicated deletion key: separate Keystore EncryptedSharedPreferences fail-closed storage, no plaintext fallback, excluded OS backup/transfer, never in saveable UI state. HTTPS required for nonloopback credential delivery (HTTP browsing unaffected). Existing project TLS/self-signed trust limitations remain.
- Companion `server/comfychair_media`: capabilities/missing/prepare/delete, 32KiB bounded streamed request body, max50 manifest, random256-bit opaque tokens principal/stat/descriptor-bound TTL120s, recorded idempotent results10min, bounded256 operations. Linux dirfd/O_NOFOLLOW every root/subdirectory component; image extension allowlist, regular files and nlink1 only, no recursion/traversal/symlink.
- Actual queue mutex held through busy/stat/journal/unlink; any pending/running job refuses. Late-bound prompt_queue adapter for ComfyUI custom-node import timing.
- Durable intent tombstones hide matching descriptors, without altering assets DB/history/shared previews. Exact thumbnail and source-list version invalidation rejects old-generation publication; upload cache, selected editing bitmap and snapshots untouched. Existing DirectInput missing404→upload tests retained.

## Explicit limits

No universal external-writer TOCTOU guarantee: POSIX check/unlink is not atomic inode-conditional; administrator must exclude external media mutation during deletion. Queue lock only coordinates code using actual queue mutex; unrelated uploads/plugins/readers aren't locked. Journaling failure or crash can hide still-existing/recreated names. Tombstones max5000 require administrator reconciliation/reindex; other clients/history/assets still see stale records. Cached first page may briefly appear before network tombstone refresh. No verified stable assets missing mutation API was assumed and no DELETE/assets used.

Installation/auth proxy/firewall/TLS and service restart require separate deployment approval; see `server/comfychair_media/README.md`. Current live service capability was probed GET only and returns404: deletion remains unavailable there. No handset present; JVM/source contracts/build are NOT gesture/Keystore/codec/R8 runtime verification.

## Verification/evidence

Evidence and release artifacts are stored in `/root/comfychair-artifacts/code59/`; final measured results, package/certificate metadata, HTTP full-GET hashes and exact changed paths are recorded in `server-gallery-handoff.md` after the full build. Existing dirty changes, untracked project skills and stash are retained.
