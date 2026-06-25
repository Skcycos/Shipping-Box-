package com.chinaex123.shipping_box.web;

import com.chinaex123.shipping_box.ShippingBox;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WebEditorLocalServer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, byte[]> ICON_CACHE = new ConcurrentHashMap<>();
    private static final byte[] TRANSPARENT_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO5WvKsAAAAASUVORK5CYII="
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(WebEditorLocalServer.class);

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
                    try { created.close(); } catch (IOException ignored) {}
                }
                created = null;
                port++;
            } catch (Exception e) {
                if (created != null) {
                    try { created.close(); } catch (IOException ignored) {}
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
            try { serverSocket.close(); } catch (IOException ignored) {}
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

            if ("GET".equals(req.method) && "/manifest".equals(path)) {
                handleManifest(out, uri);
                return;
            }

            if ("GET".equals(req.method) && "/cache_status".equals(path)) {
                handleCacheStatus(out, uri);
                return;
            }

            if ("GET".equals(req.method) && path.startsWith("/icon/cache/")) {
                handleCachedIcon(out, uri, path);
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
        BuiltInRegistries.ITEM.forEach(item -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                items.add(id.toString());
            }
        });

        JsonArray tags = new JsonArray();
        // For simplicity, we provide common tags that might be useful; full tag registry is more involved.
        // Clients can also use free text. Here we expose a basic set of vanilla tags used in examples.
        String[] commonTags = new String[] {
            "#minecraft:logs", "#minecraft:planks", "#minecraft:stone_tool_materials",
            "#minecraft:coals", "#minecraft:dirt", "#minecraft:sand", "#minecraft:gravel"
        };
        for (String t : commonTags) {
            tags.add(t);
        }

        JsonObject resp = new JsonObject();
        resp.add("items", items);
        resp.add("tags", tags);
        writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(resp).getBytes(StandardCharsets.UTF_8));
    }

    private static void handleIcon(OutputStream out, URI uri) throws IOException {
        String itemId = parseQuery(uri).getOrDefault("id", "");
        if (itemId.isBlank()) {
            itemId = parseQuery(uri).getOrDefault("item", "");
        }
        if (itemId.isBlank()) {
            writeBytes(out, 200, "image/png", TRANSPARENT_PNG);
            return;
        }

        byte[] cached = ICON_CACHE.get(itemId);
        if (cached != null) {
            writeBytes(out, 200, "image/png", cached);
            return;
        }

        byte[] png = null;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null) {
                png = extractIconForItem(rl);
            }
        } catch (Exception ignored) {}

        if (png == null) {
            png = TRANSPARENT_PNG;
        }
        ICON_CACHE.put(itemId, png);
        writeBytes(out, 200, "image/png", png);
    }

    /** 返回 manifest 或 missing_cache 提示 */
    private static void handleManifest(OutputStream out, URI uri) throws IOException {
        if (!isAuthorized(uri)) {
            writeText(out, 401, "Unauthorized");
            return;
        }

        Path manifest = EditorIconCacheManager.getInstance().getManifestFile();
        if (Files.exists(manifest)) {
            byte[] bytes = Files.readAllBytes(manifest);
            writeBytes(out, 200, "application/json; charset=utf-8", bytes);
        } else {
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "missing_cache");
            resp.addProperty("message", "Icon cache has not been generated yet. Please run /" + ShippingBox.MOD_ID + " editor cache_icons in game.");
            writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(resp).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 返回实时缓存进度（前端可轮询） */
    private static void handleCacheStatus(OutputStream out, URI uri) throws IOException {
        if (!isAuthorized(uri)) {
            writeText(out, 401, "Unauthorized");
            return;
        }

        var mgr = EditorIconCacheManager.getInstance();
        JsonObject resp = new JsonObject();
        resp.addProperty("status", mgr.getStatus().name().toLowerCase());
        resp.addProperty("processed", mgr.getProcessed());
        resp.addProperty("total", mgr.getTotal());
        if (mgr.getErrorMessage() != null) {
            resp.addProperty("error", mgr.getErrorMessage());
        }
        writeBytes(out, 200, "application/json; charset=utf-8", GSON.toJson(resp).getBytes(StandardCharsets.UTF_8));
    }

    /** 安全地服务缓存图标 */
    private static void handleCachedIcon(OutputStream out, URI uri, String fullPath) throws IOException {
        if (!isAuthorized(uri)) {
            writeText(out, 401, "Unauthorized");
            return;
        }

        // 安全检查 filename
        String filename = fullPath.substring(fullPath.lastIndexOf('/') + 1);
        if (!filename.endsWith(".png") || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            writeText(out, 400, "Invalid filename");
            return;
        }

        Path cacheRoot = EditorIconCacheManager.getInstance().getCacheRoot();
        Path target;
        if (fullPath.contains("/items/")) {
            target = cacheRoot.resolve("items").resolve(filename);
        } else if (fullPath.contains("/blocks/")) {
            target = cacheRoot.resolve("blocks").resolve(filename);
        } else {
            writeText(out, 404, "Not Found");
            return;
        }

        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            writeText(out, 404, "Not Found");
            return;
        }

        byte[] bytes = Files.readAllBytes(target);
        writeBytes(out, 200, "image/png", bytes);
    }

    /** 供缓存管理器复用 */
    public static byte[] extractIconForItemAsBytes(ResourceLocation itemRl) {
        return extractIconForItem(itemRl);
    }

    private static byte[] extractIconForItem(ResourceLocation itemRl) {
        String namespace = itemRl.getNamespace();
        String path = itemRl.getPath();

        byte[] modelBytes = null;

        // Prefer Minecraft ResourceManager (works for vanilla and resource packs)
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                var rm = mc.getResourceManager();
                // Try item model
                var modelLoc = ResourceLocation.fromNamespaceAndPath(namespace, "models/item/" + path + ".json");
                var resOpt = rm.getResource(modelLoc);
                if (resOpt.isPresent()) {
                    try (var in = resOpt.get().open()) {
                        modelBytes = in.readAllBytes();
                    }
                }
                if (modelBytes == null) {
                    // block model
                    modelLoc = ResourceLocation.fromNamespaceAndPath(namespace, "models/block/" + path + ".json");
                    resOpt = rm.getResource(modelLoc);
                    if (resOpt.isPresent()) {
                        try (var in = resOpt.get().open()) {
                            modelBytes = in.readAllBytes();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback to classpath (for mod's own assets in some environments)
        if (modelBytes == null) {
            String modelPath = "assets/" + namespace + "/models/item/" + path + ".json";
            modelBytes = tryReadBytes(modelPath);
            if (modelBytes == null) {
                modelPath = "assets/" + namespace + "/models/block/" + path + ".json";
                modelBytes = tryReadBytes(modelPath);
            }
        }

        if (modelBytes == null) {
            LOGGER.warn("[Icon] Could not load model JSON for {} (tried RM and classpath)", itemRl);
            return null;
        }

        try {
            String json = new String(modelBytes, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            // Resolve textures, following "parent" if necessary (simple 2-3 levels to avoid infinite)
            JsonObject textures = resolveTextures(obj, namespace, path, 0);

            if (textures == null || !textures.isJsonObject()) {
                return null;
            }
            textures = textures.getAsJsonObject();

            // Support common texture keys for both item (layer0) and block models
            String[] textureKeys = {"layer0", "all", "texture", "stone", "top", "side", "end", "particle"};
            String layer0 = null;
            for (String k : textureKeys) {
                if (textures.has(k)) {
                    layer0 = textures.get(k).getAsString();
                    if (layer0 != null && !layer0.isBlank()) break;
                }
            }
            if (layer0 == null || layer0.isBlank()) {
                LOGGER.warn("[Icon] No suitable texture key found in model for {}", itemRl);
                return null;
            }

            String texNamespace = namespace;
            String texPath = layer0;
            int idx = layer0.indexOf(':');
            if (idx > 0) {
                texNamespace = layer0.substring(0, idx);
                texPath = layer0.substring(idx + 1);
            }
            if (texPath.startsWith("textures/")) {
                texPath = texPath.substring("textures/".length());
            }

            String texClasspath = "assets/" + texNamespace + "/textures/" + texPath + ".png";
            byte[] png = tryReadPng(texClasspath);

            // Fallback using Minecraft ResourceManager (reliable for vanilla + resource packs)
            if (png == null) {
                try {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null && mc.getResourceManager() != null) {
                        var rm = mc.getResourceManager();
                        var texLoc = ResourceLocation.fromNamespaceAndPath(texNamespace, "textures/" + texPath + ".png");
                        var resOpt = rm.getResource(texLoc);
                        if (resOpt.isPresent()) {
                            try (var in = resOpt.get().open()) {
                                png = in.readAllBytes();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (png == null) {
                LOGGER.warn("[Icon] Could not load texture PNG for {} at {}", itemRl, texPath);
            }

            return png;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] tryReadPng(String resourcePath) {
        byte[] bytes = tryReadBytes(resourcePath);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return bytes;
    }

    private static byte[] tryReadBytes(String resourcePath) {
        // Try mod classloader first (works for mod assets)
        try (InputStream in = WebEditorLocalServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                return readAllBytes(in);
            }
        } catch (IOException ignored) {}

        // Also try context classloader (sometimes has more resources in dev)
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                return readAllBytes(in);
            }
        } catch (IOException ignored) {}

        return null;
    }

    /**
     * Recursively resolve textures by following "parent" in model JSON.
     * Returns the "textures" JsonObject from the deepest parent or the model itself.
     */
    private static JsonObject resolveTextures(JsonObject modelJson, String namespace, String path, int depth) {
        if (depth > 5) return null; // prevent infinite recursion

        if (modelJson.has("textures") && modelJson.get("textures").isJsonObject()) {
            return modelJson.getAsJsonObject("textures");
        }

        if (!modelJson.has("parent")) {
            return null;
        }

        String parent = modelJson.get("parent").getAsString();
        if (parent == null || parent.isEmpty()) return null;

        String parentNamespace = namespace;
        String parentPath = parent;
        if (parent.contains(":")) {
            String[] parts = parent.split(":", 2);
            parentNamespace = parts[0];
            parentPath = parts[1];
        }

        // Try to load parent model
        String parentModelClasspath = "assets/" + parentNamespace + "/models/" + parentPath + ".json";
        byte[] parentBytes = tryReadBytes(parentModelClasspath);
        if (parentBytes == null) {
            // try RM
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.getResourceManager() != null) {
                    var rm = mc.getResourceManager();
                    var parentLoc = ResourceLocation.fromNamespaceAndPath(parentNamespace, "models/" + parentPath + ".json");
                    var resOpt = rm.getResource(parentLoc);
                    if (resOpt.isPresent()) {
                        try (var in = resOpt.get().open()) {
                            parentBytes = in.readAllBytes();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (parentBytes == null) return null;

        try {
            String parentJsonStr = new String(parentBytes, StandardCharsets.UTF_8);
            JsonObject parentJson = JsonParser.parseString(parentJsonStr).getAsJsonObject();
            return resolveTextures(parentJson, parentNamespace, parentPath, depth + 1);
        } catch (Exception e) {
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
        writeBytes(out, status, contentType, body, "no-store");
    }

    private static void writeBytes(OutputStream out, int status, String contentType, byte[] body, String cacheControl) throws IOException {
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
                        "Cache-Control: " + (cacheControl == null || cacheControl.isBlank() ? "no-store" : cacheControl) + "\r\n" +
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