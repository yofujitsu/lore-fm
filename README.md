# Ninefold

A visual Top 9 of your favorite music releases. See [SPEC.md](specs/SPEC.md) for the full product specification.

Implemented: milestones 1–6 — release domain, Spotify search API, React search and Top 9 editor, dark/light themes, nickname and local draft persistence. Sharing and browser-side PNG export are included. MusicBrainz and aggregation are the next milestones.

## Run locally

Requirements: Java 21 and Node.js 22.12+. Backend: `F:\java_pr\lore-fm`. Frontend: sibling directory `F:\java_pr\lore-fm-frontend`.

In the first PowerShell terminal:

```powershell
cd F:\java_pr\lore-fm
# Copy .env.example to .env and fill in SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET.
.\mvnw.cmd spring-boot:run
```

Use application credentials from the Spotify Developer Dashboard. The backend reads `.env` from its working directory at startup using [Spring config import](https://docs.spring.io/spring-boot/reference/features/external-config.html). Use unquoted `KEY=value` lines. Environment variables set in PowerShell or the IDE take precedence. Restart the backend after changing credentials or settings.

Keep credentials in the backend. Never put them in the frontend or Git. Without credentials, the backend starts and returns empty search results, logging `Spotify search disabled or credentials not configured`. Spotify user login is unnecessary.

In the second terminal:

```powershell
cd F:\java_pr\lore-fm-frontend
npm.cmd ci
npm.cmd run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173). Vite proxies `/api` to `http://127.0.0.1:8080`.

If those ports are occupied, run the backend with `-Dspring-boot.run.arguments=--server.port=8081`. For the frontend, set `$env:NINEFOLD_API_TARGET = 'http://127.0.0.1:8081'` and run `npm.cmd run dev -- --port 5174`.

## Edit your Top 9

Search for a release and click **Add** to fill the first empty slot. Select a slot before adding to place or replace a release there. Each release ID can appear once. Different editions with different IDs are separate releases.

Remove a release with its **×** control. Drag a **Move** handle to another slot to swap positions. Mouse and touch are supported. For keyboard movement, focus a Move handle and use arrow keys; alternatively, click Move and select a destination.

Set an optional nickname (up to 40 Unicode characters) and choose Dark or Light. Partial boards are supported. Edits save automatically to `localStorage` under `ninefold.board.v1` and restore on reload. Drafts belong to this browser and origin; ports 5173 and 5174 have separate drafts.

Unreadable drafts are left intact until you edit a new board. Storage failures display a warning while the editor remains usable.

## Sharing and PNG export

Click **Share board** to create a compressed, versioned snapshot link. The app attempts to copy it; a read-only field and **Copy link** remain available when clipboard access is blocked. Board edits invalidate the previous link. Shared pages at `/top/<payload>` display metadata and artwork without searching providers, are read-only, and do not read or overwrite the viewer's own draft.

The snapshot preserves all nine positions, including empty slots. Limits: 8000 encoded characters and 64 KiB of inflated UTF-8 JSON. Malformed payloads, unsupported versions and unsafe artwork/source URLs are rejected; decoded text has control characters removed.

**Download PNG** exports a 1440×1740 image with Ninefold branding, the nickname, theme and uncropped artwork. Empty slots and unavailable covers use placeholders. Blocked/missing covers are reported after export. Covers load anonymously with CORS and a five-second maximum wait. PNG export also works on shared pages. Add at least one release to enable sharing/export.

These features use the native [Compression Streams API](https://developer.mozilla.org/en-US/docs/Web/API/Compression_Streams_API) and [Canvas](https://developer.mozilla.org/en-US/docs/Web/HTML/How_to/CORS_enabled_image), with no additional runtime packages. Use a current browser. Clipboard failure has a manual-copy fallback.

A localhost link only opens on the same machine. Deployed links use the current origin. The production frontend server must serve `index.html` for `/top/*` paths as well as the homepage, while forwarding `/api` to the backend.

## Search API

```http
GET /api/v1/releases/search?q=cold%20visions&limit=10&providers=SPOTIFY
```

- `q`: 2–200 Unicode characters after trimming boundary whitespace.
- `limit`: 1–20; default 10.
- `providers`: optional comma-separated enum names; omitted means all connected providers.
- Invalid values return HTTP 400 with `{ "code": "...", "message": "..." }`.

Successful responses contain `query` and normalized `results`. Artwork and release year can be null. Provider failures do not fail the public request or expose internal errors; no available results means HTTP 200 with an empty array.

Spotify currently allows at most 10 results per search request. The adapter returns up to 10 without extra pagination.

## Backend configuration

| Setting | Default | Purpose |
| --- | --- | --- |
| `SPOTIFY_ENABLED` | `true` | Enable Spotify when credentials are present |
| `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` | empty | Server-side application credentials |
| `SPOTIFY_MARKET` | `US` | Search market |
| `SPOTIFY_REQUEST_TIMEOUT` | `1500ms` | Shared token + search deadline, including token lock wait; maximum 1500ms |
| `search.cache.ttl` | `15m` | Successful provider result cache TTL |
| `search.cache.maximum-size` | `1000` | Maximum cached queries |

Access tokens are cached separately and refreshed with an expiry margin of up to 30 seconds. Provider failures are not cached. No database or Redis is required.

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

Backend tests use a local JDK HTTP server and never contact Spotify. Frontend tests cover search races, board editing, draft validation, reload, keyboard movement, storage failures, shared payload validation, readonly views, clipboard fallback and PNG rendering.

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
.\mvnw.cmd spring-boot:run
```

Run the frontend normally. Most queries return fixture releases; `empty` returns no results, `error` triggers HTTP 500 from the mock, and `timeout` delays the response. Stop the mock and remove these test variables before live use.

Provider documentation: [Spotify Search](https://developer.spotify.com/documentation/web-api/reference/search), [Client Credentials](https://developer.spotify.com/documentation/web-api/tutorials/client-credentials-flow).

