# Ninefold Search — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Реализовать milestones 1–2 из SPEC.md: пользователь открывает React приложение, ищет релиз в Spotify и видит результаты в независимом от Spotify формате Ninefold.

**Architecture:** Существующий Spring Boot backend остаётся модульным монолитом в пакете `ru.yofujitsu.lorefm`. Домен и HTTP API не содержат внешних Spotify DTO; интеграция находится за контрактом ReleaseProvider. Отдельный React frontend обращается только к backend через Vite proxy.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring MVC, RestClient с JDK HTTP client, Caffeine, существующие JUnit/Spring Boot Test, React, TypeScript, Vite, Vitest, React Testing Library.

**Spec:** [specs/SPEC.md](../../../specs/SPEC.md), прежде всего разделы 6–19, 38–39, 44–45 и milestones 1–2 в разделе 52.

## Global Constraints

- `Architecture: modular monolith`.
- `Backend: Java + Spring Boot`; сохраняем Java 21 и Spring Boot 4.1.1 из существующего pom.xml.
- `Frontend: React + TypeScript`.
- `User auth: none`; `Database: none`; `Cache: Caffeine`.
- `Minimum query length: 2 characters`.
- `Default result limit: 10`; `Maximum result limit: 20`.
- `Recommended frontend debounce: 300–500 ms`; используем 400 ms.
- `entire search request <= 2 seconds`; бюджет Spotify `<= 1000 ms`, включая получение токена.
- `Credentials MUST exist only on backend`.
- `Failure одного provider не должен приводить к failure всего search request`.
- `Original provider value всегда сохраняется для отображения`.
- `A release belongs to Ninefold's domain, not to Spotify, MusicBrainz, SoundCloud or YouTube.`

## Decisions for this stage

- Название в интерфейсе — Ninefold, как в SPEC.md. Maven artifact и корневой Java package сохраняются.
- Backend: `F:\java_pr\lore-fm`; frontend: новая соседняя папка `F:\java_pr\lore-fm-frontend`, как разрешено пользователем. Перед записью вне рабочего корня использовать механизм доступа среды.
- ReleaseId — типизированная Java record со значением `spotify:<externalId>`. В публичном JSON это строка.
- Поисковый запрос после strip: 2–200 Unicode code points. Максимум 200 — предлагаемое решение открытого вопроса №12.
- `limit` от 1 до 20; это верхняя граница, а не обещанное количество результатов. Spotify получает `min(limit, 10)` согласно текущему API; дополнительные страницы в этом этапе не запрашиваются.
- Необязательный `providers` — список enum names через запятую. Неизвестное значение даёт 400; известный, но ещё не реализованный/отключённый provider не вызывается.
- `artwork` и `releaseYear` могут быть null: отсутствие обложки или точной даты не исключает релиз из поиска.
- Spotify включается при наличии backend переменных `SPOTIFY_CLIENT_ID` и `SPOTIFY_CLIENT_SECRET`; явное `SPOTIFY_ENABLED=false` отключает его. Без credentials приложение запускается и возвращает пустые результаты. README объясняет настройку.
- Market конфигурируется через `SPOTIFY_MARKET`, первоначально `US`.
- При отсутствии доступных providers или отказе единственного provider: 200 с пустым `results`, без внешних исключений в ответе. UI показывает нейтральное пустое состояние, диагностическая причина остаётся в backend логах.
- Для frontend достаточно React hooks и fetch. TanStack Query/Zustand остаются возможными решениями будущих этапов, а не зависимостями для одного поиска.
- Подтверждение плана определяет объём текущего этапа; весь MVP остаётся следующими milestones из SPEC.md.

## Review Focus

1. Пробелы, Unicode и специальные символы в поиске: единая валидация, корректное URL encoding, отсутствие запроса при невалидном вводе (tasks 2, 3, 4).
2. Неполный Spotify ответ: неизвестный album_type, пустые images, неполная дата и повреждённый отдельный item не обрушивают выдачу (task 3).
3. Отсутствующие credentials, 401/403/429, timeout: backend остаётся доступным, секреты не попадают в HTTP ответы и логи (tasks 2, 3).
4. Быстрые изменения запроса: старый ответ не заменяет более новый; запрос отменяется при изменении ввода/unmount (task 4).
5. Одновременное истечение токена и медленная авторизация: нет бесконечного ожидания token lock, соблюдается общий бюджет provider (task 3).

