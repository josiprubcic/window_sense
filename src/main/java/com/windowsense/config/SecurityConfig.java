package com.windowsense.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class SecurityConfig {

    private static final String REGISTRATION_ID = "windowsense";

    @Bean
    @ConditionalOnProperty(name = "windowsense.security.oidc.enabled", havingValue = "true")
    public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, WindowSenseProperties properties) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/styles.css",
                                "/app.js",
                                "/api/health",
                                "/api/me",
                                "/api/device/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/", true))
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.sendRedirect(auth0LogoutUrl(properties, request))))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "windowsense.security.oidc.enabled", havingValue = "true")
    public ClientRegistrationRepository clientRegistrationRepository(WindowSenseProperties properties) {
        WindowSenseProperties.Oidc oidc = properties.getSecurity().getOidc();
        requireConfigured("windowsense.security.oidc.issuer-uri", oidc.getIssuerUri());
        requireConfigured("windowsense.security.oidc.client-id", oidc.getClientId());
        requireConfigured("windowsense.security.oidc.client-secret", oidc.getClientSecret());

        ClientRegistration registration = ClientRegistrations.fromIssuerLocation(oidc.getIssuerUri())
                .registrationId(REGISTRATION_ID)
                .clientId(oidc.getClientId())
                .clientSecret(oidc.getClientSecret())
                .scope(oidc.getScopes())
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();

        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @ConditionalOnProperty(name = "windowsense.security.oidc.enabled", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain localDevelopmentSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    private static void requireConfigured(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " mora biti postavljen kada je OIDC login ukljucen.");
        }
    }

    private static String auth0LogoutUrl(WindowSenseProperties properties, HttpServletRequest request) {
        WindowSenseProperties.Oidc oidc = properties.getSecurity().getOidc();
        String returnTo = UriComponentsBuilder
                .newInstance()
                .scheme(request.getScheme())
                .host(request.getServerName())
                .port(request.getServerPort())
                .path("/")
                .build()
                .toUriString();

        return UriComponentsBuilder
                .fromUriString(oidc.getIssuerUri().replaceAll("/+$", ""))
                .path("/v2/logout")
                .queryParam("returnTo", returnTo)
                .queryParam("client_id", oidc.getClientId())
                .build()
                .toUriString();
    }
}
