package com.example.rag.dto;

import com.example.rag.model.SearchFilters;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for product recommendation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequest {

    @NotBlank(message = "Query cannot be blank")
    @Size(min = 2, max = 500, message = "Query must be between 2 and 500 characters")
    private String query;

    @Builder.Default
    @Min(value = 1, message = "TopK must be at least 1")
    @Max(value = 50, message = "TopK cannot exceed 50")
    private Integer topK = 10;

    private SearchFilters filters;
}
