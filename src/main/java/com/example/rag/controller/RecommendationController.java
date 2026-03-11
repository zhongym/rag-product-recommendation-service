package com.example.rag.controller;

import com.example.rag.dto.RecommendationRequest;
import com.example.rag.dto.RecommendationResponse;
import com.example.rag.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST controller for product recommendations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping
    public ResponseEntity<RecommendationResponse> recommend(
            @RequestBody @Valid RecommendationRequest request
    ) {
        log.info("Received recommendation request: query={}, topK={}",
                request.getQuery(), request.getTopK());

        RecommendationResponse response = recommendationService.recommend(request);

        log.info("Returning {} recommendations in {}ms",
                response.getTotalResults(), response.getProcessingTimeMs());

        return ResponseEntity.ok(response);
    }

}
