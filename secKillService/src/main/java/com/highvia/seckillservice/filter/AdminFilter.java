package com.highvia.seckillservice.filter;

import com.highvia.common.entity.UserInfo;
import com.highvia.common.security.JwtUtils;
import com.highvia.common.utils.RsaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.security.PublicKey;

@Component
@Slf4j
public class AdminFilter extends OncePerRequestFilter {

    @Value("${jwt.publicKeyPath}")
    private String publicKeyPath;

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = null;
        if (request.getRequestURI().startsWith("/api/admin")) {
            token = extractToken(request);
            if (token == null) {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\": false, \"message\": \"No token provided\"}");
                return;
            }
            try {
                PublicKey publicKey = RsaUtils.getPublicKey(publicKeyPath);
                UserInfo userInfo = JwtUtils.getInfoFromToken(token, publicKey);
                if (!userInfo.isAdmin()) {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\": false, \"message\": \"Admin only\"}");
                    return;
                }
                log.info("Admin access: {}", userInfo.email());
            } catch (Exception e) {
                log.error("JWT validation failed", e);
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\": false, \"message\": \"Invalid token\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

}
