# Ninefold — Product & System Specification

**Status:** Draft  
**Version:** 0.1  
**Product type:** Web application  
**Architecture:** Stateless-first modular monolith  
**Primary stack:** Java / Spring Boot / React / TypeScript

---

# 1. Product vision

Ninefold — веб-приложение, позволяющее пользователю составить визуальный Top 9 любимых музыкальных релизов независимо от того, на какой платформе они представлены.

Пользователь не обязан регистрироваться или авторизовываться.

Основной пользовательский сценарий:

1. Открыть приложение.
2. Найти музыкальные релизы.
3. Добавить до 9 релизов в персональную сетку.
4. Изменить их порядок.
5. Указать никнейм.
6. Выбрать визуальную тему.
7. Поделиться результатом:
    - через URL;
    - через экспорт изображения.

Основная продуктовая идея:

> Nine releases. One taste.

Приложение ориентировано не только на массовый музыкальный каталог, но и на niche / underground / internet-native музыку, которая может существовать только на отдельных платформах.

---

# 2. Product principles

## 2.1 No mandatory authentication

Ninefold не должен требовать:

- регистрации;
- email;
- OAuth;
- аккаунта пользователя;
- профиля.

Состояние пользователя должно существовать без server-side identity.

---

## 2.2 Platform agnostic releases

Для пользователя релиз является одной сущностью независимо от источника.

Пример:

```text
Spotify album
MusicBrainz release
SoundCloud set
YouTube upload
```

могут представляться через единую доменную модель `Release`.

Frontend не должен зависеть от API-моделей внешних платформ.

---

## 2.3 Stateless by default

Пользовательский Top 9 первоначально не хранится в backend database.

Черновик хранится локально в браузере.

Финальный Top может быть представлен сериализованным состоянием внутри share URL.

---

## 2.4 External APIs are replaceable

Ни один внешний provider не должен являться обязательной зависимостью системы.

Если Spotify, SoundCloud или другой provider недоступен, приложение должно продолжать работать с доступными источниками.

---

## 2.5 Search quality over catalog purity

Цель приложения — помочь пользователю найти нужный релиз.

Если релиз существует только в виде SoundCloud set или YouTube video, приложение может разрешить использовать такой объект как `Release`.

Система не должна требовать, чтобы каждый объект являлся формальным studio album.

---

# 3. Scope

## 3.1 MVP

MVP включает:

- поиск музыкальных релизов;
- Spotify integration;
- MusicBrainz integration;
- unified search results;
- Top 9 grid;
- drag-and-drop reorder;
- удаление и замена релиза;
- nickname;
- dark/light theme;
- local draft persistence;
- shareable URL;
- PNG export;
- graceful degradation provider errors;
- backend caching.

---

## 3.2 Phase 2

После MVP:

- SoundCloud provider;
- YouTube fallback;
- ручное добавление YouTube URL;
- улучшенный deduplication;
- provider badges;
- расширенный ranking;
- additional visual themes.

---

## 3.3 Phase 3

Potential integrations:

- Genius;
- Discogs;
- Apple Music;
- experimental YouTube Music provider;
- custom/user-created releases.

---

# 4. Non-goals

На первоначальном этапе Ninefold НЕ предоставляет:

- регистрацию;
- пользовательские аккаунты;
- подписки;
- social graph;
- comments;
- likes;
- публичные user profiles;
- собственный streaming;
- скачивание музыки;
- хранение audio files;
- хранение полного каталога Spotify/SoundCloud;
- рекомендации музыки;
- прослушивание музыки внутри приложения;
- mobile native application.

---

# 5. Core domain

Главная доменная сущность:

```text
Release
```

а не:

```text
Album
```

Причина — система должна поддерживать:

- albums;
- EP;
- mixtapes;
- singles;
- compilations;
- playlists/sets;
- YouTube-only releases;
- unknown release types.

---

# 6. Domain model

## 6.1 Release

```java
Release {
    ReleaseId id;
    String title;
    List<Artist> artists;
    Integer releaseYear;
    ReleaseType type;
    Artwork artwork;
    List<ReleaseSource> sources;
}
```

`Release` является внутренней нормализованной моделью Ninefold.

Она не должна напрямую содержать Spotify DTO, MusicBrainz DTO и т.п.

---

# 7. ReleaseId

`ReleaseId` является внутренним идентификатором результата Ninefold.

Он не обязан существовать постоянно в базе данных.

Пример:

```text
spotify:4abc123
musicbrainz:de305d54...
soundcloud:123456
youtube:dQw4w9WgXcQ
```

