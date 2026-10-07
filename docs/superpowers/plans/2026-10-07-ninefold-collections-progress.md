# Named boards and editor fixes — completed

User authorized continuing the roadmap, fixing Undo and adding release titles/artists beneath every PNG cover. Implemented roadmap stage 6: up to 20 named browser-local snapshots with save, load, explicit update, rename and confirmed deletion. Working drafts and snapshots remain independent. Reused existing board validation and storage conventions; no dependencies added.

Undo excludes theme-only changes, including loading a serialized snapshot whose release values are unchanged. Undo restores release/nickname content while preserving the current theme. Regression tests reproduced both failures before the fixes.

PNG exports are 1440×1980 with uncropped covers and title/artist captions under each populated slot. Captions fit their columns and truncate at grapheme boundaries with native Intl.Segmenter. Missing artwork retains captions and the existing fallback.

Review findings addressed: value comparison for theme-only snapshot loads; native Web Locks around library writes and reset, with a fresh storage check inside the lock; grapheme-safe caption truncation. Unsupported secure storage features, corruption, quota failures and concurrent changes keep existing snapshots. Backend catalog behavior, URL payload encoding and cross-device persistence were outside this increment; existing deployment smoke checks verified integration.

Verification: final frontend Docker build passed 56 tests, TypeScript checking and Vite production build. Local and public deployment smoke checks passed: homepage, long shared routes, compiled assets and API proxy. Browser save/load/rename/delete-confirmation and Undo checked in the isolated preview. Public theme changes left Undo disabled. At 320 px, document client/scroll widths both 305 px; saved-board buttons stayed within the viewport and were 44 px high.

Actual browser Canvas export rendered nine populated slots, 1440×1980, with zero missing covers. Screenshot proof: target/collections-png-captions.jpg. Browser automation did not expose a downloaded file; raster rendering and dimensions were verified directly. Mobile proof: target/collections-public-mobile.jpg.

Deployed the frontend image to the existing VPS after SHA256 verification, preserving the backend and persistent HTTPS volumes. Prior frontend retained as ninefold-frontend:before-collections-20261006 for rollback. Public URL: https://ninefold.213.155.15.217.sslip.io/.

Next roadmap stages: SoundCloud/YouTube support; custom artwork and PNG presentation variants. Spotify URL import was removed from the roadmap at the user's request. Genius enrichment remains a later spec extension. No commits, pushes or changes to the user's staged initialization files.

Final continuation on 2026-10-07: public smoke check and mobile layout passed. The user checkpointed the implementation in backend d87246d and frontend 0c79212. Preserved those commits and staged files; the diagnostic png-verification.html is now tracked by that checkpoint and was retained. Local Docker Desktop is offline, so the temporary Docker preview is unavailable; no restart of the user's Docker environment was attempted.
