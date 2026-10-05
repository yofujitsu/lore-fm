# Execution ledger — plan: 2026-10-05-ninefold-search.md

Approved: 2026-10-05, native implementation in this chat.

Pre-flight: tasks 1→2 share Release; 2→3 share ReleaseProvider/SearchQuery; 1–3→4 share public JSON. Interfaces are consistent.

Ruling: Work in the approved existing checkout and sibling frontend, without commits or index changes. The repository has no HEAD and the approved plan explicitly preserves the user's staged initialization; worktree/commit-dependent skill scripts cannot operate here. Progress is recorded in this file instead. Cost: changes remain uncommitted until the user reviews them.

Tasks: 1 domain/build; 2 search API/cache; 3 Spotify; 4 frontend; 5 verification/docs.

Task 1: complete. ReleaseTest RED: missing domain types; GREEN: mvn test and Maven Wrapper test, 4 tests, 0 failures. Wrapper null Target fixed; Redis removed.

Task 2: complete. Service/controller tests RED: missing search/API types; GREEN: mvn test, 10 tests, 0 failures. Validation, provider filtering, isolated failures, bounded Caffeine cache covered.

Task 3: complete. Provider/token tests RED: missing Spotify types; GREEN: mvn test, 17 tests, 0 failures. Local HTTP server verifies credentials exchange, token cache, mapping, errors, timeout and lock deadline. Live credentials absent.

Ruling: Added a package-private SpotifyHttp helper shared by token/search clients to centralize deadline enforcement and sanitized HTTP errors. Cost: one extra focused file; avoids duplicated HTTP setup.

Task 4: complete. Frontend test RED: missing App; GREEN: 5 Vitest tests, TypeScript check and Vite production build pass. package-lock.json generated; npm install audit: 0 vulnerabilities.

Task 5: complete. Maven verify: 17 tests, 0 failures and executable jar; frontend: 5 tests and production build pass. Browser verified fixture search, empty state, Unicode and 390px viewport (no horizontal overflow). Public API q=a → 400; fixture search → 4 normalized releases; timeout → 200 empty in 1013ms; health UP. Provider secrets absent from backend logs.

Ruling: Default ports 8080/5173 were occupied by existing processes; smoke uses 8081/5174, with configurable NINEFOLD_API_TARGET for Vite. Existing processes were preserved. Cost: review preview uses nondefault ports, documented in README.

Live Spotify was not tested: real credentials are not configured. Test-only mock is explicit and loopback-only.

Final review: independent reviewer found malformed item decoding (P2), inconsistent Unicode trimming (P3), invalid calendar dates (P3). All reproduced: backend regression run 2 failures/1 error; frontend new Unicode regression 1 failure.

Ruling: Treat Unicode validation and invalid date mapping as Important fixes too: whitespace-only API requests must not reach providers, and malformed metadata must show unknown year. Standardize trimming to Unicode White_Space plus U+001C–U+001F/BOM on both sides. Cost: broader boundary whitespace removal than Java strip; ordinary visible text remains unchanged.

Declined review topics: future board/sharing/MusicBrainz/aggregation, live account access, production deployment. These remain subsequent milestones or external credential checks, as the approved plan specifies; no new scope was added.

Final fixed: malformed item decoding, whitespace validation and calendar parsing — regression tests RED→GREEN; final suite mvn verify 21 tests / 0 failures, frontend 6 tests / 0 failures, TypeScript and Vite build pass. Additional test confirms 401 invalidates the cached token for the next request.

Browser additionally confirmed keyboard focus on source links and generic error on transport failure. Test-only backend/mock stopped; final preview uses real default provider configuration on backend 8081/frontend 5174, without live Spotify credentials. Default production/development configuration remains 8080/5173.

Finished: source changes and user index preserved without commits. No deferred minor findings remain. This ledger is retained because there is no git commit history to replace it.

Follow-up 2026-10-05: user added live credentials to backend .env, but the running applications only read process environment variables and returned zero results. Added native optional Spring config import for .env, documented startup/restart and unquoted KEY=value syntax. Live first request exceeded the old .env timeout of 1000ms; changed that setting and .env.example to the existing allowed default of 1500ms. Maven verify: 21 tests / 0 failures, executable jar built. Restarted agent-owned preview on 8081: fresh browser search for Daft Punk returned 10 live Spotify releases through frontend 5174. User-owned backend on 8080 needs a restart to load the changed configuration. No credentials were printed or added to tracked files.
