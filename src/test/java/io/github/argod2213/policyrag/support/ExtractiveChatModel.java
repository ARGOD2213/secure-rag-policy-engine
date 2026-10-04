package io.github.argod2213.policyrag.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.argod2213.policyrag.rag.LlmAnswer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic stand-in for an LLM. It reads the sources from the grounding prompt, picks the
 * sentence with the highest term overlap with the question and returns it as structured JSON.
 * If overlap is too low it abstains. This lets the full RAG pipeline (prompt -> structured output
 * parsing -> citation verification) run in CI without network access.
 */
public class ExtractiveChatModel implements ChatModel {

    private static final Pattern SOURCE = Pattern.compile(
            "<source id=\"(S\\d+)\" title=\"[^\"]*\">\\n(.*?)\\n</source>", Pattern.DOTALL);
    private static final Pattern QUESTION = Pattern.compile("Question: (.*)");

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();

    public int calls() {
        return calls.get();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        calls.incrementAndGet();
        String user = prompt.getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .reduce("", (a, b) -> a + "\n" + b);

        Matcher q = QUESTION.matcher(user);
        String question = q.find() ? q.group(1).strip() : "";
        Set<String> questionTerms = new HashSet<>(TextFeatures.tokens(question));

        String bestSource = null;
        String bestSentence = null;
        int bestOverlap = 0;
        Matcher s = SOURCE.matcher(user);
        while (s.find()) {
            for (String sentence : s.group(2).split("(?<=[.!?])\\s+|\\n+")) {
                Set<String> terms = new HashSet<>(TextFeatures.tokens(sentence));
                terms.retainAll(questionTerms);
                if (terms.size() > bestOverlap) {
                    bestOverlap = terms.size();
                    bestSource = s.group(1);
                    bestSentence = sentence.strip();
                }
            }
        }

        boolean answerable = bestSentence != null && bestOverlap >= 2
                && bestOverlap >= Math.ceil(questionTerms.size() / 2.0);
        LlmAnswer answer = answerable
                ? new LlmAnswer(bestSentence + " [" + bestSource + "]", List.of(bestSource), true, LlmAnswer.Confidence.HIGH)
                : new LlmAnswer("The documents available to you do not cover this question.", List.of(), false,
                LlmAnswer.Confidence.LOW);
        try {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(mapper.writeValueAsString(answer)))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
