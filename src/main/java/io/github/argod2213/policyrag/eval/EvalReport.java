package io.github.argod2213.policyrag.eval;

import java.util.List;

public record EvalReport(int cases,
                         double retrievalHitRate,
                         double citationAccuracy,
                         double keywordCoverage,
                         double abstentionAccuracy,
                         int accessViolations,
                         boolean passed,
                         List<CaseResult> results) {

    public record CaseResult(String id,
                             String question,
                             List<String> roles,
                             boolean retrievalHit,
                             boolean citationHit,
                             double keywordCoverage,
                             boolean abstentionCorrect,
                             int accessViolations,
                             boolean grounded,
                             String answer) {
    }
}
