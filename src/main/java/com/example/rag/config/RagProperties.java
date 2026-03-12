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
    private Rerank rerank = new Rerank();



    @Data
    public static class Search {
        private double hybridWeightVector = 0.7;
        private double hybridWeightBm25 = 0.3;
    }

    @Data
    public static class Rerank {
        private Boolean enabled = false;
        private String provider = "jina";
        private int candidatesMultiplier = 3;
        private String model = "jina-reranker-v2-base-multilingual";
        private String apiKey;
        private Integer topN;
    }

}
