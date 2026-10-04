package io.github.argod2213.policyrag.rag;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The structured output schema the model must return. Spring AI derives a JSON schema from this
 * record and parses the model response back into it.
 */
public record LlmAnswer(
        @JsonPropertyDescription("Concise answer to the question, written only from the provided sources. "
                + "Cite sources inline like [S1].")
        String answer,
        @JsonPropertyDescription("Source ids (e.g. S1, S3) that directly support the answer.")
        List<String> citedSourceIds,
        @JsonPropertyDescription("false if the sources do not contain the information needed to answer.")
        boolean answerable,
        @JsonPropertyDescription("Confidence that the answer is fully supported by the cited sources.")
        Confidence confidence) {

    public enum Confidence { HIGH, MEDIUM, LOW }
}
