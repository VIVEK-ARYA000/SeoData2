package org.zee;


import com.microsoft.playwright.*;
import com.google.gson.*; // Required for parsing JSON in amp-analytics config
import com.microsoft.playwright.options.LoadState;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TrackingDetector1{

    private static final Map<String, List<String>> TRACKING_KEYWORDS = Map.of(
            "lotame", List.of("lotame.com", "crwdcntrl.net", "lotame"),
            "chartbeat", List.of("chartbeat.com", "chartbeat.net", "chartbeat"),
            "izooto", List.of("izooto.com", "izooto")
    );

    public static void main(String[] args) {
        // Example usage:
        String urlToCheck = "https://www.thehealthsite.com/";
        // String urlToCheck = "https://www.example.com"; // Replace with your test URL

        Map<String, String> detectedTrackers = detectTrackers(urlToCheck);

        System.out.println("--- Tracking Detection Results for: " + urlToCheck + " ---");
        detectedTrackers.forEach((tracker, status) ->
                System.out.println(tracker + ": " + status)
        );
    }

    public static Map<String, String> detectTrackers(String url) {
        Map<String, String> detectionResults = new ConcurrentHashMap<>();
        TRACKING_KEYWORDS.keySet().forEach(key -> detectionResults.put(key, "No")); // Initialize all to No

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)); // Use headless for automated checks
            Page page = browser.newPage();

            // Use a Set to store all collected sources/content to avoid redundant checks
            Set<String> allCollectedData = new HashSet<>();

            // Listener for all network requests (to capture external script src and potentially ping URLs)
            page.onRequest(request -> {
                String requestUrl = request.url().toLowerCase();
                // Add the full request URL to our collected data
                allCollectedData.add(requestUrl);
            });

            // Navigate to the URL
            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000)); // 60 seconds timeout
            } catch (PlaywrightException e) {
                System.err.println("Navigation failed for " + url + ": " + e.getMessage());
                // Mark all as "Error" if navigation fails
                TRACKING_KEYWORDS.keySet().forEach(key -> detectionResults.put(key, "Navigation Error"));
                return detectionResults;
            }

            // Wait for network idle to ensure most scripts/requests have loaded
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(2000); // Small additional wait for dynamic content

            // 1. Check <script> tags (both src and inline content)
            List<ElementHandle> scriptElements = page.querySelectorAll("script");
            for (ElementHandle script : scriptElements) {
                String src = script.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
                String content = script.textContent();
                if (content != null && !content.isEmpty()) {
                    allCollectedData.add(content.toLowerCase());
                }
            }

            // 2. Check <amp-analytics> tags
            List<ElementHandle> ampAnalyticsElements = page.querySelectorAll("amp-analytics");
            for (ElementHandle ampAnalytics : ampAnalyticsElements) {
                String type = ampAnalytics.getAttribute("type");
                if (type != null && !type.isEmpty()) {
                    allCollectedData.add(type.toLowerCase()); // Add the type (e.g., "chartbeat", "lotame")
                }

                String config = ampAnalytics.getAttribute("config"); // Inline JSON config
                if (config != null && !config.isEmpty()) {
                    try {
                        JsonElement jsonElement = JsonParser.parseString(config);
                        if (jsonElement.isJsonObject()) {
                            // Recursively extract all string values from the JSON config
                            extractStringValuesFromJson(jsonElement.getAsJsonObject(), allCollectedData);
                        }
                    } catch (JsonSyntaxException e) {
                        System.err.println("Warning: Invalid JSON in amp-analytics config: " + e.getMessage());
                    }
                }

                String src = ampAnalytics.getAttribute("src"); // External JSON config URL
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            // 3. Check other AMP components if they can load trackers (e.g., amp-pixel, amp-iframe src)
            List<ElementHandle> ampPixelElements = page.querySelectorAll("amp-pixel[src]");
            for (ElementHandle ampPixel : ampPixelElements) {
                String src = ampPixel.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            List<ElementHandle> ampIframeElements = page.querySelectorAll("amp-iframe[src]");
            for (ElementHandle ampIframe : ampIframeElements) {
                String src = ampIframe.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            // Now, iterate through all collected data and check against keywords
            for (Map.Entry<String, List<String>> entry : TRACKING_KEYWORDS.entrySet()) {
                String serviceKey = entry.getKey();
                List<String> keywords = entry.getValue();

                boolean found = allCollectedData.stream().anyMatch(data ->
                        keywords.stream().anyMatch(data::contains)
                );
                if (found) {
                    detectionResults.put(serviceKey, "Yes");
                }
            }

            browser.close();
        } catch (PlaywrightException e) {
            System.err.println("Playwright operation failed: " + e.getMessage());
            TRACKING_KEYWORDS.keySet().forEach(key -> detectionResults.put(key, "Playwright Error"));
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            TRACKING_KEYWORDS.keySet().forEach(key -> detectionResults.put(key, "Unknown Error"));
        }
        return detectionResults;
    }

    // Helper to extract all string values from a JsonObject (for amp-analytics config)
    private static void extractStringValuesFromJson(JsonObject jsonObject, Set<String> targetSet) {
        jsonObject.entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                targetSet.add(value.getAsString().toLowerCase());
            } else if (value.isJsonObject()) {
                extractStringValuesFromJson(value.getAsJsonObject(), targetSet);
            } else if (value.isJsonArray()) {
                value.getAsJsonArray().forEach(arrayElement -> {
                    if (arrayElement.isJsonPrimitive() && arrayElement.getAsJsonPrimitive().isString()) {
                        targetSet.add(arrayElement.getAsString().toLowerCase());
                    } else if (arrayElement.isJsonObject()) {
                        extractStringValuesFromJson(arrayElement.getAsJsonObject(), targetSet);
                    }
                });
            }
        });
    }
}
