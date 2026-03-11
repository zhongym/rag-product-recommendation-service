package com.example.rag.controller;

import com.example.rag.dto.HealthResponse;
import com.example.rag.dto.HealthResponse.ComponentHealth;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
public class HealthController {

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, ComponentHealth> components = new HashMap<>();

        // Check application status
        components.put("application", ComponentHealth.builder()
                .status("UP")
                .message("RAG Recommendation System is running")
                .build());

        // Check Embedding Model
        ComponentHealth embeddingHealth = checkEmbeddingModel();
        components.put("embedding-model", embeddingHealth);

        // Check Chat Model
        ComponentHealth chatModelHealth = checkChatModel();
        components.put("chat-model", chatModelHealth);

        // Overall status
        String overallStatus = components.values().stream()
                .allMatch(c -> "UP".equals(c.getStatus())) ? "UP" : "DEGRADED";

        HealthResponse response = HealthResponse.builder()
                .status(overallStatus)
                .application("rag-recommendation-system")
                .timestamp(LocalDateTime.now())
                .components(components)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> info = new HashMap<>();
        info.put("name", "RAG Product Recommendation System");
        info.put("version", "1.0.0");
        info.put("description", "Production-grade RAG system using LangChain4j, Elasticsearch, and OpenAI");
        info.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(info);
    }

    private ComponentHealth checkEmbeddingModel() {
        long startTime = System.currentTimeMillis();
        try {
            // Simple test - embed a short text
            String testText = "health check";
            embeddingModel.embed(testText);

            long responseTime = System.currentTimeMillis() - startTime;

            return ComponentHealth.builder()
                    .status("UP")
                    .message("Embedding model is responsive")
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            log.error("Embedding model health check failed", e);
            return ComponentHealth.builder()
                    .status("DOWN")
                    .message("Embedding model error: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private ComponentHealth checkChatModel() {
        long startTime = System.currentTimeMillis();
        try {
            // Simple test - generate a short response
            String testPrompt = "Say 'OK'";
            chatLanguageModel.generate(testPrompt);

            long responseTime = System.currentTimeMillis() - startTime;

            return ComponentHealth.builder()
                    .status("UP")
                    .message("Chat model is responsive")
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            log.error("Chat model health check failed", e);
            return ComponentHealth.builder()
                    .status("DOWN")
                    .message("Chat model error: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
}
