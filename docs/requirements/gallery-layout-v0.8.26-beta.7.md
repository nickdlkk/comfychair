# Standalone server gallery density and position — beta.7 / code61

## Accepted scope
- Input/Output tabs are first visible controls below safe status/cutout insets; no title/back row and no inherited Settings bottom navigation/FAB. Android system back closes preview first, otherwise returns to Settings.
- Normal gallery uses one search/sort/refresh/info row; optional scrollable info/key/LAN panel, max240dp, collapsed by default. Security configuration, transport warning, counts, source scope and capability availability remain reachable. Three-column weighted image grid consumes remaining space, without duplicate grid padding or idle footer. Touch actions retain48dp and existing EN/ZH labels.
- Multi-select toolbar replaces search/info instead of stacking; destructive confirmation and per-item outcomes remain. Original picker keeps modal source/slot header and shared grid/cache/discovery/auth.
- Successful deletion removes only successful descriptors from loaded pages without refresh/reopen/session reset. Keep source/query/sort, cursor and prior loaded window. Capture first visible surviving descriptor plus pixel offset; nearest surviving neighbor then clamped (old first index minus successful removals before it) fallback. Failed items stay. Empty list resets to0; end viewport naturally clamps to valid content.
- Cancel/invalidate in-flight refresh/append list revisions on successful deletion, retain pre-refresh pagination state, ignore stale callbacks and filter later pages against session tombstones. Legacy Input adjusts local slice boundary. No downloading historical pages to recover position. Exact owner/session guards and existing targeted disk/cache invalidation remain.
- Preview stays an opaque overlay over the still-composed gallery panel: preserve LazyGrid state/loaded pages/cursors/session/query across open/close; pause hidden preview requests and pagination. No open/refresh on preview back. Source/query changes still create a fresh session and reset to top. Using-as-source still leaves Settings using existing one-shot owner-bound ticket. No new preview internal pager was added.

## Verification
JVM controller/anchor/source-contract tests plus full cleanTestDebugUnitTest testDebugUnitTest assembleRelease/R8. No device attached: no rendered layout/gesture/IME/installation or post-R8 runtime claim. No remote server changes, live requests or media deletion. Version60/beta6 ->61/beta7 exactly once. Preserve all prior dirty/untracked project skills and stash; no commit/push/tag/Release.

See [delivery evidence](gallery-layout-handoff.md).
