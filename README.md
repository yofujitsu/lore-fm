# Ninefold

A visual Top 9 of your favorite music releases. See [SPEC.md](specs/SPEC.md) for the full product specification.

Implemented: milestones 1–9 — Spotify and MusicBrainz search, combined ranking and deduplication, bounded partial-failure handling, React Top 9 editor, themes, nickname, local drafts, sharing and browser-side PNG export.

## Run locally

Requirements: Java 21 and Node.js 22.12+. Backend: `F:\java_pr\lore-fm`. Frontend: sibling directory `F:\java_pr\lore-fm-frontend`.

In the first PowerShell terminal:

```powershell
cd F:\java_pr\lore-fm
# Copy .env.example to .env and fill in SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET.
.\mvnw.cmd spring-boot:run
```

Use application credentials from the Spotify Developer Dashboard. The backend reads `.env` from its working directory at startup using [Spring config import](https://docs.spring.io/spring-boot/reference/features/external-config.html). Use unquoted `KEY=value` lines. Environment variables set in PowerShell or the IDE take precedence. Restart the backend after changing credentials or settings.

Keep credentials in the backend. Never put them in the frontend or Git. Without Spotify credentials, MusicBrainz search remains available without authentication. Spotify user login is unnecessary. MusicBrainz requires an identifying User-Agent with a developer contact; configure `MUSICBRAINZ_USER_AGENT` for your own deployment.

In the second terminal:

```powershell
cd F:\java_pr\lore-fm-frontend
npm.cmd ci
npm.cmd run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173). Vite proxies `/api` to `http://127.0.0.1:8080`.

If those ports are occupied, run the backend with `-Dspring-boot.run.arguments=--server.port=8081`. For the frontend, set `$env:NINEFOLD_API_TARGET = 'http://127.0.0.1:8081'` and run `npm.cmd run dev -- --port 5174`.

## Docker and HTTPS

Current deployment: [Ninefold](https://ninefold.213.155.15.217.sslip.io/). The VPS Compose directory is `/opt/ninefold`; commands there use `--env-file .env.deployment`. Its backend `.env` enables Spotify and MusicBrainz. Changing server settings requires recreating the backend with `docker compose --env-file .env.deployment up -d --no-build --wait`.

Requires Docker with Linux containers and Docker Compose 2.30+. Keep `lore-fm` and `lore-fm-frontend` as sibling directories when building. Both images run their tests during the build; the runtime frontend contains only the compiled app and Caddy.

From the backend directory:

```powershell
docker compose up -d --build --wait
.\scripts\smoke-deployment.ps1
```

Open [http://localhost:8090](http://localhost:8090). Docker uses the backend `.env` as runtime environment variables; it is excluded from both build contexts. The backend has no published port. Caddy serves the frontend and forwards `/api` on the same origin. Direct visits and reloads at `/top/<payload>` work, including the maximum 8000-character payload. Missing compiled assets return 404.

For a VPS, copy `deploy.env.example` to `.env.deployment` and replace `NINEFOLD_SITE_ADDRESS` with your hostname, without a scheme or path. Point its DNS A/AAAA records at the VPS and allow incoming TCP ports 80 and 443. Only publish an AAAA record if IPv6 reaches the server. Start with:

```sh
docker compose --env-file .env.deployment up -d --build --wait
```

[Caddy manages HTTPS certificates and HTTP redirects](https://caddyserver.com/docs/automatic-https). Its certificate data survives container replacement in the `caddy_data` volume; preserve that volume. Shared links automatically use the HTTPS origin. Local drafts stay tied to their original browser and origin; use a share link to bring a local board to the public site.

Without a purchased domain, an address such as `ninefold.YOUR-VPS-IP.sslip.io` resolves to the embedded IP without registration. The [sslip.io service](https://nip.io/) also supports certificates for individual hostnames. A custom domain can replace it by changing `NINEFOLD_SITE_ADDRESS` and restarting Compose.

On a 1 GB VPS, build on your development machine and transfer the images instead of building on the server:

```powershell
docker compose build
New-Item -ItemType Directory -Force target | Out-Null
docker save -o target/ninefold-images.tar ninefold-backend:local ninefold-frontend:local
# Transfer the archive, compose.yaml, .env.deployment and the backend .env over SSH.
```

```sh
docker load -i ninefold-images.tar
chmod 600 .env
docker compose --env-file .env.deployment up -d --no-build --wait
docker compose --env-file .env.deployment ps
```

The backend container is capped at 512 MB, with the JVM heap capped at 65% of that limit; Caddy is capped at 128 MB. Provider rate limiting remains per backend process, so run one backend replica. Existing swap can help a small VPS during startup. Use `docker compose logs --tail=100` for diagnostics and `docker compose down` to stop this app while keeping certificate volumes. Avoid `down -v` unless you intend to delete those volumes.

After publication, run `scripts/smoke-deployment.ps1 -BaseUrl https://YOUR-HOSTNAME` from PowerShell. Check a shared link in another browser and on your phone, then check search, drag/Move controls and PNG export. The script verifies static files, long shared routes and the API proxy without depending on external catalog availability.

## Edit your Top 9

Search for a release and click **Add** to fill the first empty slot. Select a slot before adding to place or replace a release there. Each release ID can appear once. Different editions with different IDs are separate releases.

Remove a release with its **×** control. Drag a **Move** handle to another slot to swap positions. Mouse and touch are supported. For keyboard movement, focus a Move handle and use arrow keys; alternatively, click Move and select a destination.

Set an optional nickname (up to 40 Unicode characters) and choose Dark or Light. Partial boards are supported. Edits save automatically to `localStorage` under `ninefold.board.v1` and restore on reload. Drafts belong to this browser and origin; ports 5173 and 5174 have separate drafts.

**Undo** restores the previous release or nickname change, including adding, replacing, moving, removing, clearing and loading a saved board. Theme changes do not enter history, and Undo preserves the current theme. One undo step is kept in this tab and resets on reload. **Clear board** asks for confirmation and empties all nine slots while keeping the nickname and theme; the clear can be undone.

**Saved boards** stores up to 20 named local snapshots under `ninefold.boards.v1`. Save a new snapshot, load it into the current draft, update it explicitly, rename it or delete it with confirmation. Editing the draft never silently changes a saved snapshot. Names are limited to 40 Unicode characters and must be unique. Unreadable libraries remain stored until a confirmed reset; storage failures keep existing snapshots.

Use **Release type** and **Release year** to filter the current search results. Year matches exactly; releases with an unknown year are excluded when a year is selected. Filters stay selected for subsequent queries, and **Clear filters** restores the complete returned result set. Filtering does not make additional catalog requests or change your board.

Unreadable drafts are left intact until you edit a new board. Storage failures display a warning while the editor remains usable.

## Sharing and PNG export

Click **Share board** to create a compressed, versioned snapshot link. The app attempts to copy it; a read-only field and **Copy link** remain available when clipboard access is blocked. Board edits invalidate the previous link. Shared pages at `/top/<payload>` display metadata and artwork without searching providers, are read-only, and do not read or overwrite the viewer's own draft.

The snapshot preserves all nine positions, including empty slots. Limits: 8000 encoded characters and 64 KiB of inflated UTF-8 JSON. Malformed payloads, unsupported versions and unsafe artwork/source URLs are rejected; decoded text has control characters removed.

**Download PNG** exports a 1440×1980 image with Ninefold branding, the nickname, theme and uncropped artwork, with the release title and artists beneath each cover. Long captions end with an ellipsis to fit their column. Empty slots and unavailable covers use placeholders. Blocked/missing covers are reported after export. Covers load anonymously with CORS and a five-second maximum wait. PNG export also works on shared pages. Add at least one release to enable sharing/export.

These features use the native [Compression Streams API](https://developer.mozilla.org/en-US/docs/Web/API/Compression_Streams_API) and [Canvas](https://developer.mozilla.org/en-US/docs/Web/HTML/How_to/CORS_enabled_image), with no additional runtime packages. Named snapshots use native Web Locks to serialize writes across tabs and require HTTPS or localhost. Use a current browser. Clipboard failure has a manual-copy fallback.

A localhost link only opens on the same machine. Deployed links use the current origin. The production frontend server must serve `index.html` for `/top/*` paths as well as the homepage, while forwarding `/api` to the backend.

## Search API

```http
GET /api/v1/releases/search?q=cold%20visions&limit=10&providers=SPOTIFY
```

- `q`: 2–200 Unicode characters after trimming boundary whitespace.
- `limit`: 1–20; default 10.
- `providers`: optional comma-separated enum names; omitted means all connected providers.
- Invalid values return HTTP 400 with `{ "code": "...", "message": "..." }`.

Responses contain `query`, normalized `results`, `partial` and `unavailable`. Artwork and release year can be null. `partial: true` means some enabled sources failed but at least one responded; available results remain usable. `unavailable: true` means no selected source could respond. A healthy empty search has both flags false. All three cases return HTTP 200 without internal provider errors; the UI distinguishes them.

Enabled catalogs are searched concurrently within a two-second global deadline. Results are compared using Unicode normalization, case folding and punctuation/whitespace normalization, while original display text is preserved. Matching title and primary artist merge when known year/type are compatible. Edition suffixes and incompatible years/types remain separate. Spotify ID and metadata win a merge; missing artwork/year/type can be filled from MusicBrainz, and links to both sources remain available. Ranking favors query matches, then provider confidence, release type and stable source order; the limit applies after merging.

MusicBrainz searches release groups by literal title/artist tokens and supplies [Cover Art Archive](https://musicbrainz.org/doc/Cover_Art_Archive/API) front-cover URLs. Some release groups have no cover; the existing artwork and PNG placeholders handle missing images. MusicBrainz calls are serialized per backend process, with a one-second wait after completion to guarantee outbound spacing even on cold startup. Its 1200ms budget includes client preparation and rate-limit waiting, so rapid uncached searches can yield partial results. Multiple backend replicas would need a shared limiter.

Spotify currently allows at most 10 results per search request. The adapter returns up to 10 without extra pagination.

## Backend configuration

| Setting | Default                                | Purpose |
| --- |----------------------------------------| --- |
| `SPOTIFY_ENABLED` | `true`                                 | Enable Spotify when credentials are present |
| `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` | empty                                  | Server-side application credentials |
| `SPOTIFY_MARKET` | `US`                                   | Search market |
| `SPOTIFY_REQUEST_TIMEOUT` | `1500ms`                               | Shared token + search deadline, including token lock wait; maximum 1500ms |
| `MUSICBRAINZ_ENABLED` | `true`                                 | Enable the public release-group catalog |
| `MUSICBRAINZ_REQUEST_TIMEOUT` | `1200ms`                               | HTTP and rate-limit wait budget; maximum 1200ms |
| `MUSICBRAINZ_USER_AGENT` | `Ninefold/0.1 (mailto:mail@gmail.com)` | Application identity and developer contact |
| `SEARCH_REQUEST_TIMEOUT` | `2s`                                   | Global provider deadline; maximum 2s |
| `SEARCH_FAILURE_COOLDOWN` | `30s`                                  | Circuit cooldown after three consecutive failures; maximum 1h |
| `search.cache.ttl` | `15m`                                  | Successful provider result cache TTL |
| `search.cache.maximum-size` | `1000`                                 | Maximum cached queries |

Access tokens are cached separately and refreshed with an expiry margin of up to 30 seconds. Successful searches, including empty results, are cached per provider/query/limit; simultaneous identical searches share the cache loader. Provider failures are not cached. HTTP 429/503 honors Retry-After (bounded to 1 second–1 hour, default 30 seconds); repeated failures open a circuit that permits one recovery probe after cooldown. Cached successes remain usable during outages. There are no automatic in-request retries; subsequent searches recover when cooldown expires, and Spotify 401 invalidates the cached token. No database, Redis or extra resilience library is required.

- Health: `/actuator/health`.
- Prometheus: `/actuator/prometheus`.
- OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.

## Verify

```powershell
cd F:\java_pr\lore-fm
.\mvnw.cmd -B verify
cd F:\java_pr\lore-fm-frontend
npm.cmd test -- --run
npm.cmd run build
```

Backend tests use local JDK HTTP servers and never contact external catalogs. They cover mapping, aggregation, rate limits, deadlines, cache coalescing and circuit recovery. Frontend tests cover partial/unavailable search states, source links, search races, board editing, drafts, keyboard movement, storage failures, shared payload validation, readonly views, clipboard fallback and PNG rendering.

## Test without Spotify credentials

Start the explicit loopback-only mock:

```powershell
cd F:\java_pr\lore-fm
node scripts/spotify-mock.mjs
```

In another backend terminal, set test-only environment variables:

```powershell
$env:SPOTIFY_CLIENT_ID = 'local-test-id'
$env:SPOTIFY_CLIENT_SECRET = 'local-test-secret'
$env:SPOTIFY_API_BASE_URL = 'http://127.0.0.1:9091/v1'
$env:SPOTIFY_TOKEN_URL = 'http://127.0.0.1:9091/api/token'
$env:MUSICBRAINZ_ENABLED = 'false'
.\mvnw.cmd spring-boot:run
```

Run the frontend normally. Most queries return fixture releases; `empty` returns no results, `error` triggers HTTP 500 from the mock, and `timeout` delays the response. Stop the mock and remove these test variables before live use.

Provider documentation: [Spotify Search](https://developer.spotify.com/documentation/web-api/reference/search), [Client Credentials](https://developer.spotify.com/documentation/web-api/tutorials/client-credentials-flow), [MusicBrainz Search](https://musicbrainz.org/doc/MusicBrainz_API/Search), [MusicBrainz rate limits](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting).

