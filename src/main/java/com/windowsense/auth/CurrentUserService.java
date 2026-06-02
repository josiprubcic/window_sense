package com.windowsense.auth;

import com.windowsense.common.ForbiddenException;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.user.AppUser;
import com.windowsense.user.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private static final String LOCAL_AUTH0_SUB = "local-dev-user";
    private static final String LOCAL_EMAIL = "local-dev-user@windowsense.local";
    private static final String LOCAL_DISPLAY_NAME = "Local Dev User";

    private final AppUserRepository appUserRepository;
    private final WindowSenseProperties properties;

    public CurrentUserService(AppUserRepository appUserRepository, WindowSenseProperties properties) {
        this.appUserRepository = appUserRepository;
        this.properties = properties;
    }

    @Transactional
    public AppUser getOrCreateCurrentUser() {
        CurrentUserProfile profile = currentProfile();
        return appUserRepository.findByAuth0Sub(profile.auth0Sub())
                .map(existing -> updateProfile(existing, profile))
                .orElseGet(() -> appUserRepository.save(new AppUser(
                        profile.auth0Sub(),
                        profile.email(),
                        profile.displayName()
                )));
    }

    private AppUser updateProfile(AppUser user, CurrentUserProfile profile) {
        if (!user.getEmail().equals(profile.email()) || !user.getDisplayName().equals(profile.displayName())) {
            user.updateProfile(profile.email(), profile.displayName());
        }

        return user;
    }

    private CurrentUserProfile currentProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            if (properties.getSecurity().getOidc().isEnabled()) {
                throw new ForbiddenException("Korisnik mora biti prijavljen.");
            }

            return new CurrentUserProfile(LOCAL_AUTH0_SUB, LOCAL_EMAIL, LOCAL_DISPLAY_NAME);
        }

        if (authentication.getPrincipal() instanceof OidcUser user) {
            String email = firstPresent(user.getEmail(), user.getSubject() + "@auth0.local");
            String displayName = firstPresent(user.getFullName(), firstPresent(user.getName(), email));
            return new CurrentUserProfile(user.getSubject(), email, displayName);
        }

        String name = authentication.getName();
        String email = name.contains("@") ? name : name + "@windowsense.local";
        return new CurrentUserProfile(name, email, name);
    }

    private static String firstPresent(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private record CurrentUserProfile(String auth0Sub, String email, String displayName) {
    }
}
