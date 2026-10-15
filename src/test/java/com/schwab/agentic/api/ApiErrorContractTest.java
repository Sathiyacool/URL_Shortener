package com.schwab.agentic.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownRunAndApprovalReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/runs/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/runs/{id}/audit", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/runs/{id}/safe-stop", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/approvals/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","approver":"a","rationale":"r"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedIdentifiersAndBodiesReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/runs/not-a-uuid")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"","inputSummary":"","tasks":[]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/approvals/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"MAYBE","approver":"a","rationale":"r"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/approvals/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","approver":"","rationale":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cyclicDagIsRejectedWithClientError() throws Exception {
        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"cycle","inputSummary":"cycle","tasks":[
                                  {"taskId":"a","kind":"DOCUMENTATION","dependencyIds":["b"],"impactLevel":"LOW"},
                                  {"taskId":"b","kind":"REVIEW","dependencyIds":["a"],"impactLevel":"LOW"}]}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void resumingARunThatIsNotPausedReturnsConflict() throws Exception {
        String body = mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"conflict","inputSummary":"x","tasks":[
                                  {"taskId":"doc","kind":"DOCUMENTATION","dependencyIds":[],"impactLevel":"LOW"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String runId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("runId").asText();

        mockMvc.perform(post("/api/runs/{id}/resume", runId)).andExpect(status().isConflict());
    }

    @Test
    void shortenerErrorsUseExpectedStatuses() throws Exception {
        mockMvc.perform(get("/api/urls/missing-code/stats")).andExpect(status().isNotFound());
        mockMvc.perform(get("/missing-code")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
