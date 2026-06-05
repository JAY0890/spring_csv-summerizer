package com.org.csvsummerizer.controllers;

import com.opencsv.exceptions.CsvValidationException;
import com.org.csvsummerizer.dto.AskResponse;
import com.org.csvsummerizer.dto.QuestionRequest;
import com.org.csvsummerizer.dto.UploadResponse;
import com.org.csvsummerizer.service.CsvRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/csv")

public class CsvController {

     private final CsvRagService csvRagService;

    public CsvController(CsvRagService csvRagService) {
        this.csvRagService = csvRagService;
    }


    /**
     * Upload a CSV file to be parsed and stored for RAG queries.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadCsv(@RequestParam("file") MultipartFile file)
            throws IOException, CsvValidationException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new UploadResponse("File is empty", null, 0));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(new UploadResponse("Only CSV files are accepted", filename, 0));
        }

        CsvRagService.UploadResult result = csvRagService.uploadCsv(file);

        return ResponseEntity.ok(new UploadResponse(
                "CSV uploaded and processed successfully",
                result.filename(),
                result.rowCount()
        ));
    }

    /**
     * Ask a natural language question about the uploaded CSV data.
     */
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> askQuestion(@RequestBody QuestionRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AskResponse("Question cannot be empty", null));
        }

        String answer = csvRagService.askQuestion(request.question());
        CsvRagService.StatusInfo status = csvRagService.getStatus();

        return ResponseEntity.ok(new AskResponse(answer, status.filename()));
    }

    /**
     * Get the status of the currently loaded CSV.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        CsvRagService.StatusInfo status = csvRagService.getStatus();
        return ResponseEntity.ok(Map.of(
                "loaded", status.loaded(),
                "filename", status.filename() != null ? status.filename() : "none",
                "rowCount", status.rowCount()
        ));
    }

    /**
     * Helper endpoint to query available Gemini models.
     */
    @GetMapping("/models")
    public ResponseEntity<String> getAvailableModels(@org.springframework.beans.factory.annotation.Value("${gemini.api-key}") String apiKey) {
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching models: " + e.getMessage());
        }
    }
}
