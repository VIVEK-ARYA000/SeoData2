package org.zee;


import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AmpValidationResult {
    public enum Status {
        PASS,
        FAIL,
        API_ERROR,    // Error communicating with the validation API
        URL_ERROR,    // Invalid URL provided for validation
        NOT_VALIDATED // If validation was not attempted
    }

    private final Status status;
    private final List<String> errorMessages;
    private final String summaryMessage; // A concise message for Excel

    public AmpValidationResult(Status status, String summaryMessage, List<String> errorMessages) {
        this.status = Objects.requireNonNull(status);
        this.summaryMessage = Objects.requireNonNull(summaryMessage);
        this.errorMessages = errorMessages != null ? Collections.unmodifiableList(errorMessages) : Collections.emptyList();
    }

    public Status getStatus() {
        return status;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public static AmpValidationResult notValidated() {
        return new AmpValidationResult(Status.NOT_VALIDATED, "Not an AMP page or AMP URL not found", null);
    }

    public static AmpValidationResult urlError(String message) {
        return new AmpValidationResult(Status.URL_ERROR, "Invalid URL for validation: " + message, null);
    }

    public static AmpValidationResult apiError(String message) {
        return new AmpValidationResult(Status.API_ERROR, "API Error: " + message, null);
    }

    @Override
    public String toString() {
        return "AmpValidationResult{" +
                "status=" + status +
                ", summaryMessage='" + summaryMessage + '\'' +
                ", errorCount=" + errorMessages.size() +
                '}';
    }
}
