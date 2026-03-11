package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for RAG recommendation system
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.recommendation")
public class RagProperties {

    private int defaultTopK = 10;
    private int maxTopK = 50;
    private Search search = new Search();



    @Data
    public static class Search {
        private double hybridWeightVector = 0.7;
        private double hybridWeightBm25 = 0.3;
    }

}
