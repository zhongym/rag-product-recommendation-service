package com.example.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for product details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private List<String> tags;
    private String brand;
    private String imageUrl;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
