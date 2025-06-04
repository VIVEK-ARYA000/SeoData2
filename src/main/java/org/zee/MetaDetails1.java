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

public class MetaDetails1 {
    private static final Logger logger = LoggerFactory.getLogger(MetaDetails.class);

    static final String NOT_FOUND = "Not Found";
    private static final String YES = "Yes";
    private static final String NO = "No";
    private static final String NO_ERROR = "No Error";

    private static final Map<String, List<String>> TRACKING_KEYWORDS = Map.of(
            "lotame", List.of("lotame.com", "crwdcntrl.net"),
            "chartbeat", List.of("chartbeat.com", "chartbeat.net"),
            "izooto", List.of("izooto.com"),
            "vdo_io", List.of("vdo.ai")
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
        metaDetails.put("og_title", safeValue(getMetaTagContent(page, "meta[property='og:title']"))); // Redundant if used as fallback for main title
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

        // Tracking scripts detection
        TRACKING_KEYWORDS.keySet().forEach(key -> metaDetails.put(key, NO)); // Initialize
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
            // Prioritize direct content attribute, fallback for some cases like name/http-equiv
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
        // Recursively check for schema types in graph (common for nested structures)
        if (jsonObject.has("@graph") && jsonObject.get("@graph").isJsonArray()) {
            for (JsonElement graphElement : jsonObject.get("@graph").getAsJsonArray()) {
                parseJsonForSchemaTypes(graphElement, schemaTypes);
            }
        }
    }

    private static void detectScripts(Page page, Map<String, String> metaDetails) {
        Set<String> allScriptSources = new HashSet<>();
        try {
            // Collect external script URLs (from src attributes)
            page.querySelectorAll("script[src]").forEach(script -> {
                String src = script.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allScriptSources.add(src.toLowerCase());
                }
            });

            // Collect inline script contents
            page.querySelectorAll("script:not([src])").forEach(script -> {
                String content = script.textContent();
                if (content != null && !content.isEmpty()) {
                    allScriptSources.add(content.toLowerCase());
                }
            });
        } catch (Exception e) {
            logger.warn("Error collecting script sources for {}: {}", page.url(), e.getMessage());
            TRACKING_KEYWORDS.keySet().forEach(key -> metaDetails.put(key, "Detection Error"));
            return;
        }

        // Search each tracking keyword in the combined script data
        for (Map.Entry<String, List<String>> entry : TRACKING_KEYWORDS.entrySet()) {
            String serviceKey = entry.getKey(); // e.g., lotame, chartbeat
            List<String> keywords = entry.getValue();

            boolean found = allScriptSources.stream().anyMatch(script ->
                    keywords.stream().anyMatch(script::contains)
            );

            metaDetails.put(serviceKey, found ? YES : NO);
        }
    }


    private static String getFaviconUrl(Page page, String basePageUrl) {
        String faviconUrl = "";
        try {
            // Standard favicon links
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

            // If still not found, check for /favicon.ico at the domain root
            if (faviconUrl == null || faviconUrl.isEmpty()) {
                try {
                    URL url = new URL(basePageUrl);
                    faviconUrl = url.getProtocol() + "://" + url.getHost() + "/favicon.ico";
                    // Note: This doesn't verify if favicon.ico actually exists, just forms the common URL
                } catch (MalformedURLException e) {
                    logger.debug("Could not form base URL for favicon.ico for {}: {}", basePageUrl, e.getMessage());
                }
            }

            // Make URL absolute if it's relative
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
                    URL currentUrl = new URL(baseUrl, href); // Handles relative URLs
                    if (baseHost.equalsIgnoreCase(currentUrl.getHost())) {
                        internalLinks++;
                    } else {
                        externalLinks++;
                    }
                } catch (MalformedURLException e) {
                    logger.trace("Malformed link found on {}: {} - {}", pageUrlString, href, e.getMessage());
                    // Potentially count as external or skip, depending on policy
                    if (!href.startsWith("http")) { // Likely relative but malformed
                        // internalLinks++; // Or some other logic
                    } else {
                        externalLinks++; // Assume external if it starts with http but is malformed relative to base
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
            // This is a very basic word count from the visible text of the body.
            // It will include navigation, footers, ads, etc.
            // For a more accurate "main content" word count, advanced parsing (e.g. with Jsoup and heuristics) is needed.
            String bodyText = page.locator("body").textContent();
            if (bodyText != null && !bodyText.trim().isEmpty()) {
                // Simple word count: split by whitespace.
                // More sophisticated: use regex \b\w+\b to count words
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