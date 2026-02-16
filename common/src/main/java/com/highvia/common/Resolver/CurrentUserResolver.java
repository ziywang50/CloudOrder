package com.highvia.common.Resolver;

import com.highvia.common.annotation.CurrentUser;
import com.highvia.common.entity.UserInfo;
import com.highvia.common.exception.UnauthorizedException;
import com.highvia.common.security.JwtUtils;
import com.highvia.common.utils.RsaUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.lang.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.security.PublicKey;

@Component
@ConditionalOnClass(HandlerMethodArgumentResolver.class)
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private final PublicKey publicKey;

    public CurrentUserResolver(@Value("${jwt.publicKeyPath}") String publicKeyPath) {
        try {
            this.publicKey = RsaUtils.getPublicKey(publicKeyPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load public key from: " + publicKeyPath, e);
        }
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(UserInfo.class);
    }

    @Override
    @Nullable
    @SuppressWarnings("NullableProblems")
    public Object resolveArgument(MethodParameter parameter,
                                  @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  @Nullable WebDataBinderFactory binderFactory) {

        String token = webRequest.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                return JwtUtils.getInfoFromToken(token, publicKey);
            } catch (Exception e) {
                throw new UnauthorizedException("No valid token found");
            }
        }
        throw new UnauthorizedException("No valid token found");
    }
}
