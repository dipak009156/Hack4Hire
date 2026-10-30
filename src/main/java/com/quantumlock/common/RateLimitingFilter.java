package com.quantumlock.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map IP+endpoint -> RequestTracker
    private final ConcurrentHashMap<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();

    private static final int LOGIN_LIMIT_PER_MINUTE = 5;
    private static final int GENERAL_LIMIT_PER_MINUTE = 120;
    private static final int WINDOW_SECONDS = 60;

    private static class RequestCounter {
        final long windowStartMs;
        final AtomicInteger count;

        RequestCounter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
            this.count = new AtomicInteger(1);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();

        boolean isLogin = uri.equals("/api/v1/auth/login");
        int maxAllowed = isLogin ? LOGIN_LIMIT_PER_MINUTE : GENERAL_LIMIT_PER_MINUTE;
        String key = clientIp + ":" + (isLogin ? "auth" : "general");

        long now = System.currentTimeMillis();
        long windowStart = now - (now % (WINDOW_SECONDS * 1000L));

        RequestCounter counter = requestCounts.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStartMs != windowStart) {
                return new RequestCounter(windowStart);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (counter.count.get() > maxAllowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW_SECONDS));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Maximum " + maxAllowed + " requests per minute allowed."
            );
            problem.setTitle("Too Many Requests");
            problem.setType(URI.create("urn:problem:too-many-requests"));
            problem.setProperty("timestamp", Instant.now());
            problem.setProperty("retryAfterSeconds", WINDOW_SECONDS);

            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
        }
        return xfHeader.split(",")[0].trim();
    }
}
