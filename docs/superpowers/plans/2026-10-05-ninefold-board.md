# Ninefold board and persistence continuation

The user confirmed live search works and asked to continue in this chat, with English UI and READMEs. Continue the existing SPEC.md milestones 3–4; preserve backend behavior, existing files and Git index.

Design: nine fixed slots; add to the first empty slot, or a selected slot for replacement. Prevent duplicate release IDs. Remove leaves an empty slot. Reordering swaps slots, including empty destinations. Pointer dragging works from a dedicated handle on mouse/touch; arrow keys on that handle provide keyboard movement. Nickname: optional, 40 Unicode code points. Dark/light theme. Save version 1 drafts under ninefold.board.v1, restore on reload. Reject malformed/oversized/unsupported saved data safely and surface storage failures. No database or new dependencies.

Steps:
- [x] Translate search UI, accessibility labels and document language to English; update search tests.
- [x] Translate both READMEs.
- [x] Write and run failing checks for board mutations and persistence validation.
- [x] Implement board state and safe local storage.
- [x] Write and run failing UI checks for add/replace/remove/reorder/theme/nickname/reload/storage failure.
- [x] Connect board editor to existing search and style both themes responsively.
- [x] Run tests/build, independent review and browser checks on live search, editing, reload, pointer drag, keyboard and mobile layout.

Sharing, PNG, MusicBrainz and aggregation remain the following milestones. No commit or changes to the pre-existing index.

Verification: 19 frontend tests pass; TypeScript and Vite production build pass. UI sources, index.html and both READMEs have no Cyrillic text. Independent review findings were reproduced and fixed: post-drag Move activation, removal focus, invalid saved URLs, non-string saved themes. Browser checked live Spotify add/replace/remove, keyboard and mouse drag into empty slots, first Move activation after drag, nickname/theme/order restoration after reload, dark/light rendering and 390px layout without horizontal overflow. Mouse pointer drag was exercised in the browser; touch shares the Pointer Events implementation and dedicated touch-action:none handle. Backend code was unchanged. Preview remains open at 127.0.0.1:5174.

Whitespace checks pass for changed sources and READMEs. Repository-wide git diff --check only flags the pre-existing two-space Markdown line breaks in SPEC.md metadata; the user specification was preserved.

