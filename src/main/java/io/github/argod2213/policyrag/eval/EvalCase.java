package io.github.argod2213.policyrag.eval;

import java.util.List;

/**
 * One labelled evaluation example.
 *
 * @param roles            roles of the simulated caller
 * @param expectedTitle    document that should be retrieved and cited, or {@code null} when the
 *                         correct behaviour is to abstain
 * @param expectedKeywords facts (case-insensitive) that a good answer should mention
 * @param answerable       whether the caller should get a grounded answer
 */
public record EvalCase(String id,
                       String question,
                       List<String> roles,
                       String expectedTitle,
                       List<String> expectedKeywords,
                       boolean answerable) {

    public List<String> roles() {
        return roles == null ? List.of() : roles;
    }

    public List<String> expectedKeywords() {
        return expectedKeywords == null ? List.of() : expectedKeywords;
    }
}
