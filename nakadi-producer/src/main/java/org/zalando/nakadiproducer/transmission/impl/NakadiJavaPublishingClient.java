package org.zalando.nakadiproducer.transmission.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nakadi.NakadiClient;
import nakadi.Response;
import org.zalando.nakadiproducer.transmission.NakadiPublishingClient;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Publishing client implementation using nakadi-java library.
 *
 * This wraps nakadi-java's NakadiClient and adapts it to the NakadiPublishingClient interface.
 * It converts events to raw JSON strings and uses nakadi-java's send method, handling 207
 * partial success responses by throwing a NakadiJavaPublishingException with batch item details.
 */
public class NakadiJavaPublishingClient implements NakadiPublishingClient {
    private final NakadiClient delegate;
    private final ObjectMapper objectMapper;

    /**
     * Constructor that takes a nakadi-java NakadiClient instance.
     */
    public NakadiJavaPublishingClient(NakadiClient nakadiClient) {
        this.delegate = nakadiClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void publish(String eventType, List<?> nakadiEvents) throws Exception {
        // Convert events to JSON strings - nakadi-java treats List<String> as raw JSON
        List<String> jsonEvents = nakadiEvents.stream()
                .map(event -> {
                    try {
                        return objectMapper.writeValueAsString(event);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to serialize event", e);
                    }
                })
                .collect(Collectors.toList());

        // Call nakadi-java's send method
        // nakadi-java detects Strings at runtime and treats them as raw JSON
        @SuppressWarnings("unchecked")
        Response response = delegate.resources().events().send(eventType, (Collection) jsonEvents);

        // Handle 207 partial success - need to parse the response and throw exception with details
        if (response.statusCode() == 207) {
            String responseString = response.responseBody().asString();

            List<NakadiJavaPublishingException.BatchItemResponse> batchItems = objectMapper.readValue(
                    responseString,
                    new TypeReference<List<NakadiJavaPublishingException.BatchItemResponse>>() {});
            throw new NakadiJavaPublishingException(batchItems);
        }

        // For other errors, nakadi-java already throws exceptions, but let's check anyway
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Failed to publish events: HTTP " + response.statusCode());
        }
    }
}

