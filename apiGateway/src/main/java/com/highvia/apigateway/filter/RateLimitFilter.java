package com.highvia.apigateway.filter;

import com.highvia.apigateway.config.RateLimitConfig;
import com.highvia.apigateway.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {
    private final RateLimitService rateLimitService;
    private final RateLimitConfig rateLimitConfig;

    /**
     * filter method
     *
     * @param exchange include request and response objects
     * @param chain For continue to process requests
     * @return Mono<Void> The result of async operations
     */

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/auth/")) {
            log.debug("Skipping rate limit for auth path: {}", path);
            return chain.filter(exchange);
        }
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId == null || userId.isEmpty()){
            log.error("X-User-Id header is missing after JWT validation");
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
        String key = "rate_limit:user:" + userId;

        boolean allowed = rateLimitService.isAllowed(key, rateLimitConfig.getMaxRequests(), rateLimitConfig.getWindowSeconds());
        if (!allowed) {
            log.warn("Rate limit exceeded for user: {}", userId);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        log.debug("Rate Limit check passed for user: {}", userId);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
