package io.github.argod2213.policyrag.rag;

import io.github.argod2213.policyrag.access.Audience;

/**
 * A chunk returned by semantic search, labelled with a short source id ({@code S1}, {@code S2}, ...)
 * that the model uses to cite it.
 */
public record RetrievedChunk(String sourceId,
                             String documentId,
                             String title,
                             Audience audience,
                             int chunkIndex,
                             String text,
                             double score) {
}
