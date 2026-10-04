package io.github.argod2213.policyrag.rag;

import java.util.List;

/** API response for {@code POST /api/ask}. */
public record AnswerResponse(String question,
                             String answer,
                             boolean grounded,
                             LlmAnswer.Confidence confidence,
                             List<Citation> citations,
                             int retrievedChunks) {

    public record Citation(String sourceId,
                           String documentId,
                           String title,
                           int chunkIndex,
                           double score,
                           String snippet) {
    }
}
