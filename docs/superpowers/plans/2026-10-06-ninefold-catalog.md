# Ninefold catalog and resilience implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, as already requested by the user. Track checks below and in the companion progress file.

**Goal:** Complete approved roadmap steps 1–3: MusicBrainz, combined ranked search, and bounded partial-failure handling.

**Architecture:** Keep the existing ReleaseProvider/domain boundary. A MusicBrainz release-group adapter supplies normalized releases and CAA artwork URLs. The application searches active providers concurrently, caches successes, merges equivalent releases and ranks the combined pool. Safe response flags distinguish incomplete catalogs from genuine empty results.

**Tech stack:** Existing Spring Boot, Java 21 virtual threads, Jackson, Caffeine, React/TypeScript; no new dependencies.

**Spec:** specs/SPEC.md sections 13–24, 38–40, 48–50 and milestones 7–9.

## Global constraints
- Search query 2–200 Unicode code points; result limit 10 by default, maximum 20.
- Entire provider search budget <= 2 seconds; MusicBrainz budget <= 1200 ms including rate-limit wait. Preserve the previously approved Spotify 1500 ms setting.
- MusicBrainz request starts spaced at least one second apart, with interruptible bounded waiting; User-Agent Ninefold/0.1 (mailto:yofujitsuuu@gmail.com), configurable.
- Original display text survives comparison normalization. Merge normalized title + primary artist; preserve edition suffixes and incompatible known years/types.
- Spotify metadata/ID wins a merge; use a valid available artwork and union all distinct sources. Release-group CAA front-500 URLs may be missing; existing artwork/PNG fallbacks apply.
- UI and READMEs remain English. Work in the user's approved checkout and sibling frontend; preserve staged initialization, no commits, pushes or process changes on user ports.

## Review focus
- Malformed MusicBrainz neighbors, unsafe IDs and Unicode text must not discard valid results or produce arbitrary artwork URLs: task 1 mapping check.
- MusicBrainz rate waiting and HTTP Retry-After must never bypass spacing or produce an unbounded queue wait: tasks 1 and 3 timing checks.
- Provider completion order must not change IDs, merge metadata or ranking: task 2 reversed-input and concurrent-start checks.
- A stalled provider must leave healthy results available within the global budget: task 3 cancellation/deadline check.
- All providers failing must differ from a successful empty catalog in the API/UI; stale partial flags must clear on the next query: task 3 endpoint/UI checks.

### Task 1 — MusicBrainz adapter
Files: create provider/musicbrainz/MusicBrainzProperties.java, MusicBrainzProvider.java, MusicBrainzConfiguration.java and matching provider test; extend existing ProviderRelease with bounded confidence and ReleaseProvider.enabled(). Configure application.properties and .env.example.
Interfaces: ReleaseProvider.search(ReleaseSearchQuery) -> List<ProviderRelease>; ProviderRelease(Release, int confidence), range 0–100; existing one-argument constructor remains for adapters with default confidence. Add ProviderUnavailableException(Duration retryAfter) for safe rate/cooldown signaling.
- [x] Test first: real loopback HTTP response maps group ID/title/artist/date/type/score/CAA URL; malformed neighbor and invalid date are isolated; artist/title query tokens are escaped and URL encoded; User-Agent carries configured contact.
- [x] Test disabled adapter performs no HTTP, request spacing >= 1 s, bounded timeout and 429 cooldown without another outbound call.
- [x] Run failing checks, implement adapter, rerun backend suite.

### Task 2 — Aggregation
Files: create release/application/ReleaseAggregator.java and test; modify ReleaseSearchService, ProviderRelease and service tests.
Interfaces: ReleaseAggregator.aggregate(List<ProviderRelease>, ReleaseSearchQuery) -> List<Release>; normalize(String) for comparison only.
- [x] Test first: Unicode/punctuation/whitespace equivalence merges sources, Spotify ID/display metadata survives reversed input; distinct editions and incompatible known years/types stay separate; missing artwork can be filled from the other source.
- [x] Test ranking puts exact title/artist and combined title+artist ahead of confidence, then confidence/type/order; limit is applied after merging.
- [x] Test concurrent provider starts using latches, provider filter/cache behavior remains valid.
- [x] Run failing checks, implement aggregation and concurrent orchestration, rerun backend suite.

### Task 3 — Resilience and frontend
Files: service and API/controller tests, SearchResult.java, SearchResponse.java; Spotify HTTP cooldown support; frontend App.tsx, release.ts, App.test.tsx, styles.css; English READMEs.
Interfaces: service.search(ReleaseSearchQuery) -> SearchResult(List<Release> results, boolean partial, boolean unavailable); API retains query/results and adds these two safe booleans.
- [x] Test first: deadline cancels a stalled provider and preserves fast results; failed responses are not cached; simultaneous same-key searches share the loader.
- [x] Test circuit opens after 3 consecutive failures for configurable 30 s, then permits one recovery probe; cached successes remain available. Retry-After opens immediate cooldown without an automatic retry; ordinary local throttling does not increment failure streak.
- [x] Test API reports healthy empty, partial and unavailable distinctly without leaking provider errors. Test UI retains partial results, shows safe unavailable message, clears stale notices, and renders both source links.
- [x] Implement with the existing HTTP clients/cache and native concurrency. Avoid in-request retries that would exceed deadlines or MusicBrainz spacing; Spotify's existing 401 token invalidation remains.
- [x] Run Maven verify, frontend suite and production build; read all results.
- [x] Independent final review, fix important findings with regression checks; live API/browser smoke including MusicBrainz-only search and unchanged board/sharing/PNG, save screenshots.

## Completion
- [x] Update English configuration/run documentation and record observed limitations and final verification.

Observed live limitation: public MusicBrainz connections are currently closed by the remote/network path in this environment. MusicBrainz-only and combined adapter/API behavior passed loopback integration; real Spotify and partial UI passed live checks. See companion progress file for review fixes and evidence.
