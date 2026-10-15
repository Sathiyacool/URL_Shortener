package com.schwab.agentic.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "app.security.issuer-uri=https://issuer.example.test/tenant",
    "app.security.audience=agentic-url-shortener",
    "app.database.url=jdbc:postgresql://db.example.test/agentic",
    "app.database.username=test-user",
    "app.database.password=test-only-password",
    "spring.datasource.url=jdbc:h2:mem:prodsecurity",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProductionSecurityIntegrationTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectsOrchestrationAndAnalyticsAndUsesAuthenticatedApprovalIdentity() throws Exception {
        mockMvc.perform(get("/api/scenarios"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/scenarios").with(operatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/metrics/reliability"))
                .andExpect(status().isUnauthorized());

        MvcResult scenario = mockMvc.perform(post("/api/scenarios/ambiguous/run")
                        .with(operatorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executionMode":"SEQUENTIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andReturn();
        JsonNode scenarioBody = objectMapper.readTree(scenario.getResponse().getContentAsString());
        String approvalId = scenarioBody.at("/pendingApprovals/0/approvalId").asText();

        mockMvc.perform(post("/api/approvals/{approvalId}", approvalId)
                        .with(operatorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECT","approver":"spoofed-identity","rationale":"OIDC auth test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approver").value("assessment-operator"));
    }

    @Test
    void keepsPublicCreationAvailableButProtectsClickAnalytics() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/public","alias":"prod1"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/urls/prod1/stats"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/urls/prod1/stats").with(operatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operatorToken() {
        return jwt().jwt(token -> token.subject("assessment-operator").claim("scope", "agentic.operator"));
    }
}
