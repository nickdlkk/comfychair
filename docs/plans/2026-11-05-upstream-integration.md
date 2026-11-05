# Upstream integration implementation plan

**Goal:** Isolate integration from stable main, inventory the fork's actual differences, and prepare small upstream contribution groups without pushing or publishing.

**Architecture:** Pin stable and upstream revisions; attempt the real merge first. Preserve behavior rather than choosing whole conflict sides. If the full architecture migration cannot be verified in the available environment, leave a clean, tested phase-A branch and an explicit migration handoff, not a false merge commit.

**Tech stack:** Kotlin/Compose, existing Gradle 8.13/Java17 Android toolchain for phase A; Python/aiohttp companion tests. Full upstream requests Gradle9.4.1/AGP9.2.1/JetBrains21.

## Acceptance and scope

- Main remains c43f2a6bbb39a2333b8f97e7eec8eb9bd3d0702a; existing stash and unrelated untracked files unchanged.
- Fetch and record origin/main, merge base and actual merge conflicts.
- No push, PR creation, tag, release, version bump, server changes, credential reads or toolchain installation.
- Preserve content upload reuse, Direct Input guards, private cache policies, slots1–4/bypass, settings gallery/deletion semantics, material library, dimensions/comparison, batch/front confirmation and notification lifecycle.

## Tasks

1. Pin baseline/upstream, create integration/upstream-20261105 and run `git merge --no-commit --no-ff origin/main`.
2. Inspect 45 conflicts and six modify/delete paths against the previous read-only report. Check storage/toolchain before allocating another build.
3. When full migration cannot be safely validated, record untouched attempt externally and abort before any hand-resolved changes exist; use phase A rather than claim upstream is an ancestor.
4. Cherry-pick independently isolated upstream stale-client, GGUF LoRA and gallery-current-index fixes, preserving original author attribution.
5. Add regression tests for wiring selection and persistence/application contracts. Run targeted JVM tests; record any failing assertion/compile output before changing implementation.
6. Port only app-managed stale editor protection and clear-cache workflow preservation from their broader upstream changes. Extend pure finalizer without losing Long/graph/PNG handling. Test wrappers, slots2–4, image markers, editable literals and malformed templates.
7. Run full `testDebugUnitTest assembleDebug` and companion pytest using discovered existing environments; record real XML totals and artifact hashes. No running build while modifying sources.
8. Generate inventory from `git diff --name-status <merge-base> <stable>`, not remembered features. Separate existing equivalents, portable fixes, optional UX and fork-specific operational artifacts.
9. Write FEATURE-INVENTORY.md, UPSTREAM-PR-PLAN.md and INTEGRATION-STATUS.md. Each PR group gets files, dependencies, requirements, tests, docs, license checks and acceptance caveats.
10. Commit explicit task paths on integration branch only, inspect main/stash/untracked invariants, conflict markers, final diff and pending-operation refs.

## Full-migration gates (not satisfied by phase A)

- Expand upstream WorkflowImages/GenerationParams with all source slots and bypass and port source provenance/snapshot/batch handling with golden tests.
- Route all fork auxiliary clients through ServerTrust; isolate Cookie auth/cache epochs with keyed HMAC namespaces; no plaintext credentials or accept-all workaround.
- Rewire activity-level navigation/viewer/settings without losing compact independent gallery, picker return target, material actions or physical deletion authorization.
- Define memory-only versus separately retained picker cache/material library and restore-defaults behavior before adopting upstream storage claims.
- Install/verify approved toolchain only after sufficient disk capacity exists, then debug/release/unit/Python/device/security tests. Until then full upstream remains pending.
