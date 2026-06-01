package com.windowsense.api;

import com.windowsense.config.WindowSenseProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AuthController {

    private final WindowSenseProperties properties;

    public AuthController(WindowSenseProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("oidcEnabled", properties.getSecurity().getOidc().isEnabled());

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            response.put("authenticated", false);
            return response;
        }

        response.put("authenticated", true);
        response.put("name", authentication.getName());

        if (authentication.getPrincipal() instanceof OidcUser user) {
            response.put("name", firstPresent(user.getFullName(), user.getName()));
            response.put("email", user.getEmail());
        }

        return response;
    }

    private static String firstPresent(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
