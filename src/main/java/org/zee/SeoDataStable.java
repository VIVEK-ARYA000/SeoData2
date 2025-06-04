package org.zee;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

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
import java.util.concurrent.ConcurrentHashMap; // Kept if future parallel processing is intended
import java.util.function.Function;

import static org.zee.MetaDetails.NOT_FOUND;

public class SeoDataStable {

    private static final Logger logger = LoggerFactory.getLogger(SeoDataStable.class);
    private static final Properties config = new Properties();

    // Configuration Keys
    private static final String CONFIG_URL_INPUT_FILE = "url.input.file";
    private static final String CONFIG_EXCEL_OUTPUT_FILE = "excel.output.file";
    private static final String CONFIG_EXCEL_SHEET_NAME = "excel.sheet.name";
    private static final String CONFIG_HEADLESS_MODE = "headless.mode";
    private static final String CONFIG_PAGE_NAVIGATION_TIMEOUT_MS = "page.navigation.timeout.ms";
    private static final String CONFIG_PAGE_LOAD_WAIT_UNTIL = "page.load.waituntil";
    private static final String CONFIG_POST_DOMCONTENTLOADED_WAIT_MS = "post.domcontentloaded.wait.ms";


    // Excel Column Definitions
    private static final class ExcelColumns {
        static final int URL = 0;
        static final int TID_GA4 = 1;
        static final int PPID = 2;
        static final int TITLE = 3;
        static final int DESCRIPTION = 4;
        static final int KEYWORDS = 5;
        static final int H1_COUNT = 6;
        static final int CANONICAL_URL = 7;
        static final int CANONICAL_VALIDATION = 8;
        static final int IS_AMP = 9;
        static final int AMP_URL = 10;
        static final int LOTAME = 11;
        static final int CHARTBEAT = 12;
        static final int IZOOTO = 13;
        static final int VDO_IO = 14;
        static final int SCHEMA_PRESENT = 15;
        static final int SCHEMA_TYPES = 16;
        static final int SCHEMA_ERROR = 17;
        static final int STATUS_CODE = 18;
        // OG Tags
        static final int OG_TITLE = 19;
        static final int OG_DESCRIPTION = 20;
        static final int OG_IMAGE = 21;
        static final int OG_URL = 22;
        static final int OG_TYPE = 23;
        static final int OG_SITE_NAME = 24;
        // Twitter Card Tags
        static final int TWITTER_CARD = 25;
        static final int TWITTER_SITE = 26;
        static final int TWITTER_CREATOR = 27;
        static final int TWITTER_TITLE = 28;
        static final int TWITTER_DESCRIPTION = 29;
        static final int TWITTER_IMAGE = 30;
        // Additional Meta
        static final int HTML_LANG = 31;
        static final int VIEWPORT = 32;
        static final int META_ROBOTS = 33;
        static final int PUBLISHER_LINK = 34;
        static final int HREFLANG_LINKS = 35;
        static final int HREFLANG_COUNT = 36;
        static final int INTERNAL_LINKS_COUNT = 37;
        static final int EXTERNAL_LINKS_COUNT = 38;
        static final int BODY_WORD_COUNT = 39;
        static final int FAVICON_URL = 40;
        // Control Column
        static final int PROCESSING_ERROR = 41;


        static final String[] HEADERS = {
                "URL", "TID (GA4)", "PPID", "Title", "Description", "Keywords",
                "H1 Count", "Canonical URL", "Canonical Validation", "Is AMP Page", "AMP URL",
                "Lotame", "Chartbeat", "Izooto", "VDO.AI",
                "Schema Present", "Schema Types", "Schema Error(s)", "Status Code",
                "OG:Title", "OG:Description", "OG:Image", "OG:URL", "OG:Type", "OG:SiteName",
                "Twitter:Card", "Twitter:Site", "Twitter:Creator", "Twitter:Title", "Twitter:Description", "Twitter:Image",
                "HTML Lang", "Viewport", "Meta Robots", "Publisher Link",
                "Hreflang Links", "Hreflang Count", "Internal Links", "External Links", "Body Word Count", "Favicon URL",
                "Processing Error"
        };
        static final int TOTAL_COLUMNS = HEADERS.length;
    }

