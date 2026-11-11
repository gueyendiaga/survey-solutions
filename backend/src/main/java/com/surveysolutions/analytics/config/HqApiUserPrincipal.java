package com.surveysolutions.analytics.config;

import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

public final class HqApiUserPrincipal implements AuthenticatedPrincipal, Serializable {
    private final String username;
    private final String password;

    public HqApiUserPrincipal(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public String getName() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public String toString() {
        return username;
    }
}
