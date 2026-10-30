package com.shambapulse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * ShambaPulse gateway.
 *   POST /ussd          USSD callback (fields: sessionId, serviceCode, phoneNumber, text)
 *   POST /sms/delivery  SMS delivery reports (fields: id, status)
 *   GET  /health
 * Also runs the alert dispatcher that sends alerts queued from the PHP dashboard.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(Db.env("PORT", "8081"));
        UssdMenu menu = new UssdMenu();
        Dispatcher dispatcher = new Dispatcher(SmsGateway.fromEnv());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/ussd", ex -> {
            if ("OPTIONS".equals(ex.getRequestMethod())) { reply(ex, 204, ""); return; }
            if (!"POST".equals(ex.getRequestMethod())) { reply(ex, 405, "Method not allowed"); return; }
            Map<String, String> f = form(ex);
            String phone = f.getOrDefault("phoneNumber", "").trim();
            if (phone.isEmpty()) { reply(ex, 400, "END Missing phone number"); return; }
            reply(ex, 200, menu.handle(phone, f.getOrDefault("text", "")));
        });

        server.createContext("/sms/delivery", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) { reply(ex, 405, "Method not allowed"); return; }
            Map<String, String> f = form(ex);
            try {
                if (f.containsKey("id")) Dispatcher.recordDelivery(f.get("id"), f.getOrDefault("status", ""));
                reply(ex, 200, "OK");
            } catch (Exception e) {
                System.err.println("[delivery] " + e);
                reply(ex, 500, "Error");
            }
        });

        server.createContext("/health", ex -> reply(ex, 200, "ok"));

        dispatcher.start(10);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { dispatcher.stop(); server.stop(1); }));
        System.out.println("ShambaPulse gateway listening on :" + port);
    }

    private static Map<String, String> form(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readNBytes(16_384), StandardCharsets.UTF_8);
        Map<String, String> out = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    private static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", Db.env("SP_ALLOWED_ORIGIN", "*"));
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        ex.sendResponseHeaders(code, code == 204 ? -1 : bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
