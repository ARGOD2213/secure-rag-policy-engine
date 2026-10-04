package io.github.argod2213.policyrag.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL + pgvector in Docker, plus deterministic offline AI models. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    HashingEmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    ExtractiveChatModel chatModel() {
        return new ExtractiveChatModel();
    }
}
