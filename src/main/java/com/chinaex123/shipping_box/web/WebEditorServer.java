package com.chinaex123.shipping_box.web;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.Executors;

public final class WebEditorServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebEditorServer.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Path RULES_DIR = FMLPaths.CONFIGDIR.get().resolve("shipping_box/exchange_rules");
    private static final Path EDITOR_RULES_FILE = RULES_DIR.resolve("editor.json");

    private static volatile HttpServer httpServer;
    private static volatile int boundPort = -1;
    private static volatile String token;
    private static volatile MinecraftServer minecraftServer;

    private WebEditorServer() {}

    public static synchronized String start(MinecraftServer server) {
        minecraftServer = server;
        if (token == null) {
            token = generateToken();
        }
        if (httpServer != null) {
            return buildUrl();
        }

        int port = 38888;
        HttpServer created = null;
        while (port <= 38950) {
            try {
                created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
                break;
            } catch (IOException e) {
                port++;
            }
        }
        if (created == null) {
            throw new IllegalStateException("Failed to bind web editor server to localhost ports 38888-38950");
        }

        created.createContext("/", new StaticHtmlHandler());
        created.createContext("/api/rules", new RulesHandler());
        created.createContext("/api/save", new SaveHandler());
        created.createContext("/api/reload", new ReloadHandler());
        created.setExecutor(Executors.newCachedThreadPool());
        created.start();

        httpServer = created;
        boundPort = port;

        LOGGER.info("[Shipping Box] Web editor available at {}", buildUrl());
        return buildUrl();
    }

    public static synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        boundPort = -1;
    }

    public static boolean isRunning() {
        return httpServer != null;
    }

    private static String buildUrl() {
        return "http://127.0.0.1:" + boundPort + "/?token=" + token;
    }

    private static String generateToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isAuthorized(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return false;
        }
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = part.substring(0, idx);
            String value = part.substring(idx + 1);
            if ("token".equals(key) && Objects.equals(value, token)) {
                return true;
            }
        }
        return false;
    }

    private static void writeResponse(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void writeJson(HttpExchange exchange, int status, JsonElement element) throws IOException {
        byte[] bytes = GSON.toJson(element).getBytes(StandardCharsets.UTF_8);
        writeResponse(exchange, status, "application/json; charset=utf-8", bytes);
    }

    private static void writeText(HttpExchange exchange, int status, String text) throws IOException {
        writeResponse(exchange, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    private static final class StaticHtmlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeText(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange.getRequestURI())) {
                writeText(exchange, 401, "Unauthorized");
                return;
            }

            try (InputStream in = WebEditorServer.class.getResourceAsStream("/assets/shipping_box/web/index.html")) {
                if (in == null) {
                    writeText(exchange, 500, "Missing web resource: /assets/shipping_box/web/index.html");
                    return;
                }
                byte[] bytes = readAllBytes(in);
                writeResponse(exchange, 200, "text/html; charset=utf-8", bytes);
            }
        }
    }

    private static final class RulesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeText(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange.getRequestURI())) {
                writeText(exchange, 401, "Unauthorized");
                return;
            }
            String rulesJson = ExchangeRecipeManager.serializeRulesToJson();
            try {
                JsonObject obj = JsonParser.parseString(rulesJson).getAsJsonObject();
                writeJson(exchange, 200, obj);
            } catch (Exception e) {
                JsonObject fallback = new JsonObject();
                fallback.addProperty("rulesJson", rulesJson);
                writeJson(exchange, 200, fallback);
            }
        }
    }

    private static final class SaveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeText(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange.getRequestURI())) {
                writeText(exchange, 401, "Unauthorized");
                return;
            }

            String body = new String(readAllBytes(exchange.getRequestBody()), StandardCharsets.UTF_8);
            JsonObject obj;
            try {
                obj = JsonParser.parseString(body).getAsJsonObject();
            } catch (JsonParseException e) {
                writeText(exchange, 400, "Invalid JSON");
                return;
            }

            if (!obj.has("rules") || !obj.get("rules").isJsonArray()) {
                writeText(exchange, 400, "Expected {\"rules\": [...]}");
                return;
            }

            Files.createDirectories(RULES_DIR);
            Files.writeString(EDITOR_RULES_FILE, GSON.toJson(obj), StandardCharsets.UTF_8);

            JsonObject res = new JsonObject();
            res.addProperty("ok", true);
            res.addProperty("path", EDITOR_RULES_FILE.toString());
            writeJson(exchange, 200, res);
        }
    }

    private static final class ReloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeText(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange.getRequestURI())) {
                writeText(exchange, 401, "Unauthorized");
                return;
            }

            MinecraftServer server = minecraftServer;
            if (server == null) {
                writeText(exchange, 503, "Server not ready");
                return;
            }

            server.execute(() -> {
                try {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
                } catch (Exception e) {
                    LOGGER.warn("[Shipping Box] Failed to run /reload from web editor");
                }
            });

            JsonObject res = new JsonObject();
            res.addProperty("ok", true);
            res.addProperty("queued", true);
            writeJson(exchange, 200, res);
        }
    }
}
