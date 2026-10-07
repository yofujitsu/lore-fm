# Ninefold palette, board names and headline polish

Implemented the requested UI revision and published it at [ninefold.duckdns.org](https://ninefold.duckdns.org/).

## Changes

- Slate/lavender dark and cool light palettes across the editor, shared boards, theme metadata and native PNG export.
- Optional board name, limited to 40 Unicode characters, preserved in drafts, saved snapshots and compact share payloads. Older drafts and links remain valid. Loading an older snapshot without a board name uses its library name.
- A shared heading formatter for PNG and readonly pages: `yofujitsu's goth era`, `someone's goth era`, or the existing Top 9 fallback when no board name is set. PNG headings shrink to fit; release captions remain beneath the covers.
- Board name edits enter Undo history while theme changes remain excluded. Clear board preserves the name, nickname and theme.
- Seven typed homepage prompts with a pause control and native reduced-motion support. The accessible heading stays stable for screen readers.
- Result-list right padding of 14 px and stable scrollbar gutter. The observed Add-to-edge gap increased from 10.4 to 24.4 px. The Release type chevron sits 17 px inside the field, with sufficient text padding and native select behavior retained for forced colors.

## Verification

- Frontend regression suite: **64 tests passed** in nine files; TypeScript and Vite production build passed. Logs: `target/polish-green2.log`, `target/polish-build.log`.
- Review found no actionable issues.
- All seven full prompts were observed over complete browser cycles at 320 px and 901 px. Workspace position stayed constant during typing; 360 px was also checked. No horizontal overflow at these widths.
- Actual native 1440×1980 PNGs were generated with nine real covers and zero missing covers for both named-owner and anonymous-owner cases. Headings were visually verified.
- The published editor shows the new palette, animation, search results, spacing and optional board-name field. A real named share snapshot opens on the new domain as `yofujitsu's goth era`.
- Production smoke checks passed with normal TLS validation: homepage, compiled JavaScript, shared routes including an 8000-character payload, missing-asset 404 and backend JSON validation.
- Live default search returned ten releases from Spotify and MusicBrainz with `partial=false` and `unavailable=false`.
- HTTP redirects to HTTPS with status 308. The previous sslip.io alias still returns HTTPS 200.
- UI proof: `target/ninefold-polish-live.png` and `target/ninefold-polish-shared.png`.

## Deployment

- DuckDNS A record resolves to the VPS. Caddy configuration validated before frontend replacement and obtained HTTPS for the new hostname.
- Only the frontend was recreated. Backend image `20e042adf49a` remained healthy; existing VPN containers remained running.
- Frontend image: `7b01baa0ef65`.
- Runtime archive SHA-256: `acbee12dc8379cab054486aac4fc6324f9ef0c2f6363ccfce764dabc2f7ff19d`.
- Rollback frontend tag: `ninefold-frontend:before-polish-20261007`; deployment settings backup: `/opt/ninefold/.env.deployment.before-polish-20261007`.
- Both hostnames remain configured. Browser storage is separate by origin; existing local boards are still accessible at the old address. Share links can be opened on the new hostname.
- No secrets were included in the build archive. No commits or Git index changes were made.
