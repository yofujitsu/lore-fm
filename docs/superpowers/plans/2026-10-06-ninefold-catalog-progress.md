# Execution ledger — plan: 2026-10-06-ninefold-catalog.md

Authorized on 2026-10-06: user requested roadmap steps 1–3, continuing native implementation in this chat. User supplied MusicBrainz contact yofujitsuuu@gmail.com.

Pre-flight: existing provider/domain/API contracts read. Tasks 1–2 share ProviderRelease and enabled(); task 3 changes the service/API response and corresponding consumers together. Existing Spotify credentials remain backend-only and are never printed.

Ruling: Continue in the already authorized working checkout and sibling frontend, preserving the current user index and existing processes. No commits or Git mutations. Cost: implementation remains a reviewable working-tree change.

Ruling: No automatic in-request retries: MusicBrainz's one-request-per-second rule and 1200 ms budget leave insufficient useful retry time. Honor Retry-After and recover on subsequent searches after cooldown; preserve Spotify token invalidation on 401. Cost: a transient failure can require another user search, with available sources still shown.

Ruling: MusicBrainz release-group covers use the public CAA front-500 endpoint without extra metadata requests. Missing artwork uses existing browser and PNG fallbacks. Cost: some CAA URLs return 404 or are unavailable for PNG due to external CORS.

Task 1: complete. MusicBrainz tests RED: missing adapter; GREEN: backend suite 25 tests, zero failures. Queued cooldown regression RED: two HTTP calls instead of one; GREEN after rechecking shared cooldown on wakeup. Mapping, malformed neighbors, literal Lucene tokens, disabled provider, 1200 ms budget, one-second spacing and Retry-After covered with a loopback HTTP server.

Task 2: complete. Aggregator tests RED: missing ReleaseAggregator; GREEN: backend suite 29 tests. Service concurrency/resilience tests RED: missing result contract and constructor; GREEN: 33 tests. Unicode comparison, editions/year/type compatibility, source/metadata precedence, ranking, merge-before-limit, parallel starts, filtering and caching covered.

Task 3: implemented, final verification pending. Spotify Retry-After check RED: generic SpotifyHttp.Failure instead of safe cooldown signal; GREEN: backend suite 35 tests. UI checks RED: missing MusicBrainz link and incorrect unavailable/partial-empty states; GREEN: frontend 37 tests and TypeScript/Vite build. Deadline cancellation, coalescing, three-failure circuit, one recovery probe and cached results through cooldown covered.

Final: fixed actual outbound spacing on cold startup — spacesOutboundRequestsAndHonorsRetryAfterWithoutRetrying RED: HTTP arrivals 581 ms apart; GREEN: isolated fresh-JVM check enforces >= 1 second. Gate prepared requests through completion, then wait one second. Deadline and Retry-After waits remain bounded.

Final: fixed cold HTTP preparation escaping the total MusicBrainz budget — coldRequestPreparationConsumesProviderBudget RED: successful late response in 1.7 seconds; GREEN: fresh-JVM budget check <= 1300 ms (100 ms scheduler tolerance), recalculated timeout immediately before HTTP plus deadline checks after decoding/mapping. The same check accepts timely success on warmed clients.

Final verification: Maven verify BUILD SUCCESS, 37 tests, zero failures/errors. Frontend 37 tests and TypeScript/Vite production build passed. Real loopback adapters -> search service -> API merge one release, fill CAA artwork, retain Spotify/MusicBrainz links, and support MusicBrainz-only selection. Final reviewer reported only the two timing findings above; both fixed. No deferred minor findings. README/UI contain no Cyrillic text; git diff --check passed; four user-staged initialization files unchanged.

Live verification: reviewed backend /actuator/health UP on agent-owned 8081, frontend 5174. Real Spotify returns 10 results; failed MusicBrainz yields partial=true/unavailable=false and safe UI notice. Public MusicBrainz-only request times out inside its budget; independent public HTTPS request also fails with transport closure after ~20 seconds. Live MusicBrainz success therefore remains unconfirmed due to external connectivity, while loopback integration passes. Browser add/share/read-only viewer/PNG-export completion verified; native browser download event did not expose a file, so PNG success is based on UI completion and existing canvas tests. Desktop 1800px board/search widths 839.5/620.5; mobile 320px document width 305, no horizontal overflow. Proof: target/catalog-browser-desktop.jpg and catalog-browser-mobile.jpg. Agent preview records target/catalog-preview-pids.json; packaged preview uses separate catalog-preview.jar to avoid build locks.

Task 3: complete for implementation and local verification, with the live MusicBrainz connectivity limitation above. No commits/pushes or user-server changes; keep working tree and plan/ledger available for review.
