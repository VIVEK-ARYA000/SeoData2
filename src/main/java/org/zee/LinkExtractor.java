package org.zee;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LinkExtractor {

    private static final Logger logger = LoggerFactory.getLogger(LinkExtractor.class);

    /**
     * Extracts unique, internal, non-static links from a given base URL.
     *
     * @param baseUrl              The starting URL to extract links from.
     * @param headless             Whether to run Playwright in headless mode.
     * @param navigationTimeoutMs  Timeout for page navigation in milliseconds.
     * @param waitUntilState       Playwright's WaitUntilState for page load.
     * @param postLoadWaitMs       Additional wait after page load.
     * @param staticPageKeywords   Comma-separated string of keywords to filter out static/common pages.
     * @return A Set of unique, filtered internal URLs.
     */
    public Set<String> extractInternalLinks(
            String baseUrl,
            boolean headless,
            int navigationTimeoutMs,
            WaitUntilState waitUntilState,
            int postLoadWaitMs,
            String staticPageKeywords
    ) {
        Set<String> internalLinks = new HashSet<>();
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        Page page = null;

        try {
            URI baseUri = new URI(baseUrl);
            String baseHost = baseUri.getHost();
            Pattern staticPagePattern = compileStaticPagePattern(staticPageKeywords);

            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));

            // SUGGESTION: Add a descriptive User-Agent string
            context = browser.newContext(new Browser.NewContextOptions()
                    .setAcceptDownloads(false)
                    .setUserAgent("YourCompanySeoCrawler/1.0 (+https://yourcompany.com/bot-info.html)")); // REPLACE WITH YOUR INFO

            page = context.newPage();
            page.setDefaultNavigationTimeout(navigationTimeoutMs);

            logger.info("Navigating to base URL for link extraction: {}", baseUrl);
            Response response = page.navigate(baseUrl, new Page.NavigateOptions().setWaitUntil(waitUntilState));

            if (response == null || response.status() >= 400) {
                logger.error("Failed to navigate to base URL {}. Status code: {}. Skipping link extraction.",
                        baseUrl, response != null ? response.status() : "N/A");
                return internalLinks;
            }

            if (postLoadWaitMs > 0) {
                logger.debug("Waiting for {}ms after page load on {}", postLoadWaitMs, baseUrl);
                page.waitForTimeout(postLoadWaitMs);
            }

            // Extract all href attributes from <a> tags
            page.locator("a[href]").all().forEach(anchor -> {
                try {
                    String href = anchor.getAttribute("href");
                    if (href == null || href.trim().isEmpty() || href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("javascript:")) {
                        return; // Skip invalid or non-http links
                    }

                    // FIX: Sanitize the href string before resolving it
                    // This replaces any sequence of whitespace characters (including non-breaking spaces like \u00A0) with an empty string.
                    // This is aggressive and assumes no spaces are valid in the URL path or query.
                    String cleanedHref = href.replaceAll("\\s+", "").trim();
                    // If you need to preserve standard spaces (e.g., in query parameters), you might use:
                    // String cleanedHref = href.replace("\u00A0", " ").trim();
                    // However, for paths, removing all whitespace is often the intent for robust URLs.

                    URI resolvedUri = baseUri.resolve(cleanedHref); // Use the cleaned href
                    String resolvedUrl = resolvedUri.normalize().toString(); // Normalize the resolved URI

                    // SUGGESTION: Directly use resolvedUri instead of creating a new URI object from its string.
                    // URI currentUri = new URI(resolvedUrl); // Remove this line
                    // Use 'resolvedUri' directly below:
                    String currentHost = resolvedUri.getHost();

                    // Check if it's an internal link (same host)
                    if (baseHost != null && baseHost.equalsIgnoreCase(currentHost)) {
                        // Normalize path (remove trailing slash for comparison, unless it's just the root)
                        String normalizedPath = resolvedUri.getPath(); // Use resolvedUri directly
                        if (normalizedPath.endsWith("/") && !normalizedPath.equals("/")) {
                            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
                        }

                        // Reconstruct URL for consistent hashing (without query/fragment for basic uniqueness)
                        // Use resolvedUri.getScheme() directly
                        String normalizedUrl = resolvedUri.getScheme() + "://" + currentHost + normalizedPath;

                        // Filter out static pages
                        if (!staticPagePattern.matcher(normalizedUrl.toLowerCase()).find()) {
                            if (internalLinks.add(normalizedUrl)) { // Add if unique
                                logger.debug("Extracted internal link: {}", normalizedUrl);
                            } else {
                                logger.trace("Skipped duplicate internal link: {}", normalizedUrl);
                            }
                        } else {
                            logger.debug("Skipped static page link: {}", normalizedUrl);
                        }
                    } else {
                        logger.trace("Skipped external link: {}", resolvedUrl);
                    }
                } catch (PlaywrightException e) {
                    logger.warn("Playwright error while extracting link on {}: {}", baseUrl, e.getMessage());
                }
                // SUGGESTION: Add a general catch-all for any other unexpected runtime exceptions
                catch (Exception e) {
                    logger.warn("An unexpected error occurred processing an anchor on {}: {}. Href: {}", baseUrl, e.getMessage(), anchor.getAttribute("href"), e);
                }
            });

            logger.info("Finished extracting links from {}. Found {} unique internal links.", baseUrl, internalLinks.size());

        } catch (URISyntaxException e) {
            logger.error("Invalid base URL provided for link extraction: {}. Error: {}", baseUrl, e.getMessage());
        } catch (PlaywrightException e) {
            logger.error("Playwright error during link extraction from {}: {}", baseUrl, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during link extraction from {}: {}", baseUrl, e.getMessage(), e);
        } finally {
            if (page != null) page.close();
            if (context != null) context.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        }
        return internalLinks;
    }

    /**
     * Compiles a regex pattern from a comma-separated string of keywords.
     * The pattern will match if any keyword is found in the URL path.
     *
     * @param keywordsString Comma-separated keywords (e.g., "about,contact us,privacy")
     * @return A compiled Pattern.
     */
    private Pattern compileStaticPagePattern(String keywordsString) {
        if (keywordsString == null || keywordsString.trim().isEmpty()) {
            return Pattern.compile("(?!)"); // Pattern that never matches
        }
        String[] keywords = keywordsString.split(",");
        String regex = Arrays.stream(keywords)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Pattern::quote) // Escape special regex characters
                .collect(Collectors.joining("|", ".*(", ").*")); // Example: .*(about|contact us|privacy).*
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }
}