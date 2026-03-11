package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual recommendation result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResult {
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
