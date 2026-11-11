package com.surveysolutions.analytics.surveysolutions;

import java.util.function.Supplier;
import com.surveysolutions.analytics.config.HqApiUserPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class HqCredentialContext {
    private static final ThreadLocal<HqApiUserPrincipal> CURRENT = new ThreadLocal<>();

    private HqCredentialContext() {}

    public static HqApiUserPrincipal current() {
        HqApiUserPrincipal principal = CURRENT.get();
        if (principal != null) {
            return principal;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HqApiUserPrincipal hqPrincipal) {
            return hqPrincipal;
        }
        throw new AuthenticationCredentialsNotFoundException("Une session HQ authentifiée est requise.");
    }

    public static <T> T with(HqApiUserPrincipal principal, Supplier<T> action) {
        HqApiUserPrincipal previous = CURRENT.get();
        CURRENT.set(principal);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void with(HqApiUserPrincipal principal, Runnable action) {
        with(principal, () -> {
            action.run();
            return null;
        });
    }
}
