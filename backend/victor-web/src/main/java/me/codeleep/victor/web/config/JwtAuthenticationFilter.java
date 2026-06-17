package me.codeleep.victor.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.constant.Constants;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.core.dto.OpenApiKeyVO;
import me.codeleep.victor.core.service.OpenApiKeyService;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器，同时支持API Key认证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final OpenApiKeyService openApiKeyService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 1. 首先尝试 JWT 认证
            String jwt = extractJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && validateToken(jwt)) {
                Long userId = jwtUtils.extractUserId(jwt);
                String username = jwtUtils.extractUsername(jwt);

                // 设置UserContext
                UserContext.setUserId(userId);
                UserContext.setUsername(username);

                // 设置SecurityContext
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT认证成功: userId={}, username={}", userId, username);
            } else {
                // 2. 如果 JWT 认证失败，尝试 API Key 认证
                String apiKey = extractApiKeyFromRequest(request);
                if (StringUtils.hasText(apiKey)) {
                    OpenApiKeyVO apiKeyVO = openApiKeyService.authenticateByKey(apiKey);
                    if (apiKeyVO != null) {
                        // 设置UserContext
                        UserContext.setUserId(apiKeyVO.getUserId());
                        UserContext.setUsername("api-key-user-" + apiKeyVO.getUserId());
                        UserContext.setApiKeyId(apiKeyVO.getId());
                        UserContext.setDefaultIngestStatus(apiKeyVO.getDefaultIngestStatus());

                        // 设置SecurityContext
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                apiKeyVO.getUserId(),
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("API Key认证成功: userId={}, apiKeyId={}, defaultIngestStatus={}", 
                                apiKeyVO.getUserId(), apiKeyVO.getId(), apiKeyVO.getDefaultIngestStatus());
                    }
                }
            }
        } catch (Exception e) {
            log.error("认证失败: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            UserContext.clear();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理ThreadLocal，防止内存泄漏
            UserContext.clear();
        }
    }

    /**
     * 从请求中提取JWT
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(Constants.TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(Constants.TOKEN_PREFIX)) {
            return bearerToken.substring(Constants.TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 从请求中提取API Key
     */
    private String extractApiKeyFromRequest(HttpServletRequest request) {
        // 优先使用 X-API-Key 请求头
        String apiKey = request.getHeader(Constants.API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        // 也支持 Authorization: Bearer <api-key> 形式（当 api-key 不是 JWT 格式时）
        String authHeader = request.getHeader(Constants.TOKEN_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(Constants.TOKEN_PREFIX)) {
            String token = authHeader.substring(Constants.TOKEN_PREFIX.length());
            // 如果不是 JWT 格式（没有两个点），视为 API Key
            if (token.chars().filter(ch -> ch == '.').count() != 2) {
                return token;
            }
        }
        // 也支持查询参数
        apiKey = request.getParameter(Constants.API_KEY_PARAM);
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return null;
    }

    /**
     * 验证Token
     */
    private boolean validateToken(String token) {
        try {
            String username = jwtUtils.extractUsername(token);
            return !jwtUtils.isTokenExpired(token);
        } catch (Exception e) {
            log.warn("Token验证失败: {}", e.getMessage());
            return false;
        }
    }
}
