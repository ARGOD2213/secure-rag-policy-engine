package io.github.argod2213.policyrag.document;

import io.github.argod2213.policyrag.access.AccessPolicy;
import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ingestion pipeline: extract text -> normalize -> token-aware chunking -> embed + store in pgvector.
 *
 * <p>Every chunk is stamped with {@code document_id}, {@code title}, {@code audience} and
 * {@code chunk_index} metadata so retrieval can filter by audience and answers can cite sources.
 */
@Service
public class DocumentIngestionService {

    public static final String DOCUMENT_ID_KEY = "document_id";
    public static final String TITLE_KEY = "title";
    public static final String CHUNK_INDEX_KEY = "chunk_index";
    public static final String SOURCE_KEY = "source";

    private static final Set<String> PLAIN_TEXT_TYPES = Set.of("text/plain", "text/markdown", "text/x-markdown");
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final PolicyDocumentRepository repository;
    private final TokenTextSplitter splitter;

    public DocumentIngestionService(VectorStore vectorStore, PolicyDocumentRepository repository, AppProperties properties) {
        this.vectorStore = vectorStore;
        this.repository = repository;
        AppProperties.Ingestion cfg = properties.ingestion();
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(cfg.chunkSizeTokens())
                .withMinChunkSizeChars(cfg.minChunkSizeChars())
                .withMinChunkLengthToEmbed(cfg.minChunkLengthToEmbed())
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }

    @Transactional
    public PolicyDocument ingest(IngestCommand command) {
        String sha = sha256(command.content());
        repository.findBySha256(sha).ifPresent(existing -> {
            throw new DuplicateDocumentException(existing.id());
        });

        String text = normalize(extractText(command));
        if (text.isBlank()) {
            throw new IllegalArgumentException("Document contains no extractable text");
        }

        UUID documentId = UUID.randomUUID();
        List<Document> chunks = chunk(documentId, command, text);
        vectorStore.add(chunks);

        PolicyDocument doc = new PolicyDocument(documentId, command.title(), command.audience(), command.sourceName(),
                command.contentType(), sha, chunks.size(), command.createdBy(), Instant.now());
        repository.insert(doc);
        log.info("Ingested '{}' ({}) as {} chunks for audience {}", doc.title(), documentId, chunks.size(), doc.audience());
        return doc;
    }

    @Transactional
    public void delete(UUID documentId) {
        PolicyDocument doc = repository.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
        vectorStore.delete(new FilterExpressionBuilder().eq(DOCUMENT_ID_KEY, doc.id().toString()).build());
        repository.deleteById(doc.id());
    }

    List<Document> chunk(UUID documentId, IngestCommand command, String text) {
        List<Document> pieces = splitter.apply(List.of(new Document(text)));
        List<Document> chunks = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            chunks.add(new Document(pieces.get(i).getText(), Map.of(
                    DOCUMENT_ID_KEY, documentId.toString(),
                    TITLE_KEY, command.title(),
                    AccessPolicy.AUDIENCE_KEY, command.audience().name(),
                    CHUNK_INDEX_KEY, i,
                    SOURCE_KEY, command.sourceName())));
        }
        return chunks;
    }

    private String extractText(IngestCommand command) {
        String type = command.contentType() == null ? "" : command.contentType().toLowerCase();
        if (PLAIN_TEXT_TYPES.contains(type) || command.sourceName().endsWith(".md") || command.sourceName().endsWith(".txt")) {
            return new String(command.content(), StandardCharsets.UTF_8);
        }
        // PDF, DOCX, HTML, ... via Apache Tika
        var resource = new ByteArrayResource(command.content()) {
            @Override
            public String getFilename() {
                return command.sourceName();
            }
        };
        StringBuilder sb = new StringBuilder();
        new TikaDocumentReader(resource).get().forEach(d -> sb.append(d.getText()).append('\n'));
        return sb.toString();
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record IngestCommand(String title, Audience audience, String sourceName, String contentType,
                                byte[] content, String createdBy) {
    }
}
