package org.zee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ComscoreExtStable {

    private static final Logger logger = LoggerFactory.getLogger(ComscoreExtStable.class);

    private static final String AMP_ENDPOINT = "/p";
    private static final String NON_AMP_ENDPOINT = "/b";
    private static final String HOST_IDENTIFIER = "scorecardresearch.com";

    private static final String PARAM_C1 = "c1";
    private static final String PARAM_C2 = "c2";

    private static final String RESULT_KEY_C1 = "c1";
    private static final String RESULT_KEY_C2 = "c2";

    public static Map<String, String> extractFromRequest(String requestUrl) {
        Map<String, String> result = new HashMap<>();

        if (requestUrl == null || requestUrl.trim().isEmpty()) {
            return result;
        }

        if ((requestUrl.contains(HOST_IDENTIFIER + AMP_ENDPOINT) ||
                requestUrl.contains(HOST_IDENTIFIER + NON_AMP_ENDPOINT))) {

            logger.debug("Potential Comscore request detected: {}", requestUrl);
            Map<String, String> queryParams = extractQueryParams(requestUrl);

            String c1 = queryParams.get(PARAM_C1);
            String c2 = queryParams.get(PARAM_C2);

            if (c1 != null && !c1.isEmpty()) {
                result.put(RESULT_KEY_C1, c1);
                logger.trace("Extracted C1: {}", c1);
            }
            if (c2 != null && !c2.isEmpty()) {
                result.put(RESULT_KEY_C2, c2);
                logger.trace("Extracted C2: {}", c2);
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