## File map

Backend paths ниже относительно `F:\java_pr\lore-fm`; Java classes — относительно `src/main/java/ru/yofujitsu/lorefm`.

| Files | Responsibility |
| --- | --- |
| `release/domain/{ReleaseId,Release,Artist,Artwork,ReleaseSource,ReleaseType,Provider}.java` | Нормализованный immutable domain |
| `release/application/{ReleaseSearchQuery,ProviderRelease,ReleaseProvider,ReleaseSearchService}.java` | Валидация запроса, контракт интеграций, поиск и изоляция отказов |
| `release/api/{ReleaseController,SearchResponse,ApiError,ApiExceptionHandler}.java` | Публичный JSON API и стабильные ошибки |
| `provider/spotify/{SpotifyProperties,SpotifyConfiguration,SpotifyProvider,SpotifyTokenClient,SpotifyResponse}.java` | Конфигурация, HTTP, client credentials, маппинг DTO |
| `pom.xml`, `src/main/resources/application.properties`, `mvnw.cmd` | Зависимости, настройки, исправление wrapper |
| `.gitignore`, `.env.example`, `README.md` | Секреты исключены из git, конфигурация и запуск |

Frontend paths относительно `F:\java_pr\lore-fm-frontend`:

| Files | Responsibility |
| --- | --- |
| `package.json`, `package-lock.json`, `index.html`, `tsconfig*.json`, `vite.config.ts`, `.gitignore` | Минимальный Vite React TypeScript проект и `/api` proxy |
| `src/main.tsx`, `src/App.tsx`, `src/styles.css` | Страница поиска и оформление |
| `src/release.ts`, `src/search.ts` | Типы публичного API и fetch |
| `src/App.test.tsx`, `src/test-setup.ts` | Проверка пользовательского поиска и гонок запросов |
| `README.md` | Команды запуска frontend |

Не создавать пустые пакеты будущих providers, универсальную UI библиотеку, repositories или frontend feature hierarchy для одного экрана.

---

### Task 1: Domain foundation and reproducible backend build

**Files:** domain files из File map, `pom.xml`, `mvnw.cmd`; tests `src/test/java/ru/yofujitsu/lorefm/release/domain/ReleaseTest.java` и существующий `LoreFmApplicationTests.java`.

**Interfaces:**

- Produces: `ReleaseId(String value)` с JSON scalar serialization; `Artist(String name)`; `Artwork(String url, Integer width, Integer height, Provider provider)`; `ReleaseSource(Provider provider, String externalId, String externalUrl)`.
- Produces: `Release(ReleaseId id, String title, List<Artist> artists, Integer releaseYear, ReleaseType type, Artwork artwork, List<ReleaseSource> sources)`.
- Produces: Provider и ReleaseType enum values ровно из SPEC.md §§9,12.

- [x] Написать ReleaseTest: исходные artists/sources списки нельзя использовать для изменения Release после создания; корректный null artwork/year допустим; пустые id/title запрещены; публичная сериализация id даёт `"spotify:123"`, не объект.
- [x] Выполнить `mvn.cmd -B -Dtest=ReleaseTest test`; подтвердить ожидаемый провал до реализации.
- [x] Реализовать records и defensive list copies. Не добавлять JPA аннотации и сложную Artist identity.
- [x] Удалить два Redis starters из pom.xml: runtime и test. Сохранить уже выбранные Actuator, OpenAPI и Prometheus; добавить managed Caffeine dependency для task 2. Не менять версии Java/Spring Boot.
- [x] Исправить проверку `(Get-Item $MAVEN_M2_PATH).Target[0]` в mvnw.cmd: сначала проверить существование Target, затем индексировать. Исходное воспроизведение: `powershell -NoProfile` возвращает null Target для обычной папки `.m2`, wrapper падает с `Cannot index into a null array`.
- [x] Выполнить `mvn.cmd -B test` и `.\mvnw.cmd -B test`; оба завершаются BUILD SUCCESS, Spring context не требует Redis.

