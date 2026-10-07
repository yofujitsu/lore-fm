package ru.yofujitsu.lorefm.provider;

import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.yofujitsu.lorefm.release.application.ProviderUnavailableException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Consumer;

/** Shared bounded transport for the optional catalogs; credentials stay in headers or form bodies. */
public final class CatalogHttp {
    private final HttpClient client;
    public CatalogHttp(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMillis(1500)) > 0)
            throw new IllegalArgumentException("Catalog timeout must be between 1 and 1500 ms");
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public static long remaining(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) throw new ProviderUnavailableException(null);
        return remaining;
    }
    public JsonNode get(URI uri, Consumer<HttpHeaders> headers, long deadline) { return exchange(HttpMethod.GET, uri, headers, null, deadline); }
    public JsonNode post(URI uri, Consumer<HttpHeaders> headers, MultiValueMap<String, String> body, long deadline) { return exchange(HttpMethod.POST, uri, headers, body, deadline); }
    private JsonNode exchange(HttpMethod method, URI uri, Consumer<HttpHeaders> headers, MultiValueMap<String, String> body, long deadline) {
        try {
            var factory = new JdkClientHttpRequestFactory(client);
            var request = RestClient.builder().requestFactory(factory).defaultHeader("Accept", "application/json")
                .defaultStatusHandler(status -> !status.is2xxSuccessful(), (req, response) -> {
                    int status = response.getStatusCode().value();
                    throw new Failure(status, status == 429 || status == 503
                        ? ProviderUnavailableException.retryAfter(response.getHeaders().getFirst("Retry-After")) : null);
                }).build().method(method).uri(uri).headers(headers);
            if (body != null) request.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body);
            factory.setReadTimeout(Duration.ofNanos(remaining(deadline)));
            var result = request.retrieve().body(JsonNode.class);
            remaining(deadline);
            if (result == null) throw new ProviderUnavailableException(null);
            return result;
        } catch (RestClientException | IllegalArgumentException ex) { throw new ProviderUnavailableException(null); }
    }
    public static final class Failure extends ProviderUnavailableException {
        public final int status;
        public Failure(int status, Duration retry) { super(retry); this.status = status; }
    }
    public static String text(JsonNode node) {
        if (!node.isString()) throw new IllegalArgumentException("Invalid catalog text");
        var text = node.asString().replaceAll("\\p{Cc}", "");
        if (text.isBlank() || text.length() > 2000) throw new IllegalArgumentException("Invalid catalog text");
        return text;
    }
    public static String https(String value) {
        if (value == null || value.length() > 8192) return null;
        try {
            var uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }
}
