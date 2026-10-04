package io.github.argod2213.policyrag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Typed application configuration bound from the {@code app.*} namespace.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull Security security,
                            @Valid @NotNull Rag rag,
                            @Valid @NotNull Ingestion ingestion,
                            @Valid @NotNull Seed seed) {

    public record Security(
            @NotBlank @Size(min = 32, message = "app.security.jwt-secret must be at least 32 characters (256 bits)")
            String jwtSecret,
            @NotBlank String issuer,
            @NotNull Duration tokenTtl,
            @Valid List<DemoUser> users) {

        public List<DemoUser> users() {
            return users == null ? List.of() : users;
        }
    }

    /** A bootstrap user. Real deployments should replace this with an identity provider. */
    public record DemoUser(@NotBlank String username, @NotBlank String password, List<String> roles) {
        public List<String> roles() {
            return roles == null ? List.of() : roles;
        }
    }

    public record Rag(@Min(1) @DefaultValue("5") int topK,
                      @Min(1) @DefaultValue("10") int maxTopK,
                      @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.3") double similarityThreshold,
                      @Min(50) @DefaultValue("600") int maxSnippetChars) {
    }

    public record Ingestion(@Min(50) @DefaultValue("350") int chunkSizeTokens,
                            @Min(1) @DefaultValue("200") int minChunkSizeChars,
                            @Min(1) @DefaultValue("10") int minChunkLengthToEmbed) {
    }

    public record Seed(@DefaultValue("true") boolean enabled,
                       @DefaultValue("classpath:seed/*.md") String location) {
    }
}
