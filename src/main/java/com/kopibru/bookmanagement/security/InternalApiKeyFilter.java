package com.kopibru.bookmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Rejects direct public access to BookController APIs unless the caller sends the
 * shared internal API key (IndexShelf / librarysystems). BookManagement is an internal service.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String expectedApiKey;

    public InternalApiKeyFilter(@Value("${app.internal-api-key}") String expectedApiKey) {
        if (!StringUtils.hasText(expectedApiKey)) {
            throw new IllegalStateException(
                    "app.internal-api-key must be set (env INTERNAL_API_KEY). "
                            + "BookManagement APIs are internal and require IndexShelf to present this key.");
        }
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only gate BookController APIs
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);
        if (!isAuthorized(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"timestamp\":\"" + Instant.now() + "\","
                            + "\"status\":401,"
                            + "\"error\":\"Missing or invalid internal API key. "
                            + "Call BookManagement only via IndexShelf.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(String provided) {
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
