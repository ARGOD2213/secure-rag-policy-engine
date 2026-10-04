package io.github.argod2213.policyrag.rag;

import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.config.AppProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Retrieve -> generate (structured output) -> verify citations.
 */
@Service
public class AnswerService {

    static final String NO_CONTEXT_ANSWER =
            "I couldn't find this in the policy documents you have access to.";

    private final RetrievalService retrieval;
    private final PromptFactory prompts;
    private final ChatClient chatClient;
    private final AppProperties.Rag config;

    public AnswerService(RetrievalService retrieval, PromptFactory prompts, ChatClient.Builder chatClientBuilder,
                         AppProperties properties) {
        this.retrieval = retrieval;
        this.prompts = prompts;
        this.chatClient = chatClientBuilder.build();
        this.config = properties.rag();
    }

    public AnswerResponse answer(String question, Integer topK, Set<Audience> visible) {
        return answerWithContext(question, topK, visible).response();
    }

    /** Same as {@link #answer} but also exposes the retrieved chunks (used by the evaluator). */
    public Result answerWithContext(String question, Integer topK, Set<Audience> visible) {
        List<RetrievedChunk> chunks = retrieval.retrieve(question, topK, visible);
        if (chunks.isEmpty()) {
            // Nothing the caller may see is relevant: abstain without calling the model.
            return new Result(new AnswerResponse(question, NO_CONTEXT_ANSWER, false, LlmAnswer.Confidence.LOW,
                    List.of(), 0), chunks);
        }

        LlmAnswer llm = chatClient.prompt()
                .system(prompts.systemPrompt())
                .user(prompts.userPrompt(question, chunks))
                .call()
                .entity(LlmAnswer.class);

        return new Result(verify(question, llm, chunks), chunks);
    }

    /**
     * Drops citations that don't refer to a retrieved chunk and downgrades answers that claim to be
     * answerable but cite nothing valid.
     */
    AnswerResponse verify(String question, LlmAnswer llm, List<RetrievedChunk> chunks) {
        if (llm == null || llm.answer() == null) {
            return new AnswerResponse(question, NO_CONTEXT_ANSWER, false, LlmAnswer.Confidence.LOW, List.of(), chunks.size());
        }
        Map<String, RetrievedChunk> byId = chunks.stream()
                .collect(Collectors.toMap(RetrievedChunk::sourceId, Function.identity()));

        Set<String> cited = new LinkedHashSet<>();
        if (llm.citedSourceIds() != null) {
            llm.citedSourceIds().stream().map(String::strip).filter(byId::containsKey).forEach(cited::add);
        }

        List<AnswerResponse.Citation> citations = cited.stream()
                .map(byId::get)
                .map(c -> new AnswerResponse.Citation(c.sourceId(), c.documentId(), c.title(), c.chunkIndex(),
                        c.score(), snippet(c.text())))
                .toList();

        boolean grounded = llm.answerable() && !citations.isEmpty();
        LlmAnswer.Confidence confidence = llm.confidence() == null ? LlmAnswer.Confidence.LOW : llm.confidence();
        if (!grounded) {
            confidence = LlmAnswer.Confidence.LOW;
        }
        String answer = llm.answerable() && citations.isEmpty() ? NO_CONTEXT_ANSWER : llm.answer();
        return new AnswerResponse(question, answer, grounded, confidence, citations, chunks.size());
    }

    private String snippet(String text) {
        String clean = text.strip().replaceAll("\\s+", " ");
        return clean.length() <= config.maxSnippetChars() ? clean : clean.substring(0, config.maxSnippetChars()) + "...";
    }

    public record Result(AnswerResponse response, List<RetrievedChunk> retrieved) {
    }
}
