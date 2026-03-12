package com.example.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.rag.config.RagProperties;
import com.example.rag.model.ProductDocument;
import com.example.rag.model.SearchFilters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索和 BM25 混合检索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {
    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;
    private final RerankService rerankService;

    /**
     * 执行混合检索，结合向量相似度和 BM25
     * 如果启用了重排序，使用分别执行和分数合并：
     * - 分别执行 BM25 和 KNN 以获得可控数量的结果
     * - 按权重合并分数
     * - 然后应用重排序器
     *
     * 如果未启用重排序，使用单个 bool query 以获得更好的性能
     */
    public List<ProductDocument> hybridSearch(String query, int topK, SearchFilters filters) {
        try {
            boolean rerankEnabled = Boolean.TRUE.equals(ragProperties.getRerank().getEnabled());

            if (rerankEnabled) {
                // 启用重排序时，分别执行以更好地控制候选来源
                return hybridSearchWithRanking(query, topK, filters);
            } else {
                // 未启用重排序时，使用单个 bool query 以获得更好的性能
                return hybridSearchWithBoolQuery(query, topK, filters);
            }

        } catch (Exception e) {
            log.error("混合检索失败，查询: {}", query, e);
            throw new RuntimeException("混合检索失败", e);
        }
    }

    /**
     * 分别执行 BM25 和 KNN 的混合检索
     * 在启用重排序时使用，以便更好地控制候选来源
     *
     * 流程：
     * 1. 分别执行 BM25 和 KNN 检索（无需排序）
     * 2. 按商品 ID 去重
     * 3. 应用重排序器进行最终排序（重排序器处理评分）
     */
    private List<ProductDocument> hybridSearchWithRanking(String query, int topK, SearchFilters filters) {
        try {
            // 计算候选数量
            int candidateCount = calculateCandidateCount(topK);

            log.debug("分别执行 BM25 和 KNN 检索，目标候选数: {}", candidateCount);

            // 阶段 1: 分别执行检索（无需排序）
            List<ProductDocument> bm25Results = executeBM25Search(query, candidateCount, filters);
            log.debug("BM25 返回 {} 个结果", bm25Results.size());

            List<ProductDocument> knnResults = executeKNNSearch(query, candidateCount, filters);
            log.debug("KNN 返回 {} 个结果", knnResults.size());

            // 阶段 2: 按商品 ID 去重（重排序器将处理评分和排序）
            List<ProductDocument> candidates = deduplicateAndMerge(bm25Results, knnResults);
            log.debug("去重后: {} 个唯一候选", candidates.size());

            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }

            // 阶段 3: 应用重排序器进行最终排序
            log.debug("重排序 {} 个候选到 top {}", candidates.size(), topK);

            List<RerankService.ScoredProduct> rerankedProducts = rerankService.rerank(
                    query,
                    candidates,
                    topK
            );

            return rerankedProducts.stream()
                    .map(RerankService.ScoredProduct::getProduct)
                    .toList();

        } catch (Exception e) {
            log.error("分别执行混合检索失败，查询: {}", query, e);
            throw new RuntimeException("分别执行混合检索失败", e);
        }
    }

    /**
     * 使用单个 bool query 的混合检索（原始实现）
     * 在未启用重排序时使用，以获得更好的性能
     */
    private List<ProductDocument> hybridSearchWithBoolQuery(String query, int topK, SearchFilters filters) {
        try {
            // 生成查询向量
            List<Float> queryVector = buildVector(query);

            // 从配置获取权重
            double vectorWeight = ragProperties.getSearch().getHybridWeightVector();
            double bm25Weight = ragProperties.getSearch().getHybridWeightBm25();

            // BM25 文本查询，通过 boost 应用权重
            Query bm25Query = Query.of(q -> q
                    .bool(b -> b
                            .should(m -> m.match(ma -> ma
                                    .field(ProductDocument.FIELD_SEARCHABLE_TEXT)
                                    .query(query)
                                    .boost((float) bm25Weight)))
                            .should(m -> m.match(ma -> ma
                                    .field(ProductDocument.FIELD_NAME)
                                    .query(query)
                                    .boost(2.0f * (float) bm25Weight)))
                            .should(m -> m.match(ma -> ma
                                    .field(ProductDocument.FIELD_DESCRIPTION)
                                    .query(query)
                                    .boost((float) bm25Weight)))
                            .minimumShouldMatch("1"))
            );

            Query vectorQuery = Query.of(q ->
                    q.knn(k -> k
                            .field(ProductDocument.FIELD_EMBEDDING)
                            .queryVector(queryVector)
                            .k(topK)
                            .numCandidates(topK * 10)
                            .boost((float) vectorWeight)));

            // 结合过滤器
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                    .should(bm25Query)
                    .should(vectorQuery);

            // 添加过滤查询
            List<Query> filterQueries = buildFilterQueries(filters);
            if (!filterQueries.isEmpty()) {
                filterQueries.forEach(boolBuilder::filter);
            }

            Query toQuery = boolBuilder.build()._toQuery();
            // 构建搜索请求
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(ProductDocument.INDEX_NAME)
                    .size(topK)
                    .query(toQuery)
                    .minScore(0.1));

            log.debug("查询:{}", searchRequest.query());

            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                    searchRequest, ProductDocument.class);

            // 结果已通过 ES 按正确权重排序
            return response.hits().hits().stream()
                    .map(hit -> hit.source() != null ? hit.source() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("bool query 混合检索失败，查询: {}", query, e);
            throw new RuntimeException("bool query 混合检索失败", e);
        }
    }

    private List<Float> buildVector(String query) {
        float[] queryEmbedding = embeddingModel.embed(query).content().vector();

        List<Float> queryVector = new ArrayList<>(queryEmbedding.length);
        for (float v : queryEmbedding) {
            queryVector.add(v);
        }
        return queryVector;
    }

    /**
     * 从 SearchFilters 构建过滤查询
     */
    private List<Query> buildFilterQueries(SearchFilters filters) {
        List<Query> queries = new ArrayList<>();

        if (filters == null) {
            return queries;
        }

        // 类别过滤
        if (filters.getCategories() != null && !filters.getCategories().isEmpty()) {
            Query categoryQuery = Query.of(q -> q
                    .terms(t -> t
                            .field(ProductDocument.FIELD_CATEGORY)
                            .terms(terms -> terms.value(filters.getCategories().stream()
                                    .map(FieldValue::of)
                                    .toList()))));
            queries.add(categoryQuery);
        }

        // 品牌过滤
        if (filters.getBrands() != null && !filters.getBrands().isEmpty()) {
            Query brandQuery = Query.of(q -> q
                    .terms(t -> t
                            .field(ProductDocument.FIELD_BRAND)
                            .terms(terms -> terms.value(filters.getBrands().stream()
                                    .map(FieldValue::of)
                                    .toList()))));
            queries.add(brandQuery);
        }

        // 标签过滤
        if (filters.getTags() != null && !filters.getTags().isEmpty()) {
            Query tagsQuery = Query.of(q -> q
                    .terms(t -> t
                            .field(ProductDocument.FIELD_TAGS)
                            .terms(terms -> terms.value(filters.getTags().stream()
                                    .map(FieldValue::of)
                                    .toList()))));
            queries.add(tagsQuery);
        }

        // 价格范围过滤
        if (filters.getMinPrice() != null || filters.getMaxPrice() != null) {
            if (filters.getMinPrice() != null && filters.getMaxPrice() != null) {
                Query priceQuery = Query.of(q -> q.range(r -> r
                        .number(n -> n
                                .field(ProductDocument.FIELD_PRICE)
                                .gte(filters.getMinPrice())
                                .lte(filters.getMaxPrice()))));
                queries.add(priceQuery);
            } else if (filters.getMinPrice() != null) {
                Query priceQuery = Query.of(q -> q.range(r -> r
                        .number(n -> n
                                .field(ProductDocument.FIELD_PRICE)
                                .gte(filters.getMinPrice()))));
                queries.add(priceQuery);
            } else {
                Query priceQuery = Query.of(q -> q.range(r -> r
                        .number(n -> n
                                .field(ProductDocument.FIELD_PRICE)
                                .lte(filters.getMaxPrice()))));
                queries.add(priceQuery);
            }
            log.debug("Price filter added: min={}, max={}", filters.getMinPrice(), filters.getMaxPrice());
        }

        // 库存过滤
        if (filters.getInStock() != null && filters.getInStock()) {
            // 过滤库存 > 0 的商品
            Query stockQuery = Query.of(q -> q.range(r -> r
                    .number(n -> n
                            .field(ProductDocument.FIELD_STOCK)
                            .gt(0.0))));
            queries.add(stockQuery);
            log.debug("已添加有库存过滤 (stock > 0)");
        } else if (filters.getMinStock() != null) {
            // 过滤库存 >= minStock 的商品
            Query stockQuery = Query.of(q -> q.range(r -> r
                    .number(n -> n
                            .field(ProductDocument.FIELD_STOCK)
                            .gte(filters.getMinStock().doubleValue()))));
            queries.add(stockQuery);
            log.debug("已添加最小库存过滤 (stock >= {})", filters.getMinStock());
        }

        return queries;
    }

    /**
     * 根据重排序配置计算候选数量
     * 如果启用重排序，返回 topK * candidatesMultiplier
     * 否则返回 topK
     */
    private int calculateCandidateCount(int topK) {
        if (Boolean.TRUE.equals(ragProperties.getRerank().getEnabled())) {
            int multiplier = ragProperties.getRerank().getCandidatesMultiplier();
            int maxCandidates = ragProperties.getMaxTopK() * multiplier;
            return Math.min(topK * multiplier, maxCandidates);
        }
        return topK;
    }

    /**
     * 简单的纯向量检索
     */
    public List<ProductDocument> vectorSearch(String query, int topK) {
        try {
            // 将 float[] 转换为 List<Float> 以供 Elasticsearch 客户端使用
            List<Float> queryVector = buildVector(query);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(ProductDocument.INDEX_NAME)
                    .size(topK)
                    .query(q -> q.knn(k -> k
                            .field(ProductDocument.FIELD_EMBEDDING)
                            .queryVector(queryVector)
                            .k(topK)
                            .numCandidates(topK * 10))));

            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                    searchRequest, ProductDocument.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("向量检索失败，查询: {}", query, e);
            throw new RuntimeException("向量检索失败", e);
        }
    }

    /**
     * 执行纯 BM25 检索（带过滤器）
     * 返回未排序的商品列表（重排序器将处理评分和排序）
     *
     * @param query  搜索查询
     * @param topK   返回结果数量
     * @param filters 搜索过滤器
     * @return BM25 检索的商品列表
     */
    public List<ProductDocument> executeBM25Search(String query, int topK, SearchFilters filters) {
        try {
            // BM25 文本查询
            Query bm25Query = Query.of(q -> q
                    .bool(b -> b
                            .should(m -> m.match(ma -> ma
                                    .field(ProductDocument.FIELD_SEARCHABLE_TEXT)
                                    .query(query)))
                            .should(m -> m.match(ma -> ma
                                    .field(ProductDocument.FIELD_NAME)
                                    .query(query)
                                    .boost(2.0f)))
                            .should(m -> m.match(ma -> ma
                                    .field(ProductDocument.FIELD_DESCRIPTION)
                                    .query(query)))
                            .minimumShouldMatch("1"))
            );

            // 如果有过滤器，构建最终查询
            Query finalQuery;
            List<Query> filterQueries = buildFilterQueries(filters);
            if (!filterQueries.isEmpty()) {
                // 用过滤器包装 BM25 查询
                finalQuery = Query.of(q -> q
                        .bool(b -> b
                                .must(bm25Query)
                                .filter(filterQueries)));
            } else {
                finalQuery = bm25Query;
            }

            // 构建搜索请求（不按分数排序）
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(ProductDocument.INDEX_NAME)
                    .size(topK)
                    .query(finalQuery)
                    .minScore(0.1));

            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                    searchRequest, ProductDocument.class);

            // 提取结果（无需排序）
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("BM25 检索失败，查询: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 执行纯 KNN 检索（带过滤器）
     * 返回未排序的商品列表（重排序器将处理评分和排序）
     *
     * @param query  搜索查询
     * @param topK   返回结果数量
     * @param filters 搜索过滤器
     * @return KNN 检索的商品列表
     */
    public List<ProductDocument> executeKNNSearch(String query, int topK, SearchFilters filters) {
        try {
            // 生成查询向量
            List<Float> queryVector = buildVector(query);

            // 构建 KNN 查询
            Query knnQuery = Query.of(q -> q
                    .knn(k -> k
                            .field(ProductDocument.FIELD_EMBEDDING)
                            .queryVector(queryVector)
                            .k(topK)
                            .numCandidates(topK * 10)));

            // 如果有过滤器，构建最终查询
            Query finalQuery;
            List<Query> filterQueries = buildFilterQueries(filters);
            if (!filterQueries.isEmpty()) {
                // 用过滤器包装 KNN 查询
                finalQuery = Query.of(q -> q
                        .bool(b -> b
                                .must(knnQuery)
                                .filter(filterQueries)));
            } else {
                finalQuery = knnQuery;
            }

            // 构建搜索请求（不按分数排序）
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(ProductDocument.INDEX_NAME)
                    .size(topK)
                    .query(finalQuery));

            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                    searchRequest, ProductDocument.class);

            // 提取结果（无需排序）
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("KNN 检索失败，查询: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 对 BM25 和 KNN 的结果去重合并
     * 重排序器将处理评分和排序，因此这里只进行去重
     *
     * 流程：
     * 1. 合并 BM25 和 KNN 的结果
     * 2. 按商品 ID 去重（保留第一次出现的）
     *
     * @param bm25Results BM25 检索结果
     * @param knnResults  KNN 检索结果
     * @return 去重后的商品列表
     */
    private List<ProductDocument> deduplicateAndMerge(
            List<ProductDocument> bm25Results,
            List<ProductDocument> knnResults) {

        // 使用 LinkedHashSet 保持插入顺序并按 ID 去重
        Set<String> seenIds = new HashSet<>();
        List<ProductDocument> mergedResults = new ArrayList<>();

        // 先添加 BM25 结果
        for (ProductDocument doc : bm25Results) {
            if (seenIds.add(doc.getId())) {
                mergedResults.add(doc);
            }
        }

        // 再添加 KNN 结果（仅添加未重复的）
        for (ProductDocument doc : knnResults) {
            if (seenIds.add(doc.getId())) {
                mergedResults.add(doc);
            }
        }

        log.debug("去重统计: BM25={}, KNN={}, 合并后={}, 移除重复={}",
                bm25Results.size(), knnResults.size(), mergedResults.size(),
                bm25Results.size() + knnResults.size() - mergedResults.size());

        return mergedResults;
    }
}