    private static void loadConfiguration() {
        try (InputStream input = new FileInputStream("config.properties")) {
            config.load(input);
            logger.info("Configuration loaded successfully from config.properties");
        } catch (IOException ex) {
            logger.error("Sorry, unable to find or load config.properties. Using default values.", ex);
            // Set defaults if file not found or error
            config.setProperty(CONFIG_URL_INPUT_FILE, "URL.txt");
            config.setProperty(CONFIG_EXCEL_OUTPUT_FILE, "SeoAnalysisReport_Default.xlsx");
            config.setProperty(CONFIG_EXCEL_SHEET_NAME, "SeoDataDefault");
            config.setProperty(CONFIG_HEADLESS_MODE, "false");
            config.setProperty(CONFIG_PAGE_NAVIGATION_TIMEOUT_MS, "90000");
            config.setProperty(CONFIG_PAGE_LOAD_WAIT_UNTIL, "domcontentloaded");
            config.setProperty(CONFIG_POST_DOMCONTENTLOADED_WAIT_MS, "5000");
        }
    }


    public static void main(String[] args) {
        loadConfiguration();
        logger.info("Starting SEO Data Extraction Process...");

        List<String> urls = readUrlsFromFile(config.getProperty(CONFIG_URL_INPUT_FILE));
        if (urls.isEmpty()) {
            logger.warn("No URLs found in '{}'. Exiting.", config.getProperty(CONFIG_URL_INPUT_FILE));
            return;
        }
        logger.info("Total URLs loaded: {}", urls.size());

        boolean headless = Boolean.parseBoolean(config.getProperty(CONFIG_HEADLESS_MODE, "false"));
        int navigationTimeout = Integer.parseInt(config.getProperty(CONFIG_PAGE_NAVIGATION_TIMEOUT_MS, "90000"));
        String waitUntilString = config.getProperty(CONFIG_PAGE_LOAD_WAIT_UNTIL, "domcontentloaded");
        WaitUntilState waitUntilState = WaitUntilState.DOMCONTENTLOADED; // Default
        try {
            waitUntilState = WaitUntilState.valueOf(waitUntilString.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid waitUntil state '{}' in config. Defaulting to DOMCONTENTLOADED.", waitUntilString);
        }
        int postNavigationWait = Integer.parseInt(config.getProperty(CONFIG_POST_DOMCONTENTLOADED_WAIT_MS, "5000"));


        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
            logger.info("Browser launched in {} mode.", headless ? "headless" : "headed");

            Workbook workbook;
            Sheet sheet;
            File outputFile = new File(config.getProperty(CONFIG_EXCEL_OUTPUT_FILE));
            String sheetName = config.getProperty(CONFIG_EXCEL_SHEET_NAME);
            int rowIndex;

            if (outputFile.exists() && outputFile.length() > 0) { // Check if file exists and is not empty
                try (FileInputStream fis = new FileInputStream(outputFile)) {
                    workbook = new XSSFWorkbook(fis);
                    sheet = workbook.getSheet(sheetName);
                    if (sheet == null) {
                        logger.info("Sheet '{}' not found in existing workbook. Creating it.", sheetName);
                        sheet = workbook.createSheet(sheetName);
                        createHeaderRow(sheet);
                        rowIndex = 1; // Start after header
                    } else {
                        rowIndex = sheet.getLastRowNum() + 1;
                        if (sheet.getPhysicalNumberOfRows() == 0) { // Sheet exists but is empty
                            createHeaderRow(sheet);
                            rowIndex = 1;
                        } else if (rowIndex == 0 && sheet.getRow(0) != null) { // Only header exists
                            rowIndex = 1;
                        }
                        logger.info("Appending data to existing sheet '{}'. Starting at row: {}", sheetName, rowIndex);
                    }
                } catch (Exception e) {
                    logger.warn("Could not open existing workbook '{}', creating a new one. Error: {}", outputFile.getName(), e.getMessage());
                    workbook = new XSSFWorkbook();
                    sheet = workbook.createSheet(sheetName);
                    createHeaderRow(sheet);
                    rowIndex = 1;
                }
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet(sheetName);
                createHeaderRow(sheet);
                rowIndex = 1;
                logger.info("Creating new workbook '{}' and sheet '{}'.", outputFile.getName(), sheetName);
            }


            int totalUrls = urls.size();
            for (int i = 0; i < totalUrls; i++) {
                String url = urls.get(i);
                logger.info("Processing URL {}/{}: {}", (i + 1), totalUrls, url);

                Set<String> uniqueTids = ConcurrentHashMap.newKeySet();
                final String[] ppidHolder = {""}; // Using final array for lambda modification
                Map<String, String> metaDetails = new HashMap<>();
                String processingError = "N/A";

                Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                        .setUserAgent(getRandomUserAgent())
                        .setAcceptDownloads(false); // Example: disable downloads unless needed

                try (BrowserContext context = browser.newContext(contextOptions)) {
                    Page page = context.newPage();
                    page.setDefaultNavigationTimeout(navigationTimeout);

                    page.onRequest(request -> {
                        Map<String, String> data = Ga4ExtStable.extractFromRequest(request.url());
                        data.forEach((key, value) -> {
                            if ("tid".equals(key) && value != null && !value.isEmpty()) uniqueTids.add(value);
                            if ("ppid".equals(key) && value != null && !value.isEmpty()) ppidHolder[0] = value;
                        });
                    });

                    Response response = null;
                    try {
                        logger.debug("Navigating to {} with waitUntil: {}", url, waitUntilState);
                        response = page.navigate(url, new Page.NavigateOptions().setWaitUntil(waitUntilState));
                        logger.debug("Navigation to {} completed with status: {}", url, response != null ? response.status() : "N/A");

                        if (postNavigationWait > 0) {
                            logger.debug("Waiting for {}ms post-navigation for dynamic content on {}", postNavigationWait, url);
                            page.waitForTimeout(postNavigationWait);
                        }

                        metaDetails = MetaDetails.extractMetaTags(page, response);

//                        // Canonical validation
//                        String canonicalExtracted = metaDetails.getOrDefault("canonical", MetaDetails.NOT_FOUND);
//                        if (!MetaDetails.NOT_FOUND.equals(canonicalExtracted)) {
//                            if (areUrlsEquivalent(url, canonicalExtracted)) {
//                                metaDetails.put("canonical_validation", "✅ Valid");
//                            } else {
//                                metaDetails.put("canonical_validation", "❌ Invalid (URL: " + url + " vs Canon: " + canonicalExtracted + ")");
//                            }
//                        } else {
//                            metaDetails.put("canonical_validation", "N/A (Canonical not found)");
//                        }
                        // Canonical validation with AMP check
                        String canonicalExtracted = metaDetails.getOrDefault("canonical", NOT_FOUND);

                        if (!NOT_FOUND.equals(canonicalExtracted)) {
                            boolean isAmpPage = isAmpUrl(url);
                            boolean isAmpCanonical = isAmpUrl(canonicalExtracted);

                            if (isAmpPage && isAmpCanonical) {
                                metaDetails.put("canonical_validation", "❌ Invalid (AMP page points to AMP canonical)");
                                logger.warn("AMP Canonical mismatch for {}. Canonical is also AMP: {}", url, canonicalExtracted);
                            } else if (urlsMatchAfterAmpNormalization(url, canonicalExtracted)) {
                                metaDetails.put("canonical_validation", "✅ Valid");
                            } else {
                                metaDetails.put("canonical_validation", "❌ Invalid");
                                logger.warn("Canonical mismatch for {}. Page: {}, Canonical: {}", url, url, canonicalExtracted);
                            }
                        } else {
                            metaDetails.put("canonical_validation", "N/A (Canonical not found)");
                        }



                    } catch (PlaywrightException e) {
                        logger.error("Playwright error processing {}: {}", url, e.getMessage().split("\n")[0], e);
                        processingError = "Playwright Error: " + e.getMessage().split("\n")[0];
                        metaDetails.put("status_code", response != null ? String.valueOf(response.status()) : "Navigation Error");
                    } catch (Exception e) {
                        logger.error("Unexpected error processing {}: {}", url, e.getMessage(), e);
                        processingError = "Unexpected Error: " + e.getMessage().split("\n")[0];
                        metaDetails.put("status_code", "Processing Error");
                    } finally {
                        try {
                            page.close();
                        } catch (PlaywrightException e) {
                            logger.warn("Error closing page for {}: {}", url, e.getMessage());
                        }
                    }
                } // Context closes automatically

                metaDetails.put("processing_error", processingError);
                Row row = sheet.createRow(rowIndex++);
                writeRowToSheet(row, url, metaDetails, uniqueTids, ppidHolder[0]);

                // Save periodically to avoid data loss on very long runs (e.g., every 50 URLs)
                if ((i + 1) % 50 == 0 && i < totalUrls -1) {
                    try (FileOutputStream out = new FileOutputStream(outputFile)) {
                        workbook.write(out);
                        logger.info("Progress saved to Excel ({} URLs processed).", i + 1);
                    } catch (IOException e) {
                        logger.error("Error saving intermediate Excel progress: {}", e.getMessage(), e);
                    }
                }
            }

            // Auto resize columns at the end
            for (int j = 0; j < ExcelColumns.TOTAL_COLUMNS; j++) {
                sheet.autoSizeColumn(j);
            }

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                workbook.write(out);
                logger.info("📊 Final report saved to: {}", outputFile.getAbsolutePath());
            } finally {
                if (workbook != null) workbook.close();
            }
            if (browser != null) browser.close();

        } catch (IOException e) {
            logger.error("File I/O error for Excel report: {}", e.getMessage(), e);
        } catch (PlaywrightException e) {
            logger.error("Playwright initialization or critical browser error: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("An unexpected critical error occurred: {}", e.getMessage(), e);
        }
        logger.info("🏁 SEO Data Extraction Process Finished.");
    }
    private static boolean isAmpUrl(String url) {
        if (url == null) return false;
        url = url.toLowerCase();
        return url.contains("/amp") || url.contains("?amp=") || url.endsWith(".amp.html");
    }

