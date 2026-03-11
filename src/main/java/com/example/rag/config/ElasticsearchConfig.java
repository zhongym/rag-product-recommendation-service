package com.example.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import java.time.Duration;

/**
 * Elasticsearch configuration for product index with vector search support
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchProperties elasticsearchProperties;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClientBuilder builder = RestClient.builder(
                HttpHost.create(elasticsearchProperties.getUris())
        );

        // Set timeouts
        Duration connectionTimeout = elasticsearchProperties.getConnectionTimeout();
        Duration socketTimeout = elasticsearchProperties.getSocketTimeout();

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(Math.toIntExact(connectionTimeout.toMillis()))
                .setSocketTimeout(Math.toIntExact(socketTimeout.toMillis())));

        // Set authentication if credentials are provided
        if (elasticsearchProperties.hasAuthentication()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(
                            elasticsearchProperties.getUsername(),
                            elasticsearchProperties.getPassword()
                    ));

            builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider));
        }

        // Create ObjectMapper for JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.findAndRegisterModules();

        // Create transport with Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(
                builder.build(),
                new JacksonJsonpMapper(objectMapper)
        );

        return new ElasticsearchClient(transport);
    }

    @Bean
    public RestClient restClient() {
        RestClientBuilder builder = RestClient.builder(
                HttpHost.create(elasticsearchProperties.getUris())
        );

        // Set authentication if credentials are provided
        if (elasticsearchProperties.hasAuthentication()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(
                            elasticsearchProperties.getUsername(),
                            elasticsearchProperties.getPassword()
                    ));

            builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider));
        }

        // Set default headers
        Header[] defaultHeaders = new Header[]{
                new BasicHeader("Content-Type", "application/json")
        };
        builder.setDefaultHeaders(defaultHeaders);

        return builder.build();
    }
}
