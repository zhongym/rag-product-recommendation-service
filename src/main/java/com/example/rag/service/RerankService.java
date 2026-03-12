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
 * Service for reranking search results using scoring models
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final RagProperties ragProperties;

    /**
     * Lazy initialization of ScoringModel
     */
    @Getter(lazy = true)
    private final ScoringModel scoringModel = initScoringModel();

    private ScoringModel initScoringModel() {
        RagProperties.Rerank rerankConfig = ragProperties.getRerank();
        if (rerankConfig == null || rerankConfig.getApiKey() == null || rerankConfig.getApiKey().isBlank()) {
            log.warn("Rerank API key not configured, reranking will be disabled");
            return null;
        }

        log.info("Initializing Jina ScoringModel with model: {}", rerankConfig.getModel());
        return JinaScoringModel.builder()
                .apiKey(rerankConfig.getApiKey())
                .modelName(rerankConfig.getModel())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Rerank candidate products based on query relevance using batch scoring
     *
     * @param query      User query
     * @param candidates List of candidate products
     * @param topK       Number of top results to return
     * @return Reranked products with scores
     */
    public List<ScoredProduct> rerank(String query, List<ProductDocument> candidates, int topK) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Check if reranking is enabled
        RagProperties.Rerank rerankConfig = ragProperties.getRerank();
        if (rerankConfig == null || !Boolean.TRUE.equals(rerankConfig.getEnabled())) {
            log.debug("Reranking is disabled, returning original order");
            return toScoredProducts(candidates, topK);
        }

        // Check if scoring model is initialized
        if (getScoringModel() == null) {
            log.warn("Scoring model not initialized, returning original order");
            return toScoredProducts(candidates, topK);
        }

        try {
            log.debug("Batch reranking {} candidates for query: {}", candidates.size(), query);

            // Convert all products to text segments
            List<TextSegment> segments = candidates.stream()
                    .map(product -> TextSegment.from(buildProductText(product)))
                    .toList();

            // Batch score all segments at once using scoreAll
            var scoreList = getScoringModel().scoreAll(segments, query).content();

            // Map scores back to products (order is preserved)
            List<ScoredProduct> scoredProducts = new ArrayList<>();
            for (int i = 0; i < candidates.size() && i < scoreList.size(); i++) {
                ProductDocument product = candidates.get(i);
                double score = scoreList.get(i);
                scoredProducts.add(new ScoredProduct(product, score));
            }

            // Sort by score (descending) and take top K
            List<ScoredProduct> result = scoredProducts.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .toList();

            log.debug("Batch reranking completed, returning top {} results", result.size());
            return result;

        } catch (Exception e) {
            log.error("Batch reranking failed for query: {}, returning original order", query, e);
            return toScoredProducts(candidates, topK);
        }
    }

    /**
     * Build text representation of product for reranking
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
     * Convert products to ScoredProduct without scores (original order)
     */
    private List<ScoredProduct> toScoredProducts(List<ProductDocument> products, int topK) {
        return products.stream()
                .limit(topK)
                .map(p -> new ScoredProduct(p, 1.0))
                .toList();
    }

    /**
     * Wrapper class for product with rerank score
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
