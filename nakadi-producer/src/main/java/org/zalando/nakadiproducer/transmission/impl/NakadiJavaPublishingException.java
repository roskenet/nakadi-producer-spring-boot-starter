package org.zalando.nakadiproducer.transmission.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Exception thrown when event publishing fails with partial success (207 response).
 * This wraps the batch item responses from nakadi-java to maintain compatibility with the existing error handling.
 */
public class NakadiJavaPublishingException extends Exception {
    private final List<BatchItemResponse> responses;

    public NakadiJavaPublishingException(List<BatchItemResponse> responses) {
        super("Event publishing failed with partial success");
        this.responses = responses;
    }

    public List<BatchItemResponse> getResponses() {
        return responses;
    }

    /**
     * Wrapper class for batch item responses that doesn't depend on nakadi-java imports.
     */
    public static class BatchItemResponse {
        @JsonProperty("eid")
        private String eid;

        @JsonProperty("publishing_status")
        private String publishingStatus;

        @JsonProperty("step")
        private String step;

        @JsonProperty("detail")
        private String detail;

        public BatchItemResponse() {}

        public String getEid() {
            return eid;
        }

        public void setEid(String eid) {
            this.eid = eid;
        }

        public String getPublishingStatus() {
            return publishingStatus;
        }

        public void setPublishingStatus(String publishingStatus) {
            this.publishingStatus = publishingStatus;
        }

        public String getStep() {
            return step;
        }

        public void setStep(String step) {
            this.step = step;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}


