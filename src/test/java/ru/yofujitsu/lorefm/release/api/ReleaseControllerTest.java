package ru.yofujitsu.lorefm.release.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.yofujitsu.lorefm.release.application.*;
import ru.yofujitsu.lorefm.release.domain.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReleaseControllerTest {
    MockMvc mvc;
    AtomicInteger calls = new AtomicInteger();
    int requestedLimit;

    @BeforeEach
    void setup() {
        ReleaseProvider source = new ReleaseProvider() {
            public Provider provider() { return Provider.SPOTIFY; }
            public List<ProviderRelease> search(ReleaseSearchQuery q) {
                calls.incrementAndGet();
                requestedLimit = q.limit();
                if (q.query().equals("fail")) throw new IllegalStateException("private-provider-secret");
                if (q.query().equals("empty")) return List.of();
                return List.of(new ProviderRelease(new Release(new ReleaseId("spotify:123"), "Cold Visions",
                        List.of(new Artist("Bladee")), 2024, ReleaseType.ALBUM, null, List.of())));
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(new ReleaseController(new ReleaseSearchService(List.of(source), Duration.ofMinutes(15), 1000, Duration.ofSeconds(2), Duration.ofSeconds(30))))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void rejectsUnicodeWhitespaceOnlyQueriesBeforeProviderCalls() throws Exception {
        for (String query : List.of("\u00a0\u00a0", "\u202f\u202f", "\u0085\u0085", "a\u001c")) {
            mvc.perform(get("/api/v1/releases/search").param("q", query))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        }
        assertThat(calls.get()).isZero();
    }

    @Test
    void rejectsInvalidParametersBeforeCallingProviders() throws Exception {
        for (String q : List.of("", " ", "a", "x".repeat(201))) {
            mvc.perform(get("/api/v1/releases/search").param("q", q)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        }
        mvc.perform(get("/api/v1/releases/search")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        for (String limit : List.of("0", "21", "abc", "9999999999999")) {
            mvc.perform(get("/api/v1/releases/search").param("q", "ab").param("limit", limit))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH_LIMIT"));
        }
        for (String providers : List.of("invalid", "", "SPOTIFY,")) {
            mvc.perform(get("/api/v1/releases/search").param("q", "ab").param("providers", providers))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH_PROVIDER"));
        }
        assertThat(calls.get()).isZero();
    }

    @Test
    void returnsDomainJsonAndDefaultsLimit() throws Exception {
        mvc.perform(get("/api/v1/releases/search").param("q", "  Cold Visions  ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Cold Visions"))
                .andExpect(jsonPath("$.results[0].id").value("spotify:123"))
                .andExpect(jsonPath("$.results[0].artists[0].name").value("Bladee"));
        assertThat(requestedLimit).isEqualTo(10);
        mvc.perform(get("/api/v1/releases/search").param("q", "ab").param("limit", "20")).andExpect(status().isOk());
        assertThat(requestedLimit).isEqualTo(20);
    }

    @Test
    void exposesAvailabilityWithoutMistakingHealthyEmptyResultsForFailure() throws Exception {
        mvc.perform(get("/api/v1/releases/search").param("q", "empty"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results").isEmpty())
                .andExpect(jsonPath("$.partial").value(false)).andExpect(jsonPath("$.unavailable").value(false));
        mvc.perform(get("/api/v1/releases/search").param("q", "fail"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partial").value(false))
                .andExpect(jsonPath("$.unavailable").value(true));
    }

    @Test
    void hidesInternalErrorsAndSkipsUnimplementedProvider() throws Exception {
        mvc.perform(get("/api/v1/releases/search").param("q", "fail")).andExpect(status().isOk())
                .andExpect(content().json("{\"query\":\"fail\",\"results\":[]}"));
        mvc.perform(get("/api/v1/releases/search").param("q", "ab").param("providers", "YOUTUBE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results").isEmpty());
        assertThat(calls.get()).isEqualTo(1);
    }
}