### Task 2: Validated search endpoint with isolated provider failures

**Files:** application/api files из File map; `application.properties`; tests `release/application/ReleaseSearchServiceTest.java`, `release/api/ReleaseControllerTest.java` под `src/test/java/ru/yofujitsu/lorefm`.

**Interfaces:**

- Consumes: domain records из task 1.
- Produces: `ReleaseSearchQuery(String query, int limit, Set<Provider> providers)`; пустой set означает все доступные providers.
- Produces: `ProviderRelease(Release release)` — нормализованный результат provider до cross-provider resolution.
- Produces: `ReleaseProvider.provider(): Provider`, `ReleaseProvider.search(ReleaseSearchQuery query): List<ProviderRelease>`.
- Produces: `ReleaseSearchService.search(ReleaseSearchQuery query): List<Release>`.
- Produces: `GET /api/v1/releases/search?q=...&limit=10&providers=SPOTIFY` → `SearchResponse(String query, List<Release> results)`.
- Produces: `ApiError(String code, String message)`; invalid q → `INVALID_SEARCH_QUERY`, invalid limit → `INVALID_SEARCH_LIMIT`, invalid providers → `INVALID_SEARCH_PROVIDER`.

- [x] Написать service/controller tests с локальными fake ReleaseProvider: пустой, однобуквенный, 201 code point query, неверный limit и provider дают 400; providers не вызываются. Корректный query strip сохраняет внутренние Unicode/пунктуацию; default limit 10; limit 20 разрешён.
- [x] В тех же tests проверить: выбранный provider вызывается, остальные нет; ошибка одного не скрывает успешный результат другого; пустой список providers и полный отказ дают 200 с пустым results; ни ошибка, ни response не содержат текст внутреннего исключения/секрет.
- [x] Выполнить `mvn.cmd -B -Dtest=ReleaseSearchServiceTest,ReleaseControllerTest test`; подтвердить ожидаемый провал.
- [x] Реализовать контракт, service, controller и advice. Текущий этап сохраняет provider order; глобальные normalization/deduplication/ranking остаются milestone 8. Не добавлять интерфейс для единственного search service.
- [x] Добавить ограниченный Caffeine cache успешных provider результатов: максимум 1000 entries, TTL 15 минут, key включает provider, query и limit. Не кэшировать failures; успешная пустая выдача может кэшироваться. TTL/size конфигурируются. Токен кэшируется отдельно в task 3.
- [x] Дополнить service test: повторный успешный запрос не вызывает provider повторно; ошибка не превращается в кэшированную пустую выдачу; различный query/limit имеет отдельный cache key.
- [x] Выполнить targeted tests и `mvn.cmd -B test`; подтвердить pass.

### Task 3: Spotify provider and client credentials

**Files:** Spotify files из File map; `.env.example`, `.gitignore`, `application.properties`; tests `provider/spotify/SpotifyProviderTest.java`, `provider/spotify/SpotifyTokenClientTest.java` и `src/test/resources/spotify/search.json`.

**Interfaces:**

- Consumes: ReleaseProvider и ReleaseSearchQuery из task 2.
- Produces: SpotifyProvider implements ReleaseProvider; внешние DTO доступны только пакету provider.spotify.
- Produces: `SpotifyProperties`: enabled, clientId, clientSecret, market, requestTimeout (1000 ms), apiBaseUrl, tokenUrl. Production defaults используют официальные HTTPS endpoints; tests подставляют loopback mock server.
- Produces: `SpotifyTokenClient.accessToken(long deadlineNanos): String` и `invalidateToken(): void`; token не пересекает provider boundary.

