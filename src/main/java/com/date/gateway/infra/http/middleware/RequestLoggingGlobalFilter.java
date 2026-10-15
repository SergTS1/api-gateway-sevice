package com.date.gateway.infra.http.middleware;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.UUID;

@Slf4j
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);

        var request = exchange.getRequest();
        var response = exchange.getResponse();

        long startTime = System.currentTimeMillis();

        // Передаём идентификатор дальше в микросервис.
        var mutatedRequest = request.mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
                .build();

        var mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Возвращаем идентификатор клиенту.
        response.getHeaders().set(REQUEST_ID_HEADER, requestId);

        log.info("[{}] === Incoming Request ===", requestId);
        log.info("[{}] Method: {} | URL: {}",
                requestId,
                request.getMethod(),
                request.getURI());

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            log.info("[{}] Remote Address: {}", requestId, remoteAddress);
        }

        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        if (userAgent != null) {
            log.info("[{}] User Agent: {}", requestId, userAgent);
        }

        request.getQueryParams().forEach((key, values) -> {
            if (isSensitiveParameter(key)) {
                log.info("[{}] Query Parameter: {}=[REDACTED]", requestId, key);
            } else {
                log.info("[{}] Query Parameter: {}={}",
                        requestId, key, values);
            }
        });

        logImportantHeaders(request.getHeaders(), requestId);

        return chain.filter(mutatedExchange)
                .doOnError(ex ->
                        log.error("[{}] Request failed: {} - {}",
                                requestId,
                                ex.getClass().getSimpleName(),
                                ex.getMessage()))
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;

                    var status = response.getStatusCode();

                    log.info(
                            "[{}] === Request Completed === Signal: {} | Status: {} | Duration: {}ms",
                            requestId,
                            signalType,
                            status != null ? status.value() : "not set",
                            duration
                    );

                    String contentType = response.getHeaders()
                            .getFirst(HttpHeaders.CONTENT_TYPE);

                    if (contentType != null) {
                        log.info("[{}] Response Content-Type: {}",
                                requestId, contentType);
                    }

                    log.info("[{}] ========================", requestId);
                });
    }

    private void logImportantHeaders(
            HttpHeaders headers,
            String requestId) {

        String[] importantHeaders = {
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.CONTENT_LENGTH,
                HttpHeaders.ACCEPT,
                HttpHeaders.ACCEPT_LANGUAGE,
                "X-Forwarded-For"
        };

        for (String headerName : importantHeaders) {
            String value = headers.getFirst(headerName);

            if (value != null) {
                log.info("[{}] Header {}: {}",
                        requestId, headerName, value);
            }
        }

        // Не выводим сам токен в лог.
        if (headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            log.debug("[{}] Authorization header is present", requestId);
        }
    }

    private boolean isSensitiveParameter(String name) {
        String normalized = name.toLowerCase();

        return normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.equals("code");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
