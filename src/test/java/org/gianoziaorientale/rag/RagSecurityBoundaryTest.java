package org.gianoziaorientale.rag;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.gianoziaorientale.agent.AgenteSmith;
import org.gianoziaorientale.agent.App;
import org.gianoziaorientale.agent.ModuliAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@WebMvcTest(
        controllers = {
            RagController.class,
            RagModelConfigurationController.class,
            RagSecurityBoundaryTest.ProbeController.class
        },
        properties = {
            "agentsmith.rag.enabled=true",
            "agentsmith.rag.elasticsearch-url=https://elasticsearch.example",
            "agentsmith.rag.elasticsearch-read-api-key=test-read-key",
            "agentsmith.rag.answer-model-id=gemini:approved-model",
            "agentsmith.rag.embedding-model=test-embedding",
            "agentsmith.rag.embedding-dimensions=768",
            "agentsmith.security.rag.enabled=true",
            "agentsmith.security.rag.operator-username=rag-operator",
        })
@org.springframework.test.context.ContextConfiguration(classes = App.class)
@Import({
    RagController.class,
    RagModelConfigurationController.class,
    RagSecurityConfiguration.class,
    RagSecurityBoundaryTest.ProbeController.class
})
class RagSecurityBoundaryTest {

    @DynamicPropertySource
    static void passwordHashes(DynamicPropertyRegistry registry) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        registry.add("agentsmith.security.rag.operator-password-hash",
                () -> "{bcrypt}" + encoder.encode("operator-test-password"));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgenteSmith agenteSmith;

    @MockitoBean
    private ModuliAiService moduliAiService;

    @MockitoBean
    private RagService ragService;

    @MockitoBean
    private RagModelConfigurationService configurationService;

    @BeforeEach
    void setUpLegacyEndpointMocks() {
        when(moduliAiService.moduliDisponibili()).thenReturn(List.of());
        when(moduliAiService.spiega(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new ModuliAiService.Risposta("test", 0));
    }

    @Test
    void allowsAnonymousRagReadRequestsAndReturnsOnlyAnswerAndSourceReferences() throws Exception {
        when(ragService.query("question")).thenReturn(new RagService.RagResponse(
                "answer", List.of(new RagService.SourceReference("doc-1", "docs/guide.md", 2, 4))));

        mockMvc.perform(post("/api/rag/query")
                        .contentType("application/json")
                        .content("""
                                {"question":"question"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("answer"))
                .andExpect(jsonPath("$.sources[0].sourceId").value("doc-1"))
                .andExpect(jsonPath("$.sources[0].path").value("docs/guide.md"))
                .andExpect(jsonPath("$.sources[0].startLine").value(2))
                .andExpect(jsonPath("$.sources[0].endLine").value(4))
                .andExpect(jsonPath("$.sources[0].text").doesNotExist());
    }

    @Test
    void reportsInvalidRagQuestionsAsBadRequests() throws Exception {
        when(ragService.query("")).thenThrow(new IllegalArgumentException("Invalid question."));

        mockMvc.perform(post("/api/rag/query")
                        .contentType("application/json")
                        .content("""
                                {"question":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La richiesta RAG non è valida."));
    }

    @Test
    void rejectsUnauthenticatedRagConfigurationWrites() throws Exception {
        mockMvc.perform(post("/api/rag/config")
                        .contentType("application/json")
                        .content("""
                                {"answerModelId":"gemini:approved-model"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsOperatorConfigurationRequests() throws Exception {
        mockMvc.perform(post("/api/rag/config")
                        .contentType("application/json")
                        .content("""
                                {"answerModelId":"gemini:approved-model"}
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                                "rag-operator", "operator-test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerModelId").value("gemini:approved-model"));
        verify(configurationService).selectAnswerModel("gemini:approved-model");
    }

    @Test
    void restrictsActuatorMetricsToRagOperators() throws Exception {
        mockMvc.perform(get("/actuator/metrics/test"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics/test")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(
                                "rag-operator", "operator-test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void preservesUnauthenticatedLegacyRoutes() throws Exception {
        mockMvc.perform(get("/moduli"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/descrivi")
                        .contentType("application/json")
                        .content("""
                                {"linguaggio":"Java","code":"class Test {}","modello":""}
                                """))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/actuator/metrics/test")
        String metrics() {
            return "metrics";
        }

    }
}
