# Changelog

## 1.0.0 — 2026-10-07

First stable Ninefold release. Pick nine music releases, make a board for your mood or era, share it with friends.

- Combined Spotify/MusicBrainz search, ranking and compatible-release deduplication, with provider source links, caching, bounded requests and partial-failure handling.
- Nine-slot editor with add, replace, remove, drag/touch/keyboard swaps, duplicate protection and one-step Undo that preserves the current theme.
- Optional board name and nickname, local draft autosave and up to 20 named saved snapshots.
- Release-type and exact-year filters applied to returned results.
- Validated compressed snapshot links, readonly shared pages and clipboard fallback.
- Native 1440×1980 PNG export with uncropped artwork, release/artist captions, owner-and-board headings and missing-cover fallbacks.
- Slate/lavender dark and cool light palettes, desktop/mobile layouts and typed music prompts with pause and reduced-motion support.
- Developer GitHub link and Spotify/MusicBrainz attribution in the UI and export.
- Docker Compose deployment, Caddy HTTPS, smoke checks and live DuckDNS hosting.
- Optional SoundCloud sets/playlist and YouTube video adapters for explicit API calls; disabled until configured and hidden from normal UI searches.
- MIT project license, third-party notices and Contributor Covenant 3.0 Code of Conduct.

Application version is 1.0.0. Board, saved-library and share schemas remain at version 1; existing snapshots stay compatible. There are no user accounts, server-stored boards or public discovery feed in this release.
