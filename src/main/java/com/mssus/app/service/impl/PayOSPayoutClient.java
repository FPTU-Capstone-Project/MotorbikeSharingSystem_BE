package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.exception.PayosClientException;
import com.mssus.app.dto.request.PayoutOrderRequest;
import com.mssus.app.dto.request.wallet.PayOSPayoutListRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.util.retry.Retry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PayOSPayoutClient {

    private static final String DEFAULT_ENDPOINT = "https://api-merchant.payos.vn/v1/payouts";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter PAYOS_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${payos.payout.client-id}")
    private String payoutClientId;

    @Value("${payos.payout.api-key}")
    private String payoutApiKey;

    @Value("${payos.payout.checksum-key}")
    private String payoutChecksumKey;


    public PayOSPayoutClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Create PayOS payout order with retry mechanism.
     * Implements 3 retry attempts with exponential backoff for network errors.
     */
    public JsonNode createPayoutOrder(PayoutOrderRequest request, String idempotencyKey) {
        try {
//            String body = objectMapper.writeValueAsString(request);
            Map<String, Object> requestBody = buildRequestBody(request);
            String responseBody = webClient.post()
                    .uri(DEFAULT_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> buildHeaders(headers, request, idempotencyKey))
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> {
                                        log.error("PayOS payout API 4xx error: status={}, body={}",
                                                clientResponse.statusCode(), message);
                                        return new PayosClientException(
                                                "PayOS payout API client error: " + clientResponse.statusCode() + " body=" + message);
                                    }))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> {
                                        log.warn("PayOS payout API 5xx error: status={}, body={}, will retry",
                                                clientResponse.statusCode(), message);
                                        return new RuntimeException(
                                                "PayOS payout API server error: " + clientResponse.statusCode() + " body=" + message);
                                    }))
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                            .filter(throwable -> {
                                // Retry on network errors and 5xx, but not 4xx
                                if (throwable instanceof WebClientResponseException ex) {
                                    return ex.getStatusCode().is5xxServerError();
                                }
                                return throwable instanceof RuntimeException &&
                                       !(throwable instanceof PayosClientException);
                            })
                            .doBeforeRetry(retrySignal ->
                                    log.info("Retrying PayOS payout API call, attempt {}/{}",
                                            retrySignal.totalRetries() + 1, MAX_RETRY_ATTEMPTS))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                log.error("PayOS payout API exhausted all retries after {} attempts", MAX_RETRY_ATTEMPTS);
                                return new PayosClientException(
                                        "PayOS payout API failed after " + MAX_RETRY_ATTEMPTS + " retry attempts",
                                        retrySignal.failure());
                            }))
                    .timeout(READ_TIMEOUT)
                    .block();

            if (responseBody == null) {
                throw new PayosClientException("PayOS payout API returned empty body");
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            log.info("PayOS payout order created ref={}, code={}, desc={}",
                    request.getReferenceId(),
                    jsonNode.path("code").asText(),
                    jsonNode.path("desc").asText());

            return jsonNode;
        } catch (PayosClientException ex) {
            throw ex;
        } catch (WebClientResponseException ex) {
            log.error("PayOS payout API failed with status {} body {}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            if (ex.getStatusCode().is4xxClientError()) {
                throw new PayosClientException("PayOS payout API client error: " + ex.getResponseBodyAsString(), ex);
            }
            throw new PayosClientException("PayOS payout API server error: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Unexpected error when calling PayOS payout API", ex);
            throw new PayosClientException("Failed to create PayOS payout order: " + ex.getMessage(), ex);
        }
    }

    /**
     * Get payout status by referenceId (for polling fallback).
     * Used when webhook is not received within expected time.
     */
    public JsonNode getPayoutStatusByRef(String referenceId) {
        try {
            String responseBody = webClient.get()
                    .uri(DEFAULT_ENDPOINT + "/" + referenceId)
                    .headers(headers -> {
                        headers.set("x-client-id", payoutClientId);
                        headers.set("x-api-key", payoutApiKey);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new PayosClientException(
                                            "PayOS get payout status 4xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new RuntimeException(
                                            "PayOS get payout status 5xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof RuntimeException &&
                                               !(throwable instanceof PayosClientException))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    new PayosClientException("PayOS get payout status failed after retries", retrySignal.failure())))
                    .timeout(READ_TIMEOUT)
                    .block();

            if (responseBody == null) {
                throw new PayosClientException("PayOS get payout status returned empty body");
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            log.debug("PayOS payout status retrieved ref={}, code={}", referenceId, jsonNode.path("code").asText());
            return jsonNode;
        } catch (PayosClientException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error when getting PayOS payout status for ref={}", referenceId, ex);
            throw new PayosClientException("Failed to get PayOS payout status: " + ex.getMessage(), ex);
        }
    }

    private void buildHeaders(HttpHeaders headers,
                              PayoutOrderRequest request,
                              String idempotencyKey) {
        headers.set("x-client-id", payoutClientId);
        headers.set("x-api-key", payoutApiKey);
        headers.set("x-idempotency-key", idempotencyKey);
        headers.set("x-signature", generateSignature(request));
    }

    /**
     * Generate HMAC-SHA256 signature per PayOS docs using checksum key + canonical payload.
     * PayOS payout API signature format: key1=value1&key2=value2&... (sorted alphabetically)
     */
    private String generateSignature(PayoutOrderRequest request) {
        String payload = buildSignaturePayload(request);
        log.debug("PayOS payout signature payload: {}", payload);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(payoutChecksumKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String signature = bytesToHex(rawHmac);
            log.debug("PayOS payout signature: {}", signature);
            return signature;
        } catch (Exception e) {
            log.error("Error generating PayOS payout signature", e);
            throw new IllegalStateException("Unable to generate PayOS payout signature", e);
        }
    }

    /**
     * Build signature payload for PayOS payout API.
     * PayOS payout API signature format:
     * 1. Sort fields alphabetically: amount, category, description, referenceId, toAccountNumber, toBin
     * 2. Format: key=value&key=value (URL encoded values)
     * 3. Include category if present
     */
    private String buildSignaturePayload(PayoutOrderRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", String.valueOf(request.getAmount()));
        fields.put("description", request.getDescription());
        fields.put("referenceId", request.getReferenceId());
        fields.put("toAccountNumber", request.getToAccountNumber());
        fields.put("toBin", request.getToBin());

        StringBuilder builder = new StringBuilder();
        fields.forEach((key, value) -> {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(key)
                    .append("=")
                    .append(urlEncode(nullSafe(value)));
        });
        return builder.toString();
    }
    private Map<String, Object> buildRequestBody(PayoutOrderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("referenceId", request.getReferenceId());
        body.put("amount", request.getAmount());
        body.put("description", request.getDescription());
        body.put("toBin", request.getToBin());
        body.put("toAccountNumber", request.getToAccountNumber());
        return body;
    }
    /**
     * URL encode string (equivalent to JavaScript encodeURI)
     */
    private String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            log.warn("Error URL encoding value: {}", value, e);
            return value;
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    /**
     * Retrieve list of payout orders from PayOS with optional filters.
     */
    public JsonNode listPayoutOrders(PayOSPayoutListRequest filterRequest) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(DEFAULT_ENDPOINT);
            if (filterRequest.getLimit() != null) {
                uriBuilder.queryParam("limit", filterRequest.getLimit());
            }
            if (filterRequest.getOffset() != null) {
                uriBuilder.queryParam("offset", filterRequest.getOffset());
            }
            if (filterRequest.getReferenceId() != null) {
                uriBuilder.queryParam("referenceId", filterRequest.getReferenceId());
            }
            if (filterRequest.getApprovalState() != null) {
                uriBuilder.queryParam("approvalState", filterRequest.getApprovalState());
            }
            List<String> categories = filterRequest.getCategory();
            if (!categories.isEmpty()) {
                uriBuilder.queryParam("category", String.join(",", categories));
            }
            if (filterRequest.getFromDate() != null) {
                uriBuilder.queryParam("fromDate", PAYOS_DATETIME_FORMATTER.format(filterRequest.getFromDate().withNano(0)));
            }
            if (filterRequest.getToDate() != null) {
                uriBuilder.queryParam("toDate", PAYOS_DATETIME_FORMATTER.format(filterRequest.getToDate().withNano(0)));
            }

            String responseBody = webClient.get()
                    .uri(uriBuilder.build().toUri())
                    .headers(headers -> {
                        headers.set("x-client-id", payoutClientId);
                        headers.set("x-api-key", payoutApiKey);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new PayosClientException(
                                            "PayOS list payout orders 4xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new RuntimeException(
                                            "PayOS list payout orders 5xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof RuntimeException &&
                                               !(throwable instanceof PayosClientException))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    new PayosClientException("PayOS list payout orders failed after retries", retrySignal.failure())))
                    .timeout(READ_TIMEOUT)
                    .block();

            if (responseBody == null) {
                throw new PayosClientException("PayOS list payout orders returned empty body");
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            log.debug("PayOS payout list retrieved: code={}, desc={}",
                    jsonNode.path("code").asText(),
                    jsonNode.path("desc").asText());
            return jsonNode;
        } catch (PayosClientException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error when listing PayOS payout orders", ex);
            throw new PayosClientException("Failed to list PayOS payout orders: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retrieve detail of a single payout order by payoutId.
     */
    public JsonNode getPayoutOrder(String payoutId) {
        try {
            String responseBody = webClient.get()
                    .uri(DEFAULT_ENDPOINT + "/" + payoutId)
                    .headers(headers -> {
                        headers.set("x-client-id", payoutClientId);
                        headers.set("x-api-key", payoutApiKey);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new PayosClientException(
                                            "PayOS get payout order 4xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new RuntimeException(
                                            "PayOS get payout order 5xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof RuntimeException &&
                                               !(throwable instanceof PayosClientException))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    new PayosClientException("PayOS get payout order failed after retries", retrySignal.failure())))
                    .timeout(READ_TIMEOUT)
                    .block();

            if (responseBody == null) {
                throw new PayosClientException("PayOS get payout order returned empty body");
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            log.debug("PayOS payout detail retrieved id={}, code={}, desc={}",
                    payoutId,
                    jsonNode.path("code").asText(),
                    jsonNode.path("desc").asText());
            return jsonNode;
        } catch (PayosClientException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error when getting PayOS payout order {}", payoutId, ex);
            throw new PayosClientException("Failed to get PayOS payout order: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retrieve payout account balance information.
     */
    public JsonNode getPayoutAccountBalance() {
        try {
            String responseBody = webClient.get()
                    .uri(DEFAULT_ENDPOINT + "-account/balance")
                    .headers(headers -> {
                        headers.set("x-client-id", payoutClientId);
                        headers.set("x-api-key", payoutApiKey);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new PayosClientException(
                                            "PayOS payout balance 4xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(message -> new RuntimeException(
                                            "PayOS payout balance 5xx error: " + clientResponse.statusCode() + " body=" + message)))
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof RuntimeException &&
                                               !(throwable instanceof PayosClientException))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    new PayosClientException("PayOS payout balance failed after retries", retrySignal.failure())))
                    .timeout(READ_TIMEOUT)
                    .block();

            if (responseBody == null) {
                throw new PayosClientException("PayOS payout balance returned empty body");
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            log.debug("PayOS payout account balance retrieved: code={}, desc={}",
                    jsonNode.path("code").asText(),
                    jsonNode.path("desc").asText());
            return jsonNode;
        } catch (PayosClientException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error when getting PayOS payout account balance", ex);
            throw new PayosClientException("Failed to get PayOS payout account balance: " + ex.getMessage(), ex);
        }
    }
}

