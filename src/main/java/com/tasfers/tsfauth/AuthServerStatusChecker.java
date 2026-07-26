package com.tasfers.tsfauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuthServerStatusChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("tsf-auth-status");
    public static volatile boolean isOnline = true;
    
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TsfAuth-StatusChecker");
        t.setDaemon(true);
        return t;
    });

    public static void start() {
        SCHEDULER.scheduleWithFixedDelay(AuthServerStatusChecker::checkStatus, 0, 10, TimeUnit.SECONDS);
    }

    public static CompletableFuture<Void> triggerCheck() {
        return CompletableFuture.runAsync(AuthServerStatusChecker::checkStatus);
    }

    public static void checkStatus() {
        String host = TsfAuthPreLaunch.getAuthHost();
        if (host == null || host.isEmpty()) {
            isOnline = false;
            return;
        }

        String protocol = (host.contains("localhost") || host.contains("127.0.0.1")) ? "http://" : "https://";
        String bypassHost = host;
        if (host.contains("localhost")) {
            bypassHost = host.replace("localhost", "127.0.0.1");
        } else if (host.contains("127.0.0.1")) {
            bypassHost = host.replace("127.0.0.1", "localhost");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(protocol + bypassHost + "/"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            // Server error 5xx or CF 501 means offline
            isOnline = (code >= 200 && code < 500);
        } catch (Exception e) {
            isOnline = false;
        }
    }
}
