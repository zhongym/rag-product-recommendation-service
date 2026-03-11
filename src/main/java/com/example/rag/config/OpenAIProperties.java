package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for OpenAI API
 */
@Data
@Component
@ConfigurationProperties(prefix = "openai.api")
public class OpenAIProperties {

    /**
     * OpenAI API key
     * Can be set via environment variable: OPENAI_API_KEY
     */
    private String key;

    /**
     * OpenAI API base URL
     * Default: https://api.openai.com
     */
    private String baseUrl = "https://api.openai.com";

    /**
     * Request timeout for API calls
     * Default: 60 seconds
     */
    private Duration timeout = Duration.ofSeconds(120);

    private Integer maxRetries = 0;

    private Embedding embedding = new Embedding();


    private Llm llm = new Llm();


    @Data
    public static class Embedding {
        private String model = "text-embedding-3-small";
        private int dimensions = 1536;
    }

    @Data
    public static class Llm {
        private String model = "gpt-4o-mini";
        private Integer maxTokens = 2000;
        private Double temperature = 0.7;
    }
}
