package com.chinaex123.shipping_box.web;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.network.PacketEditorReadFile;
import com.chinaex123.shipping_box.network.PacketEditorReloadRequest;
import com.chinaex123.shipping_box.network.PacketEditorSaveRules;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WebEditorLocalServer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile HttpServer httpServer;
    private static volatile int boundPort = -1;
    private static volatile String token;

    private WebEditorLocalServer() {}

    public static synchronized String start(String desiredToken) {
        if (desiredToken == null || desiredToken.isBlank()) {
            token = generateToken();
        } else {
            token = desiredToken;
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
            throw new IllegalStateException("Failed to bind web editor to localhost ports 38888-38950");
        }

        created.createContext("/", new StaticHtmlHandler());
        created.createContext("/api/rules", new RulesHandler());
        created.createContext("/api/load", new LoadHandler());
        created.createContext("/api/save", new SaveHandler());
        created.createContext("/api/reload", new ReloadHandler());
        created.setExecutor(Executors.newCachedThreadPool());
        created.start();

        httpServer = created;
        boundPort = port;
        return buildUrl();
    }

    public static synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        boundPort = -1;
    }

    public static synchronized String rotateToken() {
        token = generateToken();
        return token;
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

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = part.substring(0, idx);
            String value = part.substring(idx + 1);
            try {
                map.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
            } catch (Exception e) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static String getRequestedFile(URI uri) {
        String file = parseQuery(uri).getOrDefault("file", "editor.json");
        if (file == null || file.isBlank()) {
            return "editor.json";
        }
        return file;
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

            try (InputStream in = WebEditorLocalServer.class.getResourceAsStream("/assets/shipping_box/web/index.html")) {
                if (in == null) {
                    writeText(exchange, 500, "Missing web resource: /assets/shipping_box/web/index.html");
                    return;
                }
                byte[] bytes = readAllBytes(in);
                writeResponse(exchange, 200, "text/html; charset=utf-8", bytes);
            }
        }
    }

    private static final class LoadHandler implements HttpHandler {
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

            String file = getRequestedFile(exchange.getRequestURI());
            String requestId = UUID.randomUUID().toString();
            CompletableFuture<WebEditorRequestTracker.Response> future = WebEditorRequestTracker.create(requestId);
            PacketDistributor.sendToServer(new PacketEditorReadFile(requestId, file));

            WebEditorRequestTracker.Response response;
            try {
                response = future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                writeText(exchange, 504, "Timed out waiting for server");
                return;
            }

            if (!response.ok()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("ok", false);
                obj.addProperty("error", response.error());
                writeJson(exchange, 200, obj);
                return;
            }

            String content = response.payload();
            try {
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                writeJson(exchange, 200, obj);
            } catch (Exception e) {
                JsonObject fallback = new JsonObject();
                fallback.addProperty("ok", false);
                fallback.addProperty("error", "Invalid JSON in file: " + file);
                fallback.addProperty("raw", content);
                writeJson(exchange, 200, fallback);
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

            String file = getRequestedFile(exchange.getRequestURI());
            String requestId = UUID.randomUUID().toString();
            CompletableFuture<WebEditorRequestTracker.Response> future = WebEditorRequestTracker.create(requestId);
            PacketDistributor.sendToServer(new PacketEditorSaveRules(requestId, file, GSON.toJson(obj)));

            WebEditorRequestTracker.Response response;
            try {
                response = future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                writeText(exchange, 504, "Timed out waiting for server");
                return;
            }

            JsonObject res = new JsonObject();
            res.addProperty("ok", response.ok());
            if (response.ok()) {
                res.addProperty("savedPath", response.payload());
            } else {
                res.addProperty("error", response.error());
            }
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

            PacketDistributor.sendToServer(new PacketEditorReloadRequest());

            JsonObject res = new JsonObject();
            res.addProperty("ok", true);
            writeJson(exchange, 200, res);
        }
    }
}
