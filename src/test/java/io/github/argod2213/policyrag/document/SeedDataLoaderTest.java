package io.github.argod2213.policyrag.document;

import io.github.argod2213.policyrag.access.Audience;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedDataLoaderTest {

    @Test
    void parsesFrontMatter() {
        var seed = SeedDataLoader.parse("---\ntitle: Remote Work Policy\naudience: employee\n---\n# Body\ntext\n");
        assertThat(seed.title()).isEqualTo("Remote Work Policy");
        assertThat(seed.audience()).isEqualTo(Audience.EMPLOYEE);
        assertThat(seed.body()).isEqualTo("# Body\ntext");
    }

    @Test
    void rejectsInvalidAudience() {
        assertThatThrownBy(() -> SeedDataLoader.parse("---\ntitle: X\naudience: everyone\n---\nbody"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesWhitespace() {
        assertThat(DocumentIngestionService.normalize("a\r\nb\t\tc   d\n\n\n\ne")).isEqualTo("a\nb c d\n\ne");
    }
}