На MVP допустимо использовать provider-qualified ID.

После появления полноценного deduplication может использоваться внутренний transient/generated ID.

---

# 8. Artist

```java
Artist {
    String name;
}
```

На MVP отдельная сложная Artist domain model не требуется.

В будущем возможно расширение:

```text
external ids
artwork
aliases
provider links
```

---

# 9. ReleaseType

```java
enum ReleaseType {
    ALBUM,
    EP,
    MIXTAPE,
    SINGLE,
    COMPILATION,
    PLAYLIST,
    VIDEO_RELEASE,
    UNKNOWN
}
```

Provider должен преобразовать собственную классификацию в один из этих типов.

---

# 10. Artwork

```java
Artwork {
    String url;
    Integer width;
    Integer height;
    Provider provider;
}
```

Artwork является внешней ссылкой.

На MVP приложение не должно автоматически копировать provider artwork в собственный S3/MinIO.

---

# 11. ReleaseSource

```java
ReleaseSource {
    Provider provider;
    String externalId;
    String externalUrl;
}
```

После deduplication один `Release` может иметь несколько источников.

Пример:

```json
[
  {
    "provider": "SPOTIFY",
    "externalId": "...",
    "externalUrl": "..."
  },
  {
    "provider": "MUSICBRAINZ",
    "externalId": "...",
    "externalUrl": "..."
  }
]
```

---

# 12. Provider

```java
enum Provider {
    SPOTIFY,
    MUSICBRAINZ,
    SOUNDCLOUD,
    YOUTUBE,
    GENIUS
}
```

Наличие enum value не означает, что provider обязательно включён.

Providers должны конфигурироваться.

Пример:

```yaml
providers:
  spotify:
    enabled: true

  musicbrainz:
    enabled: true

  soundcloud:
    enabled: false
```

---

# 13. Release provider contract

Все catalog integrations должны реализовывать общий application-level contract.

```java
public interface ReleaseProvider {

    Provider provider();

    List<ProviderRelease> search(ReleaseSearchQuery query);
}
```

`ProviderRelease` представляет нормализованный результат конкретного provider до глобального deduplication.

Provider отвечает за:

- внешний HTTP request;
- authentication;
- parsing external response;
- mapping external data;
- provider-specific error handling.

Provider НЕ отвечает за:

- cross-provider deduplication;
- global ranking;
- выбор лучшего artwork;
- HTTP API frontend;
- пользовательское состояние.

---

# 14. Search use case

Основной backend use case:

```text
SearchReleases
```

Input:

```text
query
```

Optional:

```text
limit
providers
```

Пример:

```http
GET /api/v1/releases/search?q=cold%20visions
```

---

# 15. Search flow

```text
Client
  │
  ▼
ReleaseController
  │
  ▼
ReleaseSearchService
  │
  ├── SpotifyProvider
  │
  ├── MusicBrainzProvider
  │
  └── future providers
  │
  ▼
Provider results
  │
  ▼
Normalizer
  │
  ▼
Deduplicator
  │
  ▼
Ranker
  │
  ▼
Unified releases
  │
  ▼
Client
```

Provider requests SHOULD execute concurrently where provider rate limits allow it.

---

# 16. Search request requirements

Minimum query length:

```text
2 characters
```

Recommended frontend debounce:

```text
300–500 ms
```

Default result limit:

```text
10
```

Maximum result limit:

```text
20
```

Empty queries must not invoke external providers.

---

# 17. Search response

Example:

```json
{
  "query": "cold visions",
  "results": [
    {
      "id": "spotify:123",
      "title": "Cold Visions",
      "artists": [
        {
          "name": "Bladee"
        }
      ],
      "releaseYear": 2024,
      "type": "ALBUM",
      "artwork": {
        "url": "https://...",
        "width": 640,
        "height": 640,
        "provider": "SPOTIFY"
      },
      "sources": [
        {
          "provider": "SPOTIFY",
          "externalId": "123",
          "externalUrl": "https://open.spotify.com/..."
        }
      ]
    }
  ]
}
```

---

# 18. Partial provider failure

Failure одного provider не должен приводить к failure всего search request.

Пример:

```text
Spotify       SUCCESS
MusicBrainz   TIMEOUT
```

Результат:

```http
200 OK
```

с результатами Spotify.

Система SHOULD логировать provider failure.

Frontend не обязан отображать техническую ошибку конкретного provider пользователю.

---

# 19. Global search timeout

Search не должен бесконечно ожидать внешний API.

Target:

```text
entire search request <= 2 seconds
```

