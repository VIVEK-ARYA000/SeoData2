package org.zee;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.LoadState; // Import LoadState for explicit waitForLoadState usage

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.zee.MetaDetails.NOT_FOUND;

public class SeoTest {

    private static final Logger logger = LoggerFactory.getLogger(SeoTest.class);
    private static final Properties config = new Properties();

    // Configuration Keys
    //ello
    private static final String CONFIG_URL_INPUT_FILE = "url.input.file";
    private static final String CONFIG_EXCEL_OUTPUT_FILE = "excel.output.file";
    private static final String CONFIG_EXCEL_SHEET_NAME = "excel.sheet.name";
    private static final String CONFIG_HEADLESS_MODE = "headless.mode";
    private static final String CONFIG_PAGE_NAVIGATION_TIMEOUT_MS = "page.navigation.timeout.ms";
    private static final String CONFIG_PAGE_LOAD_WAIT_UNTIL = "page.load.waituntil";
    private static final String CONFIG_POST_DOMCONTENTLOADED_WAIT_MS = "post.domcontentloaded.wait.ms";
    private static final String CONFIG_NUMBER_OF_THREADS = "number.of.threads";
    private static final String CONFIG_BASE_URL = "base.url";
    private static final String CONFIG_STATIC_PAGE_KEYWORDS = "static.page.keywords";
    private static final String CONFIG_READ_URL_FILE_ENABLED = "read.url.file.enabled";

    private static final String CONFIG_ELEMENT_WAIT_TIMEOUT_MS = "element.wait.timeout.ms";
    private static final String CONFIG_NETWORK_METRIC_MAX_RETRIES = "network.metric.max.retries";
    private static final String CONFIG_NETWORK_METRIC_RETRY_DELAY_MS = "network.metric.retry.delay.ms";


    // Excel Column Definitions
    private static final class ExcelColumns {
        static final int URL = 0;
        static final int TID_GA4 = 1;
        static final int COMSCORE = 2;
        static final int PPID = 3;
        static final int TITLE = 4;
        static final int DESCRIPTION = 5;
        static final int KEYWORDS = 6;
        static final int H1_COUNT = 7;
        static final int CANONICAL_URL = 8;
        static final int CANONICAL_VALIDATION = 9;
        static final int IS_AMP = 10;
        static final int AMP_URL = 11;
        static final int LOTAME = 12;
        static final int CHARTBEAT = 13;
        static final int IZOOTO = 14;
        static final int VDO_IO = 15;
        static final int SCHEMA_PRESENT = 16;
        static final int SCHEMA_TYPES = 17;
        static final int SCHEMA_ERROR = 18;
        static final int STATUS_CODE = 19;
        static final int OG_TITLE = 20;
        static final int OG_DESCRIPTION = 21;
        static final int OG_IMAGE = 22;
        static final int OG_URL = 23;
        static final int OG_TYPE = 24;
        static final int OG_SITE_NAME = 25;
        static final int TWITTER_CARD = 26;
        static final int TWITTER_SITE = 27;
        static final int TWITTER_CREATOR = 28;
        static final int TWITTER_TITLE = 29;
        static final int TWITTER_DESCRIPTION = 30;
        static final int TWITTER_IMAGE = 31;
        static final int HTML_LANG = 32;
        static final int VIEWPORT = 33;
        static final int META_ROBOTS = 34;
        static final int PUBLISHER_LINK = 35;
        static final int HREFLANG_LINKS = 36;
        static final int HREFLANG_COUNT = 37;
        static final int INTERNAL_LINKS_COUNT = 38;
        static final int EXTERNAL_LINKS_COUNT = 39;
        static final int BODY_WORD_COUNT = 40;
        static final int FAVICON_URL = 41;
        static final int TABOOLA_WIDGET = 42;
        static final int PROCESSING_ERROR = 43;

