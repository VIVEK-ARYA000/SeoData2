package org.zee;

import com.google.gson.*;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class MetaDetails {
    private static final Logger logger = LoggerFactory.getLogger(MetaDetails.class); // Corrected class name here

    static final String NOT_FOUND = "Not Found";
    private static final String YES = "Yes";
    private static final String NO = "No";
    private static final String NO_ERROR = "No Error";

    static final Map<String, List<String>> TRACKING_KEYWORDS = Map.of(
            "lotame", List.of("lotame.com", "crwdcntrl.net", "lotame"),
            "chartbeat", List.of("chartbeat.com", "chartbeat.net", "chartbeat"),
            "izooto", List.of("izooto.com", "izooto"),
            "vdo_io", List.of("vdo.ai")
    );

    // Common global variables where dynamic JSON might be stored
    private static final List<String> GLOBAL_JSON_VARIABLES = List.of(
            "window.__INITIAL_STATE__",
            "window.__PRELOADED_STATE__",
            "window._INIT_DATA_",
            "window.ytInitialData", // Specific to YouTube, but shows a pattern
            "window.APP_DATA",
            "window.pageData"
            // Add more as you discover them on target websites
    );

    public static Map<String, String> extractMetaTags(Page page, Response response) {
        Map<String, String> metaDetails = new HashMap<>();
        String pageUrlString = page.url();

        // Status Code
        metaDetails.put("status_code", response != null ? String.valueOf(response.status()) : "N/A (Navigation Error)");

        // Title (with OG fallback)
        String title = "";
        try {
            title = page.title();
            if (title == null || title.trim().isEmpty()) {
                title = getMetaTagContent(page, "meta[property='og:title']");
            }
        } catch (Exception e) {
            logger.warn("Could not extract title for {}: {}", pageUrlString, e.getMessage());
        }
        metaDetails.put("title", safeValue(title));

        metaDetails.put("description", safeValue(getMetaTagContent(page, "meta[name='description']")));
        metaDetails.put("keywords", safeValue(getMetaTagContent(page, "meta[name='keywords']")));
        metaDetails.put("viewport", safeValue(getMetaTagContent(page, "meta[name='viewport']")));
        metaDetails.put("meta_robots", safeValue(getMetaTagContent(page, "meta[name='robots']")));

        // Canonical URL
        String canonical = safeValue(getLinkHref(page, "link[rel='canonical']"));
        metaDetails.put("canonical", canonical);
        // Canonical validation now done in SeoDataStable, as it needs the original input URL

        // AMP detection
        String ampUrl = safeValue(getLinkHref(page, "link[rel='amphtml']"));
        boolean isAmp = isAmpPage(page) || !ampUrl.equals(NOT_FOUND);
        metaDetails.put("amp_url", ampUrl);
        metaDetails.put("is_amp", isAmp ? YES : NO);

        // H1 Count
        try {
            metaDetails.put("h1_count", String.valueOf(page.locator("h1").count()));
        } catch (Exception e) {
            metaDetails.put("h1_count", "0");
            logger.warn("Could not count H1 tags for {}: {}", pageUrlString, e.getMessage());
        }

        // Open Graph tags
        metaDetails.put("og_title", safeValue(getMetaTagContent(page, "meta[property='og:title']")));
        metaDetails.put("og_description", safeValue(getMetaTagContent(page, "meta[property='og:description']")));
        metaDetails.put("og_image", safeValue(getMetaTagContent(page, "meta[property='og:image']")));
        metaDetails.put("og_url", safeValue(getMetaTagContent(page, "meta[property='og:url']")));
        metaDetails.put("og_type", safeValue(getMetaTagContent(page, "meta[property='og:type']")));
        metaDetails.put("og_site_name", safeValue(getMetaTagContent(page, "meta[property='og:site_name']")));

        // Twitter Card tags
        metaDetails.put("twitter_card", safeValue(getMetaTagContent(page, "meta[name='twitter:card']")));
        metaDetails.put("twitter_site", safeValue(getMetaTagContent(page, "meta[name='twitter:site']")));
        metaDetails.put("twitter_creator", safeValue(getMetaTagContent(page, "meta[name='twitter:creator']")));
        metaDetails.put("twitter_title", safeValue(getMetaTagContent(page, "meta[name='twitter:title']")));
        metaDetails.put("twitter_description", safeValue(getMetaTagContent(page, "meta[name='twitter:description']")));
        metaDetails.put("twitter_image", safeValue(getMetaTagContent(page, "meta[name='twitter:image']")));

        // Schema detection
        extractSchemaTypes(page, metaDetails);

        // **NEW**: Extract dynamic JSON from global variables
        extractGlobalJsonData(page, metaDetails);

        // Tracking scripts detection
        detectScripts(page, metaDetails);

        // Additional Metadata
        metaDetails.put("html_lang", safeValue(page.evaluate("document.documentElement.getAttribute('lang')")));
        metaDetails.put("publisher_link", safeValue(getLinkHref(page, "link[rel='publisher']")));
        metaDetails.put("favicon_url", safeValue(getFaviconUrl(page, pageUrlString)));

        extractHreflangLinks(page, metaDetails);
        countLinks(page, metaDetails, pageUrlString);
        extractBodyWordCount(page, metaDetails);

        return metaDetails;
    }

    private static String safeValue(Object valueObj) {
        if (valueObj == null) return NOT_FOUND;
        String value = String.valueOf(valueObj).trim();
        return value.isEmpty() ? NOT_FOUND : value;
    }

    private static String getMetaTagContent(Page page, String selector) {
        try {
            String content = page.locator(selector).first().getAttribute("content");
            if (content != null) return content.trim();
        } catch (Exception e) {
            logger.trace("Selector {} not found or no content attribute: {}", selector, e.getMessage());
        }
        return "";
    }

    private static String getLinkHref(Page page, String selector) {
        try {
            String href = page.locator(selector).first().getAttribute("href");
            if (href != null) return href.trim();
        } catch (Exception e) {
            logger.trace("Selector {} not found or no href attribute: {}", selector, e.getMessage());
        }
        return "";
    }

    private static boolean isAmpPage(Page page) {
        try {
            return page.locator("html[amp], html[\\⚡]").count() > 0;
        } catch (Exception e) {
            logger.debug("Error checking AMP attribute for {}: {}", page.url(), e.getMessage());
            return false;
        }
    }

    private static void extractSchemaTypes(Page page, Map<String, String> metaDetails) {
        List<ElementHandle> schemaScripts;
        try {
            schemaScripts = page.querySelectorAll("script[type='application/ld+json']");
        } catch (Exception e) {
            logger.warn("Could not query schema scripts for {}: {}", page.url(), e.getMessage());
            metaDetails.put("schema_present", NO);
            metaDetails.put("schema_types", NOT_FOUND);
            metaDetails.put("schema_error", "Query Error: " + e.getMessage().split("\n")[0]);
            return;
        }

        if (schemaScripts.isEmpty()) {
            metaDetails.put("schema_present", NO);
            metaDetails.put("schema_types", NOT_FOUND);
            metaDetails.put("schema_error", NO_ERROR);
            return;
        }

        Set<String> schemaTypes = new HashSet<>();
        StringBuilder schemaErrors = new StringBuilder();

        for (int i = 0; i < schemaScripts.size(); i++) {
            ElementHandle script = schemaScripts.get(i);
            String content = "";
            try {
                content = script.textContent();
            } catch (Exception e) {
                logger.warn("Could not get text content of schema script #{} for {}: {}", i + 1, page.url(), e.getMessage());
                schemaErrors.append("[Script ").append(i + 1).append(" Content Error] ").append(e.getMessage().split("\n")[0]).append("; ");
                continue;
            }

            if (content != null && !content.trim().isEmpty()) {
                try {
                    JsonElement jsonElement = JsonParser.parseString(content);
                    parseJsonForSchemaTypes(jsonElement, schemaTypes);
                } catch (JsonSyntaxException e) {
                    logger.warn("JSON Syntax error in schema script #{} for {}: {}", i + 1, page.url(), e.getMessage());
                    schemaErrors.append("[Script ").append(i + 1).append(" JSON Syntax Error] ").append(e.getMessage().split("\n")[0]).append("; ");
                } catch (Exception e) {
                    logger.warn("Unexpected error parsing schema script #{} for {}: {}", i + 1, page.url(), e.getMessage());
                    schemaErrors.append("[Script ").append(i + 1).append(" Parsing Error] ").append(e.getMessage().split("\n")[0]).append("; ");
                }
            }
        }

        metaDetails.put("schema_present", schemaTypes.isEmpty() ? NO : YES);
        metaDetails.put("schema_types", schemaTypes.isEmpty() ? NOT_FOUND : String.join(", ", schemaTypes));
        metaDetails.put("schema_error", schemaErrors.length() > 0 ? schemaErrors.toString().trim() : NO_ERROR);
    }

    private static void parseJsonForSchemaTypes(JsonElement jsonElement, Set<String> schemaTypes) {
        if (jsonElement.isJsonObject()) {
            collectSchemaTypeFromObject(jsonElement.getAsJsonObject(), schemaTypes);
        } else if (jsonElement.isJsonArray()) {
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    collectSchemaTypeFromObject(element.getAsJsonObject(), schemaTypes);
                }
            }
        }
    }

    private static void collectSchemaTypeFromObject(JsonObject jsonObject, Set<String> schemaTypes) {
        if (jsonObject.has("@type")) {
            JsonElement typeElement = jsonObject.get("@type");
            if (typeElement.isJsonPrimitive()) {
                schemaTypes.add(typeElement.getAsString());
            } else if (typeElement.isJsonArray()) {
                for (JsonElement type : typeElement.getAsJsonArray()) {
                    if (type.isJsonPrimitive()) {
                        schemaTypes.add(type.getAsString());
                    }
                }
            }
        }
        if (jsonObject.has("@graph") && jsonObject.get("@graph").isJsonArray()) {
            for (JsonElement graphElement : jsonObject.get("@graph").getAsJsonArray()) {
                parseJsonForSchemaTypes(graphElement, schemaTypes);
            }
        }
    }

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

    private static void detectScripts(Page page, Map<String, String> metaDetails) {
        Set<String> allCollectedData = new HashSet<>();

        // Set up request listener (This part should ideally be done once per Page instance setup,
        // not repeatedly in this method if called multiple times for the same page session)
        // For a single page load scenario, it's fine here.
        page.onRequest(request -> {
            String requestUrl = request.url().toLowerCase();
            allCollectedData.add(requestUrl);
        });

        try {
            // Collect script src and inline content
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

            // Collect amp-analytics data
            List<ElementHandle> ampAnalyticsElements = page.querySelectorAll("amp-analytics");
            for (ElementHandle ampAnalytics : ampAnalyticsElements) {
                String type = ampAnalytics.getAttribute("type");
                if (type != null && !type.isEmpty()) {
                    allCollectedData.add(type.toLowerCase());
                }
                String config = ampAnalytics.getAttribute("config");
                if (config != null && !config.isEmpty()) {
                    try {
                        JsonElement jsonElement = JsonParser.parseString(config);
                        if (jsonElement.isJsonObject()) {
                            extractStringValuesFromJson(jsonElement.getAsJsonObject(), allCollectedData);
                        }
                    } catch (JsonSyntaxException ex) {
                        logger.warn("Warning: Invalid JSON in amp-analytics config on {}: {}", page.url(), ex.getMessage());
                    }
                }
                String src = ampAnalytics.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            // Collect amp-pixel src
            List<ElementHandle> ampPixelElements = page.querySelectorAll("amp-pixel[src]");
            for (ElementHandle ampPixel : ampPixelElements) {
                String src = ampPixel.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            // Collect amp-iframe src
            List<ElementHandle> ampIframeElements = page.querySelectorAll("amp-iframe[src]");
            for (ElementHandle ampIframe : ampIframeElements) {
                String src = ampIframe.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }
        } catch (Exception e) {
            logger.warn("Error collecting tracking data for {}: {}", page.url(), e.getMessage());
        }

        // Check against TRACKING_KEYWORDS
        for (Map.Entry<String, List<String>> entry : TRACKING_KEYWORDS.entrySet()) {
            String serviceKey = entry.getKey();
            List<String> keywords = entry.getValue();
            boolean found = allCollectedData.stream().anyMatch(data ->
                    keywords.stream().anyMatch(data::contains)
            );
            metaDetails.put(serviceKey, found ? YES : NO);
        }
    }

    /**
     * Extracts potential JSON data from well-known global JavaScript variables.
     * This method evaluates JavaScript expressions in the browser context.
     * The extracted JSON is then added to metaDetails under 'dynamic_json_data'.
     *
     * @param page The Playwright Page object.
     * @param metaDetails The map to store extracted meta details.
     */
    private static void extractGlobalJsonData(Page page, Map<String, String> metaDetails) {
        Map<String, JsonElement> dynamicJsonMap = new HashMap<>();

        for (String varName : GLOBAL_JSON_VARIABLES) {
            try {
                // Evaluate the JavaScript expression to get the content of the global variable
                Object jsonString = page.evaluate("() => JSON.stringify(" + varName + ")");

                if (jsonString instanceof String && !((String) jsonString).trim().isEmpty()) {
                    try {
                        JsonElement jsonElement = JsonParser.parseString((String) jsonString);
                        // Store the parsed JSON element for further processing or just note its presence
                        dynamicJsonMap.put(varName.replace("window.", ""), jsonElement);

                        // Optionally, you can extract specific fields like title, description from these dynamic JSONs
                        if (jsonElement.isJsonObject()) {
                            JsonObject obj = jsonElement.getAsJsonObject();
                            if (obj.has("title") && metaDetails.get("dynamic_title") == null) {
                                metaDetails.put("dynamic_title", safeValue(obj.get("title").getAsString()));
                            }
                            if (obj.has("description") && metaDetails.get("dynamic_description") == null) {
                                metaDetails.put("dynamic_description", safeValue(obj.get("description").getAsString()));
                            }
                            // Add more specific fields as needed
                        }

                    } catch (JsonSyntaxException e) {
                        logger.debug("Non-JSON content or malformed JSON in global variable {}: {}", varName, e.getMessage().split("\n")[0]);
                    }
                }
            } catch (Exception e) {
                // This typically means the global variable doesn't exist on the page
                logger.trace("Global variable {} not found or inaccessible on {}: {}", varName, page.url(), e.getMessage());
            }
        }

        if (!dynamicJsonMap.isEmpty()) {
            // Convert the map of JsonElements to a single JSON string for storage, if needed.
            // Or iterate through `dynamicJsonMap` to extract specific values.
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            metaDetails.put("dynamic_json_variables_detected", YES);
            // You can choose to store the entire JSON or just flags/specific values.
            // Storing full JSON might be too verbose if you only need certain fields.
            // For demonstration, let's store a summary or key names.
            metaDetails.put("dynamic_json_variable_names", String.join(", ", dynamicJsonMap.keySet()));
            // Example of storing the first detected JSON's string representation (can be large)
            // if(!dynamicJsonMap.isEmpty()) {
            //     Map.Entry<String, JsonElement> firstEntry = dynamicJsonMap.entrySet().iterator().next();
            //     metaDetails.put("first_dynamic_json_content_" + firstEntry.getKey(), gson.toJson(firstEntry.getValue()));
            // }

        } else {
            metaDetails.put("dynamic_json_variables_detected", NO);
            metaDetails.put("dynamic_json_variable_names", NOT_FOUND);
        }
    }


    private static String getFaviconUrl(Page page, String basePageUrl) {
        String faviconUrl = "";
        try {
            List<String> selectors = Arrays.asList(
                    "link[rel='icon']",
                    "link[rel='shortcut icon']",
                    "link[rel='apple-touch-icon']",
                    "link[rel='apple-touch-icon-precomposed']"
            );
            for (String selector : selectors) {
                ElementHandle el = page.querySelector(selector);
                if (el != null) {
                    faviconUrl = el.getAttribute("href");
                    if (faviconUrl != null && !faviconUrl.isEmpty()) break;
                }
            }

            if (faviconUrl == null || faviconUrl.isEmpty()) {
                try {
                    URL url = new URL(basePageUrl);
                    faviconUrl = url.getProtocol() + "://" + url.getHost() + "/favicon.ico";
                } catch (MalformedURLException e) {
                    logger.debug("Could not form base URL for favicon.ico for {}: {}", basePageUrl, e.getMessage());
                }
            }

            if (faviconUrl != null && !faviconUrl.isEmpty() && !faviconUrl.startsWith("http") && !faviconUrl.startsWith("//")) {
                try {
                    URL base = new URL(basePageUrl);
                    URL absolute = new URL(base, faviconUrl);
                    faviconUrl = absolute.toString();
                } catch (MalformedURLException e) {
                    logger.debug("Could not make favicon URL absolute: {} (base: {}): {}", faviconUrl, basePageUrl, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting favicon for {}: {}", basePageUrl, e.getMessage());
        }
        return safeValue(faviconUrl);
    }

    private static void extractHreflangLinks(Page page, Map<String, String> metaDetails) {
        List<String> hreflangDetails = new ArrayList<>();
        try {
            List<ElementHandle> hreflangElements = page.querySelectorAll("link[rel='alternate'][hreflang]");
            for (ElementHandle el : hreflangElements) {
                String lang = el.getAttribute("hreflang");
                String href = el.getAttribute("href");
                if (lang != null && href != null) {
                    hreflangDetails.add(lang.trim() + ":" + href.trim());
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting hreflang links for {}: {}", page.url(), e.getMessage());
        }
        metaDetails.put("hreflang_links", hreflangDetails.isEmpty() ? NOT_FOUND : String.join("; ", hreflangDetails));
        metaDetails.put("hreflang_count", String.valueOf(hreflangDetails.size()));
    }

    private static void countLinks(Page page, Map<String, String> metaDetails, String pageUrlString) {
        int internalLinks = 0;
        int externalLinks = 0;
        try {
            URL baseUrl = new URL(pageUrlString);
            String baseHost = baseUrl.getHost();

            List<ElementHandle> allLinks = page.querySelectorAll("a[href]");
            for (ElementHandle linkEl : allLinks) {
                String href = linkEl.getAttribute("href");
                if (href == null || href.trim().isEmpty() || href.startsWith("#") || href.startsWith("javascript:") || href.startsWith("mailto:")) {
                    continue;
                }
                try {
                    URL currentUrl = new URL(baseUrl, href);
                    if (baseHost.equalsIgnoreCase(currentUrl.getHost())) {
                        internalLinks++;
                    } else {
                        externalLinks++;
                    }
                } catch (MalformedURLException e) {
                    logger.trace("Malformed link found on {}: {} - {}", pageUrlString, href, e.getMessage());
                    if (!href.startsWith("http")) {
                        // Assuming relative paths that are malformed are still internal attempts
                        internalLinks++;
                    } else {
                        externalLinks++;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error counting links for {}: {}", pageUrlString, e.getMessage());
        }
        metaDetails.put("internal_links_count", String.valueOf(internalLinks));
        metaDetails.put("external_links_count", String.valueOf(externalLinks));
    }

    private static void extractBodyWordCount(Page page, Map<String, String> metaDetails) {
        long wordCount = 0;
        try {
            String bodyText = page.locator("body").textContent();
            if (bodyText != null && !bodyText.trim().isEmpty()) {
                Pattern wordPattern = Pattern.compile("\\b\\w+\\b");
                java.util.regex.Matcher matcher = wordPattern.matcher(bodyText);
                while (matcher.find()) {
                    wordCount++;
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting body text for word count on {}: {}", page.url(), e.getMessage());
        }
        metaDetails.put("body_word_count", String.valueOf(wordCount));
    }
}