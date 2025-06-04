package org.zee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Ga4ExtStable {
    private static final Logger logger = LoggerFactory.getLogger(Ga4ExtStable.class);

    private static final String GA_COLLECT_PATH_FRAGMENT_1 = "/g/collect";
    private static final String GA_COLLECT_PATH_FRAGMENT_2 = "google-analytics.com/g/collect";
    private static final String PARAM_TID = "tid";
    private static final String PARAM_PPID_EP = "ep.PPID_es";
    private static final String PARAM_PPID_UP = "up.PPID";
    private static final String RESULT_KEY_TID = "tid";
    private static final String RESULT_KEY_PPID = "ppid";

    public static Map<String, String> extractFromRequest(String requestUrl) {
        Map<String, String> result = new HashMap<>();
        if (requestUrl == null || requestUrl.trim().isEmpty()) {
            return result;
        }

        // 1. Check for GA4 collect requests (/g/collect)
        if (requestUrl.contains(GA_COLLECT_PATH_FRAGMENT_1) || requestUrl.contains(GA_COLLECT_PATH_FRAGMENT_2)) {
            logger.debug("GA4 collect request detected: {}", requestUrl);
            Map<String, String> queryParams = extractQueryParams(requestUrl);

            String tid = queryParams.get(PARAM_TID);
            String ppid = queryParams.getOrDefault(PARAM_PPID_EP, queryParams.get(PARAM_PPID_UP));

            if (tid != null && !tid.isEmpty()) {
                result.put(RESULT_KEY_TID, tid);
                logger.trace("Extracted TID from /g/collect: {}", tid);
            }
            if (ppid != null && !ppid.isEmpty()) {
                result.put(RESULT_KEY_PPID, ppid);
                logger.trace("Extracted PPID from /g/collect: {}", ppid);
            }
        }

        // 2. Check for gtag.js script requests (often contains the main GA4 ID)
        // This check should happen regardless of whether a /g/collect was found,
        // as gtag.js provides the base Measurement ID.
        if (requestUrl.contains("gtag/js?id=G-")) {
            logger.debug("GA4 gtag script detected: {}", requestUrl);
            Map<String, String> queryParams = extractQueryParams(requestUrl);
            // The GA4 Measurement ID in gtag/js is under the parameter "id"
            String gtagId = queryParams.get("id");
            if (gtagId != null && !gtagId.isEmpty()) {
                // Only add if not already found from a collect request, or if it's a new ID
                if (!result.containsKey(RESULT_KEY_TID) || !result.get(RESULT_KEY_TID).equals(gtagId)) {
                    result.put(RESULT_KEY_TID, gtagId);
                    logger.trace("Extracted TID from gtag.js: {}", gtagId);
                }
            }
        }

        return result;
    }

    private static Map<String, String> extractQueryParams(String url) {
        Map<String, String> queryPairs = new HashMap<>();
        try {
            String[] parts = url.split("\\?", 2);
            if (parts.length > 1 && parts[1] != null && !parts[1].isEmpty()) {
                String query = parts[1];
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    if (pair.trim().isEmpty()) continue;
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length > 0 && !keyValue[0].trim().isEmpty()) {
                        String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                        String value = "";
                        if (keyValue.length > 1) {
                            value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                        }
                        queryPairs.put(key, value);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            logger.warn("URL decoding error for query part of URL ({}): {}", url, e.getMessage());
        } catch (Exception e) {
            logger.error("URL parsing failed for URL ({}): {}", url, e.getMessage(), e);
        }
        return queryPairs;
    }
}