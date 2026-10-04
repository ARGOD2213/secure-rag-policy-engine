package io.github.argod2213.policyrag.rag;

import io.github.argod2213.policyrag.access.AccessPolicy;
import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.config.AppProperties;
import io.github.argod2213.policyrag.document.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Role-aware semantic retrieval. The audience filter is applied inside the pgvector query, so
 * restricted chunks are never even returned to the application, let alone sent to the LLM.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final AccessPolicy accessPolicy;
    private final AppProperties.Rag config;

    public RetrievalService(VectorStore vectorStore, AccessPolicy accessPolicy, AppProperties properties) {
        this.vectorStore = vectorStore;
        this.accessPolicy = accessPolicy;
        this.config = properties.rag();
    }

    public List<RetrievedChunk> retrieve(String question, Integer requestedTopK, Set<Audience> visible) {
        int topK = Math.min(requestedTopK == null ? config.topK() : requestedTopK, config.maxTopK());
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(config.similarityThreshold())
                .filterExpression(accessPolicy.filterFor(visible))
                .build();

        List<Document> hits = vectorStore.similaritySearch(request);
        List<RetrievedChunk> chunks = new ArrayList<>(hits.size());
        for (Document hit : hits) {
            if (!accessPolicy.canSee(hit, visible)) {
                // Should be impossible given the pushed-down filter; fail closed and make it loud.
                log.error("Access filter bypass detected for chunk {} - dropping it", hit.getId());
                continue;
            }
            var meta = hit.getMetadata();
            chunks.add(new RetrievedChunk(
                    "S" + (chunks.size() + 1),
                    String.valueOf(meta.get(DocumentIngestionService.DOCUMENT_ID_KEY)),
                    String.valueOf(meta.get(DocumentIngestionService.TITLE_KEY)),
                    Audience.valueOf(String.valueOf(meta.get(AccessPolicy.AUDIENCE_KEY))),
                    toInt(meta.get(DocumentIngestionService.CHUNK_INDEX_KEY)),
                    hit.getText(),
                    hit.getScore() == null ? 0.0 : hit.getScore()));
        }
        return chunks;
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
