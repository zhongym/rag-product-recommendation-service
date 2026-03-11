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
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for hybrid vector and BM25 search
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    /**
     * Perform hybrid search combining vector similarity and BM25
     */
    public List<ProductDocument> hybridSearch(String query, int topK, SearchFilters filters) {
        try {
            // Generate embedding for query
            List<Float> queryVector = buildVector(query);

            // Get weights from configuration
            double vectorWeight = ragProperties.getSearch().getHybridWeightVector();
            double bm25Weight = ragProperties.getSearch().getHybridWeightBm25();

            // BM25 text query with weight applied via boost
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

            // Combine with filters
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                    .should(bm25Query)
                    .should(q -> q.knn(k -> k
                            .field(ProductDocument.FIELD_EMBEDDING)
                            .queryVector(queryVector)
                            .k(topK)
                            .numCandidates(topK * 10)
                            .boost((float) vectorWeight)));

            // Add filter queries
            List<Query> filterQueries = buildFilterQueries(filters);
            if (!filterQueries.isEmpty()) {
                filterQueries.forEach(boolBuilder::filter);
            }

            Query toQuery = boolBuilder.build()._toQuery();
            // Build search request
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(ProductDocument.INDEX_NAME)
                    .size(topK)
                    .query(toQuery)
                    .minScore(0.1));

            log.debug("query:{}",searchRequest.query());

            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                    searchRequest, ProductDocument.class);

            // Results are already ranked by ES with correct weights via boost
            return response.hits().hits().stream()
                    .map(hit -> hit.source() != null ? hit.source() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in hybrid search for query: {}", query, e);
            throw new RuntimeException("Hybrid search failed", e);
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
     * Build filter queries from SearchFilters
     */
    private List<Query> buildFilterQueries(SearchFilters filters) {
        List<Query> queries = new ArrayList<>();

        if (filters == null) {
            return queries;
        }

        // Category filter
        if (filters.getCategories() != null && !filters.getCategories().isEmpty()) {
            Query categoryQuery = Query.of(q -> q
                    .terms(t -> t
                            .field(ProductDocument.FIELD_CATEGORY)
                            .terms(terms -> terms.value(filters.getCategories().stream()
                                    .map(FieldValue::of)
                                    .toList()))));
            queries.add(categoryQuery);
        }

        // Brand filter
        if (filters.getBrands() != null && !filters.getBrands().isEmpty()) {
            Query brandQuery = Query.of(q -> q
                    .terms(t -> t
                            .field(ProductDocument.FIELD_BRAND)
                            .terms(terms -> terms.value(filters.getBrands().stream()
                                    .map(FieldValue::of)
                                    .toList()))));
            queries.add(brandQuery);
        }

        // Tags filter
        if (filters.getTags() != null && !filters.getTags().isEmpty()) {
            Query tagsQuery = Query.of(q -> q
                    .terms(t -> t
                            .field(ProductDocument.FIELD_TAGS)
                            .terms(terms -> terms.value(filters.getTags().stream()
                                    .map(FieldValue::of)
                                    .toList()))));
            queries.add(tagsQuery);
        }

        // Price range filter
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

        // Stock filter
        if (filters.getInStock() != null && filters.getInStock()) {
            // Filter for products with stock > 0
            Query stockQuery = Query.of(q -> q.range(r -> r
                    .number(n -> n
                            .field(ProductDocument.FIELD_STOCK)
                            .gt(0.0))));
            queries.add(stockQuery);
            log.debug("In-stock filter added (stock > 0)");
        } else if (filters.getMinStock() != null) {
            // Filter for products with stock >= minStock
            Query stockQuery = Query.of(q -> q.range(r -> r
                    .number(n -> n
                            .field(ProductDocument.FIELD_STOCK)
                            .gte(filters.getMinStock().doubleValue()))));
            queries.add(stockQuery);
            log.debug("Min stock filter added (stock >= {})", filters.getMinStock());
        }

        return queries;
    }

    /**
     * Simple vector-only search
     */
    public List<ProductDocument> vectorSearch(String query, int topK) {
        try {
            // Convert float[] to List<Float> for Elasticsearch client
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
            log.error("Error in vector search for query: {}", query, e);
            throw new RuntimeException("Vector search failed", e);
        }
    }
}
