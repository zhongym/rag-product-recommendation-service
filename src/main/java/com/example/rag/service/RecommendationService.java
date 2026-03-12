package com.example.rag.service;

import com.example.rag.config.OpenAIProperties;
import com.example.rag.config.RagProperties;
import com.example.rag.dto.LlmRecommendationItem;
import com.example.rag.dto.RecommendationRequest;
import com.example.rag.dto.RecommendationResponse;
import com.example.rag.model.ProductDocument;
import com.example.rag.model.SearchFilters;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心 RAG 推荐服务，集成检索和生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final VectorSearchService vectorSearchService;
    private final ChatLanguageModel chatLanguageModel;
    private final RagProperties ragProperties;
    private final OpenAIProperties openAIProperties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("RecommendationService 初始化完成，使用模型: {}",
                openAIProperties.getLlm().getModel());
    }

    /**
     * 根据用户查询生成商品推荐
     */
    public RecommendationResponse recommend(RecommendationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 验证并调整 topK
            int topK = validateTopK(request.getTopK());

            // 阶段 1: 使用混合检索获取候选商品
            log.debug("正在检索候选商品，查询: {}", request.getQuery());
            List<ProductDocument> candidates = vectorSearchService.hybridSearch(
                    request.getQuery(),
                    Math.min(topK * 3, 50),
                    request.getFilters()
            );

            if (candidates.isEmpty()) {
                log.warn("未找到候选商品，查询: {}", request.getQuery());
                return buildEmptyResponse(request, startTime);
            }

            // 阶段 2: 使用候选商品构建提示词
            String prompt = buildRecommendationPrompt(
                    request.getQuery(),
                    candidates,
                    topK
            );

            log.debug("提示词:{}", prompt);
            log.debug("已生成 LLM 提示词");

            // 阶段 3: 使用 LLM 生成推荐
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(new UserMessage(prompt))
                    .build();
            String llmResponse = chatLanguageModel.chat(chatRequest).aiMessage().text();
            log.debug("已收到 LLM 响应");

            // 阶段 4: 解析 LLM 响应
            List<RecommendationResponse.RecommendationResult> results = parseLlmResponse(
                    llmResponse,
                    candidates
            );

            // 阶段 5: 构建响应
            long processingTime = System.currentTimeMillis() - startTime;

            return RecommendationResponse.builder()
                    .query(request.getQuery())
                    .recommendations(results)
                    .totalResults(results.size())
                    .processingTimeMs(processingTime)
                    .modelUsed(openAIProperties.getLlm().getModel())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("生成推荐失败，查询: {}", request.getQuery(), e);
            throw new RuntimeException("生成推荐失败", e);
        }
    }

    /**
     * 生成流式推荐（SSE）
     * 注意：这是简化实现 - 完整的流式需要正确的 Reactor 集成
     */
    public Flux<RecommendationResponse> recommendStream(RecommendationRequest request) {
        // 目前仅返回包装在 Flux 中的非流式结果
        // TODO: 实现真正的流式和正确的 SSE 支持
        return Flux.just(recommend(request));
    }

    /**
     * 为 LLM 构建推荐提示词
     */
    private String buildRecommendationPrompt(String query, List<ProductDocument> candidates, int topN) {
        StringBuilder candidatesText = new StringBuilder();

        for (int i = 0; i < candidates.size(); i++) {
            ProductDocument doc = candidates.get(i);
            candidatesText.append(String.format("""
                            [%d] ID: %s
                                Name: %s
                                Description: %s
                                Category: %s
                                Price: %.2f
                                Brand: %s
                            """,
                    i + 1,
                    doc.getId(),
                    doc.getName(),
                    doc.getDescription(),
                    doc.getCategory(),
                    doc.getPrice(),
                    doc.getBrand()
            ));
        }

        return String.format("""
                You are a product recommendation expert. Based on the user's query and the candidate products below,
                recommend the %d most suitable products.
                
                User Query: %s
                
                Candidate Products:
                %s
                
                Please analyze the user's needs and recommend %d products. For each recommendation, provide:
                1. The product ID (from the list above)
                2. The product name
                3. A brief recommendation reason (max 50 Chinese characters)
                
                Return ONLY a valid JSON array in this exact format:
                [
                  {
                    "id": "product_id",
                    "name": "product name",
                    "reason": "推荐理由（不超过50字）"
                  }
                ]
                
                Do not include any additional text or explanation outside the JSON array.
                """, topN, query, candidatesText, topN);
    }

    /**
     * 解析 LLM 的 JSON 响应
     */
    private List<RecommendationResponse.RecommendationResult> parseLlmResponse(String llmResponse, List<ProductDocument> candidates) {
        try {
            // 清理响应 - 提取 JSON 数组
            String cleaned = llmResponse.trim();

            // 查找 JSON 数组边界
            int startIndex = cleaned.indexOf('[');
            int endIndex = cleaned.lastIndexOf(']');

            if (startIndex >= 0 && endIndex > startIndex) {
                cleaned = cleaned.substring(startIndex, endIndex + 1);
            }

            // 解析 JSON 为 DTO
            List<LlmRecommendationItem> parsed = objectMapper.readValue(
                    cleaned,
                    new TypeReference<List<LlmRecommendationItem>>() {
                    }
            );

            // 为候选文档创建查找映射
            Map<String, ProductDocument> candidateMap = candidates.stream()
                    .collect(Collectors.toMap(
                            ProductDocument::getId,
                            doc -> doc
                    ));

            // 构建推荐结果
            List<RecommendationResponse.RecommendationResult> results = new ArrayList<>();
            for (LlmRecommendationItem item : parsed) {
                ProductDocument doc = candidateMap.get(item.getId());

                if (doc != null) {
                    results.add(RecommendationResponse.RecommendationResult.builder()
                            .id(item.getId())
                            .name(item.getName())
                            .description(doc.getDescription())
                            .category(doc.getCategory())
                            .price(doc.getPrice())
                            .brand(doc.getBrand())
                            .imageUrl(doc.getImageUrl())
                            .reason(item.getReason())
                            .build());
                } else {
                    log.warn("推荐的商品 ID {} 在候选商品中未找到", item.getId());
                }
            }

            return results;

        } catch (Exception e) {
            log.error("解析 LLM 响应失败: {}", llmResponse, e);

            // 降级处理：返回检索结果中的前几个候选
            return candidates.stream()
                    .limit(5)
                    .map(doc -> RecommendationResponse.RecommendationResult.builder()
                            .id(doc.getId())
                            .name(doc.getName())
                            .description(doc.getDescription())
                            .category(doc.getCategory())
                            .price(doc.getPrice())
                            .brand(doc.getBrand())
                            .reason("基于您的需求推荐")
                            .build())
                    .collect(Collectors.toList());
        }
    }

    /**
     * 验证并调整 topK 参数
     */
    private int validateTopK(Integer topK) {
        if (topK == null || topK < 1) {
            return ragProperties.getDefaultTopK();
        }
        return Math.min(topK, ragProperties.getMaxTopK());
    }

    /**
     * 未找到候选商品时构建空响应
     */
    private RecommendationResponse buildEmptyResponse(RecommendationRequest request, long startTime) {
        return RecommendationResponse.builder()
                .query(request.getQuery())
                .recommendations(Collections.emptyList())
                .totalResults(0)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .modelUsed(openAIProperties.getLlm().getModel())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
