package org.zee;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TaboolaWidgetChecker {
    private static final Logger logger = LoggerFactory.getLogger(TaboolaWidgetChecker.class);

    // Keep these constants
    public static final String TYPE_INFINITE_FEED = "Infinite Position (Feed)";
    public static final String TYPE_SINGLE_IMAGE_STANDARD = "Single Image (Standard)";
    public static final String TYPE_UNKNOWN_MODE_PREFIX = "Unknown Mode: ";
    public static final String NO_WIDGET_FOUND = "No Taboola Widget Found";
    public static final String ERROR_PREFIX = "Error checking Taboola: ";

    /**
     * Checks the Taboola widget types and modes from the given HTML content.
     * It collects all detected instances.
     *
     * @param htmlContent The HTML content of the webpage to analyze.
     * @param pageUrl     The URL of the page (for logging purposes).
     * @return A List of strings, each indicating a widget type/mode found.
     * Returns a list containing NO_WIDGET_FOUND if none are found.
     */
    public static List<String> checkTaboolaWidgetFromContent(String htmlContent, String pageUrl) {
        List<String> detectedWidgets = new ArrayList<>();

        if (htmlContent == null || htmlContent.isEmpty()) {
            logger.warn("HTML content is empty for URL: {}. Cannot check for Taboola widget.", pageUrl);
            detectedWidgets.add(NO_WIDGET_FOUND);
            return detectedWidgets;
        }

        try {
            Document doc = Jsoup.parse(htmlContent);

            // Set to store unique modes found to avoid duplicates
            // We'll use a descriptive string that includes the source (attribute, script, container)
            // and the mode/type.
            List<String> uniqueWidgetDescriptions = new ArrayList<>();

            // --- Step 1: Check for data-taboola-mode attributes ---
            Elements taboolaDivs = doc.select("div[data-taboola-mode]");
            for (Element div : taboolaDivs) {
                String modeAttr = div.attr("data-taboola-mode").trim();
                String widgetType = classifyMode(modeAttr);
                String description = "Attribute: " + modeAttr + " (" + widgetType + ")";
                if (!uniqueWidgetDescriptions.contains(description)) {
                    uniqueWidgetDescriptions.add(description);
                    logger.info("Detected Taboola widget with mode attribute: '{}' for URL: {}", modeAttr, pageUrl);
                }
            }

            // --- Step 2: Scan scripts for mode ---
            String taboolaScriptText = doc.select("script").stream()
                    .map(Element::html)
                    .filter(script -> script.contains("_taboola.push") || script.contains("window._taboola"))
                    .collect(Collectors.joining(" "));

            if (!taboolaScriptText.isEmpty()) {
                Pattern pattern = Pattern.compile("mode\\s*[:=]\\s*['\"]?([^,'\"\\s\\}]+)['\"]?");
                Matcher matcher = pattern.matcher(taboolaScriptText);
                while (matcher.find()) { // Use while(matcher.find()) to get all matches
                    String mode = matcher.group(1).trim();
                    String widgetType = classifyMode(mode);
                    String description = "Script: " + mode + " (" + widgetType + ")";
                    if (!uniqueWidgetDescriptions.contains(description)) {
                        uniqueWidgetDescriptions.add(description);
                        logger.info("Taboola mode '{}' found in script for URL: {}", mode, pageUrl);
                    }
                }
            }

            // --- Step 3: Enhanced fallback based on container and content hints ---
            Elements taboolaContainers = doc.select("[id*='taboola'], [class*='taboola']");
            if (!taboolaContainers.isEmpty()) {
                // Heuristic for feed - check multiple containers for feed-like characteristics
                boolean potentialFeedContainer = taboolaContainers.stream().anyMatch(el ->
                        el.hasClass("taboola-feed") || el.attr("id").toLowerCase().contains("feed") ||
                                (el.attr("style") != null && el.attr("style").toLowerCase().contains("height: auto")) ||
                                el.select("div.trc_rbox_outer").size() > 5 || // Heuristic for feed
                                el.select("div[id^='taboola-infinite-feed']").isEmpty() // AMP specific feed check for container
                );

                String description = "Container: ";
                if(potentialFeedContainer) {
                    description += TYPE_INFINITE_FEED;
                } else {
                    description += TYPE_SINGLE_IMAGE_STANDARD; // Default for container based
                }

                if (!uniqueWidgetDescriptions.contains(description)) {
                    uniqueWidgetDescriptions.add(description);
                    logger.info("Taboola container detected for URL: {}", pageUrl);
                }
            }

            // --- Step 4: Check for Taboola script presence without specific mode/container ---
            // This is a last resort to indicate Taboola is present even if a specific widget couldn't be classified.
            if (uniqueWidgetDescriptions.isEmpty()) {
                boolean scriptSrcPresent = !doc.select("script[src*='taboola.com']").isEmpty();
                boolean scriptHtmlPresent = !taboolaScriptText.isEmpty();

                if (scriptSrcPresent || scriptHtmlPresent) {
                    String description = "Script Present (Unclassified)";
                    if (!uniqueWidgetDescriptions.contains(description)) {
                        uniqueWidgetDescriptions.add(description);
                        logger.info("Taboola script present but no identifiable widget mode/container for URL: {}", pageUrl);
                    }
                }
            }


            if (uniqueWidgetDescriptions.isEmpty()) {
                logger.info("No Taboola widgets or identifiable scripts found for URL: {}", pageUrl);
                detectedWidgets.add(NO_WIDGET_FOUND);
            } else {
                detectedWidgets.addAll(uniqueWidgetDescriptions);
            }
            return detectedWidgets;

        } catch (Exception e) {
            logger.error("Error processing HTML for Taboola widget on URL {}: {}", pageUrl, e.getMessage(), e);
            detectedWidgets.clear(); // Clear any partial findings on error
            detectedWidgets.add(ERROR_PREFIX + e.getClass().getSimpleName() + ": " + e.getMessage().split("\\n")[0]);
            return detectedWidgets;
        }
    }

    /**
     * Helper method to classify a Taboola mode string into a more general type.
     */
    private static String classifyMode(String mode) {
        String lowerMode = mode.toLowerCase();
        if (lowerMode.contains("feed") || lowerMode.contains("infinity")) {
            return TYPE_INFINITE_FEED;
        }
        if (lowerMode.contains("thumbnails") || lowerMode.contains("grid") ||
                lowerMode.contains("standard") || lowerMode.contains("text-links") ||
                lowerMode.contains("single")) {
            return TYPE_SINGLE_IMAGE_STANDARD;
        }
        return TYPE_UNKNOWN_MODE_PREFIX + mode;
    }
}