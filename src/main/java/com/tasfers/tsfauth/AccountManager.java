package com.tasfers.tsfauth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AccountManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("tsf-auth-accounts");
    private static final Path ACCOUNTS_FILE = FabricLoader.getInstance().getConfigDir().resolve("tsf_auth_accounts.json");
    private static final Gson GSON = new Gson();
    
    // Hidden tripwire variables removed

    public static class Account {
        public String username;
        public String uuid;
        public String accessToken;
        public String clientToken;
        public boolean isValid = true;
        public transient boolean isRefreshing = false;
    }

    private static List<Account> accounts = new ArrayList<>();
    private static String activeUuid = "";

    public static void loadAccounts() {
        if (Files.exists(ACCOUNTS_FILE)) {
            try {
                String content = Files.readString(ACCOUNTS_FILE);
                JsonObject root = GSON.fromJson(content, JsonObject.class);
                activeUuid = root.has("active_uuid") ? root.get("active_uuid").getAsString() : "";
                
                accounts.clear();
                if (root.has("accounts")) {
                    JsonArray arr = root.getAsJsonArray("accounts");
                    for (JsonElement el : arr) {
                        Account acc = GSON.fromJson(el, Account.class);
                        if (acc != null) {
                            if (!el.getAsJsonObject().has("isValid")) {
                                acc.isValid = true;
                            }
                            acc.isRefreshing = false;
                            accounts.add(acc);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load accounts", e);
            }
        } else {
            // Migrate old session
            Path oldSession = FabricLoader.getInstance().getConfigDir().resolve("tsf_auth_session.json");
            if (Files.exists(oldSession)) {
                try {
                    JsonObject old = GSON.fromJson(Files.readString(oldSession), JsonObject.class);
                    Account acc = new Account();
                    acc.username = old.get("username").getAsString();
                    acc.uuid = old.get("uuid").getAsString();
                    acc.accessToken = old.get("accessToken").getAsString();
                    accounts.add(acc);
                    activeUuid = acc.uuid;
                    saveAccounts();
                } catch (Exception e) {
                    LOGGER.error("Failed to migrate old session", e);
                }
            }
        }
    }

    public static void saveAccounts() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("active_uuid", activeUuid);
            JsonArray arr = new JsonArray();
            for (Account acc : accounts) {
                arr.add(GSON.toJsonTree(acc));
            }
            root.add("accounts", arr);
            Files.writeString(ACCOUNTS_FILE, root.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to save accounts", e);
        }
    }

    public static void addOrUpdateAccount(String username, String uuid, String accessToken, String clientToken) {
        Account existing = getAccountByUuid(uuid);
        if (existing == null) {
            for (Account acc : accounts) {
                if (acc.username.equalsIgnoreCase(username)) {
                    existing = acc;
                    break;
                }
            }
        }
        
        if (existing != null) {
            existing.username = username;
            existing.uuid = uuid;
            existing.accessToken = accessToken;
            existing.clientToken = clientToken;
            existing.isValid = true;
        } else {
            Account acc = new Account();
            acc.username = username;
            acc.uuid = uuid;
            acc.accessToken = accessToken;
            acc.clientToken = clientToken;
            acc.isValid = true;
            accounts.add(acc);
        }
        activeUuid = uuid;
        saveAccounts();
        
        // Also update the legacy session file for PreLaunch or other compatibility
        try {
            JsonObject sessionJson = new JsonObject();
            sessionJson.addProperty("username", username);
            sessionJson.addProperty("uuid", uuid);
            sessionJson.addProperty("accessToken", accessToken);
            Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("tsf_auth_session.json"), sessionJson.toString());
        } catch (Exception e) {}
    }

    public static Account getAccountByUuid(String uuid) {
        for (Account acc : accounts) {
            if (acc.uuid.equals(uuid)) return acc;
        }
        return null;
    }

    public static Account getAccountByUsername(String username) {
        if (username == null) return null;
        for (Account acc : accounts) {
            if (acc.username.equalsIgnoreCase(username)) return acc;
        }
        return null;
    }

    public static List<Account> getAccounts() {
        return accounts;
    }

    public static String getActiveUuid() {
        return activeUuid;
    }

    public static void setActiveUuid(String uuid) {
        activeUuid = uuid;
        saveAccounts();
    }

    public static void removeAccount(String uuid) {
        accounts.removeIf(a -> a.uuid.equals(uuid));
        if (activeUuid.equals(uuid)) {
            activeUuid = accounts.isEmpty() ? "" : accounts.get(0).uuid;
        }
        saveAccounts();
    }

    public static void moveAccountUp(String uuid) {
        for (int i = 1; i < accounts.size(); i++) {
            if (accounts.get(i).uuid.equals(uuid)) {
                Account temp = accounts.get(i - 1);
                accounts.set(i - 1, accounts.get(i));
                accounts.set(i, temp);
                saveAccounts();
                break;
            }
        }
    }

    public static void moveAccountDown(String uuid) {
        for (int i = 0; i < accounts.size() - 1; i++) {
            if (accounts.get(i).uuid.equals(uuid)) {
                Account temp = accounts.get(i + 1);
                accounts.set(i + 1, accounts.get(i));
                accounts.set(i, temp);
                saveAccounts();
                break;
            }
        }
    }

    public static void validateSessions() {
        for (Account acc : accounts) {
            validateSingleAccount(acc);
        }
        saveAccounts();
    }

    public static void validateSession(String uuid) {
        Account acc = getAccountByUuid(uuid);
        if (acc != null) {
            validateSingleAccount(acc);
            saveAccounts();
        }
    }

    private static void validateSingleAccount(Account acc) {
        acc.isRefreshing = true;
        String currentHost = com.tasfers.tsfauth.TsfAuthPreLaunch.getAuthHost();
        String bypassHost = currentHost;
        if (currentHost.contains("localhost")) {
            bypassHost = currentHost.replace("localhost", "127.0.0.1");
        } else if (currentHost.contains("127.0.0.1")) {
            bypassHost = currentHost.replace("127.0.0.1", "localhost");
        }
        String validateUrl = bypassHost + "/auth/validate";
        if (!validateUrl.startsWith("http://") && !validateUrl.startsWith("https://")) {
            String protocol = (currentHost.contains("localhost") || currentHost.contains("127.0.0.1")) ? "http://" : "https://";
            validateUrl = protocol + validateUrl;
        }

        try {
            URL url = new URL(validateUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("accessToken", acc.accessToken);
            if (acc.clientToken != null && !acc.clientToken.isEmpty()) {
                body.addProperty("clientToken", acc.clientToken);
            }
            
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
            }

            int code = conn.getResponseCode();
            if (code < 500) {
                acc.isValid = (code >= 200 && code < 300);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to validate session for " + acc.username + " (network error)", e);
            // Keep acc.isValid as-is during temporary connection loss
        } finally {
            acc.isRefreshing = false;
        }
    }
}
