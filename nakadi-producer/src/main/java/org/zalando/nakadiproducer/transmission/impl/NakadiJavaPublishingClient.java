package org.zalando.nakadiproducer.transmission.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zalando.nakadiproducer.transmission.NakadiPublishingClient;

import java.lang.reflect.InvocationTargetException;
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
    private final Object delegate; // nakadi.NakadiClient
    private final ObjectMapper objectMapper;

    /**
     * Constructor that takes a nakadi-java NakadiClient instance.
     * We use Object type to avoid compile-time dependency on nakadi-java.
     */
    public NakadiJavaPublishingClient(Object nakadiClient) {
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

        // Use reflection to call: delegate.resources().events().send(eventType, jsonEvents)
        try {
            // Get the resources object
            Object resources = delegate.getClass().getMethod("resources").invoke(delegate);

            // Get the EventResource
            Object eventResource = resources.getClass().getMethod("events").invoke(resources);

            // Call send method - nakadi-java accepts List<String> as raw JSON
            Object response = eventResource.getClass()
                    .getMethod("send", String.class, java.util.Collection.class)
                    .invoke(eventResource, eventType, (Object) jsonEvents);

            // Check response status code using reflection
            int statusCode = (int) response.getClass().getMethod("statusCode").invoke(response);

            // Handle 207 partial success - need to parse the response and throw exception with details
            if (statusCode == 207) {
                Object responseBody = response.getClass().getMethod("responseBody").invoke(response);
                String responseString = (String) responseBody.getClass().getMethod("asString").invoke(responseBody);

                List<NakadiJavaPublishingException.BatchItemResponse> batchItems = objectMapper.readValue(
                        responseString,
                        new TypeReference<List<NakadiJavaPublishingException.BatchItemResponse>>() {});
                throw new NakadiJavaPublishingException(batchItems);
            }

            // For other errors, nakadi-java already throws exceptions, but let's check anyway
            if (statusCode >= 300) {
                throw new RuntimeException("Failed to publish events: HTTP " + statusCode);
            }
        } catch (NakadiJavaPublishingException e) {
            // Re-throw our custom exception as-is
            throw e;
        } catch (InvocationTargetException e) {
            // Unwrap invocation target exceptions from nakadi-java
            if (e.getTargetException() instanceof Exception) {
                throw (Exception) e.getTargetException();
            }
            throw new RuntimeException("Failed to publish events", e);
        }
    }
}






