package com.tasfers.tsfauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

public class LocalAuthProxyServer {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("tsf-auth-proxy");
    private static HttpServer server;
    private static int activePort = 52495;
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static volatile String cachedMetadataJson = "";
    public static volatile String signaturePublicKey = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjJ+fwTitSJxKbAEBNQM1\n" +
            "cwJvybPqnc83m9vELk1qtDgTUk8NmBhCQykL1iYpWA5NuQWgWhcvYc0obuahm3GM\n" +
            "h5pNkBv0cWHzJZwJwk7d0kxYOdCuPhU5Z/rjFKY28AeKkBsshQw95L0RrKNSMlE6\n" +
            "Pvn/lUN0ZTxzLMvFm5hdDv/vackyEhDccCWk7OytNLV6BjG2qVumICdUjW/KQhZ/\n" +
            "eoXspHTjMAIOZZ0LhBAOpYd9IBOGrVUA2AT0MP5chjCP9xTo1/WA8MhXkdiHqzhM\n" +
            "w2hBvfLp4eugcxESSOaS0jSqaOe8QvncqqhL8dQk9dQhiM4zKkZY3k9/I9g6VqXF\n" +
            "fQIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    private static final java.nio.file.Path METADATA_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("tsf_auth_metadata.json");
    private static volatile boolean isMetadataFresh = false;

    public static int getActivePort() {
        return activePort;
    }

    public static boolean hasFreshMetadata() {
        return isMetadataFresh;
    }

    public static void start() {
        if (server != null) return;

        // Load cached metadata from disk if present
        try {
            if (java.nio.file.Files.exists(METADATA_FILE)) {
                String diskContent = java.nio.file.Files.readString(METADATA_FILE).trim();
                if (!diskContent.isEmpty()) {
                    JsonObject json = JsonParser.parseString(diskContent).getAsJsonObject();
                    if (json.has("signaturePublickey")) {
                        signaturePublicKey = json.get("signaturePublickey").getAsString();
                    }
                    cachedMetadataJson = diskContent;
                }
            }
        } catch (Exception ignored) {}

        // Fetch fresh metadata from remote server
        refreshMetadata();

        for (int p = 52495; p < 52595; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0);
                activePort = p;
                server.createContext("/", new ProxyHandler());
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
                System.out.println("[TsfAuth] Local proxy server started on port " + activePort);
                return;
            } catch (IOException e) {
                // Port occupied, try next
            }
        }
    }

    public static synchronized boolean refreshMetadata() {
        try {
            String host = TsfAuthPreLaunch.getAuthHost();
            if (host == null || host.isEmpty()) return false;
            String protocol = (host.contains("localhost") || host.contains("127.0.0.1")) ? "http://" : "https://";
            String remoteUrl = protocol + host + "/authlib-injector";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(remoteUrl))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isEmpty()) {
                String body = resp.body().trim();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("signaturePublickey")) {
                    signaturePublicKey = json.get("signaturePublickey").getAsString();
                }
                cachedMetadataJson = body;
                isMetadataFresh = true;
                try {
                    java.nio.file.Files.writeString(METADATA_FILE, body);
                } catch (Exception ignored) {}
                LOGGER.info("Successfully fetched and saved auth metadata from " + remoteUrl);
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch remote metadata: " + e.getMessage());
        }
        return false;
    }

    static class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/authlib-injector") || path.equals("/inj") || path.equals("/auth") || path.equals("/authserver") || path.equals("/")) {
                serveMetadata(exchange);
                return;
            }

            forwardRequest(exchange);
        }

        private void serveMetadata(HttpExchange exchange) throws IOException {
            if (cachedMetadataJson == null || cachedMetadataJson.isEmpty()) {
                refreshMetadata();
            }

            String responseBody = cachedMetadataJson;
            if (responseBody == null || responseBody.isEmpty()) {
                String host = TsfAuthPreLaunch.getAuthHost();
                String domain = host.split(":")[0];

                responseBody = "{\n" +
                        "  \"meta\": {\n" +
                        "    \"serverName\": \"tsf\",\n" +
                        "    \"implementationName\": \"tsf-yggdrasil-proxy\",\n" +
                        "    \"implementationVersion\": \"1.0.0\"\n" +
                        "  },\n" +
                        "  \"skinDomains\": [\n" +
                        "    \"" + domain + "\",\n" +
                        "    \"ibb.co\",\n" +
                        "    \".ibb.co\"\n" +
                        "  ],\n" +
                        "  \"signaturePublickey\": " + new com.google.gson.Gson().toJson(signaturePublicKey) + "\n" +
                        "}";
            }

            byte[] bytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private void forwardRequest(HttpExchange exchange) throws IOException {
            byte[] requestBodyBytes = exchange.getRequestBody().readAllBytes();
            boolean success = tryForward(exchange, requestBodyBytes, false);
            if (!success) {
                // If failed, immediately refresh host from remote and retry once
                String oldHost = TsfAuthPreLaunch.activeHostname;
                String refreshedHost = TsfAuthPreLaunch.fetchRemoteHost(true);
                refreshMetadata();
                boolean retried = tryForward(exchange, requestBodyBytes, true);
                if (!retried) {
                    serveOfflineError(exchange);
                }
            }
        }

        private boolean tryForward(HttpExchange exchange, byte[] requestBodyBytes, boolean isRetry) {
            String host = TsfAuthPreLaunch.getAuthHost();
            String protocol = (host.contains("localhost") || host.contains("127.0.0.1")) ? "http://" : "https://";
            String remoteUrl = protocol + host + exchange.getRequestURI().toString();

            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(remoteUrl))
                        .timeout(Duration.ofSeconds(4));

                String method = exchange.getRequestMethod();
                if (method.equalsIgnoreCase("GET")) {
                    reqBuilder.GET();
                } else if (method.equalsIgnoreCase("POST")) {
                    reqBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBodyBytes));
                } else if (method.equalsIgnoreCase("PUT")) {
                    reqBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(requestBodyBytes));
                } else if (method.equalsIgnoreCase("DELETE")) {
                    reqBuilder.DELETE();
                }

                exchange.getRequestHeaders().forEach((name, values) -> {
                    for (String val : values) {
                        try {
                            reqBuilder.header(name, val);
                        } catch (IllegalArgumentException ignored) {
                            // Ignore Java HttpClient restricted headers
                        }
                    }
                });

                HttpResponse<byte[]> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = resp.statusCode();

                if (status >= 500) {
                    return false;
                }

                resp.headers().map().forEach((name, values) -> {
                    if (!name.equalsIgnoreCase("Transfer-Encoding") && !name.equalsIgnoreCase("Content-Length")) {
                        for (String val : values) {
                            exchange.getResponseHeaders().add(name, val);
                        }
                    }
                });

                exchange.sendResponseHeaders(status, resp.body().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp.body());
                }
                return true;

            } catch (Exception e) {
                if (isRetry) {
                    LOGGER.error("Proxy error forwarding request on retry to " + remoteUrl, e);
                }
                return false;
            }
        }

        private void serveOfflineError(HttpExchange exchange) throws IOException {
            String errorJson = "{\n" +
                    "  \"error\": \"AuthenticationUnavailableException\",\n" +
                    "  \"errorMessage\": \"Authentication servers are down. Please try again later.\"\n" +
                    "}";
            byte[] bytes = errorJson.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
