package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Search filters for product recommendations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFilters {

    private List<String> categories;

    private Double minPrice;

    private Double maxPrice;

    private List<String> brands;

    private List<String> tags;

    private Boolean inStock;

    private Integer minStock;
}
