package com.example.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main Spring Boot application class for RAG Product Recommendation System
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
@RequiredArgsConstructor
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
        log.info("RAG Product Recommendation System started successfully");
    }
}
