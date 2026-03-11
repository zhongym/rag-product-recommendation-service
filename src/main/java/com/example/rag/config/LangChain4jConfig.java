package com.example.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j configuration for OpenAI models
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LangChain4jConfig {

    private final OpenAIProperties openAIProperties;

    /**
     * Embedding model for generating text vectors
     * Uses text-embedding-3-small (dimensions: 1536)
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAIProperties.getKey())
                .baseUrl(openAIProperties.getBaseUrl())
                .modelName(openAIProperties.getEmbedding().getModel())
                .timeout(openAIProperties.getTimeout())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Chat language model for generating recommendations
     * Uses gpt-4o-mini with JSON response format
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAIProperties.getKey())
                .baseUrl(openAIProperties.getBaseUrl())
                .modelName(openAIProperties.getLlm().getModel())
                .temperature(openAIProperties.getLlm().getTemperature())
                .maxTokens(openAIProperties.getLlm().getMaxTokens())
                .timeout(openAIProperties.getTimeout())
                .maxRetries(openAIProperties.getMaxRetries())
                .responseFormat("JSON_OBJECT")
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Streaming chat model for SSE-based recommendations
     */
    @Bean
    public StreamingChatLanguageModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAIProperties.getKey())
                .baseUrl(openAIProperties.getBaseUrl())
                .modelName(openAIProperties.getLlm().getModel())
                .temperature(openAIProperties.getLlm().getTemperature())
                .maxTokens(openAIProperties.getLlm().getMaxTokens())
                .timeout(openAIProperties.getTimeout())
                .responseFormat("JSON_OBJECT")
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}

