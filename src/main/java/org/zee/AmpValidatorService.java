package org.zee;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AmpValidatorService {
    private static final Logger logger = LoggerFactory.getLogger(AmpValidatorService.class);
    private static final String VALIDATOR_API_URL_FORMAT = "https://validator.amp.dev/validator.json?url=%s";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private final HttpClient httpClient;
    private final Gson gson;

    public AmpValidatorService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.gson = new Gson();
    }

    public AmpValidationResult validate(String ampUrlString) {
        if (ampUrlString == null || ampUrlString.trim().isEmpty() || "Not Found".equalsIgnoreCase(ampUrlString)) {
            return AmpValidationResult.urlError("AMP URL is null, empty, or 'Not Found'");
        }

        String encodedUrl;
        try {
            // Ensure the URL is properly encoded for the query parameter
            encodedUrl = URLEncoder.encode(ampUrlString, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            logger.error("Failed to encode AMP URL {}: {}", ampUrlString, e.getMessage());
            return AmpValidationResult.urlError("URL encoding failed: " + e.getMessage());
        }

        String requestUriString = String.format(VALIDATOR_API_URL_FORMAT, encodedUrl);
        logger.info("Validating AMP URL: {}", ampUrlString);
        logger.debug("Requesting AMP validation from: {}", requestUriString);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUriString))
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", "SeoDataStableBot/1.0 (+https://example.com/bot)") // Be a good citizen
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseValidationResponse(response.body(), ampUrlString);
            } else {
                logger.error("AMP Validator API request failed for URL {} with status code: {}. Response: {}",
                        ampUrlString, response.statusCode(), response.body().substring(0, Math.min(response.body().length(), 500)));
                return AmpValidationResult.apiError("HTTP Status " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error calling AMP Validator API for URL {}: {}", ampUrlString, e.getMessage(), e);
            Thread.currentThread().interrupt(); // Restore interrupted status
            return AmpValidationResult.apiError(e.getClass().getSimpleName() + ": " + e.getMessage().split("\n")[0]);
        } catch (Exception e) {
            logger.error("Unexpected error during AMP validation for URL {}: {}", ampUrlString, e.getMessage(), e);
            return AmpValidationResult.apiError("Unexpected: " + e.getMessage().split("\n")[0]);
        }
    }

    private AmpValidationResult parseValidationResponse(String responseBody, String validatedUrl) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            String statusString = jsonResponse.has("status") ? jsonResponse.get("status").getAsString() : "UNKNOWN";

            if ("PASS".equalsIgnoreCase(statusString)) {
                logger.info("AMP validation PASSED for: {}", validatedUrl);
                return new AmpValidationResult(AmpValidationResult.Status.PASS, "PASS", null);
            } else if ("FAIL".equalsIgnoreCase(statusString)) {
                List<String> errors = new ArrayList<>();
                if (jsonResponse.has("errors") && jsonResponse.get("errors").isJsonArray()) {
                    JsonArray errorsArray = jsonResponse.getAsJsonArray("errors");
                    for (JsonElement errorElement : errorsArray) {
                        JsonObject errorObject = errorElement.getAsJsonObject();
                        // Providing a concise error message
                        String errorMsg = String.format("L%d C%d: %s (%s)",
                                errorObject.has("line") ? errorObject.get("line").getAsInt() : 0,
                                errorObject.has("col") ? errorObject.get("col").getAsInt() : 0,
                                errorObject.has("message") ? errorObject.get("message").getAsString() : "Unknown error",
                                errorObject.has("code") ? errorObject.get("code").getAsString() : "NO_CODE"
                        );
                        errors.add(errorMsg);
                    }
                }
                String summary = "FAIL (" + errors.size() + " error" + (errors.size() == 1 ? "" : "s") + ")";
                logger.warn("AMP validation FAILED for: {}. Summary: {}. First error: {}", validatedUrl, summary, errors.isEmpty() ? "N/A" : errors.get(0));
                return new AmpValidationResult(AmpValidationResult.Status.FAIL, summary, errors);
            } else {
                logger.warn("AMP Validator API returned unknown status '{}' for URL {}. Response: {}", statusString, validatedUrl, responseBody.substring(0, Math.min(responseBody.length(), 500)));
                return AmpValidationResult.apiError("Unknown status: " + statusString);
            }
        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse AMP validation JSON response for URL {}: {}", validatedUrl, e.getMessage());
            logger.debug("AMP JSON Response Body: {}", responseBody);
            return AmpValidationResult.apiError("JSON Parse Error: " + e.getMessage().split("\n")[0]);
        }
    }
}
