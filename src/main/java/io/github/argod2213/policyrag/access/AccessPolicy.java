package io.github.argod2213.policyrag.access;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single place that decides which documents a caller may see.
 *
 * <p>The rule: everyone sees {@link Audience#PUBLIC}; a role named after an audience
 * (e.g. {@code HR}) unlocks that audience; {@code ADMIN} sees everything. The resulting
 * set is pushed down into the vector search as a metadata filter, and re-checked on the
 * results as defense in depth.
 */
@Component
public class AccessPolicy {

    public static final String AUDIENCE_KEY = "audience";
    public static final String ADMIN_ROLE = "ADMIN";

    public Set<Audience> visibleAudiences(Collection<String> roles) {
        Set<Audience> visible = EnumSet.of(Audience.PUBLIC);
        for (String role : roles) {
            String normalized = stripRolePrefix(role).toUpperCase(Locale.ROOT);
            if (ADMIN_ROLE.equals(normalized)) {
                return EnumSet.allOf(Audience.class);
            }
            Audience.parse(normalized).ifPresent(visible::add);
        }
        return visible;
    }

    /** Builds the pgvector metadata filter: {@code audience IN [...]}. */
    public Filter.Expression filterFor(Set<Audience> visible) {
        if (visible.isEmpty()) {
            throw new IllegalArgumentException("visible audiences must not be empty");
        }
        List<Object> values = visible.stream().map(Audience::name).map(Object.class::cast).toList();
        return new FilterExpressionBuilder().in(AUDIENCE_KEY, values).build();
    }

    /** Post-retrieval check so a misconfigured filter can never leak a restricted chunk. */
    public boolean canSee(Document chunk, Set<Audience> visible) {
        Object raw = chunk.getMetadata().get(AUDIENCE_KEY);
        return raw != null && Audience.parse(raw.toString()).map(visible::contains).orElse(false);
    }

    private static String stripRolePrefix(String role) {
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }
}