    private static boolean urlsMatchAfterAmpNormalization(String url1, String url2) {
        return normalizeUrl(stripAmp(url1)).equals(normalizeUrl(stripAmp(url2)));
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        return url.trim().replaceAll("/+$", "").toLowerCase();
    }

    private static String stripAmp(String url) {
        if (url == null) return "";
        return url
                .replaceAll("(/amp/|/amp$|\\?amp=1|\\.amp\\.html)$", "")
                .replace("/amp", ""); // Handles mid-path '/amp' if misused
    }
    private static List<String> readUrlsFromFile(String filePath) {
        Set<String> urlSet = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String url = line.trim();
                if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    urlSet.add(url);
                } else if (!url.isEmpty()) {
                    logger.warn("Skipping invalid URL (must start with http:// or https://): {}", url);
                }
            }
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
            // Optional: Add styling, e.g., bold
        }
        logger.info("Excel header row created in sheet '{}'.", sheet.getSheetName());
    }

    private static void writeRowToSheet(Row row, String url, Map<String, String> meta, Set<String> tids, String ppid) {
        // Helper to safely get values
        //String getMeta = (key) -> meta.getOrDefault(key, MetaDetails.NOT_FOUND); // Ensure NOT_FOUND is accessible or redefine
        Function<String, String> getMeta = key -> meta.getOrDefault(key, NOT_FOUND);


        row.createCell(ExcelColumns.URL).setCellValue(url);
        row.createCell(ExcelColumns.TID_GA4).setCellValue(tids.isEmpty() ? NOT_FOUND : String.join(", ", tids));
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

        row.createCell(ExcelColumns.PROCESSING_ERROR).setCellValue(getMeta.apply("processing_error"));
    }

    // User-Agent Rotation
    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:108.0) Gecko/20100101 Firefox/108.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.1 Safari/605.1.15"
    );
    private static final Random RANDOM = new Random();

    private static String getRandomUserAgent() {
        return USER_AGENTS.get(RANDOM.nextInt(USER_AGENTS.size()));
    }

    // Helper for canonical validation (handles trailing slashes and http/https)
    private static boolean areUrlsEquivalent(String url1, String url2) {
        if (url1 == null || url2 == null) return false;
        try {
            URI uri1 = new URI(url1.replaceFirst("^http:", "https")).normalize();
            URI uri2 = new URI(url2.replaceFirst("^http:", "https")).normalize();

            String path1 = uri1.getPath().replaceAll("/$", "");
            String path2 = uri2.getPath().replaceAll("/$", "");

            return uri1.getHost().equalsIgnoreCase(uri2.getHost()) &&
                    path1.equalsIgnoreCase(path2) &&
                    Objects.equals(uri1.getQuery(), uri2.getQuery()); // Query params should match if present
        } catch (URISyntaxException e) {
            logger.warn("Could not parse URLs for equivalence check: {} vs {} - {}", url1, url2, e.getMessage());
            return url1.equalsIgnoreCase(url2); // Fallback to simple ignore case
        }
    }
}