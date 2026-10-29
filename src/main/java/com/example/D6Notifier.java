package com.example;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends a copy of every new entry to the D6 review app.
 * Runs in the background so saving in D5 never waits on (or fails because of) D6.
 * App settings on D5:  D6_BASE_URL (e.g. https://my-d6.azurewebsites.net)  and  D6_INTAKE_TOKEN.
 */
final class D6Notifier {
    private static final Logger LOG = Logger.getLogger(D6Notifier.class.getName());
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "d6-notifier");
        t.setDaemon(true);
        return t;
    });

    private D6Notifier() { }

    static void send(int id, String fullName, String emailAddress, String city, String gender) {
        String base = env("D6_BASE_URL");
        String token = env("D6_INTAKE_TOKEN");
        if (base == null || token == null) {
            LOG.warning("D6_BASE_URL / D6_INTAKE_TOKEN not set - entry " + id + " was not sent to D6.");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("fullName", fullName);
        body.put("emailAddress", emailAddress);
        body.put("city", city);
        body.put("gender", gender);
        String json = GSON.toJson(body);
        String url = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/api/intake";

        POOL.submit(() -> {
            long[] waitMs = {0, 5_000, 20_000};          // 3 tries; D6 on a free plan may be cold-starting
            for (int attempt = 0; attempt < waitMs.length; attempt++) {
                try {
                    if (waitMs[attempt] > 0) Thread.sleep(waitMs[attempt]);
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(60))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();
                    HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() / 100 == 2) return;
                    LOG.warning("D6 answered " + r.statusCode() + " for entry " + id + " (attempt " + (attempt + 1) + ")");
                    if (r.statusCode() == 400 || r.statusCode() == 401 || r.statusCode() == 403) return; // retrying won't help
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Could not reach D6 for entry " + id + " (attempt " + (attempt + 1) + ")", e);
                }
            }
            LOG.severe("Entry " + id + " was NOT delivered to D6 after 3 attempts.");
        });
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
