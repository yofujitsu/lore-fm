package ru.yofujitsu.lorefm.provider.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class SpotifyMockServer implements AutoCloseable {
    final HttpServer server;
    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    SpotifyMockServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.start();
    }
    String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    SpotifyProperties properties() {
        var p = new SpotifyProperties();
        p.setClientId("test-id"); p.setClientSecret("test-secret");
        p.setApiBaseUrl(url() + "/v1"); p.setTokenUrl(url() + "/api/token");
        return p;
    }
    static void json(HttpExchange exchange, int status, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
    public void close() { server.stop(0); executor.shutdownNow(); }
}
