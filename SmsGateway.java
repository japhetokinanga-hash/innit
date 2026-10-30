package com.shambapulse;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sends one SMS. Result 'ok' means "submitted to the provider", NOT "delivered". */
public interface SmsGateway {

    record Result(boolean ok, String providerRef) {}

    Result send(String phone, String text);

    /** Picks the real gateway when AT_USERNAME and AT_API_KEY are set, otherwise a simulator. */
    static SmsGateway fromEnv() {
        String user = Db.env("AT_USERNAME", "");
        String key = Db.env("AT_API_KEY", "");
        if (user.isEmpty() || key.isEmpty()) {
            System.out.println("[sms] No AT_USERNAME/AT_API_KEY set: using SIMULATED sms (demo only).");
            return (phone, text) -> {
                System.out.println("[sms-simulated] to " + phone + ": " + text);
                return new Result(true, "SIM-" + UUID.randomUUID());
            };
        }
        return new AfricasTalking(user, key,
                "sandbox".equals(user) ? "https://api.sandbox.africastalking.com" : "https://api.africastalking.com");
    }

    /** Africa's Talking bulk SMS (https://developers.africastalking.com). */
    final class AfricasTalking implements SmsGateway {
        private static final Pattern ID = Pattern.compile("\"messageId\"\\s*:\\s*\"([^\"]+)\"");
        private final HttpClient http = HttpClient.newHttpClient();
        private final String user, key, base;

        AfricasTalking(String user, String key, String base) { this.user = user; this.key = key; this.base = base; }

        @Override
        public Result send(String phone, String text) {
            try {
                String form = "username=" + enc(user) + "&to=" + enc(phone) + "&message=" + enc(text);
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/version1/messaging"))
                        .header("apiKey", key)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                Matcher m = ID.matcher(res.body());
                boolean ok = res.statusCode() / 100 == 2 && m.find();
                return new Result(ok, ok ? m.group(1) : null);
            } catch (Exception ex) {
                System.err.println("[sms] send failed: " + ex.getMessage());
                return new Result(false, null);
            }
        }

        private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    }
}
