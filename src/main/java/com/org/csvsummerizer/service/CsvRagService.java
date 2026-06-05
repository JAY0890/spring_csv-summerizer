package com.org.csvsummerizer.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class CsvRagService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;

    private String currentFilename;
    private int currentRowCount;
    private boolean csvLoaded = false;

    public CsvRagService(EmbeddingStore<TextSegment> embeddingStore,
                         EmbeddingModel embeddingModel,
                         ChatModel chatModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    /**
     * Parses the uploaded CSV file, converts each row into a TextSegment,
     * generates embeddings, and stores them in the embedding store.
     */
    public UploadResult uploadCsv(MultipartFile file) throws IOException, CsvValidationException {
        String filename = file.getOriginalFilename();
        log.info("Processing CSV upload: {}", filename);

        List<TextSegment> segments = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            // Read header row
            String[] headers = reader.readNext();
            if (headers == null || headers.length == 0) {
                throw new IllegalArgumentException("CSV file is empty or has no headers");
            }

            // Read data rows and convert to TextSegments
            String[] row;
            int rowNumber = 0;
            while ((row = reader.readNext()) != null) {
                rowNumber++;
                String content = buildRowContent(headers, row);

                Metadata metadata = new Metadata();
                metadata.put("source", filename);
                metadata.put("row", rowNumber);

                // Also add each column value as metadata for structured queries
                for (int i = 0; i < headers.length && i < row.length; i++) {
                    metadata.put(headers[i].trim(), row[i].trim());
                }

                TextSegment segment = TextSegment.from(content, metadata);
                segments.add(segment);
            }

            if (segments.isEmpty()) {
                throw new IllegalArgumentException("CSV file has headers but no data rows");
            }

            log.info("Parsed {} rows from CSV. Generating embeddings...", segments.size());

            // Generate embeddings for all segments
            List<Embedding> allEmbeddings = embeddingModel.embedAll(segments).content();

            // Store in embedding store
            embeddingStore.addAll(allEmbeddings, segments);

            this.currentFilename = filename;
            this.currentRowCount = segments.size();
            this.csvLoaded = true;

            log.info("Successfully loaded {} documents into embedding store", segments.size());
        }

        return new UploadResult(filename, segments.size());
    }

    /**
     * Uses RAG to answer a question grounded in the uploaded CSV data.
     * 1. Embeds the question
     * 2. Retrieves relevant rows from the embedding store
     * 3. Sends the context + question to Gemini
     */
    public String askQuestion(String question) {
        if (!csvLoaded) {
            throw new IllegalStateException("No CSV file has been uploaded yet. Please upload a CSV first.");
        }

        log.info("Processing question: {}", question);

        // Step 1: Embed the question
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // Step 2: Search for relevant rows (top 15 matches)
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(15)
                .minScore(0.5)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        if (matches.isEmpty()) {
            return "I couldn't find any relevant data in the CSV to answer your question.";
        }

        // Step 3: Build context from matched rows
        StringBuilder context = new StringBuilder();
        context.append("Here is the relevant data from the CSV file:\n\n");
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            context.append("Row ").append(i + 1).append(": ")
                    .append(match.embedded().text())
                    .append("\n");
        }

        // Step 4: Build the prompt with context
        String prompt = """
                You are a helpful data analyst assistant. Answer the following question based ONLY on the CSV data provided below.
                Be precise and use actual data values in your answer. If the data doesn't contain enough information, say so clearly.
                When relevant, format numbers nicely and present data in a readable way.
                
                %s
                
                Question: %s
                
                Answer:
                """.formatted(context.toString(), question);

        // Step 5: Get answer from Gemini
        String answer = chatModel.chat(prompt);

        log.info("Generated answer for question");
        return answer;
    }

    /**
     * Returns status info about the currently loaded CSV.
     */
    public StatusInfo getStatus() {
        return new StatusInfo(csvLoaded, currentFilename, currentRowCount);
    }

    /**
     * Builds a human-readable text representation of a CSV row.
     * Example: "Name: John, Age: 30, City: New York"
     */
    private String buildRowContent(String[] headers, String[] row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.length && i < row.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(headers[i].trim()).append(": ").append(row[i].trim());
        }
        return sb.toString();
    }

    // Inner result types
    public record UploadResult(String filename, int rowCount) {}
    public record StatusInfo(boolean loaded, String filename, int rowCount) {}
}
