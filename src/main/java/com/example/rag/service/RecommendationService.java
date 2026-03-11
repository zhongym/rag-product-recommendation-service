package com.example.rag.service;

import com.example.rag.config.OpenAIProperties;
import com.example.rag.config.RagProperties;
import com.example.rag.dto.LlmRecommendationItem;
import com.example.rag.dto.RecommendationRequest;
import com.example.rag.dto.RecommendationResponse;
import com.example.rag.model.ProductDocument;
import com.example.rag.model.SearchFilters;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core RAG recommendation service integrating retrieval and generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final VectorSearchService vectorSearchService;
    private final ChatLanguageModel chatLanguageModel;
    private final RagProperties ragProperties;
    private final OpenAIProperties openAIProperties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("RecommendationService initialized with model: {}",
                openAIProperties.getLlm().getModel());
    }

    /**
     * Generate product recommendations based on user query
     */
    public RecommendationResponse recommend(RecommendationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Validate and adjust topK
            int topK = validateTopK(request.getTopK());

            // Step 1: Retrieve candidate products using hybrid search
            log.debug("Retrieving candidates for query: {}", request.getQuery());
            List<ProductDocument> candidates = vectorSearchService.hybridSearch(
                    request.getQuery(),
                    Math.min(topK * 3, 50), // Get more candidates for reranking
                    request.getFilters()
            );

            if (candidates.isEmpty()) {
                log.warn("No candidates found for query: {}", request.getQuery());
                return buildEmptyResponse(request, startTime);
            }

            // Step 2: Build prompt with candidates
            String prompt = buildRecommendationPrompt(
                    request.getQuery(),
                    candidates,
                    topK
            );

            log.debug("prompt:{}", prompt);
            log.debug("Generated prompt for LLM");

            // Step 3: Generate recommendations using LLM
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(new UserMessage(prompt))
                    .build();
            String llmResponse = chatLanguageModel.chat(chatRequest).aiMessage().text();
            log.debug("LLM response received");

            // Step 4: Parse LLM response
            List<RecommendationResponse.RecommendationResult> results = parseLlmResponse(
                    llmResponse,
                    candidates
            );

            // Step 5: Build response
            long processingTime = System.currentTimeMillis() - startTime;

            return RecommendationResponse.builder()
                    .query(request.getQuery())
                    .recommendations(results)
                    .totalResults(results.size())
                    .processingTimeMs(processingTime)
                    .modelUsed(openAIProperties.getLlm().getModel())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error generating recommendations for query: {}", request.getQuery(), e);
            throw new RuntimeException("Failed to generate recommendations", e);
        }
    }

    /**
     * Generate streaming recommendations (SSE)
     * Note: This is a simplified implementation - full streaming requires proper Reactor integration
     */
    public Flux<RecommendationResponse> recommendStream(RecommendationRequest request) {
        // For now, just return the non-streaming result wrapped in a Flux
        // TODO: Implement true streaming with proper SSE support
        return Flux.just(recommend(request));
    }

    /**
     * Build recommendation prompt for LLM
     */
    private String buildRecommendationPrompt(String query, List<ProductDocument> candidates, int topN) {
        StringBuilder candidatesText = new StringBuilder();

        for (int i = 0; i < candidates.size(); i++) {
            ProductDocument doc = candidates.get(i);
            candidatesText.append(String.format("""
                            [%d] ID: %s
                                Name: %s
                                Description: %s
                                Category: %s
                                Price: %.2f
                                Brand: %s
                            """,
                    i + 1,
                    doc.getId(),
                    doc.getName(),
                    doc.getDescription(),
                    doc.getCategory(),
                    doc.getPrice(),
                    doc.getBrand()
            ));
        }

        return String.format("""
                You are a product recommendation expert. Based on the user's query and the candidate products below,
                recommend the %d most suitable products.
                
                User Query: %s
                
                Candidate Products:
                %s
                
                Please analyze the user's needs and recommend %d products. For each recommendation, provide:
                1. The product ID (from the list above)
                2. The product name
                3. A brief recommendation reason (max 50 Chinese characters)
                
                Return ONLY a valid JSON array in this exact format:
                [
                  {
                    "id": "product_id",
                    "name": "product name",
                    "reason": "推荐理由（不超过50字）"
                  }
                ]
                
                Do not include any additional text or explanation outside the JSON array.
                """, topN, query, candidatesText, topN);
    }

    /**
     * Parse LLM JSON response
     */
    private List<RecommendationResponse.RecommendationResult> parseLlmResponse(String llmResponse, List<ProductDocument> candidates) {
        try {
            // Clean up response - extract JSON array
            String cleaned = llmResponse.trim();

            // Find JSON array boundaries
            int startIndex = cleaned.indexOf('[');
            int endIndex = cleaned.lastIndexOf(']');

            if (startIndex >= 0 && endIndex > startIndex) {
                cleaned = cleaned.substring(startIndex, endIndex + 1);
            }

            // Parse JSON into DTO
            List<LlmRecommendationItem> parsed = objectMapper.readValue(
                    cleaned,
                    new TypeReference<List<LlmRecommendationItem>>() {
                    }
            );

            // Create lookup map for candidate documents
            Map<String, ProductDocument> candidateMap = candidates.stream()
                    .collect(Collectors.toMap(
                            ProductDocument::getId,
                            doc -> doc
                    ));

            // Build recommendation results
            List<RecommendationResponse.RecommendationResult> results = new ArrayList<>();
            for (LlmRecommendationItem item : parsed) {
                ProductDocument doc = candidateMap.get(item.getId());

                if (doc != null) {
                    results.add(RecommendationResponse.RecommendationResult.builder()
                            .id(item.getId())
                            .name(item.getName())
                            .description(doc.getDescription())
                            .category(doc.getCategory())
                            .price(doc.getPrice())
                            .brand(doc.getBrand())
                            .imageUrl(doc.getImageUrl())
                            .reason(item.getReason())
                            .build());
                } else {
                    log.warn("Recommended product ID {} not found in candidates", item.getId());
                }
            }

            return results;

        } catch (Exception e) {
            log.error("Error parsing LLM response: {}", llmResponse, e);

            // Fallback: return top candidates from search
            return candidates.stream()
                    .limit(5)
                    .map(doc -> RecommendationResponse.RecommendationResult.builder()
                            .id(doc.getId())
                            .name(doc.getName())
                            .description(doc.getDescription())
                            .category(doc.getCategory())
                            .price(doc.getPrice())
                            .brand(doc.getBrand())
                            .reason("基于您的需求推荐")
                            .build())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Validate and adjust topK parameter
     */
    private int validateTopK(Integer topK) {
        if (topK == null || topK < 1) {
            return ragProperties.getDefaultTopK();
        }
        return Math.min(topK, ragProperties.getMaxTopK());
    }

    /**
     * Build empty response when no candidates found
     */
    private RecommendationResponse buildEmptyResponse(RecommendationRequest request, long startTime) {
        return RecommendationResponse.builder()
                .query(request.getQuery())
                .recommendations(Collections.emptyList())
                .totalResults(0)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .modelUsed(openAIProperties.getLlm().getModel())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
