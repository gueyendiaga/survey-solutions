package com.surveysolutions.analytics.config;

import java.util.List;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SurveySolutionsApiAuthenticationProvider implements AuthenticationProvider {
    private final SurveySolutionsService surveySolutions;

    public SurveySolutionsApiAuthenticationProvider(SurveySolutionsService surveySolutions) {
        this.surveySolutions = surveySolutions;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object suppliedPassword = authentication.getCredentials();
        if (!StringUtils.hasText(username) || !(suppliedPassword instanceof String password)
                || !StringUtils.hasText(password)) {
            throw new BadCredentialsException("Identifiants HQ invalides.");
        }

        boolean validApiUser;
        try {
            validApiUser = surveySolutions.isApiUser(username.trim(), password);
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException("Survey Solutions HQ est indisponible.", exception);
        }
        if (!validApiUser) {
            throw new BadCredentialsException("Identifiants HQ invalides ou compte sans rôle API.");
        }

        return UsernamePasswordAuthenticationToken.authenticated(
                new HqApiUserPrincipal(username.trim(), password),
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_ANALYTICS_USER"),
                        new SimpleGrantedAuthority("ROLE_HQ_API_USER")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
