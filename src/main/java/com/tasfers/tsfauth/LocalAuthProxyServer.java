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
    private static HttpServer server;
    private static int activePort = 52495;

    public static String signaturePublicKey = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiTQWGROyOUUvjEzOaAdp\n" +
            "VT6DOaW5WweMeKIbsm29Q9sEaDKgwyUdRzU67aTBsZpE8IKVyVEdnyxIqHA9pdN4\n" +
            "BvAssQVqYuJFsEVsOXgB/F8ESyrenOGkLCGWvT0beXew6RKktGTNTapYsuZwVx0a\n" +
            "74rGxwAWWUD6bWPH+JfEvNXyStJUNr1rrwjjtlLAqD4zE6e0Y7hc0FOwVoozvmTq\n" +
            "5u4mCMnFezPxIeVMwt9v0krwDQOm4iwMr6l9YQR/7B59vgrAufGNArXpYgGCJ95a\n" +
            "uBoJDp/QCFQK+UIDcKzQ0Vg0DqtAxPEJMRCTN/6cSaZ6FHwmvFMNjT3VSq5kPxGL\n" +
            "jwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    public static int getActivePort() {
        return activePort;
    }

    public static void start() {
        if (server != null) return;
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

    static class ProxyHandler implements HttpHandler {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/authlib-injector") || path.equals("/inj") || path.equals("/auth") || path.equals("/authserver")) {
                serveMetadata(exchange);
                return;
            }

            forwardRequest(exchange);
        }

        private void serveMetadata(HttpExchange exchange) throws IOException {
            try {
                String host = TsfAuthPreLaunch.getAuthHost();
                String protocol = (host.contains("localhost") || host.contains("127.0.0.1")) ? "http://" : "https://";
                String remoteUrl = protocol + host + "/authlib-injector";

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(remoteUrl))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (json.has("signaturePublickey")) {
                        signaturePublicKey = json.get("signaturePublickey").getAsString();
                    }
                }
            } catch (Exception ignored) {}

            String host = TsfAuthPreLaunch.getAuthHost();
            String domain = host.split(":")[0];

            String responseBody = "{\n" +
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

            byte[] bytes = responseBody.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private void forwardRequest(HttpExchange exchange) throws IOException {
            String host = TsfAuthPreLaunch.getAuthHost();
            String protocol = (host.contains("localhost") || host.contains("127.0.0.1")) ? "http://" : "https://";
            String remoteUrl = protocol + host + exchange.getRequestURI().toString();

            try {
                byte[] requestBodyBytes = exchange.getRequestBody().readAllBytes();

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
                    if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Length")) {
                        for (String val : values) {
                            reqBuilder.header(name, val);
                        }
                    }
                });

                HttpResponse<byte[]> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = resp.statusCode();

                if (status >= 500) {
                    serveOfflineError(exchange);
                    return;
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

            } catch (Exception e) {
                serveOfflineError(exchange);
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