        static final String[] HEADERS = {
                "URL", "TID (GA4)", "COMSCORE", "PPID", "Title", "Description", "Keywords", "H1 Count",
                "Canonical URL", "Canonical Validation", "Is AMP Page", "AMP URL",
                "Lotame", "Chartbeat", "Izooto", "VDO.AI",
                "Schema Present", "Schema Types", "Schema Error(s)", "Status Code",
                "OG:Title", "OG:Description", "OG:Image", "OG:URL", "OG:Type", "OG:SiteName",
                "Twitter:Card", "Twitter:Site", "Twitter:Creator", "Twitter:Title", "Twitter:Description", "Twitter:Image",
                "HTML Lang", "Viewport", "Meta Robots", "Publisher Link",
                "Hreflang Links", "Hreflang Count", "Internal Links", "External Links", "Body Word Count", "Favicon URL",
                "Taboola Widget", "Processing Error"
        };

        static final int TOTAL_COLUMNS = HEADERS.length;
    }

    private static class SeoResult {
        String url;
        Map<String, String> metaDetails;
        Set<String> uniqueTids;
        String ppid;

        public SeoResult(String url, Map<String, String> metaDetails, Set<String> uniqueTids, String ppid) {
            this.url = url;
            this.metaDetails = metaDetails;
            this.uniqueTids = uniqueTids;
            this.ppid = ppid;
        }
    }

    private static void loadConfiguration() {
        try (InputStream input = new FileInputStream("config.properties")) {
            config.load(input);
            logger.info("Configuration loaded successfully from config.properties");
        } catch (IOException ex) {
            logger.error("Sorry, unable to find or load config.properties. Using default values.", ex);
            config.setProperty(CONFIG_URL_INPUT_FILE, "URL.txt");
            config.setProperty(CONFIG_EXCEL_OUTPUT_FILE, "SeoAnalysisReport_Default.xlsx");
            config.setProperty(CONFIG_EXCEL_SHEET_NAME, "SeoDataDefault");
            config.setProperty(CONFIG_HEADLESS_MODE, "false");
            config.setProperty(CONFIG_PAGE_NAVIGATION_TIMEOUT_MS, "90000");
            config.setProperty(CONFIG_PAGE_LOAD_WAIT_UNTIL, "domcontentloaded");
            config.setProperty(CONFIG_POST_DOMCONTENTLOADED_WAIT_MS, "5000");
            config.setProperty(CONFIG_NUMBER_OF_THREADS, "1");
            config.setProperty(CONFIG_BASE_URL, ""); // No default base URL, assumes file input if not set
            config.setProperty(CONFIG_STATIC_PAGE_KEYWORDS, "about,contact-us,privacy-policy,terms-of-use,careers,sitemap,advertise,feedback");
            config.setProperty(CONFIG_READ_URL_FILE_ENABLED, "true"); // Default to true for reading URL.txt

            // Ensure these properties are set even if config.properties is missing
            config.setProperty(CONFIG_NETWORK_METRIC_MAX_RETRIES, "3");
            config.setProperty(CONFIG_NETWORK_METRIC_RETRY_DELAY_MS, "2000");
        }
    }

