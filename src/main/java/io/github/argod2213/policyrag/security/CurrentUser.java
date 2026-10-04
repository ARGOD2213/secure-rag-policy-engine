package io.github.argod2213.policyrag.security;

import io.github.argod2213.policyrag.access.AccessPolicy;
import io.github.argod2213.policyrag.access.Audience;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Resolves the caller's identity and visible audiences from the authenticated JWT. */
@Component
public class CurrentUser {

    private final AccessPolicy accessPolicy;

    public CurrentUser(AccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    public Set<Audience> visibleAudiences(Authentication authentication) {
        return accessPolicy.visibleAudiences(
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
