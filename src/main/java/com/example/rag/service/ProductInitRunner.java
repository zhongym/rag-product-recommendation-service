package com.example.rag.service;

import com.example.rag.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductInitRunner implements CommandLineRunner {
    private final ProductIndexService productIndexService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking for initial product data...");

        // Check if index is empty
        long count = productIndexService.getIndexCount();
        if (count > 0) {
            log.info("Index already contains {} products, skipping initial load", count);
            return;
        }

        // Try to load products from JSON file
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:data/*.json");

        if (resources.length == 0) {
            log.warn("No product data files found in classpath:data/");
            return;
        }

        for (Resource resource : resources) {
            if (resource.getFilename().equals("products.json")) {
                log.info("Loading products from: {}", resource.getFilename());

                try (InputStream is = resource.getInputStream()) {
                    Product[] productsArray = objectMapper.readValue(is, Product[].class);
                    List<Product> products = Arrays.asList(productsArray);

                    log.info("Found {} products to index", products.size());

                    // Index products in bulk
                    productIndexService.batchIndexProducts(products).get();

                    log.info("Successfully indexed {} products", products.size());
                } catch (Exception e) {
                    log.error("Error loading products from {}", resource.getFilename(), e);
                }
            }
        }
    }
}
