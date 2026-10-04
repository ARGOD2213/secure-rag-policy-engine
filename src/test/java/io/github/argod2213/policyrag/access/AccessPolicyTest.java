package io.github.argod2213.policyrag.access;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessPolicyTest {

    private final AccessPolicy policy = new AccessPolicy();

    @Test
    void everyoneSeesPublic() {
        assertThat(policy.visibleAudiences(List.of())).containsExactly(Audience.PUBLIC);
    }

    @Test
    void rolesUnlockMatchingAudiences() {
        assertThat(policy.visibleAudiences(List.of("ROLE_EMPLOYEE", "ROLE_HR")))
                .containsExactlyInAnyOrder(Audience.PUBLIC, Audience.EMPLOYEE, Audience.HR);
    }

    @Test
    void unknownRolesAreIgnored() {
        assertThat(policy.visibleAudiences(List.of("ROLE_EMPLOYEE", "SCOPE_read", "ROLE_SUPERUSER")))
                .containsExactlyInAnyOrder(Audience.PUBLIC, Audience.EMPLOYEE);
    }

    @Test
    void adminSeesEverything() {
        assertThat(policy.visibleAudiences(List.of("ROLE_ADMIN"))).isEqualTo(EnumSet.allOf(Audience.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsInFilterOnAudienceMetadata() {
        Filter.Expression expr = policy.filterFor(EnumSet.of(Audience.PUBLIC, Audience.HR));
        assertThat(expr.type()).isEqualTo(Filter.ExpressionType.IN);
        assertThat(((Filter.Key) expr.left()).key()).isEqualTo(AccessPolicy.AUDIENCE_KEY);
        assertThat((List<Object>) ((Filter.Value) expr.right()).value()).containsExactlyInAnyOrder("PUBLIC", "HR");
    }

    @Test
    void rejectsEmptyAudienceSet() {
        assertThatThrownBy(() -> policy.filterFor(Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canSeeFailsClosed() {
        Set<Audience> employee = EnumSet.of(Audience.PUBLIC, Audience.EMPLOYEE);
        assertThat(policy.canSee(new Document("x", Map.of("audience", "EMPLOYEE")), employee)).isTrue();
        assertThat(policy.canSee(new Document("x", Map.of("audience", "HR")), employee)).isFalse();
        assertThat(policy.canSee(new Document("x", Map.of("audience", "bogus")), employee)).isFalse();
        assertThat(policy.canSee(new Document("x", Map.of()), employee)).isFalse();
    }
}