- [x] Написать tests на локальном JDK HttpServer (без новой mock-server dependency): POST token с `grant_type=client_credentials`, Basic credentials и form content type; GET `/v1/search` с `type=album`, configured market, limit не более 10 и Bearer token.
- [x] Проверить маппинг: provider-qualified id, все artists, год из даты с precision year/month/day, album/single/compilation/unknown types, максимальная доступная обложка без обрезки, original title/artist, singleton Spotify source с external URL. Пустые images → null artwork, неверная дата → null year, повреждённый отдельный item пропускается.
- [x] Проверить cache токена и его обновление до expiry; 401 инвалидирует cache для следующего запроса; 403/429/5xx/timeout не раскрывают response body или credentials. Без credentials и при enabled=false внешних запросов нет.
- [x] Проверить concurrent token acquisition и общий deadline: ожидание token lock также ограничено; token HTTP + search HTTP не превышают configured provider budget с разумным допуском test runtime. Не тестировать стенное время по слишком жёсткой границе в загруженном CI.
- [x] Выполнить `mvn.cmd -B -Dtest=SpotifyProviderTest,SpotifyTokenClientTest test`; подтвердить ожидаемый провал.
- [x] Реализовать RestClient с JDK HTTP factory, connection/read timeouts и оставшимся monotonic deadline для каждого HTTP запроса. Применить bounded ожидание token lock; кешировать token в памяти с expiry safety margin. Не добавлять OAuth login, Redis, общий retry framework или немедленный retry на 429.
- [x] Логировать только provider name, HTTP status/error category, duration; provider response bodies, Basic/Bearer headers и exception message внешнего API не логировать.
- [x] Добавить backend env bindings. `.env.example` содержит только placeholders; `.env`, `.env.*` исключить из git с исключением `.env.example`. Пояснить, что Maven/Spring не загружает `.env` автоматически: в README использовать PowerShell environment variables.
- [x] Выполнить targeted tests и `mvn.cmd -B test`; подтвердить pass.

### Task 4: React search screen connected to the backend

**Files:** frontend files из File map; новая соседняя папка.

**Interfaces:**

- Consumes: JSON SearchResponse/Release из tasks 1–3.
- Produces: `searchReleases(query: string, signal: AbortSignal): Promise<SearchResponse>`; relative `/api/v1/releases/search` URL.
- Produces: экран Ninefold с input, status, release artwork/title/artists/year/type и ссылкой на source.

- [x] Создать минимальный React/TypeScript/Vite проект, зафиксировать package-lock.json, настроить `/api` proxy на `http://localhost:8080` и Vitest/RTL. Выбрать поддерживаемые Node 22 версии пакетов; не добавлять state/routing/UI библиотеки.
- [x] Написать App.test.tsx: до двух символов и до истечения 400 ms нет fetch; query со специальными символами правильно encoded; после debounce выдача отображается; missing artwork даёт placeholder; transport error и пустая выдача имеют разные состояния.
- [x] Добавить сценарий гонки: slow response для старого query не меняет выдачу нового; очищение ввода отменяет запрос и очищает results; unmount отменяет запрос. Использовать fake timers и controlled promises.
- [x] Выполнить `npm.cmd test -- --run`; подтвердить провал до реализации поведения.
- [x] Реализовать API types, fetch и React hooks с AbortController и защитой от stale responses. Только React text rendering; никакого dangerouslySetInnerHTML. Для source links принимать только HTTPS URLs, добавить `rel="noopener noreferrer"`.
- [x] Сделать responsive страницу: заголовок Ninefold и `Nine releases. One taste.`, доступный label input, видимые keyboard focus, loading/error/empty status, сетка результатов. Указать источник ссылкой/атрибуцией рядом с метаданными; обложку показывать целиком. Board и неработающие share/export controls не добавлять.
- [x] Выполнить `npm.cmd test -- --run` и `npm.cmd run build`; подтвердить pass и успешную TypeScript проверку.

### Task 5: Running instructions and verification of the vertical slice

**Files:** backend/frontend README.md, при необходимости regression tests предыдущих tasks.

**Interfaces:**