Initial provider timeout:

```text
Spotify      <= 1000 ms
MusicBrainz  <= 1200 ms
```

Значения должны конфигурироваться.

---

# 20. Normalization

Нормализация используется только для:

- comparison;
- matching;
- deduplication;
- ranking.

Original provider value всегда сохраняется для отображения.

Base normalization:

```text
trim
lowercase
Unicode normalization
collapse whitespace
normalize punctuation
```

Example:

```text
"COLD VISIONS"
"Cold Visions"
" cold   visions "
```

→

```text
cold visions
```

---

# 21. Deduplication

Цель:

если один релиз найден несколькими provider, пользователь должен видеть один результат.

MVP алгоритм:

```text
normalized title equality
AND
normalized primary artist equality
```

Optional additional signal:

```text
release year compatibility
```

Example:

```text
Spotify:
Cold Visions — Bladee

MusicBrainz:
Cold Visions — Bladee
```

→

```text
one Release
sources = [Spotify, MusicBrainz]
```

---

# 22. Advanced deduplication

Future version:

Weighted similarity:

```text
title similarity        50%
artist similarity       35%
release year            15%
```

Suggested interpretation:

```text
score >= 0.90
auto merge

0.75 <= score < 0.90
possible duplicate

score < 0.75
separate releases
```

Это не является частью MVP.

---

# 23. Result ranking

MVP ranking factors:

1. Exact title match.
2. Exact artist match.
3. Combined exact title + artist match.
4. Provider confidence.
5. Release type relevance.
6. Provider response order.

Provider popularity metrics MAY be used later.

---

# 24. Provider precedence

Provider precedence не должен напрямую определять порядок результатов.

Но при merge нескольких sources система должна выбрать canonical metadata.

Initial precedence MAY be:

```text
Spotify
MusicBrainz
SoundCloud
YouTube
```

Для artwork precedence может отличаться от metadata precedence.

---

# 25. Top board

Основной пользовательский объект frontend:

```ts
interface TopBoard {
    version: 1;
    nickname?: string;
    theme: Theme;
    releases: Array<BoardRelease | null>;
}
```

Размер:

```text
9 slots
```

---

# 26. Board interaction

Пользователь может:

- добавить release;
- удалить release;
- заменить release;
- переместить release;
- изменить nickname;
- изменить theme.

Изменения должны автоматически сохраняться локально.

---

# 27. Local persistence

Draft сохраняется в:

```text
localStorage
```

Cookies для draft state не требуются.

Suggested key:

```text
ninefold.board.v1
```

При открытии приложения существующий draft должен автоматически восстанавливаться.

---

# 28. Sharing

Ninefold MVP не требует server-side хранения shared boards.

Shareable state кодируется в URL.

Conceptually:

```text
TopBoard
↓
compact DTO
↓
JSON
↓
compression
↓
base64url
↓
share URL
```

Example:

```text
https://ninefold.example/top/<payload>
```

---

# 29. Shared state versioning

Shared payload обязательно содержит schema version.

```json
{
  "v": 1
}
```

Это требуется для будущей backward compatibility.

---

# 30. Shared release model

Share payload должен быть достаточно автономным, чтобы shared page не требовал обязательного повторного поиска.

Initial model:

```ts
interface SharedReleaseV1 {
    provider: Provider;
    externalId: string;

    title: string;
    artist: string;

    artworkUrl: string;
    externalUrl?: string;
}
```

Trade-off:

payload становится больше, но открытие shared page не требует N внешних API requests.

---

# 31. Shared board model

Example:

```json
{
  "v": 1,
  "n": "lord",
  "t": "dark",
  "r": [
    {
      "p": "SPOTIFY",
      "id": "...",
      "title": "Cold Visions",
      "artist": "Bladee",
      "cover": "...",
      "url": "..."
    }
  ]
}
```

Production serialization SHOULD use compact field names.

---

# 32. Share URL constraints

Система должна:

- использовать URL-safe encoding;
- reject malformed payload;
- reject unsupported versions;
- enforce maximum payload size;
- sanitize all decoded text;
- never evaluate decoded content as executable code.

---

# 33. Image export

Пользователь может экспортировать board как PNG.

Image generation происходит client-side.

Initial implementation MAY use:

```text
html-to-image
```

или аналогичную browser-side библиотеку.

Backend rendering service на MVP не требуется.

---

# 34. Image contents

PNG должен содержать:

- 3×3 artwork grid;
- optional nickname;
- Ninefold branding;
- selected theme.

Provider artwork должен отображаться без unnecessary modification.

