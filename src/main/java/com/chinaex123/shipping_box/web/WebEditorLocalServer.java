package com.chinaex123.shipping_box.web;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.network.PacketEditorReadFile;
import com.chinaex123.shipping_box.network.PacketEditorReloadRequest;
import com.chinaex123.shipping_box.network.PacketEditorSaveRules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.net.ServerSocket;
import java.net.Socket;

public final class WebEditorLocalServer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile ServerSocket serverSocket;
    private static volatile Thread acceptThread;
    private static volatile ExecutorService executor;
    private static volatile int boundPort = -1;
    private static volatile String token;
    private static volatile boolean running = false;

    private WebEditorLocalServer() {}

    public static synchronized String start(String desiredToken) {
        if (desiredToken == null || desiredToken.isBlank()) {
            token = generateToken();
        } else {
            token = desiredToken;
        }

        if (running) {
            return buildUrl();
        }

        int port = 38888;
        ServerSocket created = null;
        while (port <= 38950) {
            try {
                created = new ServerSocket();
                created.setReuseAddress(true);
                created.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
                break;
            } catch (IOException e) {
                if (created != null) {
                    try {
                        created.close();
                    } catch (IOException ignored) {}
                }
                created = null;
                port++;
            } catch (Exception e) {
                if (created != null) {
                    try {
                        created.close();
                    } catch (IOException ignored) {}
                }
                created = null;
                port++;
            }
        }
        if (created == null) {
            throw new IllegalStateException("Failed to bind web editor to localhost ports 38888-38950");
        }

        serverSocket = created;
        boundPort = port;
        running = true;

        executor = Executors.newCachedThreadPool();
        acceptThread = new Thread(WebEditorLocalServer::acceptLoop, "shipping_box_web_editor");
        acceptThread.setDaemon(true);
        acceptThread.start();

        return buildUrl();
    }

    public static synchronized void stop() {
        running = false;
        boundPort = -1;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
            serverSocket = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        acceptThread = null;
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

    private static byte[] readAllBytes(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    private static void acceptLoop() {
        while (running) {
            ServerSocket socket = serverSocket;
            if (socket == null) {
                return;
            }
            try {
                Socket client = socket.accept();
                client.setSoTimeout(8000);
                ExecutorService exec = executor;
                if (exec != null) {
                    exec.execute(() -> handleClient(client));
                } else {
                    client.close();
                }
            } catch (IOException e) {
                if (running) {
                    continue;
                }
                return;
            }
        }
    }

    private static void handleClient(Socket client) {
        try (client;
             InputStream rawIn = new BufferedInputStream(client.getInputStream());
             OutputStream out = client.getOutputStream()) {

            Request req = readRequest(rawIn);
            if (req == null) {
                writeText(out, 400, "Bad Request");
                return;
            }

            URI uri;
            try {
                uri = URI.create(req.pathWithQuery);
            } catch (Exception e) {
                writeText(out, 400, "Bad Request");
                return;
            }

            if ("OPTIONS".equals(req.method)) {
                writeBytes(out, 204, "text/plain; charset=utf-8", new byte[0]);
                return;
            }

            if (!isAuthorized(uri)) {
                writeText(out, 401, "Unauthorized");
                return;
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                writeText(out, 404, "Not Found");
                return;
            }

            if ("GET".equals(req.method) && ("/".equals(path) || "/index.html".equals(path))) {
                byte[] html = readClasspath("/assets/shipping_box/web/index.html");
                if (html == null) {
                    writeText(out, 500, "Missing web resource");
                    return;
                }
                writeBytes(out, 200, "text/html; charset=utf-8", html);
                return;
            }

            if ("GET".equals(req.method) && "/api/rules".equals(path)) {
                String rulesJson = ExchangeRecipeManager.serializeRulesToJson();
                writeBytes(out, 200, "application/json; charset=utf-8", rulesJson.getBytes(StandardCharsets.UTF_8));
                return;
            }

            if ("GET".equals(req.method) && "/api/registry".equals(path)) {
                handleRegistry(out);
                return;
            }

            if ("GET".equals(req.method) && "/api/icon".equals(path)) {
                handleIcon(out, uri);
                return;
            }

            if ("GET".equals(req.method) && "/api/load".equals(path)) {
                handleLoad(out, uri);
                return;
            }

            if ("POST".equals(req.method) && "/api/save".equals(path)) {
                handleSave(out, uri, req.bodyBytes);
                return;
            }

            if ("POST".equals(req.method) && "/api/reload".equals(path)) {
                PacketDistributor.sendToServer(new PacketEditorReloadRequest());
                writeBytes(out, 200, "application/json; charset=utf-8", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            writeText(out, 404, "Not Found");
        } catch (IOException ignored) {
        }
    }

    private static void handleRegistry(OutputStream out) throws IOException {
        JsonArray items = new JsonArray();
        BuiltInRegistries.ITEM.keySet().stream()
                .map(Object::toString)
                .sorted()
                .forEach(items::add);

        JsonArray tags = new JsonArray();
        try {
            BuiltInRegistries.ITEM.getTagNames()
                    .map(tagKey -> "#" + tagKey.location())
                    .map(Object::toString)
                    .sorted()
                    .forEach(tags::add);
        } catch (Throwable ignored) {}

        JsonObject res = new JsonObject();
        res.add("items", items);
        res.add("tags", tags);
        writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(res).getBytes(StandardCharsets.UTF_8));
    }

    private static void handleIcon(OutputStream out, URI uri) throws IOException {
        String itemId = parseQuery(uri).getOrDefault("item", "");
        itemId = itemId == null ? "" : itemId.trim();
        if (itemId.isEmpty()) {
            writeText(out, 400, "Missing item");
            return;
        }

        ResourceLocation itemLoc = ResourceLocation.tryParse(itemId);
        if (itemLoc == null) {
            writeText(out, 400, "Invalid item");
            return;
        }
        if (!BuiltInRegistries.ITEM.containsKey(itemLoc)) {
            writeText(out, 404, "Not Found");
            return;
        }

        ResourceManager resourceManager = getClientResourceManager();
        if (resourceManager == null) {
            writeText(out, 503, "Resource manager unavailable");
            return;
        }

        String ns = itemLoc.getNamespace();
        String path = itemLoc.getPath();
        ResourceLocation itemTexture = ResourceLocation.fromNamespaceAndPath(ns, "textures/item/" + path + ".png");
        ResourceLocation blockTexture = ResourceLocation.fromNamespaceAndPath(ns, "textures/block/" + path + ".png");

        byte[] bytes = readPng(resourceManager, itemTexture);
        if (bytes == null) {
            bytes = readPng(resourceManager, blockTexture);
        }
        if (bytes == null) {
            writeText(out, 404, "Not Found");
            return;
        }
        writeBytes(out, 200, "image/png", bytes);
    }

    private static byte[] readPng(ResourceManager resourceManager, ResourceLocation location) {
        try {
            var opt = resourceManager.getResource(location);
            if (opt.isEmpty()) {
                return null;
            }
            Resource res = opt.get();
            try (InputStream in = res.open()) {
                return readAllBytes(in);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static ResourceManager getClientResourceManager() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            Object rm = mc.getClass().getMethod("getResourceManager").invoke(mc);
            if (rm instanceof ResourceManager resourceManager) {
                return resourceManager;
            }
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void handleLoad(OutputStream out, URI uri) throws IOException {
        String file = getRequestedFile(uri);
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<WebEditorRequestTracker.Response> future = WebEditorRequestTracker.create(requestId);
        PacketDistributor.sendToServer(new PacketEditorReadFile(requestId, file));

        WebEditorRequestTracker.Response response;
        try {
            response = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            writeText(out, 504, "Timed out waiting for server");
            return;
        }

        if (!response.ok()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("ok", false);
            obj.addProperty("error", response.error());
            writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(obj).getBytes(StandardCharsets.UTF_8));
            return;
        }

        String content = response.payload();
        try {
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(obj).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("ok", false);
            fallback.addProperty("error", "Invalid JSON in file: " + file);
            fallback.addProperty("raw", content);
            writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(fallback).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void handleSave(OutputStream out, URI uri, byte[] bodyBytes) throws IOException {
        String body = new String(bodyBytes == null ? new byte[0] : bodyBytes, StandardCharsets.UTF_8);
        JsonObject obj;
        try {
            obj = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonParseException e) {
            writeText(out, 400, "Invalid JSON");
            return;
        }

        if (!obj.has("rules") || !obj.get("rules").isJsonArray()) {
            writeText(out, 400, "Expected {\"rules\": [...]}");
            return;
        }

        String file = getRequestedFile(uri);
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<WebEditorRequestTracker.Response> future = WebEditorRequestTracker.create(requestId);
        PacketDistributor.sendToServer(new PacketEditorSaveRules(requestId, file, GSON.toJson(obj)));

        WebEditorRequestTracker.Response response;
        try {
            response = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            writeText(out, 504, "Timed out waiting for server");
            return;
        }

        JsonObject res = new JsonObject();
        res.addProperty("ok", response.ok());
        if (response.ok()) {
            res.addProperty("savedPath", response.payload());
        } else {
            res.addProperty("error", response.error());
        }
        writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(res).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readClasspath(String path) throws IOException {
        try (InputStream in = WebEditorLocalServer.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return readAllBytes(in);
        }
    }

    private static void writeText(OutputStream out, int status, String text) throws IOException {
        writeBytes(out, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String reason = switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            case 504 -> "Gateway Timeout";
            default -> "OK";
        };

        byte[] bytes = body == null ? new byte[0] : body;
        String headers =
                "HTTP/1.1 " + status + " " + reason + "\r\n" +
                        "Content-Type: " + contentType + "\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type\r\n" +
                        "Access-Control-Max-Age: 86400\r\n" +
                        "Connection: close\r\n" +
                        "Content-Length: " + bytes.length + "\r\n" +
                        "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    private static Request readRequest(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isBlank()) {
            return null;
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return null;
        }
        String method = parts[0].trim().toUpperCase();
        String pathWithQuery = parts[1].trim();

        int contentLength = 0;
        boolean hasContentLength = false;
        while (true) {
            String line = readLine(in);
            if (line == null) {
                return null;
            }
            if (line.isEmpty()) {
                break;
            }
            int idx = line.indexOf(':');
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if ("Content-Length".equalsIgnoreCase(key)) {
                    try {
                        contentLength = Integer.parseInt(value);
                        hasContentLength = true;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        byte[] body = null;
        if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) && hasContentLength) {
            if (contentLength < 0) {
                return null;
            }
            body = readFixedBytes(in, contentLength);
            if (body.length != contentLength) {
                return null;
            }
        }
        return new Request(method, pathWithQuery, body);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (baos.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                baos.write(b);
            }
        }
        return baos.toString(StandardCharsets.ISO_8859_1);
    }

    private static byte[] readFixedBytes(InputStream in, int length) throws IOException {
        if (length <= 0) {
            return new byte[0];
        }
        byte[] buf = new byte[length];
        int off = 0;
        while (off < length) {
            int read = in.read(buf, off, length - off);
            if (read == -1) {
                break;
            }
            off += read;
        }
        if (off == length) {
            return buf;
        }
        byte[] partial = new byte[off];
        System.arraycopy(buf, 0, partial, 0, off);
        return partial;
    }

    private record Request(String method, String pathWithQuery, byte[] bodyBytes) {}
}
