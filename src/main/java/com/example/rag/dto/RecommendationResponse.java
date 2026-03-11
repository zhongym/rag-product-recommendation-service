package com.example.rag.dto;

import com.example.rag.model.RecommendationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for product recommendation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    private String query;
    private List<RecommendationResult> recommendations;
    private int totalResults;
    private long processingTimeMs;
    private String modelUsed;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationResult {
        private String id;
        private String name;
        private String description;
        private String category;
        private Double price;
        private String brand;
        private String reason;
        private Double score;
        private String imageUrl;
    }
}