---

# 35. Frontend architecture

Recommended:

```text
React
TypeScript
Vite
TanStack Query
Zustand
```

Possible structure:

```text
src/

├── app
├── entities
│   └── release
├── features
│   ├── release-search
│   ├── add-release
│   ├── reorder-board
│   ├── share-board
│   └── export-board
├── widgets
│   ├── top-grid
│   └── search-dialog
└── shared
```

Exact frontend architecture is not fixed by this specification.

---

# 36. Backend architecture

Ninefold backend starts as modular monolith.

Recommended structure:

```text
com.ninefold

├── release
│   ├── domain
│   ├── application
│   └── api
│
├── provider
│   ├── spotify
│   ├── musicbrainz
│   ├── soundcloud
│   └── youtube
│
├── resolution
│   ├── normalization
│   ├── deduplication
│   └── ranking
│
└── common
```

---

# 37. Infrastructure

MVP infrastructure:

```text
Nginx
Frontend
Spring Boot
```

Optional:

```text
Caffeine
```

No mandatory:

```text
PostgreSQL
Redis
Kafka
RabbitMQ
Kubernetes
MinIO
```

---

# 38. Caching

External provider queries SHOULD be cached.

Initial implementation:

```text
Caffeine
```

Cache categories:

```text
search query result
release details
provider authentication token
```

Cache policies are provider-specific.

---

# 39. Spotify integration

Spotify is an MVP catalog provider.

Authentication:

```text
OAuth2 Client Credentials
```

Credentials MUST exist only on backend.

Frontend must never receive:

```text
client_secret
provider access token
```

Spotify failures must not crash global search.

---

# 40. MusicBrainz integration

MusicBrainz is an MVP catalog provider.

System must respect provider-specific request rate constraints.

MusicBrainz integration MUST have an application-level rate limiter.

Requests SHOULD be cached aggressively where permitted.

---

# 41. SoundCloud integration

SoundCloud is planned for Phase 2.

SoundCloud sets/playlists MAY map to:

```text
ALBUM
EP
MIXTAPE
PLAYLIST
UNKNOWN
```

depending on available metadata.

Ninefold does not require semantic equivalence between Spotify Album and SoundCloud Playlist.

---

# 42. YouTube integration

YouTube is treated primarily as fallback.

Primary UX:

```text
Search YouTube
```

или:

```text
Paste YouTube URL
```

YouTube search SHOULD NOT execute automatically for every normal release search due to quota and result quality considerations.

YouTube video may map to:

```text
VIDEO_RELEASE
```

---

# 43. Genius integration

Genius is initially classified as:

```text
optional enrichment provider
```

rather than primary catalog provider.

Potential purposes:

- additional release metadata;
- association between tracks and albums;
- external Genius link;
- additional deduplication evidence.

Lyrics scraping is outside project scope.

---

# 44. Error model

Public API should use stable application errors.

Example:

```json
{
  "code": "INVALID_SEARCH_QUERY",
  "message": "Search query must contain at least 2 characters."
}
```

Provider-specific internal exceptions should not leak to frontend.

---

# 45. Security

Backend must:

- keep provider credentials server-side;
- validate all query parameters;
- enforce max query length;
- sanitize external text;
- configure outbound HTTP timeouts;
- limit request rate where appropriate;
- never trust shared URL payload;
- reject excessively large payloads.

---

# 46. Observability

Backend SHOULD expose:

```text
Spring Boot Actuator
Micrometer metrics
structured logs
```

Useful metrics:

```text
release.search.count

provider.request.count
provider.request.duration
provider.request.error
provider.request.rate_limited

cache.hit
cache.miss

search.result.count
search.duration
```

---

# 47. Testing

Unit tests:

```text
normalization
deduplication
ranking
share serialization
```

Provider client tests:

```text
WireMock
```

Integration tests:

```text
Spring Boot Test
```

Frontend:

```text
Vitest
React Testing Library
```

E2E:

```text
Playwright
```

---

# 48. Critical E2E scenario

System must successfully support:

```text
Open application
↓
Search release
↓
Add release
↓
Fill all 9 slots
↓
Reorder releases
↓
Set nickname
↓
Reload page
↓
Board restored
↓
Generate share link
↓
Open share link in clean browser
↓
Same Top 9 displayed
↓
Export PNG
```

---

# 49. Performance targets

Initial targets:

Search backend:

```text
p95 < 2000 ms
```

Cached search:

```text
p95 < 200 ms
```

Frontend:

```text
no external provider request on every keystroke
```

