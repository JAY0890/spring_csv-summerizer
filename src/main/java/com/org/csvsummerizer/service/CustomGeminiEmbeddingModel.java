package com.org.csvsummerizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CustomGeminiEmbeddingModel implements EmbeddingModel {

    private final String apiKey;
    private final String modelName;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CustomGeminiEmbeddingModel(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        log.info("Embedding {} segments using Custom Gemini API call...", textSegments.size());

        List<Embedding> allEmbeddings = new ArrayList<>();
        
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":embedContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<Embedding>> futures = new ArrayList<>();
            
            for (TextSegment segment : textSegments) {
                futures.add(executor.submit(() -> {
                    Map<String, Object> body = new HashMap<>();
                    
                    Map<String, Object> content = new HashMap<>();
                    List<Map<String, Object>> parts = new ArrayList<>();
                    parts.add(Map.of("text", segment.text()));
                    content.put("parts", parts);
                    
                    body.put("content", content);

                    int retries = 0;
                    boolean success = false;
                    Embedding resultEmbedding = null;
                    
                    while (!success && retries < 3) {
                        try {
                            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                            String responseStr = restTemplate.postForObject(url, entity, String.class);
                            
                            JsonNode root = objectMapper.readTree(responseStr);
                            JsonNode embeddingNode = root.path("embedding");
                            
                            if (embeddingNode.isObject()) {
                                JsonNode valuesNode = embeddingNode.path("values");
                                if (valuesNode.isArray()) {
                                    float[] vector = new float[valuesNode.size()];
                                    for (int i = 0; i < valuesNode.size(); i++) {
                                        vector[i] = (float) valuesNode.get(i).asDouble();
                                    }
                                    resultEmbedding = Embedding.from(vector);
                                }
                            } else {
                                throw new RuntimeException("Failed to extract embedding from Gemini response: " + responseStr);
                            }
                            
                            success = true;
                            // Small delay to prevent hammering the API too violently in parallel
                            Thread.sleep(50);
                            
                        } catch (org.springframework.web.client.HttpStatusCodeException e) {
                            if (e.getStatusCode().value() == 429) {
                                retries++;
                                log.warn("Rate limit hit (429)! Thread waiting 16 seconds before retry {}/3...", retries);
                                try {
                                    Thread.sleep(16000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            } else {
                                log.error("HTTP Error from Gemini: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                                throw new RuntimeException("Gemini API Error: " + e.getResponseBodyAsString(), e);
                            }
                        } catch (Exception e) {
                            log.error("Failed to generate embedding: {}", e.getMessage(), e);
                            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
                        }
                    }
                    
                    if (!success) {
                        throw new RuntimeException("Failed to generate embedding after 3 retries due to rate limits.");
                    }
                    return resultEmbedding;
                }));
            }
            
            // Collect the results in the exact same order
            for (var future : futures) {
                allEmbeddings.add(future.get());
            }
            
        } catch (Exception e) {
            log.error("Failed to execute concurrent embedding requests", e);
            throw new RuntimeException("Failed to execute concurrent embedding requests", e);
        }

        return Response.from(allEmbeddings);
    }
}
