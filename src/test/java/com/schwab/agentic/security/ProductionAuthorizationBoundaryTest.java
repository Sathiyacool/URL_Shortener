package com.schwab.agentic.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.security.issuer-uri=https://issuer.example.test/tenant",
    "app.security.audience=agentic-url-shortener",
    "app.database.url=jdbc:postgresql://db.example.test/agentic",
    "app.database.username=test-user",
    "app.database.password=test-only-password",
    "spring.datasource.url=jdbc:h2:mem:prodauth",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProductionAuthorizationBoundaryTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousRequestsToOperatorEndpointsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/approvals/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/runs/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/runs/{id}/safe-stop", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/scenarios/greenfield/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionMode\":\"SEQUENTIAL\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedTokenWithoutOperatorScopeIsForbidden() throws Exception {
        mockMvc.perform(get("/api/scenarios").with(viewerToken())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/approvals/pending").with(viewerToken())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/runs/{id}/safe-stop", UUID.randomUUID()).with(viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorReceivesApplicationErrorsAndPublicEndpointsStayOpen() throws Exception {
        mockMvc.perform(get("/api/runs/{id}", UUID.randomUUID()).with(operatorToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/approvals/{id}", UUID.randomUUID())
                        .with(operatorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/missing-code")).andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operatorToken() {
        return jwt().jwt(token -> token.subject("boundary-operator").claim("scope", "agentic.operator"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor viewerToken() {
        return jwt().jwt(token -> token.subject("viewer").claim("scope", "agentic.viewer"))
                .authorities(new SimpleGrantedAuthority("SCOPE_agentic.viewer"));
    }
}