Search should use debounce.

---

# 50. Graceful degradation

The application remains functional if one provider fails.

Examples:

```text
Spotify down
→ MusicBrainz search works

MusicBrainz rate limited
→ Spotify results returned

SoundCloud disabled
→ no visible application failure
```

---

# 51. Deployment

Initial production deployment:

```text
VPS

Docker Compose
├── reverse-proxy
├── frontend
└── backend
```

Horizontal scaling and Redis are deferred until supported by actual traffic measurements.

---

# 52. Implementation milestones

## Milestone 1 — Domain foundation

Implement:

```text
Release
Artist
Artwork
ReleaseSource
Provider
ReleaseType
```

No external API.

---

## Milestone 2 — Spotify vertical slice

Implement end-to-end:

```text
React search
↓
Spring API
↓
Spotify
↓
Release mapping
↓
display results
```

Definition of Done:

user can search Spotify releases and see normalized results.

---

## Milestone 3 — Board

Implement:

```text
add
remove
replace
reorder
nickname
theme
```

---

## Milestone 4 — Persistence

Implement:

```text
localStorage
```

Reloading browser must preserve unfinished board.

---

## Milestone 5 — Sharing

Implement:

```text
SharedBoardV1
serialization
compression
URL
deserialization
read-only shared view
```

---

## Milestone 6 — PNG export

Implement browser-side export.

---

## Milestone 7 — MusicBrainz

Add second provider.

This milestone validates provider abstraction.

---

## Milestone 8 — Aggregation

Implement:

```text
parallel search
normalization
deduplication
ranking
```

---

## Milestone 9 — Resilience

Implement:

```text
Caffeine
timeouts
rate limiting
retry where appropriate
circuit breaker where appropriate
```

---

## Milestone 10 — Underground catalog

Add:

```text
SoundCloud
```

---

## Milestone 11 — Long-tail fallback

Add:

```text
YouTube search
YouTube URL import
```

---

## Milestone 12 — Enrichment

Experiment with:

```text
Genius
Discogs
```

---

# 53. MVP Definition of Done

Ninefold MVP считается завершённым, когда пользователь без регистрации может:

1. Найти релизы минимум в Spotify и MusicBrainz.
2. Добавить 9 релизов.
3. Изменить порядок.
4. Указать nickname.
5. Выбрать dark/light theme.
6. Закрыть и снова открыть вкладку без потери draft.
7. Создать share URL.
8. Открыть URL в другом браузере.
9. Увидеть тот же Top 9.
10. Скачать Top 9 как изображение.
11. Продолжить работу, если один внешний provider временно недоступен.

---

# 54. Architectural decision summary

Для MVP принимаются следующие решения:

```text
Architecture:
modular monolith

Backend:
Java + Spring Boot

Frontend:
React + TypeScript

User auth:
none

User persistence:
localStorage

Shared board persistence:
URL payload

Database:
none

Cache:
Caffeine

Primary providers:
Spotify + MusicBrainz

Phase 2 providers:
SoundCloud + YouTube

Enrichment:
Genius

Deployment:
Docker Compose on VPS
```

---

# 55. Open decisions

Перед началом реализации необходимо отдельно определить:

1. Финальное название продукта.
2. Формат внутренних `ReleaseId`.
3. Финальный share URL payload.
4. Максимальную длину nickname.
5. Разрешать ли Top меньше 9 релизов.
6. Разрешать ли duplicate release в одном Top.
7. Что считать canonical release при нескольких editions.
8. Нужно ли отображать provider badges.
9. Нужна ли возможность вручную изменить title/artist у YouTube release.
10. Как именно объединять deluxe / remaster / anniversary editions.
11. Можно ли пользователю выбирать artwork из нескольких providers.
12. Максимальную длину поискового запроса.

---

# 56. Design principle for future changes

Новая функциональность не должна заставлять существующий frontend зависеть от конкретной внешней платформы.

Предпочтительный dependency direction:

```text
Spotify API ──────┐
MusicBrainz ──────┤
SoundCloud ───────┤
YouTube ──────────┤
                  ▼
             Provider layer
                  ▼
              Ninefold
              domain
                  ▼
             REST API
                  ▼
               React
```

Ninefold domain является центром системы.

Внешние музыкальные сервисы являются адаптерами.

---

# 57. Core architectural invariant

Главное правило проекта:

> A release belongs to Ninefold's domain, not to Spotify, MusicBrainz, SoundCloud or YouTube.

External platforms provide representations of a release.

Ninefold decides how those representations are normalized, combined and shown to the user.