package com.highvia.apigateway.filter;

import com.highvia.common.utils.RsaUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.PublicKey;

@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    @Value("${jwt.publicKeyPath}")
    private String publicKeyPath;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.publicKey = RsaUtils.getPublicKey(publicKeyPath);
            log.info("Public key loaded successfully from: {}", publicKeyPath);
        } catch (Exception e) {
            log.error("Failed to load public key from: {}", publicKeyPath, e);
            throw new RuntimeException("Failed to initialize JWT filter", e);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (path.startsWith("/api/auth/")){
            log.debug("Skipping JWT validation for auth path: {}", path);
            return chain.filter(exchange);
        }
        if (path.startsWith("/api/debug/")){
            log.debug("Skipping JWT validation for debug path: {}", path);
            return chain.filter(exchange);
        }
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid header");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        //Remove "Bearer "
        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser().setSigningKey(publicKey).build().parseClaimsJws(token).getBody();
            Object idObj = claims.get("id");
            String userId = idObj != null ? String.valueOf(idObj) : null;
            if (userId == null || "null".equals(userId)) {
                log.warn("Invalid JWT: no userId found");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Admin path check — must have ADMIN role
            if (path.startsWith("/api/admin/")) {
                String role = claims.get("role", String.class);
                if (!"ADMIN".equals(role)) {
                    log.warn("Access denied to admin path {} for user: {}", path, userId);
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }

            ServerHttpRequest modifiedRequest = request.mutate().header("X-User-Id", userId).build();
            log.debug("JWT validated for user: {}", userId);

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
