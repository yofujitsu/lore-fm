# Ninefold sharing and PNG continuation

User tested milestones 3–4 and asked to continue in this chat. Implement existing SPEC.md sections 28–34 and milestones 5–6. UI and READMEs stay English. Preserve the user's Git index and unrelated files.

Design:
- Versioned compact SharedBoardV1 DTO retaining nine slots, all visible release metadata, artwork and source links; no provider re-search on open.
- Native gzip CompressionStream/DecompressionStream and base64url. Route /top/<payload>, without a router dependency. Limit encoded payload to 8000 characters and inflated UTF-8 JSON to 64 KiB; validate structure/version, text and HTTPS URLs through existing board validation. Reject corrupted or excessive input, including decompression bombs.
- Shared pages are read-only and never load or save the viewer's local draft. A homepage link returns to their own editor.
- Share action generates a snapshot link, supports clipboard with a visible manual-copy fallback and opening the link. Changes invalidate generated links; stale async results cannot display an old link.
- Canvas PNG at 1440×1740, theme colors, Ninefold branding, optional nickname and uncropped 3×3 artwork grid. CORS-anonymous image loading with a bounded wait; placeholders for missing or blocked covers, and visible fallback count. Export available from editor and shared view. No extra product dependencies.
- At least one selected release is required to share/export; incomplete boards retain empty positions.

Steps:
- [x] Write and run failing codec/security checks, then implement.
- [x] Write and run failing readonly/shared-page and share-action checks, then implement.
- [x] Write and run export checks, implement canvas rendering and downloads.
- [x] Update English READMEs, run frontend tests and build.
- [x] Independent review and fixes, then browser verification: sharing roundtrip, viewer draft preservation, invalid links, PNG download and visual inspection, both themes and mobile.

Verified on 2026-10-05:
- Frontend: 35 tests passed; TypeScript and Vite production build passed.
- Independent review found no actionable issues.
- Real Spotify covers round-tripped through a 548-character share URL; clipboard copying succeeded. Shared views retained nine positions and source links. Returning from shared and malformed links preserved the editor draft.
- Mobile shared view at a 390-pixel viewport had no horizontal overflow.
- Real PNG downloads from the shared view and editor were visually checked in dark and light themes. Both files are 1440×1740; all three Spotify covers loaded without fallback. Preview artifacts: target/ninefold-preview.png and target/ninefold-preview-light.png.
- UI and both READMEs contain no Russian text. Git whitespace check passed excluding the user's existing SPEC.md trailing spaces. No Git index changes or commits.

Backend search is unchanged. MusicBrainz and aggregation remain the following milestones.

