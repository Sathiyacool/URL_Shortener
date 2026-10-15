package com.schwab.agentic.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.repository.ClickEventRepository;
import com.schwab.agentic.shortener.repository.ShortUrlRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public class AssessmentStepDefinitions {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    private MvcResult response;
    private String shortCode;

    @Before
    public void clearShortenerState() {
        clickEventRepository.deleteAll();
        shortUrlRepository.deleteAll();
        response = null;
        shortCode = null;
    }

    @Given("the shortener has no stored links")
    public void shortenerHasNoStoredLinks() {
        assertEquals(0, shortUrlRepository.count());
    }

    @When("a user shortens {string} using alias {string}")
    public void createShortUrl(String destination, String alias) throws Exception {
        response = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", destination, "alias", alias))))
                .andReturn();
        JsonNode body = body();
        if (response.getResponse().getStatus() == 201) {
            shortCode = body.path("code").asText();
        }
    }

    @When("a visitor follows the returned link from {string}")
    public void followShortUrl(String referrer) throws Exception {
        assertFalse(shortCode == null || shortCode.isBlank(), "A successfully created short code is required");
        response = mockMvc.perform(get("/{code}", shortCode)
                        .header("Referer", referrer)
                        .header("User-Agent", "cucumber-bdd"))
                .andReturn();
    }

    @When("a visitor follows expired link {string}")
    public void followExpiredLink(String code) throws Exception {
        response = mockMvc.perform(get("/{code}", code)).andReturn();
    }

    @When("a user submits {string} as a destination")
    public void submitDestination(String destination) throws Exception {
        response = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", destination))))
                .andReturn();
    }

    @When("I run scenario {string} in {string} mode")
    public void runScenario(String scenarioId, String executionMode) throws Exception {
        response = mockMvc.perform(post("/api/scenarios/{scenarioId}/run", scenarioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("executionMode", executionMode))))
                .andReturn();
    }

    @Given("expired link {string} exists")
    public void expiredLinkExists(String code) {
        Instant now = Instant.now();
        shortUrlRepository.save(new ShortUrl(code, "https://example.com/expired", now.minusSeconds(120), now.minusSeconds(60)));
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expectedStatus) {
        assertEquals(expectedStatus, response.getResponse().getStatus());
    }

    @Then("the redirect points to {string}")
    public void redirectPointsTo(String destination) throws Exception {
        assertEquals(302, response.getResponse().getStatus());
        assertEquals(destination, response.getResponse().getHeader("Location"));
    }

    @Then("analytics reports {long} click")
    public void analyticsReportsClicks(long expectedClicks) throws Exception {
        MvcResult stats = mockMvc.perform(get("/api/urls/{code}/stats", shortCode)).andReturn();
        assertEquals(200, stats.getResponse().getStatus());
        JsonNode statsBody = objectMapper.readTree(stats.getResponse().getContentAsString());
        assertEquals(expectedClicks, statsBody.path("totalClicks").asLong());
        if (expectedClicks > 0) {
            assertEquals("https://referrer.example", statsBody.at("/recentClicks/0/referrer").asText());
            assertEquals("cucumber-bdd", statsBody.at("/recentClicks/0/userAgent").asText());
        }
    }

    @Then("expired link {string} has no recorded clicks")
    public void expiredLinkHasNoClicks(String code) {
        assertEquals(0, clickEventRepository.countByShortCode(code));
    }

    @Then("scenario run status is {string}")
    public void scenarioRunStatusIs(String expectedStatus) throws Exception {
        assertEquals(200, response.getResponse().getStatus());
        assertEquals(expectedStatus, body().path("status").asText());
    }

    @Then("there are no pending approvals")
    public void noPendingApprovals() throws Exception {
        assertTrue(body().path("pendingApprovals").isArray());
        assertEquals(0, body().path("pendingApprovals").size());
    }

    @Then("pending approval is for task {string}")
    public void pendingApprovalIsForTask(String expectedTaskId) throws Exception {
        JsonNode approvals = body().path("pendingApprovals");
        assertFalse(approvals.isEmpty(), "An approval checkpoint should be present");
        assertEquals(expectedTaskId, approvals.get(0).path("taskId").asText());
    }

    @Then("task {string} has state {string}")
    public void taskHasState(String taskId, String expectedState) throws Exception {
        JsonNode tasks = body().at("/run/tasks");
        boolean found = false;
        for (JsonNode task : tasks) {
            if (task.path("taskId").asText().equals(taskId)) {
                assertEquals(expectedState, task.path("state").asText());
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected task was not persisted in the run: " + taskId);
    }

    private JsonNode body() throws Exception {
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }
}
