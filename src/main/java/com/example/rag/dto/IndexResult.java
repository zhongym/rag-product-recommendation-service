package com.example.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for indexing operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexResult {

    private int successCount;
    private int failureCount;
    private String message;
    private long processingTimeMs;
    private LocalDateTime timestamp;
}
