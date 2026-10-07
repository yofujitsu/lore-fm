# Ninefold interface redesign

User-approved scope: simplify public search to Spotify and MusicBrainz, replace the platform menu with automatic search, and redesign the editor, readonly share page and exported PNG together.

Implemented:

- Larger desktop board beside a compact sticky search panel; mobile stacks search above the board.
- Rounded controls, larger text, slate/coral dark theme and warm light theme.
- Short English copy, captions below artwork, settings below the grid.
- Shared header with the existing Ninefold identity and developer GitHub link; centered Spotify/MusicBrainz footer credits and bundled SVG assets.
- Matching native Canvas PNG layout, larger captions and provider logos.
- Default backend search excludes optional providers even when their adapters are enabled; explicit API selection remains available.
- Existing Undo, drafts, snapshots, share validation and keyboard/touch editing are preserved.

Verification:

- Backend Maven verify: 44 tests passed.
- Frontend: 58 tests passed; TypeScript and Vite production build passed.
- Read-only review: no critical/important findings; reported trailing whitespace removed.
- Real Spotify search and nine populated slots verified in the browser.
- Both themes inspected. Editor and shared page verified at 320px with no horizontal overflow.
- Keyboard swap followed by Undo restored release order and kept the light theme.
- Actual Canvas PNG rendered at 1440x1980, with nine covers, release captions and both provider logos; zero missing covers.

Deployed to the existing VPS at `https://ninefold.213.155.15.217.sslip.io/` using the tested JAR and compiled frontend assets. Runtime archive SHA256: `9c3f019d744ffcabf6161c07d3f32724195bd033ef23f94892543ab0fbceaf60`.

- Backend image: `20e042adf49a`; frontend image: `3e977b5a6ae4`.
- Previous images retained as `ninefold-backend:before-redesign-20261007` and `ninefold-frontend:before-redesign-20261007`.
- Compose readiness and public smoke checks passed: homepage, long share routes, compiled assets and API proxy.
- Both bundled brand SVGs return HTTP 200 with the SVG content type.
- First cold request exceeded provider deadlines on the 1-vCPU VPS. Subsequent Spotify-only, MusicBrainz-only and default searches each returned 10 results with `partial=false` and `unavailable=false`; the default response contains only Spotify/MusicBrainz sources.
- Live browser verified automatic search, the new header/footer and a readonly populated share page.
- Backend healthy, frontend running; app memory approximately 210 MiB. Existing Amnezia containers remained running.

The existing hostname remains in use until the owner registers a new DuckDNS name. README contains the availability and migration steps.
