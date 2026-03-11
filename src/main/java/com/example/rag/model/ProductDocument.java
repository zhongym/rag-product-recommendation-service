package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Elasticsearch document for product with vector embedding support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {
    public static final String INDEX_NAME = "test_products";

    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_PRICE = "price";
    public static final String FIELD_TAGS = "tags";
    public static final String FIELD_BRAND = "brand";
    public static final String FIELD_STOCK = "stock";
    public static final String FIELD_EMBEDDING = "embedding";
    public static final String FIELD_SEARCHABLE_TEXT = "searchableText";

    private String id;

    private String name;

    private String description;

    private String category;

    private Double price;

    private List<String> tags;

    private String brand;

    private String imageUrl;

    private Integer stock;

    private float[] embedding;

    private String searchableText; // Combined text for better search
}
