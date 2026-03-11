package com.example.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.rag.config.OpenAIProperties;
import com.example.rag.config.RagProperties;
import com.example.rag.model.Product;
import com.example.rag.model.ProductDocument;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.example.rag.model.ProductDocument.INDEX_NAME;

/**
 * Service for indexing products to Elasticsearch with vector embeddings
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;
    private final OpenAIProperties openAIProperties;


    private static Resource MAPPING = new ClassPathResource("/mapping/elasticsearch-mapping.json");

    @PostConstruct
    public void initializeIndex() {
        try {
            // Check if index exists
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                    .value();

            if (!exists) {
                createIndex();
                log.info("Created Elasticsearch index: {}", INDEX_NAME);
            } else {
                log.info("Elasticsearch index {} already exists", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("Error initializing Elasticsearch index", e);
            throw new RuntimeException("Failed to initialize Elasticsearch index", e);
        }
    }

    private void createIndex() {
        try {
            // Read mapping from resource file
            String mapping = new String(MAPPING.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            CreateIndexResponse response = elasticsearchClient.indices()
                    .create(c -> c.index(INDEX_NAME)
                            .withJson(new StringReader(mapping)));

            log.info("Index created: acknowledged={}, shardsAcknowledged={}",
                    response.acknowledged(), response.shardsAcknowledged());
        } catch (Exception e) {
            log.error("Error creating index", e);
            throw new RuntimeException("Failed to create index", e);
        }
    }

    /**
     * Index a single product with embedding
     */
    @Async
    public CompletableFuture<Void> indexProduct(Product product) {
        return CompletableFuture.runAsync(() -> {
            try {
                ProductDocument document = convertToDocument(product);
                IndexResponse response = elasticsearchClient.index(i -> i
                        .index(INDEX_NAME)
                        .id(document.getId())
                        .document(document));

                log.debug("Indexed product {}: result={}", product.getId(), response.result());
            } catch (Exception e) {
                log.error("Error indexing product: {}", product.getId(), e);
                throw new RuntimeException("Failed to index product", e);
            }
        });
    }

    /**
     * Batch index multiple products
     */
    @Async
    public CompletableFuture<BulkResponse> batchIndexProducts(List<Product> products) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<BulkOperation> operations = products.stream()
                        .map(product -> {
                            ProductDocument document = convertToDocument(product);
                            return BulkOperation.of(b -> b
                                    .index(idx -> idx
                                            .index(INDEX_NAME)
                                            .id(document.getId())
                                            .document(document)));
                        })
                        .collect(Collectors.toList());

                BulkResponse response = elasticsearchClient.bulk(b -> b.operations(operations));

                if (response.errors()) {
                    log.warn("Bulk indexing completed with errors");
                    response.items().forEach(item -> {
                        if (item.error() != null) {
                            log.error("Error indexing document {}: {}", item.id(), item.error().reason());
                        }
                    });
                } else {
                    log.info("Successfully indexed {} products", products.size());
                }

                return response;
            } catch (Exception e) {
                log.error("Error in bulk indexing", e);
                throw new RuntimeException("Failed to bulk index products", e);
            }
        });
    }

    /**
     * Delete a product from index
     */
    public void deleteProduct(String productId) {
        try {
            DeleteResponse response = elasticsearchClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(productId));

            log.debug("Deleted product {}: result={}", productId, response.result());
        } catch (Exception e) {
            log.error("Error deleting product: {}", productId, e);
            throw new RuntimeException("Failed to delete product", e);
        }
    }


    /**
     * Get product document by ID
     */
    public ProductDocument getProductById(String productId) {
        try {
            GetResponse<ProductDocument> response = elasticsearchClient.get(
                    g -> g.index(INDEX_NAME).id(productId),
                    ProductDocument.class);

            if (response.found()) {
                return response.source();
            }
            return null;
        } catch (Exception e) {
            log.error("Error getting product: {}", productId, e);
            throw new RuntimeException("Failed to get product", e);
        }
    }

    /**
     * Convert Product entity to ProductDocument with embedding
     */
    private ProductDocument convertToDocument(Product product) {
        // Build searchable text from various fields
        String searchableText = buildSearchableText(product);

        // Generate embedding from combined text
        float[] embedding;
        try {
            embedding = embeddingModel.embed(searchableText).content().vector();
        } catch (Exception e) {
            log.error("Error generating embedding for product: {}", product.getId(), e);
            embedding = new float[openAIProperties.getEmbedding().getDimensions()];
        }

        return ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .price(product.getPrice() != null ? product.getPrice().doubleValue() : null)
                .tags(product.getTags())
                .brand(product.getBrand())
                .stock(product.getStock())
                .imageUrl(product.getImageUrl())
                .searchableText(searchableText)
                .embedding(embedding)
                .build();
    }

    /**
     * Build searchable text from product fields for better embedding
     */
    private String buildSearchableText(Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append(product.getName()).append(" ");
        sb.append(product.getDescription()).append(" ");
        sb.append("Category: ").append(product.getCategory()).append(" ");

        if (product.getBrand() != null) {
            sb.append("Brand: ").append(product.getBrand()).append(" ");
        }

        if (product.getTags() != null && !product.getTags().isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", product.getTags())).append(" ");
        }

        if (product.getPrice() != null) {
            sb.append("Price: ").append(product.getPrice()).append(" ");
        }

        return sb.toString().trim();
    }

    /**
     * Get index statistics
     */
    public long getIndexCount() {
        try {
            CountResponse response = elasticsearchClient.count(c -> c.index(INDEX_NAME));
            return response.count();
        } catch (Exception e) {
            log.error("Error getting index count", e);
            return 0;
        }
    }
}
