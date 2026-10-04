package io.github.argod2213.policyrag.rag;

import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerServiceVerifyTest {

    private final List<RetrievedChunk> chunks = List.of(
            new RetrievedChunk("S1", "d1", "Remote Work Policy", Audience.EMPLOYEE, 0, "Up to 3 days per week.", 0.9),
            new RetrievedChunk("S2", "d2", "Leave Policy", Audience.EMPLOYEE, 1, "22 days of PTO.", 0.7));

    private final AnswerService service = newService();

    @Test
    void keepsValidCitationsAndDropsHallucinatedOnes() {
        var llm = new LlmAnswer("You can work remotely 3 days [S1][S9].", List.of("S1", "S9", " S1 "), true,
                LlmAnswer.Confidence.HIGH);

        AnswerResponse response = service.verify("q", llm, chunks);

        assertThat(response.grounded()).isTrue();
        assertThat(response.citations()).extracting(AnswerResponse.Citation::sourceId).containsExactly("S1");
        assertThat(response.citations().getFirst().title()).isEqualTo("Remote Work Policy");
        assertThat(response.confidence()).isEqualTo(LlmAnswer.Confidence.HIGH);
    }

    @Test
    void answerWithoutValidCitationIsNotTrusted() {
        var llm = new LlmAnswer("Made up answer.", List.of("S7"), true, LlmAnswer.Confidence.HIGH);

        AnswerResponse response = service.verify("q", llm, chunks);

        assertThat(response.grounded()).isFalse();
        assertThat(response.answer()).isEqualTo(AnswerService.NO_CONTEXT_ANSWER);
        assertThat(response.confidence()).isEqualTo(LlmAnswer.Confidence.LOW);
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void modelAbstentionIsPassedThroughAsUngrounded() {
        var llm = new LlmAnswer("Not covered.", List.of(), false, LlmAnswer.Confidence.MEDIUM);

        AnswerResponse response = service.verify("q", llm, chunks);

        assertThat(response.grounded()).isFalse();
        assertThat(response.answer()).isEqualTo("Not covered.");
        assertThat(response.confidence()).isEqualTo(LlmAnswer.Confidence.LOW);
    }

    @Test
    void nullModelOutputIsHandled() {
        assertThat(service.verify("q", null, chunks).grounded()).isFalse();
    }

    private static AnswerService newService() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        AppProperties props = new AppProperties(
                new AppProperties.Security("x".repeat(32), "test", Duration.ofHours(1), List.of()),
                new AppProperties.Rag(5, 10, 0.3, 600),
                new AppProperties.Ingestion(350, 200, 10),
                new AppProperties.Seed(false, "classpath:seed/*.md"));
        return new AnswerService(mock(RetrievalService.class), new PromptFactory(), builder, props);
    }
}