    public static void main(String[] args) {
        loadConfiguration();
        logger.info("Starting SEO Data Extraction Process...");

        Set<String> allUrlsToProcess = new LinkedHashSet<>(); // Use LinkedHashSet to maintain order and ensure uniqueness

        // --- 1. Get URLs from base URL extraction (if configured) ---
        String baseUrl = config.getProperty(CONFIG_BASE_URL);
        if (baseUrl != null && !baseUrl.isEmpty()) {
            logger.info("Attempting to extract internal links from base URL: {}", baseUrl);
            LinkExtractor linkExtractor = new LinkExtractor();
            boolean headless = Boolean.parseBoolean(config.getProperty(CONFIG_HEADLESS_MODE, "true"));
            int navigationTimeout = Integer.parseInt(config.getProperty(CONFIG_PAGE_NAVIGATION_TIMEOUT_MS, "90000"));
            String waitUntilString = config.getProperty(CONFIG_PAGE_LOAD_WAIT_UNTIL, "domcontentloaded");
            WaitUntilState waitUntilState = WaitUntilState.DOMCONTENTLOADED;
            try {
                waitUntilState = WaitUntilState.valueOf(waitUntilString.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid waitUntil state '{}' in config. Defaulting to DOMCONTENTLOADED.", waitUntilString);
            }
            int postNavigationWait = Integer.parseInt(config.getProperty(CONFIG_POST_DOMCONTENTLOADED_WAIT_MS, "5000"));
            String staticPageKeywords = config.getProperty(CONFIG_STATIC_PAGE_KEYWORDS);

            Set<String> extractedLinks = linkExtractor.extractInternalLinks(
                    baseUrl, headless, navigationTimeout, waitUntilState, postNavigationWait, staticPageKeywords
            );
            allUrlsToProcess.addAll(extractedLinks);
            logger.info("Extracted {} unique internal links from base URL.", extractedLinks.size());
        } else {
            logger.info("No base URL configured for link extraction. Skipping dynamic link discovery.");
        }


        // --- 2. Get URLs from URL.txt file (if enabled) ---
        boolean readUrlFileEnabled = Boolean.parseBoolean(config.getProperty(CONFIG_READ_URL_FILE_ENABLED, "true"));
        if (readUrlFileEnabled) {
            List<String> fileUrls = readUrlsFromFile(config.getProperty(CONFIG_URL_INPUT_FILE));
            if (!fileUrls.isEmpty()) {
                logger.info("Adding {} URLs from file '{}'.", fileUrls.size(), config.getProperty(CONFIG_URL_INPUT_FILE));
                allUrlsToProcess.addAll(fileUrls); // Add all from file, LinkedHashSet handles duplicates
            } else {
                logger.warn("No URLs found in '{}'.", config.getProperty(CONFIG_URL_INPUT_FILE));
            }
        } else {
            logger.info("Reading URLs from file is disabled via 'read.url.file.enabled=false'.");
        }

        // Convert the Set back to a List for threaded processing
        List<String> finalUrlsToProcess = new ArrayList<>(allUrlsToProcess);

        if (finalUrlsToProcess.isEmpty()) {
            logger.warn("No URLs found to process from any source. Exiting.");
            return;
        }
        logger.info("Total unique URLs to process: {}", finalUrlsToProcess.size());
        int numberOfThreads = Integer.parseInt(config.getProperty(CONFIG_NUMBER_OF_THREADS, "1"));
        boolean headless = Boolean.parseBoolean(config.getProperty(CONFIG_HEADLESS_MODE, "true"));
        int navigationTimeout = Integer.parseInt(config.getProperty(CONFIG_PAGE_NAVIGATION_TIMEOUT_MS, "90000"));
        String waitUntilString = config.getProperty(CONFIG_PAGE_LOAD_WAIT_UNTIL, "domcontentloaded");
        WaitUntilState waitUntilState = WaitUntilState.DOMCONTENTLOADED;
        try {
            waitUntilState = WaitUntilState.valueOf(waitUntilString.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid waitUntil state '{}' in config. Defaulting to DOMCONTENTLOADED.", waitUntilString);
        }
        int postNavigationWait = Integer.parseInt(config.getProperty(CONFIG_POST_DOMCONTENTLOADED_WAIT_MS, "5000"));

        Workbook workbook = null;
        Sheet sheet = null;
        File outputFile = new File(config.getProperty(CONFIG_EXCEL_OUTPUT_FILE));
        String sheetName = config.getProperty(CONFIG_EXCEL_SHEET_NAME);
        int initialRowIndex = 0;

        try {
            if (outputFile.exists() && outputFile.length() > 0) {
                try (FileInputStream fis = new FileInputStream(outputFile)) {
                    workbook = new XSSFWorkbook(fis);
                    sheet = workbook.getSheet(sheetName);
                    if (sheet == null) {
                        logger.info("Sheet '{}' not found in existing workbook. Creating it.", sheetName);
                        sheet = workbook.createSheet(sheetName);
                        createHeaderRow(sheet);
                        initialRowIndex = 1;
                    } else {
                        if (sheet.getPhysicalNumberOfRows() == 0 || sheet.getRow(0) == null || sheet.getRow(0).getLastCellNum() <= 0) {
                            createHeaderRow(sheet);
                            initialRowIndex = 1;
                        } else {
                            initialRowIndex = sheet.getLastRowNum() + 1;
                            logger.info("Appending data to existing sheet '{}'. Starting at row: {}", sheetName, initialRowIndex);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not open existing workbook '{}', creating a new one. Error: {}", outputFile.getName(), e.getMessage());
                    workbook = new XSSFWorkbook();
                    sheet = workbook.createSheet(sheetName);
                    createHeaderRow(sheet);
                    initialRowIndex = 1;
                }
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet(sheetName);
                createHeaderRow(sheet);
                initialRowIndex = 1;
                logger.info("Creating new workbook '{}' and sheet '{}'.", outputFile.getName(), sheetName);
            }

            List<SeoResult> allSeoResults = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            List<Future<SeoResult>> futures = new ArrayList<>();

            // Retrieve retry configuration from properties outside the loop
            final int maxRetries = Integer.parseInt(config.getProperty(CONFIG_NETWORK_METRIC_MAX_RETRIES, "3"));
            final int retryDelay = Integer.parseInt(config.getProperty(CONFIG_NETWORK_METRIC_RETRY_DELAY_MS, "2000"));

            for (String url : finalUrlsToProcess) {
                UrlProcessorTask task = new UrlProcessorTask(
                        url, headless, navigationTimeout, waitUntilState, postNavigationWait, maxRetries, retryDelay
                );
                futures.add(executor.submit(task));
            }

            int processedCount = 0;
            for (Future<SeoResult> future : futures) {
                try {
                    SeoResult result = future.get();
                    allSeoResults.add(result);
                    processedCount++;
                    logger.info("Completed processing for URL: {}", result.url);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error processing URL in a thread: {}", e.getMessage(), e);
                }
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                    logger.warn("Executor did not terminate in the specified time.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("Executor termination interrupted.", e);
                executor.shutdownNow();
            }

            for (SeoResult result : allSeoResults) {
                Row row = sheet.createRow(initialRowIndex++);
                writeRowToSheet(row, result.url, result.metaDetails, result.uniqueTids, result.ppid);
            }

            for (int j = 0; j < ExcelColumns.TOTAL_COLUMNS; j++) {
                try {
                    sheet.autoSizeColumn(j);
                } catch (Exception e) {
                    logger.warn("Failed to autoSize column {}. Error: {}", j, e.getMessage());
                }
            }
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                workbook.write(out);
                logger.info("SEO analysis report saved to '{}'", outputFile.getAbsolutePath());
            }

        } catch (IOException e) {
            logger.error("Error writing Excel file: {}", e.getMessage(), e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    logger.error("Error closing workbook: {}", e.getMessage(), e);
                }
            }
            logger.info("SEO Data Extraction Process Finished.");
        }
    }


    private static List<String> readUrlsFromFile(String filePath) {
        Set<String> urlSet = new LinkedHashSet<>(); // Use a Set to automatically handle duplicates
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) { // Ignore empty lines and comments
                    urlSet.add(line);
                }
            }
            logger.info("Successfully read {} URLs from '{}'.", urlSet.size(), filePath);
        } catch (FileNotFoundException e) {
            logger.error("URL input file not found: {}", filePath);
        } catch (IOException e) {
            logger.error("Error reading URL file '{}': {}", filePath, e.getMessage(), e);
        }
        return new ArrayList<>(urlSet);
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < ExcelColumns.HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(ExcelColumns.HEADERS[i]);
        }
        logger.info("Excel header row created in sheet '{}'.", sheet.getSheetName());
    }

    private static void writeRowToSheet(Row row, String urlValue, Map<String, String> meta, Set<String> tids, String ppid) {
        Function<String, String> getMeta = key -> meta.getOrDefault(key, NOT_FOUND);
        row.createCell(ExcelColumns.URL).setCellValue(urlValue);
        row.createCell(ExcelColumns.TID_GA4).setCellValue(tids.isEmpty() ? NOT_FOUND : String.join(", ", tids));
        row.createCell(ExcelColumns.COMSCORE).setCellValue(getMeta.apply("comscore"));
        row.createCell(ExcelColumns.PPID).setCellValue(ppid.isEmpty() ? NOT_FOUND : ppid);
        row.createCell(ExcelColumns.TITLE).setCellValue(getMeta.apply("title"));
        row.createCell(ExcelColumns.DESCRIPTION).setCellValue(getMeta.apply("description"));
        row.createCell(ExcelColumns.KEYWORDS).setCellValue(getMeta.apply("keywords"));
        row.createCell(ExcelColumns.H1_COUNT).setCellValue(getMeta.apply("h1_count"));
        row.createCell(ExcelColumns.CANONICAL_URL).setCellValue(getMeta.apply("canonical"));
        row.createCell(ExcelColumns.CANONICAL_VALIDATION).setCellValue(getMeta.apply("canonical_validation"));
        row.createCell(ExcelColumns.IS_AMP).setCellValue(getMeta.apply("is_amp"));
        row.createCell(ExcelColumns.AMP_URL).setCellValue(getMeta.apply("amp_url"));
        row.createCell(ExcelColumns.LOTAME).setCellValue(getMeta.apply("lotame"));
        row.createCell(ExcelColumns.CHARTBEAT).setCellValue(getMeta.apply("chartbeat"));
        row.createCell(ExcelColumns.IZOOTO).setCellValue(getMeta.apply("izooto"));
        row.createCell(ExcelColumns.VDO_IO).setCellValue(getMeta.apply("vdo_io"));
        row.createCell(ExcelColumns.SCHEMA_PRESENT).setCellValue(getMeta.apply("schema_present"));
        row.createCell(ExcelColumns.SCHEMA_TYPES).setCellValue(getMeta.apply("schema_types"));
        row.createCell(ExcelColumns.SCHEMA_ERROR).setCellValue(getMeta.apply("schema_error"));
        row.createCell(ExcelColumns.STATUS_CODE).setCellValue(getMeta.apply("status_code"));
        row.createCell(ExcelColumns.OG_TITLE).setCellValue(getMeta.apply("og_title"));
        row.createCell(ExcelColumns.OG_DESCRIPTION).setCellValue(getMeta.apply("og_description"));
        row.createCell(ExcelColumns.OG_IMAGE).setCellValue(getMeta.apply("og_image"));
        row.createCell(ExcelColumns.OG_URL).setCellValue(getMeta.apply("og_url"));
        row.createCell(ExcelColumns.OG_TYPE).setCellValue(getMeta.apply("og_type"));
        row.createCell(ExcelColumns.OG_SITE_NAME).setCellValue(getMeta.apply("og_site_name"));
        row.createCell(ExcelColumns.TWITTER_CARD).setCellValue(getMeta.apply("twitter_card"));
        row.createCell(ExcelColumns.TWITTER_SITE).setCellValue(getMeta.apply("twitter_site"));
        row.createCell(ExcelColumns.TWITTER_CREATOR).setCellValue(getMeta.apply("twitter_creator"));
        row.createCell(ExcelColumns.TWITTER_TITLE).setCellValue(getMeta.apply("twitter_title"));
        row.createCell(ExcelColumns.TWITTER_DESCRIPTION).setCellValue(getMeta.apply("twitter_description"));
        row.createCell(ExcelColumns.TWITTER_IMAGE).setCellValue(getMeta.apply("twitter_image"));
        row.createCell(ExcelColumns.HTML_LANG).setCellValue(getMeta.apply("html_lang"));
        row.createCell(ExcelColumns.VIEWPORT).setCellValue(getMeta.apply("viewport"));
        row.createCell(ExcelColumns.META_ROBOTS).setCellValue(getMeta.apply("meta_robots"));
        row.createCell(ExcelColumns.PUBLISHER_LINK).setCellValue(getMeta.apply("publisher_link"));
        row.createCell(ExcelColumns.HREFLANG_LINKS).setCellValue(getMeta.apply("hreflang_links"));
        row.createCell(ExcelColumns.HREFLANG_COUNT).setCellValue(getMeta.apply("hreflang_count"));
        row.createCell(ExcelColumns.INTERNAL_LINKS_COUNT).setCellValue(getMeta.apply("internal_links_count"));
        row.createCell(ExcelColumns.EXTERNAL_LINKS_COUNT).setCellValue(getMeta.apply("external_links_count"));
        row.createCell(ExcelColumns.BODY_WORD_COUNT).setCellValue(getMeta.apply("body_word_count"));
        row.createCell(ExcelColumns.FAVICON_URL).setCellValue(getMeta.apply("favicon_url"));
        row.createCell(ExcelColumns.TABOOLA_WIDGET).setCellValue(getMeta.apply("taboola_widget"));
        row.createCell(ExcelColumns.PROCESSING_ERROR).setCellValue(getMeta.apply("processing_error"));
    }

    private static String getRandomUserAgent() {
        String[] userAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        };
        return userAgents[new Random().nextInt(userAgents.length)];
    }

    private static class UrlProcessorTask implements Callable<SeoResult> {
        private final String url;
        private final boolean headless;
        private final int navigationTimeout;
        private final WaitUntilState waitUntilState;
        private final int postNavigationWait;
        private final int maxRetries;
        private final int retryDelay;

        public UrlProcessorTask(String url, boolean headless, int navigationTimeout,
                                WaitUntilState waitUntilState, int postNavigationWait,
                                int maxRetries, int retryDelay) {
            this.url = url;
            this.headless = headless;
            this.navigationTimeout = navigationTimeout;
            this.waitUntilState = waitUntilState;
            this.postNavigationWait = postNavigationWait;
            this.maxRetries = maxRetries;
            this.retryDelay = retryDelay;
        }

        @Override
        public SeoResult call() throws Exception {
            Set<String> uniqueTids = ConcurrentHashMap.newKeySet();
            final String[] ppidHolder = {""};
            Map<String, String> metaDetails = new HashMap<>();
            String processingError = "N/A"; // Default to N/A
            Response response = null;
            metaDetails.put("comscore", "Not Found");
            final String[] currentComscoreResult = {"Not Found"};
            boolean success = false;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                Playwright playwright = null;
                Browser browser = null;
                BrowserContext context = null;
                Page page = null;
                try {
                    logger.info("🔁 Attempt {}/{} for URL: {}", attempt, maxRetries, url);
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
                    context = browser.newContext(new Browser.NewContextOptions()
                            .setUserAgent(getRandomUserAgent())
                            .setAcceptDownloads(false));
                    page = context.newPage();
                    page.setDefaultNavigationTimeout(navigationTimeout); // Apply default timeout

                    page.onRequest(request -> {
                        String reqUrl = request.url();
                        Map<String, String> data = Ga4ExtStable.extractFromRequest(reqUrl); //
                        data.forEach((key, value) -> {
                            if ("tid".equals(key) && value != null && !value.isEmpty()) uniqueTids.add(value);
                            if ("ppid".equals(key) && value != null && !value.isEmpty()) ppidHolder[0] = value;
                        });
                        Map<String, String> comscoreData = ComscoreExtStable.extractFromRequest(reqUrl);
                        if (!comscoreData.isEmpty()) {
                            String c1 = comscoreData.getOrDefault("c1", "N/A");
                            String c2 = comscoreData.getOrDefault("c2", "N/A");
                            currentComscoreResult[0] = "c1=" + c1 + ", c2=" + c2;
                            logger.trace("✅ Comscore detected via network for {}: {}", url, currentComscoreResult[0]);
                        }
                    });

                    // Explicitly setting timeout for navigation
                    response = page.navigate(url, new Page.NavigateOptions()
                            .setWaitUntil(waitUntilState)
                            .setTimeout(navigationTimeout));

                    if (response == null || !response.ok()) {
                        String status = (response != null) ? String.valueOf(response.status()) : "No Response";
                        logger.warn("Received non-OK response (Status: {}) for URL: {}", status, url);
                        processingError = "HTTP Status: " + status;
                        throw new IOException("Non-OK HTTP status or no response");
                    }

                    // Optional: wait for network idle after initial load state
                    // page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(navigationTimeout));

                    if (postNavigationWait > 0) {
                        logger.debug("Waiting for {}ms after navigation for URL: {}", postNavigationWait, url);
                        Thread.sleep(postNavigationWait); // Give more time for dynamic content
                    }

                    // Extract meta tags
                    metaDetails = MetaDetails.extractMetaTags(page, response); //
                    metaDetails.put("comscore", currentComscoreResult[0]);

                    // Canonical URL validation (Moved from MetaDetails as it needs original input URL)
                    String canonicalUrl = metaDetails.get("canonical");
                    String canonicalValidation = "N/A";
                    if (!NOT_FOUND.equals(canonicalUrl)) {
                        canonicalValidation = areUrlsEquivalent(url, canonicalUrl) ? "Valid" : "Mismatch";
                        logger.debug("Canonical validation for {}: Original='{}', Canonical='{}', Result='{}'", url, url, canonicalUrl, canonicalValidation);
                    } else {
                        canonicalValidation = "Not Found";
                    }
                    metaDetails.put("canonical_validation", canonicalValidation);

                    success = true; // Mark as success if all operations complete
                    break; // Exit retry loop on success

                } catch (PlaywrightException e) {
                    processingError = "Playwright Error (Timeout/Navigation): " + e.getMessage().split("\n")[0];
                    logger.error("❌ Playwright error on attempt {}/{} for URL {}: {}", attempt, maxRetries, url, e.getMessage());
                    if (attempt < maxRetries) {
                        logger.warn("Retrying in {}ms for URL: {}", retryDelay, url);
                        Thread.sleep(retryDelay);
                    }
                } catch (IOException e) {
                    processingError = "Network/IO Error: " + e.getMessage().split("\n")[0];
                    logger.error("❌ Network/IO error on attempt {}/{} for URL {}: {}", attempt, maxRetries, url, e.getMessage());
                    if (attempt < maxRetries) {
                        logger.warn("Retrying in {}ms for URL: {}", retryDelay, url);
                        Thread.sleep(retryDelay);
                    }
                } catch (Exception e) {
                    processingError = "Unexpected Error: " + e.getMessage().split("\n")[0];
                    logger.error("❌ Unexpected error on attempt {}/{} for URL {}: {}", attempt, maxRetries, url, e.getMessage(), e);
                    if (attempt < maxRetries) {
                        logger.warn("Retrying in {}ms for URL: {}", retryDelay, url);
                        Thread.sleep(retryDelay);
                    }
                } finally {
                    if (page != null) page.close();
                    if (context != null) context.close();
                    if (browser != null) browser.close();
                    if (playwright != null) playwright.close();
                }
            }

            if (!success) {
                logger.error("❌ All {} attempts failed for URL: {}", maxRetries, url);
                metaDetails.put("status_code", "Failed after retries");
                if ("N/A".equals(processingError)) { // If error wasn't set by a specific catch block
                    processingError = "Failed after " + maxRetries + " retries.";
                }
            } else if (response != null) {
                metaDetails.put("status_code", String.valueOf(response.status()));
            } else {
                // This case should ideally not be reached if success is true, but as a fallback
                metaDetails.put("status_code", "No Response (Success but response null)");
            }

            metaDetails.put("processing_error", processingError);
            return new SeoResult(url, metaDetails, uniqueTids, ppidHolder[0]);
        }
    }

    private static boolean isAmpUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("/amp/") || lowerUrl.matches(".*[&?]amp(=1|=true|&|$).*") || lowerUrl.contains(".amp.html") || (lowerUrl.endsWith("/amp"));
    }

    private static boolean areUrlsEquivalent(String url1, String url2) {
        if (url1 == null || url2 == null) {
            return false;
        }

        // Normalize both URLs before comparison
        String normalizedUrl1 = normalizeSchemeAndHost(url1);
        String normalizedUrl2 = normalizeSchemeAndHost(url2);

        try {
            URI uri1 = new URI(normalizedUrl1);
            URI uri2 = new URI(normalizedUrl2);

            String host1 = uri1.getHost() == null ? "" : uri1.getHost().toLowerCase();
            String host2 = uri2.getHost() == null ? "" : uri2.getHost().toLowerCase();

            // Paths: remove trailing slashes and normalize to "/" if empty
            String path1 = uri1.getPath() == null ? "/" : uri1.getPath().replaceAll("/+$", "");
            if (path1.isEmpty()) path1 = "/";
            String path2 = uri2.getPath() == null ? "/" : uri2.getPath().replaceAll("/+$", "");
            if (path2.isEmpty()) path2 = "/";

            String query1 = sortQueryParameters(uri1.getQuery());
            String query2 = sortQueryParameters(uri2.getQuery());

            return host1.equalsIgnoreCase(host2) &&
                    path1.equalsIgnoreCase(path2) &&
                    Objects.equals(query1, query2);
        } catch (URISyntaxException e) {
            logger.warn("URISyntaxException during areUrlsEquivalent for '{}' vs '{}': {}. Falling back to case-insensitive string comparison of paths.", url1, url2, e.getMessage());
            // Fallback for malformed URIs: simple normalization and comparison
            String simpleNorm1 = url1.replaceFirst("^(http://|https://)(www\\.)?", "").replaceAll("/+$", "").toLowerCase();
            String simpleNorm2 = url2.replaceFirst("^(http://|https://)(www\\.)?", "").replaceAll("/+$", "").toLowerCase();
            return simpleNorm1.equals(simpleNorm2);
        }
    }

    private static String normalizeSchemeAndHost(String url) {
        if (url == null) return null;
        // Convert http to https
        url = url.replaceFirst("^http:", "https:");
        // Remove 'www.' from host
        url = url.replaceFirst("^(https://)www\\.", "$1");
        return url;
    }

    private static String sortQueryParameters(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String[] params = query.split("&");
        Map<String, String> queryParams = new TreeMap<>(); // Use TreeMap to keep keys sorted
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : "";
            queryParams.put(key, value);
        }

        StringBuilder sortedQuery = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (sortedQuery.length() > 0) {
                sortedQuery.append("&");
            }
            sortedQuery.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                sortedQuery.append("=").append(entry.getValue());
            }
        }
        return sortedQuery.toString();
    }


    private static String stripAmp(String url) {
        if (url == null || url.isEmpty()) return "";
        String noAmpUrl = url.replaceAll("\\.amp\\.html$", ""); // Remove .amp.html
        noAmpUrl = noAmpUrl.replaceAll("\\?amp(=[^&]*)?(&|$)", "?"); // Remove ?amp or ?amp=value
        noAmpUrl = noAmpUrl.replaceAll("&amp(=[^&]*)?(&|$)", "&"); // Remove &amp or &amp=value
        noAmpUrl = noAmpUrl.replaceAll("\\?$", "").replaceAll("&$", ""); // Clean trailing ? or & if empty

        // Remove /amp/ segment or /amp at the end
        noAmpUrl = noAmpUrl.replaceAll("/amp(/|$)", "/");

        // Ensure trailing slash consistency if path becomes empty after stripping /amp
        try {
            URI uri = new URI(noAmpUrl);
            if (uri.getPath() == null || uri.getPath().isEmpty()) {
                if (!noAmpUrl.endsWith("/")) noAmpUrl += "/";
            }
        } catch (URISyntaxException e) {
            // If URI syntax is invalid, just proceed with current noAmpUrl
        }

        return noAmpUrl.replaceAll("/+", "/"); // Normalize multiple slashes
    }


}