- Consumes: готовые backend и frontend.
- Produces: воспроизводимый запуск из двух терминалов и проверяемый пользовательский поиск.

- [x] Описать backend `mvn.cmd spring-boot:run`, frontend `npm.cmd install` / `npm.cmd run dev`, адреса 8080/5173 и локальный proxy. Отдельно описать Spotify client credentials как backend env variables без реальных значений.
- [x] Выполнить `mvn.cmd -B verify`; сохранить число тестов и итог BUILD SUCCESS.
- [x] Выполнить frontend `npm.cmd test -- --run` и `npm.cmd run build`; сохранить итог.
- [x] Запустить приложения и проверить в браузере: поиск, loading, empty, error, Unicode query, переход к source, узкий viewport и keyboard navigation. Использовать локальный Spotify mock для end-to-end проверки без секретов; live запрос выполнять только при уже настроенных credentials.
- [x] Проверить HTTP endpoint: q длины 1 → 400 со стабильным code; валидный q → 200 в ожидаемом domain формате; shutdown/mock timeout не обрушает backend.
- [x] Проверить, что frontend bundle, HTTP responses и git changes не содержат provider credentials/tokens; проверить `git diff --check` и финальный diff.
- [x] В отчёте разделить подтверждённый поиск через mock и live Spotify проверку. Если credentials отсутствуют, явно сообщить, что live поиск не проверен.

## Deferred coverage and next milestones

Текущий план покрывает domain foundation и Spotify vertical slice. Top 9 editing/reordering, nickname/themes и localStorage — milestones 3–4; versioned compressed share URL и read-only view — milestone 5; PNG — milestone 6; MusicBrainz — milestone 7; global normalization/deduplication/ranking — milestone 8. Таймауты, token cache, базовый search cache и безопасная изоляция отказов включены уже здесь, поскольку без них первая интеграция не готова к использованию. SoundCloud/YouTube/Genius и deployment находятся в следующих этапах исходной спеки.

Открытые решения о nickname, duplicates, неполной сетке, editions, выборе artwork и sharing payload принимаются при планировании соответствующего milestone и не блокируют domain/search.

## Initial inspection evidence

- 2026-10-05: проект содержит только Spring Boot application, properties и contextLoads test; frontend отсутствует.
- Java 21.0.7, Node 22.22.2, npm 10.9.7 доступны.
- `mvn.cmd -B test`: BUILD SUCCESS, 1 test, 0 failures, 0 errors.
- `.\mvnw.cmd -B test`: исходный wrapper падает на null Target в Windows PowerShell до запуска Maven; исправление предусмотрено task 1.
- Git repository ещё без commits; исходные пользовательские файлы уже staged. Не включать их в автоматический initial commit, не менять существующий index. По завершении оставить изменения для review, если пользователь отдельно не попросит commit.

## Verified external references

- [Spring Boot 4.1.1 system requirements](https://docs.spring.io/spring-boot/system-requirements.html): существующий Java 21 подходит.
- [Spring RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html): imperative REST client для Spring MVC приложения.
- [Spotify Client Credentials](https://developer.spotify.com/documentation/web-api/tutorials/client-credentials-flow): server-side authentication.
- [Spotify Search](https://developer.spotify.com/documentation/web-api/reference/search): текущий maximum limit 10, market и album response fields.
- [Spotify February 2026 changes](https://developer.spotify.com/documentation/web-api/references/changes/february-2026): изменение search limit и удалённые поля; на эти поля не опираться при маппинге.

## Plan self-review

- Scope ограничен milestones 1–2; остальные требования явно сопоставлены последующим milestones.
- JSON Release/SearchResponse и provider signatures согласованы между tasks.
- Каждый Review Focus имеет конкретную проверку в соответствующем task.
- Стартовый backend package, Java/Spring версии и пользовательский git index сохраняются.
- Execution complete: пользователь выбрал native execution; tasks 1–5 выполнены, независимое ревью проведено, замечания исправлены. Итог: 21 backend тест и 6 frontend тестов, сборки проходят. Подробности в 2026-10-05-ninefold-search-progress.md.
