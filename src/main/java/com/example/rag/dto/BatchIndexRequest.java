package com.example.rag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch indexing products
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchIndexRequest {

    @NotEmpty(message = "Product list cannot be empty")
    @Valid
    private List<ProductIndexRequest> products;
}
