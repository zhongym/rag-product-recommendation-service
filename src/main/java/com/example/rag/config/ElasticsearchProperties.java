package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for Elasticsearch connection
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.elasticsearch")
public class ElasticsearchProperties {

    /**
     * Elasticsearch server URLs (comma-separated for multiple nodes)
     * Example: http://localhost:9200
     */
    private String uris;

    /**
     * Username for authentication (optional)
     * Leave empty if no authentication required
     */
    private String username = "";

    /**
     * Password for authentication (optional)
     * Leave empty if no authentication required
     */
    private String password = "";

    /**
     * Connection timeout for establishing connection to Elasticsearch
     * Default: 10 seconds
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * Socket timeout for reading data from Elasticsearch
     * Default: 30 seconds
     */
    private Duration socketTimeout = Duration.ofSeconds(30);

    /**
     * Check if authentication is configured
     */
    public boolean hasAuthentication() {
        return username != null && !username.isEmpty() && password != null && !password.isEmpty();
    }
}
