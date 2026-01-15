package org.di.digital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.ChatResponse;
import org.di.digital.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${python.model.host:localhost}")
    private String pythonHost;

    @Value("${python.model.port:5000}")
    private String pythonPort;

    @Value("${python.model.stream-endpoint:/stream}")
    private String streamEndpoint;

    @Value("${python.model.complete-endpoint:/generate}")
    private String completeEndpoint;

    @Value("${python.model.health-endpoint:/health}")
    private String healthEndpoint;

    /**
     * Stream chat response using SSE Emitter (traditional approach)
     */
    public void streamChatResponse(ChatRequest request, SseEmitter emitter) {
        String url = buildUrl(streamEndpoint);

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(300000); // 5 minutes for long responses

            // Send request
            String jsonRequest = objectMapper.writeValueAsString(request);
            connection.getOutputStream().write(jsonRequest.getBytes(StandardCharsets.UTF_8));

            // Read streaming response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int chunkCount = 0;

                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        chunkCount++;
                        log.debug("Received chunk {}: {}", chunkCount, line);

                        // Send chunk to client
                        emitter.send(SseEmitter.event()
                                .data(line)
                                .name("message"));
                    }
                }

                log.info("Streaming complete. Total chunks: {}", chunkCount);
                emitter.complete();

            } catch (Exception e) {
                log.error("Error reading stream: ", e);
                emitter.completeWithError(e);
            }

        } catch (Exception e) {
            log.error("Error connecting to Python model: ", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * Stream chat response using Reactive WebClient (modern approach)
     */
    public Flux<String> streamChatResponseFlux(ChatRequest request) {
        String url = buildUrl(streamEndpoint);

        WebClient webClient = webClientBuilder.build();

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMinutes(5))
                .doOnNext(chunk -> log.debug("Received chunk: {}", chunk))
                .doOnComplete(() -> log.info("Streaming completed"))
                .doOnError(error -> log.error("Streaming error: ", error))
                .onErrorResume(error -> {
                    log.error("Error in streaming: ", error);
                    return Flux.just("Error: " + error.getMessage());
                });
    }

    /**
     * Get complete chat response (non-streaming)
     */
    public ChatResponse getChatResponse(ChatRequest request) {
        String url = buildUrl(completeEndpoint);

        WebClient webClient = webClientBuilder.build();

        try {
            return webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .timeout(Duration.ofMinutes(2))
                    .block();

        } catch (Exception e) {
            log.error("Error getting chat response: ", e);
            return ChatResponse.builder()
                    .response("Error: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * Check if Python model service is healthy
     */
    public boolean checkModelHealth() {
        String url = buildUrl(healthEndpoint);

        WebClient webClient = webClientBuilder.build();

        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            log.info("Health check response: {}", response);
            return response != null && response.contains("ok");

        } catch (Exception e) {
            log.error("Health check failed: ", e);
            return false;
        }
    }

    private String buildUrl(String endpoint) {
        return String.format("http://%s:%s%s", pythonHost, pythonPort, endpoint);
    }
}