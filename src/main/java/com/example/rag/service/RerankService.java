package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.model.ProductDocument;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.jina.JinaScoringModel;
import dev.langchain4j.data.segment.TextSegment;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 使用评分模型对搜索结果进行重排序的服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final RagProperties ragProperties;

    /**
     * ScoringModel 的懒加载初始化
     */
    @Getter(lazy = true)
    private final ScoringModel scoringModel = initScoringModel();

    private ScoringModel initScoringModel() {
        RagProperties.Rerank rerankConfig = ragProperties.getRerank();
        if (rerankConfig == null || rerankConfig.getApiKey() == null || rerankConfig.getApiKey().isBlank()) {
            log.warn("未配置 Rerank API 密钥，重排序将被禁用");
            return null;
        }

        log.info("正在初始化 Jina ScoringModel，模型: {}", rerankConfig.getModel());
        return JinaScoringModel.builder()
                .apiKey(rerankConfig.getApiKey())
                .modelName(rerankConfig.getModel())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 基于查询相关性使用批量评分对候选商品进行重排序
     *
     * @param query      用户查询
     * @param candidates 候选商品列表
     * @param topK       返回的 top 结果数量
     * @return 带分数的重排序商品列表
     */
    public List<ScoredProduct> rerank(String query, List<ProductDocument> candidates, int topK) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 检查是否启用重排序
        RagProperties.Rerank rerankConfig = ragProperties.getRerank();
        if (rerankConfig == null || !Boolean.TRUE.equals(rerankConfig.getEnabled())) {
            log.debug("重排序已禁用，返回原始顺序");
            return toScoredProducts(candidates, topK);
        }

        // 检查评分模型是否已初始化
        if (getScoringModel() == null) {
            log.warn("评分模型未初始化，返回原始顺序");
            return toScoredProducts(candidates, topK);
        }

        try {
            log.debug("批量重排序 {} 个候选商品，查询: {}", candidates.size(), query);

            // 将所有商品转换为文本片段
            List<TextSegment> segments = candidates.stream()
                    .map(product -> TextSegment.from(buildProductText(product)))
                    .toList();

            // 使用 scoreAll 批量对所有片段进行评分
            var scoreList = getScoringModel().scoreAll(segments, query).content();

            // 将分数映射回商品（保持顺序）
            List<ScoredProduct> scoredProducts = new ArrayList<>();
            for (int i = 0; i < candidates.size() && i < scoreList.size(); i++) {
                ProductDocument product = candidates.get(i);
                double score = scoreList.get(i);
                scoredProducts.add(new ScoredProduct(product, score));
            }

            // 按分数排序（降序）并取 top K
            List<ScoredProduct> result = scoredProducts.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .toList();

            log.debug("批量重排序完成，返回 top {} 个结果", result.size());
            return result;

        } catch (Exception e) {
            log.error("批量重排序失败，查询: {}，返回原始顺序", query, e);
            return toScoredProducts(candidates, topK);
        }
    }

    /**
     * 构建商品的文本表示用于重排序
     */
    private String buildProductText(ProductDocument product) {
        StringBuilder sb = new StringBuilder();
        sb.append(product.getName());

        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            sb.append(" ").append(product.getDescription());
        }

        sb.append(" Category: ").append(product.getCategory());

        if (product.getBrand() != null && !product.getBrand().isBlank()) {
            sb.append(" Brand: ").append(product.getBrand());
        }

        if (product.getPrice() != null) {
            sb.append(String.format(" Price: %.2f", product.getPrice()));
        }

        if (product.getTags() != null && !product.getTags().isEmpty()) {
            sb.append(" Tags: ").append(String.join(", ", product.getTags()));
        }

        return sb.toString();
    }

    /**
     * 将商品转换为不带分数的 ScoredProduct（原始顺序）
     */
    private List<ScoredProduct> toScoredProducts(List<ProductDocument> products, int topK) {
        return products.stream()
                .limit(topK)
                .map(p -> new ScoredProduct(p, 1.0))
                .toList();
    }

    /**
     * 带重排序分数的商品包装类
     */
    @Getter
    public static class ScoredProduct {
        private final ProductDocument product;
        private final Double score;

        public ScoredProduct(ProductDocument product, Double score) {
            this.product = product;
            this.score = score;
        }
    }
}
