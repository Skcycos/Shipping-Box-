package com.chinaex123.shipping_box.web;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class WebEditorRequestTracker {
    private static final Map<String, CompletableFuture<Response>> PENDING = new ConcurrentHashMap<>();

    private WebEditorRequestTracker() {}

    public static CompletableFuture<Response> create(String requestId) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        PENDING.put(requestId, future);
        return future;
    }

    public static void complete(String requestId, Response response) {
        CompletableFuture<Response> future = PENDING.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    public record Response(boolean ok, String payload, String error) {}
}
