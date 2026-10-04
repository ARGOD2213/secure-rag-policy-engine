package io.github.argod2213.policyrag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.argod2213.policyrag.access.AccessPolicy;
import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.rag.AnswerResponse;
import io.github.argod2213.policyrag.rag.AnswerService;
import io.github.argod2213.policyrag.rag.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs the labelled evaluation set end-to-end through retrieval and generation and reports:
 * <ul>
 *   <li><b>retrievalHitRate</b> - expected document appears in the retrieved chunks</li>
 *   <li><b>citationAccuracy</b> - expected document is among the verified citations</li>
 *   <li><b>keywordCoverage</b> - share of expected facts mentioned in the answer</li>
 *   <li><b>abstentionAccuracy</b> - answered when it should, abstained when it should</li>
 *   <li><b>accessViolations</b> - retrieved chunks outside the caller's audiences (must be 0)</li>
 * </ul>
 */
@Service
public class EvalService {

    private final AnswerService answers;
    private final AccessPolicy accessPolicy;
    private final ObjectMapper mapper;
    private final Resource evalSet;
    private final double minRetrievalHitRate;
    private final double minAbstentionAccuracy;

    public EvalService(AnswerService answers, AccessPolicy accessPolicy, ObjectMapper mapper,
                       @Value("${app.eval.dataset:classpath:eval/eval-set.json}") Resource evalSet,
                       @Value("${app.eval.min-retrieval-hit-rate:0.8}") double minRetrievalHitRate,
                       @Value("${app.eval.min-abstention-accuracy:0.8}") double minAbstentionAccuracy) {
        this.answers = answers;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
        this.evalSet = evalSet;
        this.minRetrievalHitRate = minRetrievalHitRate;
        this.minAbstentionAccuracy = minAbstentionAccuracy;
    }

    public List<EvalCase> loadCases() throws IOException {
        try (InputStream in = evalSet.getInputStream()) {
            return mapper.readValue(in, new TypeReference<>() {
            });
        }
    }

    public EvalReport run() throws IOException {
        return run(loadCases());
    }

    public EvalReport run(List<EvalCase> cases) {
        List<EvalReport.CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            results.add(evaluate(c));
        }

        long withExpectedDoc = cases.stream().filter(c -> c.expectedTitle() != null).count();
        double retrievalHitRate = ratio(results.stream().filter(EvalReport.CaseResult::retrievalHit).count(), withExpectedDoc);
        double citationAccuracy = ratio(results.stream().filter(EvalReport.CaseResult::citationHit).count(), withExpectedDoc);
        double keywordCoverage = results.stream().mapToDouble(EvalReport.CaseResult::keywordCoverage).average().orElse(1.0);
        double abstentionAccuracy = ratio(results.stream().filter(EvalReport.CaseResult::abstentionCorrect).count(), results.size());
        int violations = results.stream().mapToInt(EvalReport.CaseResult::accessViolations).sum();

        boolean passed = violations == 0
                && retrievalHitRate >= minRetrievalHitRate
                && abstentionAccuracy >= minAbstentionAccuracy;

        return new EvalReport(cases.size(), round(retrievalHitRate), round(citationAccuracy), round(keywordCoverage),
                round(abstentionAccuracy), violations, passed, results);
    }

    private EvalReport.CaseResult evaluate(EvalCase c) {
        Set<Audience> visible = accessPolicy.visibleAudiences(c.roles());
        AnswerService.Result result = answers.answerWithContext(c.question(), null, visible);
        AnswerResponse response = result.response();
        List<RetrievedChunk> retrieved = result.retrieved();

        int violations = (int) retrieved.stream().filter(ch -> !visible.contains(ch.audience())).count();
        boolean retrievalHit = c.expectedTitle() != null
                && retrieved.stream().anyMatch(ch -> ch.title().equalsIgnoreCase(c.expectedTitle()));
        boolean citationHit = c.expectedTitle() != null
                && response.citations().stream().anyMatch(ci -> ci.title().equalsIgnoreCase(c.expectedTitle()));
        boolean abstentionCorrect = response.grounded() == c.answerable();

        double coverage = 1.0;
        if (c.answerable() && !c.expectedKeywords().isEmpty()) {
            String answer = response.answer().toLowerCase(Locale.ROOT);
            long found = c.expectedKeywords().stream().filter(k -> answer.contains(k.toLowerCase(Locale.ROOT))).count();
            coverage = (double) found / c.expectedKeywords().size();
        }

        return new EvalReport.CaseResult(c.id(), c.question(), c.roles(), retrievalHit, citationHit, round(coverage),
                abstentionCorrect, violations, response.grounded(), response.answer());
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
