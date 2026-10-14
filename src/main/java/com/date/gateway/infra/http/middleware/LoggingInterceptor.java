package com.date.gateway.infra.http.middleware;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String REQUEST_START_TIME = "requestStartTime";
    private static final String REQUEST_ID = "requestId";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler)
            throws Exception {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        request.setAttribute(REQUEST_ID, requestId);

        long startTime = System.currentTimeMillis();
        request.setAttribute(REQUEST_START_TIME, startTime);

        log.info("[{}] === Incoming Request ===", requestId);
        log.info("[{}] Method: {} | URL: {}", requestId, request.getMethod(), request.getRequestURL());
        log.info("[{}] Remote Address: {}", requestId, request.getRemoteAddr());
        log.info("[{}] User Agent: {}", requestId, request.getHeader("User-Agent"));

        if (!request.getParameterMap().isEmpty()) {
            log.info("[{}] Query Parameters:", requestId);
            request.getParameterMap()
                    .forEach((key, values) -> log.info("[{}]   {}: {}", requestId, key, String.join(", ", values)));
        }

        logImportantHeaders(request, requestId);

        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) throws Exception {

        String requestId = (String) request.getAttribute(REQUEST_ID);
        log.info("[{}] Request processed successfully", requestId);
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex)
            throws Exception {

        String requestId = (String) request.getAttribute(REQUEST_ID);
        Long startTime = (Long) request.getAttribute(REQUEST_START_TIME);

        long duration = System.currentTimeMillis() - startTime;

        if (ex != null) {
            log.error("[{}] === Request Completed with Exception ===", requestId);
            log.error("[{}] Exception: {} - {}", requestId, ex.getClass().getSimpleName(), ex.getMessage());
            log.error("[{}] Status: {} | Duration: {}ms", requestId, response.getStatus(), duration);
        } else {
            log.info("[{}] === Request Completed Successfully ===", requestId);
            log.info("[{}] Status: {} | Duration: {}ms", requestId, response.getStatus(), duration);
        }

        logResponseHeaders(response, requestId);

        log.info("[{}] ========================", requestId);
    }

    private void logImportantHeaders(HttpServletRequest request, String requestId) {
        String[] importantHeaders = {
                "Content-Type", "Content-Length", "Authorization",
                "Accept", "Accept-Language", "X-Forwarded-For"
        };

        for (String headerName : importantHeaders) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null) {
                if ("Authorization".equalsIgnoreCase(headerName)) {
                    headerValue = maskSensitiveData(headerValue);
                }
                log.info("[{}] Header {}: {}", requestId, headerName, headerValue);
            }
        }
    }

    private void logResponseHeaders(HttpServletResponse response, String requestId) {
        String contentType = response.getContentType();
        if (contentType != null) {
            log.info("[{}] Response Content-Type: {}", requestId, contentType);
        }
    }

    private String maskSensitiveData(String value) {
        if (value == null || value.length() <= 10) {
            return "***";
        }
        return value.substring(0, 6) + "***" + value.substring(value.length() - 4);
    }
}
