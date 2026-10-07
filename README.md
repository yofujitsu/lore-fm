# ninefold

<img width="1933" height="1262" alt="image" src="https://github.com/user-attachments/assets/080ac741-9ed6-4767-be86-35025284bbdb" />


**1.0.0** · [Open Ninefold](https://ninefold.duckdns.org/) · [Changelog](CHANGELOG.md) · [Frontend repository](https://github.com/yofujitsu/lore-fm-frontend)

Pick your top 9 music releases. Albums, EPs, singles. Make a board for a genre, a mood or that very specific era of your life. Send it to your friends and steal a few good recs from theirs.

## The vibe

Your goth era. The anime soundtracks you had on repeat. Deep cuts for the late bus home. Nine releases that made you, or nine that just make you dance.

Every board can be its own thing. Mix the classics with the weird little EP you keep coming back to. Give it a name, put your nickname on it, pass it around. Your friends can open it straight from the link — no sign-up or Spotify login needed.

## What's in 1.0.0

- **Find your releases.** Spotify and MusicBrainz search together, with source links, merged duplicates and usable results when one catalog is having a bad day. Filter by Album, EP, Single or release year.
- **Build your nine.** Add, replace, remove and swap covers. Drag the Move handle, use arrow keys, or pick a destination. Partial boards work too.
- **Make it yours.** Optional board name and nickname, dark lavender or cool light theme. Shared pages and PNGs use the same look.
- **Keep a few eras around.** Autosaved browser draft and up to 20 named local snapshots. Save, load, update, rename or delete a snapshot. Draft edits leave saved snapshots alone.
- **Undo the last edit.** Release, board-name and nickname changes can be undone. Switching themes keeps your history and current theme. Clear board asks first and keeps your name, nickname and theme.
- **Pass the aux.** Share a snapshot link with a readonly board page. Copy-link fallback included. There's no public feed or browseable board directory in this release; exploring happens through links your friends send you.
- **Take the grid with you.** Export a 1440×1980 PNG with uncropped covers, release titles and artists.
- **Desktop and phone.** A roomy board beside the search panel on desktop; stacked panels on mobile. Typed headline prompts have a pause button and respect reduced motion.

Board names and nicknames allow up to 40 Unicode characters each. Saved snapshot names must be unique. Duplicate release IDs cannot occupy two slots; different editions with different IDs can.

## Under the hood

| Part | Stack in this release |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC, Maven Wrapper |
| Search | Provider adapters, Caffeine cache, deadline and rate-limit handling |
| API and ops | Springdoc OpenAPI 3.1.0, Actuator, Micrometer/Prometheus |
| Frontend | React 19.3.0, TypeScript 5.9, Vite 8.3.2, plain CSS |
| Browser state | localStorage, native Web Locks, Compression Streams, Canvas PNG export |
| Checks | JUnit/Spring tests, Vitest 5.0.3, Testing Library, jsdom |
| Serving | Docker, Docker Compose 2.30+, Caddy 2 with automatic HTTPS |
| Catalogs | Spotify Web API, MusicBrainz release groups, Cover Art Archive |

The backend is a modular monolith. Boards live in the browser or inside share links; there is no database or user-account service. A search calls enabled catalogs concurrently within a two-second budget, merges compatible releases and keeps available results when a provider fails. Successful results are cached for 15 minutes; failed requests are not cached. MusicBrainz requests are spaced per backend process, so use one backend replica.

Optional SoundCloud and YouTube adapters are implemented for explicit API queries. They are disabled until configured, excluded from default searches and hidden from the public editor. Genius remains a reserved provider name, not an implemented integration. The [original spec](specs/SPEC.md) also contains future work; this README describes the shipped release.

## Local development

Install **JDK 21** and **Node.js 22.12+** with npm, and point `JAVA_HOME` at JDK 21. The Maven Wrapper supplies Maven; a global Maven installation is unnecessary. Keep the two checkouts as siblings because Compose builds the frontend from `../lore-fm-frontend`:

```text
your-workspace/
├── lore-fm/             # Spring backend and Compose
└── lore-fm-frontend/    # React frontend
```

From `lore-fm`, create the backend configuration:

```powershell
Copy-Item .env.example .env
```

Edit `.env` using unquoted `KEY=value` lines. Set `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` from your [Spotify app](https://developer.spotify.com/dashboard). To use MusicBrainz, set `MUSICBRAINZ_ENABLED=true` and replace the User-Agent contact with your own public developer email or project URL. For example:

```dotenv
MUSICBRAINZ_ENABLED=true
MUSICBRAINZ_USER_AGENT=Ninefold/1.0.0 (mailto:you@example.com)
```

MusicBrainz needs no key. It can handle search without Spotify credentials when enabled. The supplied template leaves MusicBrainz disabled until you configure its identifying contact. Keep keys in the backend `.env`; both Git and Docker build contexts exclude it.

Start the backend from the `lore-fm` root in the first terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

Start the frontend from the workspace in another terminal:

```powershell
cd lore-fm-frontend
npm.cmd ci
npm.cmd run dev
```

On Linux/macOS, use `cp .env.example .env`, `sh ./mvnw spring-boot:run`, `npm ci` and `npm run dev` instead. Open [127.0.0.1:5173](http://127.0.0.1:5173/). Vite forwards `/api` to the backend at `127.0.0.1:8080`. Spring reads `.env` at startup; restart it after configuration changes. Environment variables override the file.

If the ports are busy, run the backend with:

```powershell
.\mvnw.cmd '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run
```

Then, in the frontend terminal:

```powershell
$env:NINEFOLD_API_TARGET = 'http://127.0.0.1:8081'
npm.cmd run dev -- --port 5174
```

On Linux/macOS: `NINEFOLD_API_TARGET=http://127.0.0.1:8081 npm run dev -- --port 5174`. `vite preview` previews a build; it does not provide the development API proxy or replace Caddy for deployment.

### Checks

Run from the backend root:

```powershell
.\mvnw.cmd -B verify
```

Run from the frontend root:

```powershell
npm.cmd test -- --run
npm.cmd run build
```

Backend tests use local HTTP fixtures rather than real music catalogs. Frontend checks cover search races, editing/history, storage failures, sharing validation, reduced motion and PNG rendering. The release JAR is `target/lore-fm-1.0.0.jar`; the frontend output is `dist/`.

### Search without live catalog calls

From the backend root, start `node scripts/spotify-mock.mjs` in a separate terminal. In the backend terminal, set these test-only variables and start Spring:

```powershell
$env:SPOTIFY_CLIENT_ID = 'local-test-id'
$env:SPOTIFY_CLIENT_SECRET = 'local-test-secret'
$env:SPOTIFY_API_BASE_URL = 'http://127.0.0.1:9091/v1'
$env:SPOTIFY_TOKEN_URL = 'http://127.0.0.1:9091/api/token'
$env:MUSICBRAINZ_ENABLED = 'false'
.\mvnw.cmd spring-boot:run
```

The loopback-only mock returns fixtures. Queries `empty`, `error` and `timeout` exercise empty results, HTTP failures and deadlines. Close this terminal or remove the test variables before using live catalogs again.

## Docker and deployment

The live app is [ninefold.duckdns.org](https://ninefold.duckdns.org/). The current VPS runs the Compose project in `/opt/ninefold`; its old sslip.io hostname remains an HTTPS alias.

### Local Compose

With Docker running **Linux containers** and Compose **2.30+**, run from the backend root:

```powershell
docker compose up -d --build --wait
.\scripts\smoke-deployment.ps1
```

Open [localhost:8090](http://localhost:8090/). Builds run both test suites. Caddy serves the compiled frontend and proxies `/api`; shared `/top/<payload>` routes also work after reload. The backend has no published host port. Docker loads the backend `.env` at runtime; secrets stay outside the images.

### Ubuntu VPS with HTTPS

Install Docker Engine and the Compose plugin using the [official Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/). Point your domain's A record at the VPS, and allow incoming TCP ports **80** and **443**. Add an AAAA record only if the VPS is reachable over IPv6. A free [DuckDNS](https://www.duckdns.org/) hostname works too: register the name and set its IP to the VPS address.

For a 1 GB VPS, build on your development machine and transfer the images. From the backend root:

```powershell
docker compose build
New-Item -ItemType Directory -Force target | Out-Null
docker save -o target/ninefold-images.tar ninefold-backend:local ninefold-frontend:local
ssh root@YOUR_VPS 'mkdir -p /opt/ninefold'
scp target/ninefold-images.tar compose.yaml deploy.env.example root@YOUR_VPS:/opt/ninefold/
```

On the VPS, configure it once:

```sh
cd /opt/ninefold
cp deploy.env.example .env.deployment
nano .env.deployment
```

Use your own hostname, with no scheme or path:

```dotenv
NINEFOLD_SITE_ADDRESS=your-name.duckdns.org
NINEFOLD_BIND_ADDRESS=0.0.0.0
NINEFOLD_HTTP_PORT=80
NINEFOLD_HTTPS_PORT=443
```

Create `/opt/ninefold/.env` with the backend catalog settings and credentials described above. Then:

```sh
chmod 600 .env .env.deployment
docker load -i ninefold-images.tar
docker compose --env-file .env.deployment up -d --no-build --wait
docker compose --env-file .env.deployment ps
```

[Caddy obtains certificates and redirects HTTP to HTTPS](https://caddyserver.com/docs/automatic-https). Certificate state lives in the `caddy_data` volume. The backend container is capped at 512 MB with a JVM heap budget of 65%; Caddy is capped at 128 MB. The current small VPS has swap enabled. Keep one backend replica for the per-process provider rate limits.

From your Windows development machine, check the public app:

```powershell
.\scripts\smoke-deployment.ps1 -BaseUrl https://your-name.duckdns.org
```

The script checks the homepage, compiled assets, short/long shared routes, missing-asset 404 and API proxy. Then check a real search and shared board in your browser; the smoke script does not test external provider availability.

### Updates, rollback and a different domain

Build and transfer new images with the same process. Before loading them, keep rollback tags on the VPS:

```sh
docker tag ninefold-backend:local ninefold-backend:previous
docker tag ninefold-frontend:local ninefold-frontend:previous
docker load -i ninefold-images.tar
docker compose --env-file .env.deployment up -d --no-build --wait
```

For a frontend-only update, recreate only that service: `docker compose --env-file .env.deployment up -d --no-build --no-deps --wait frontend`. Backend `.env` changes require recreating the backend. To roll back, tag the saved `:previous` images as `:local` again and run the same Compose command.

To change domain, point DNS at the VPS, update only `NINEFOLD_SITE_ADDRESS`, and recreate the frontend. Caddy also accepts a comma-and-space-separated list of hostnames if you want to keep an old alias. Use `docker compose --env-file .env.deployment logs --tail=100` for diagnostics. `docker compose --env-file .env.deployment down` stops the app while retaining certificate volumes; `down -v` deletes them.

## Your boards and share links

Drafts and saved boards belong to the current browser **and origin**. Switching domain or port opens a separate library; previous boards remain at the old address. A shared board is a readonly snapshot, not an automatic import into your draft. Anyone with the full link can read it. After changing a board, generate a fresh link.

Draft format `ninefold.board.v1` and saved-library format `ninefold.boards.v1` stay at schema version 1; the app's `1.0.0` release does not invalidate existing data. Older drafts and links without a board name still open. Loading an older saved snapshot uses its library name when it has no board name.

Links preserve empty positions and have an 8000-character encoded limit plus a 64 KiB decoded JSON limit. Corrupt data, unsupported schemas and unsafe artwork/source URLs are rejected. PNG covers use anonymous CORS requests with a five-second wait; unavailable artwork gets a placeholder and is reported after export. Share/export needs at least one release. Use a current browser with Compression Streams, Canvas and Web Locks; saved-board writes require HTTPS or localhost.

## API and configuration

```http
GET /api/v1/releases/search?q=Daft%20Punk&limit=10
```

`q` accepts 2–200 Unicode characters after trimming. `limit` is 1–20, default 10. Omitted `providers` searches enabled Spotify and MusicBrainz; an explicit comma-separated list such as `providers=SPOTIFY,MUSICBRAINZ` restricts the sources. The Spotify adapter requests at most 10 results per call; the final limit is applied after catalog merging. Invalid inputs return HTTP 400 with `code` and `message`.

Responses include `query`, `results`, `partial` and `unavailable`. A partial result keeps the catalogs that responded. `unavailable=true` means no selected catalog could respond; an empty but healthy search has both flags false. Those search states return HTTP 200. Artwork, type and release year can be unknown.

| Setting | Application default | Purpose |
| --- | --- | --- |
| `SPOTIFY_ENABLED` | `true` | Active when both Spotify credentials are present |
| `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` | empty | Server-side application credentials |
| `SPOTIFY_MARKET` | `US` | Search market |
| `SPOTIFY_REQUEST_TIMEOUT` | `1500ms` | Token and search budget; maximum 1500ms |
| `MUSICBRAINZ_ENABLED` | `false` | Enable after setting an identifying developer contact |
| `MUSICBRAINZ_USER_AGENT` | `Ninefold/1.0.0 (mailto:yofujitsuuu@gmail.com)` | Override with your own public contact for another deployment |
| `MUSICBRAINZ_REQUEST_TIMEOUT` | `1200ms` | HTTP and rate-limit wait budget; maximum 1200ms |
| `SEARCH_REQUEST_TIMEOUT` | `2s` | Global provider deadline; maximum 2s |
| `SEARCH_FAILURE_COOLDOWN` | `30s` | Cooldown after three failures; maximum 1h |
| `search.cache.ttl`, `search.cache.maximum-size` | `15m`, `1000` | Successful result cache settings in `application.properties` |
| `SOUNDCLOUD_ENABLED`, `YOUTUBE_ENABLED` | `false` | Optional catalogs for explicit API requests |
| `SOUNDCLOUD_CLIENT_ID`, `SOUNDCLOUD_CLIENT_SECRET`, `YOUTUBE_API_KEY` | empty | Optional server-side credentials |
| `SOUNDCLOUD_REQUEST_TIMEOUT`, `YOUTUBE_REQUEST_TIMEOUT` | `1500ms` | Optional provider budgets; maximum 1500ms |

Access tokens are reused and refreshed before expiry. Identical simultaneous searches share the cache loader. HTTP 429/503 respects bounded Retry-After cooldowns; repeated failures open a circuit, and cached successes remain available. There's no automatic retry loop inside a search request.

For optional adapters, obtain credentials through the [SoundCloud API guide](https://developers.soundcloud.com/docs/api/guide) or [YouTube Data API setup](https://developers.google.com/youtube/v3/getting-started), enable the corresponding setting, restart the backend and select the provider explicitly in the API query. SoundCloud returns sets/playlists with uploader attribution; YouTube returns videos with channel attribution and does not treat upload dates as release years.

Local backend endpoints: `/actuator/health`, `/actuator/prometheus`, `/v3/api-docs` and `/swagger-ui.html`. Caddy publicly forwards only `/api`; the ops and documentation endpoints stay inside the deployment network.

## Contributing and credits

Small fixes, missing-release examples and mobile feedback are welcome. For a search issue, include the query and catalog link; leave credentials out of reports. Run the checks above before sending a patch, and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

By [yofujitsu](https://github.com/yofujitsu). Music metadata and artwork come from Spotify, MusicBrainz and Cover Art Archive. Provider links and credits stay visible in the UI and exports.

Ninefold code and original documentation use the [MIT License](LICENSE). The Code of Conduct and third-party assets have separate terms listed in [Third-party notices](THIRD_PARTY_NOTICES.md); MIT does not grant rights to catalog artwork or provider trademarks.
