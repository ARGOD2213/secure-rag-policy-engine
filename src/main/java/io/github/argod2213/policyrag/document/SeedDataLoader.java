package io.github.argod2213.policyrag.document;

import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the bundled sample policies on first start so the API is demoable immediately.
 * Each seed file starts with a tiny front-matter block:
 * <pre>
 * ---
 * title: Remote Work Policy
 * audience: EMPLOYEE
 * ---
 * </pre>
 */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final AppProperties.Seed config;
    private final PolicyDocumentRepository repository;
    private final DocumentIngestionService ingestion;

    public SeedDataLoader(AppProperties properties, PolicyDocumentRepository repository, DocumentIngestionService ingestion) {
        this.config = properties.seed();
        this.repository = repository;
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!config.enabled() || repository.count() > 0) {
            return;
        }
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(config.location());
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        int seeded = 0;
        for (Resource resource : resources) {
            SeedFile seed = parse(resource.getContentAsString(StandardCharsets.UTF_8));
            try {
                ingestion.ingest(new DocumentIngestionService.IngestCommand(seed.title(), seed.audience(),
                        resource.getFilename(), "text/markdown", seed.body().getBytes(StandardCharsets.UTF_8), "seed"));
                seeded++;
            } catch (RuntimeException e) {
                // Keep the API up (health, auth, docs) even if the embedding provider is misconfigured;
                // seeding is retried on the next start because nothing was committed.
                log.error("SEEDING FAILED for '{}' - check the embedding provider settings "
                        + "(OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_EMBEDDING_MODEL / EMBEDDING_DIMENSIONS). Cause: {}",
                        seed.title(), rootCauseMessage(e));
                return;
            }
        }
        log.info("Seeded {} sample policy documents", seeded);
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    static SeedFile parse(String raw) {
        String text = raw.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new IllegalArgumentException("Seed file is missing front matter");
        }
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException("Seed file front matter is not terminated");
        }
        Map<String, String> meta = new HashMap<>();
        for (String line : text.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                meta.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        Audience audience = Audience.parse(meta.get("audience"))
                .orElseThrow(() -> new IllegalArgumentException("Seed file has invalid audience: " + meta.get("audience")));
        String title = meta.get("title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Seed file is missing a title");
        }
        return new SeedFile(title, audience, text.substring(end + 5).strip());
    }

    record SeedFile(String title, Audience audience, String body) {
    }
}
