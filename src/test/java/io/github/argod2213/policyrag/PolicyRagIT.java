package io.github.argod2213.policyrag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.argod2213.policyrag.access.AccessPolicy;
import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.eval.EvalCase;
import io.github.argod2213.policyrag.eval.EvalReport;
import io.github.argod2213.policyrag.eval.EvalService;
import io.github.argod2213.policyrag.rag.RetrievalService;
import io.github.argod2213.policyrag.rag.RetrievedChunk;
import io.github.argod2213.policyrag.support.ExtractiveChatModel;
import io.github.argod2213.policyrag.support.TestInfrastructure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: real PostgreSQL + pgvector (Testcontainers), real Spring Security JWT flow,
 * real Spring AI vector store and ChatClient structured output, with deterministic offline models.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
class PolicyRagIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    EvalService evalService;
    @Autowired
    RetrievalService retrievalService;
    @Autowired
    AccessPolicy accessPolicy;
    @Autowired
    ExtractiveChatModel chatModel;

    // ---- authentication ----------------------------------------------------------------------

    @Test
    void rejectsBadCredentials() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuedTokenCarriesRoles() throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hana\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(node.get("roles")).extracting(JsonNode::asText).containsExactly("EMPLOYEE", "HR");
    }

    // ---- role-based document visibility ------------------------------------------------------

    @Test
    void documentListIsFilteredByRole() throws Exception {
        assertThat(audiencesListedFor("alice")).containsOnly("PUBLIC", "EMPLOYEE");
        assertThat(audiencesListedFor("hana")).containsOnly("PUBLIC", "EMPLOYEE", "HR");
        assertThat(audiencesListedFor("admin")).containsOnly("PUBLIC", "EMPLOYEE", "HR", "FINANCE", "LEGAL");
    }

    @Test
    void retrievalNeverReturnsChunksOutsideCallersAudiences() throws Exception {
        List<List<String>> roleSets = List.of(List.of(), List.of("EMPLOYEE"), List.of("EMPLOYEE", "HR"),
                List.of("EMPLOYEE", "FINANCE"), List.of("LEGAL"));
        for (EvalCase c : evalService.loadCases()) {
            for (List<String> roles : roleSets) {
                Set<Audience> visible = accessPolicy.visibleAudiences(roles);
                List<RetrievedChunk> chunks = retrievalService.retrieve(c.question(), 10, visible);
                assertThat(chunks).as("%s as %s", c.id(), roles)
                        .allSatisfy(ch -> assertThat(visible).contains(ch.audience()));
            }
        }
    }

    // ---- grounded Q&A -----------------------------------------------------------------------

    @Test
    void employeeGetsGroundedAnswerWithCitation() throws Exception {
        JsonNode answer = ask("alice", "How many days per week can I work remotely?");
        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("answer").asText()).contains("3 days");
        assertThat(answer.get("citations")).isNotEmpty();
        assertThat(answer.get("citations").get(0).get("title").asText()).isEqualTo("Remote Work Policy");
        assertThat(answer.get("citations").get(0).get("snippet").asText()).isNotBlank();
    }

    @Test
    void employeeCannotLearnHrOnlyFacts() throws Exception {
        JsonNode answer = ask("alice", "What is the salary band for an L3 Senior Engineer?");
        assertThat(answer.get("grounded").asBoolean()).isFalse();
        assertThat(answer.toString()).doesNotContain("135,000").doesNotContain("Compensation Bands");
    }

    @Test
    void hrUserCanAnswerTheSameQuestion() throws Exception {
        JsonNode answer = ask("hana", "What is the salary band for an L3 Senior Engineer?");
        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("answer").asText()).contains("$135,000");
        assertThat(answer.get("citations").get(0).get("title").asText()).isEqualTo("Compensation Bands and Salary Review");
    }

    @Test
    void validatesQuestion() throws Exception {
        mvc.perform(post("/api/ask").header("Authorization", bearer("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- ingestion --------------------------------------------------------------------------

    @Test
    void onlyAdminsCanIngestAndIngestedDocsAreSearchableByTheirAudience() throws Exception {
        String content = "# Pet Policy\n\nEmployees may bring well-behaved dogs to the Berlin office on Fridays. "
                + "Dogs must stay out of meeting rooms and the kitchen.";
        String payload = json.writeValueAsString(Map.of("title", "Pet Policy", "audience", "EMPLOYEE", "content", content));

        mvc.perform(post("/api/documents/text").header("Authorization", bearer("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/documents/text").header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();

        mvc.perform(post("/api/documents/text").header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());

        JsonNode answer = ask("alice", "Can I bring dogs to the Berlin office?");
        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("citations").get(0).get("documentId").asText()).isEqualTo(id);

        mvc.perform(delete("/api/documents/" + id).header("Authorization", bearer("admin")))
                .andExpect(status().isNoContent());
        assertThat(ask("alice", "Can I bring dogs to the Berlin office?").get("grounded").asBoolean()).isFalse();
    }

    @Test
    void multipartUploadIsChunkedWithAudience() throws Exception {
        var file = new MockMultipartFile("file", "travel-safety.txt", "text/plain",
                "Travel safety: legal staff travelling to high-risk regions must register their itinerary with the legal desk."
                        .getBytes());
        mvc.perform(multipart("/api/documents").file(file).param("title", "Travel Safety").param("audience", "LEGAL")
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audience").value("LEGAL"));

        assertThat(ask("alice", "Who must register their itinerary with the legal desk?").get("grounded").asBoolean())
                .isFalse();
    }

    // ---- evaluation -------------------------------------------------------------------------

    @Test
    void evaluationSetPassesWithZeroAccessViolations() throws Exception {
        EvalReport report = evalService.run();

        assertThat(report.accessViolations()).isZero();
        assertThat(report.retrievalHitRate()).isGreaterThanOrEqualTo(0.9);
        assertThat(report.abstentionAccuracy()).isGreaterThanOrEqualTo(0.9);
        assertThat(report.passed()).isTrue();
    }

    @Test
    void evaluationEndpointIsAdminOnly() throws Exception {
        mvc.perform(post("/api/eval/run").header("Authorization", bearer("alice"))).andExpect(status().isForbidden());
    }

    @Test
    void modelIsNotCalledWhenNothingRelevantIsVisible() throws Exception {
        int before = chatModel.calls();
        JsonNode answer = ask("alice", "zxqv quarterly wombat frobnication");
        assertThat(answer.get("grounded").asBoolean()).isFalse();
        assertThat(answer.get("retrievedChunks").asInt()).isZero();
        assertThat(chatModel.calls()).isEqualTo(before);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private List<String> audiencesListedFor(String user) throws Exception {
        String body = mvc.perform(get("/api/documents").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).findValuesAsText("audience");
    }

    private JsonNode ask(String user, String question) throws Exception {
        String body = mvc.perform(post("/api/ask").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }
}
