package com.schwab.agentic.shortener.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.repository.ClickEventRepository;
import com.schwab.agentic.shortener.repository.ShortUrlRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @BeforeEach
    void clearDatabase() {
        clickEventRepository.deleteAll();
        shortUrlRepository.deleteAll();
    }

    @Test
    void createsRedirectsAndReportsClickAnalytics() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/articles","alias":"demo1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/demo1"))
                .andExpect(jsonPath("$.code").value("demo1"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.url").value("https://example.com/articles"));

        mockMvc.perform(get("/demo1")
                        .header("Referer", "https://source.example")
                        .header("User-Agent", "integration-test"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/articles"));

        mockMvc.perform(get("/api/urls/demo1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1))
                .andExpect(jsonPath("$.recentClicks[0].referrer").value("https://source.example"))
                .andExpect(jsonPath("$.recentClicks[0].userAgent").value("integration-test"));
    }

    @Test
    void rejectsNonHttpDestinations() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"file:///etc/passwd"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPrivateNetworkDestinations() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"http://192.168.1.1/admin"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesRequestFieldsAndReportsAliasConflicts() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":" "}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/path","alias":"bad alias"}
                                """))
                .andExpect(status().isBadRequest());

        String validRequest = """
                {"url":"https://example.com/path","alias":"dupe1"}
                """;
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/urls/missing1/stats"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsGoneForExpiredUrlsWithoutRecordingClicks() throws Exception {
        shortUrlRepository.save(new ShortUrl(
                "expired1",
                "https://example.com",
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60)));

        mockMvc.perform(get("/expired1"))
                .andExpect(status().isGone());

        org.junit.jupiter.api.Assertions.assertEquals(0, clickEventRepository.countByShortCode("expired1"));
    }

    @Test
    void returnsNotFoundForUnknownCodes() throws Exception {
        mockMvc.perform(get("/missing1"))
                .andExpect(status().isNotFound());
    }
}
