package com.schwab.agentic.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatedSpecificationDocumentsBusinessEndpointsSchemasAndAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Agentic URL Shortener API"))
                .andExpect(jsonPath("$.paths['/api/urls'].post.summary").value("Create a short URL"))
                .andExpect(jsonPath("$.paths['/api/urls'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/urls'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/urls'].post.responses['400'].content").exists())
                .andExpect(jsonPath("$.paths['/api/urls'].post.requestBody.content['application/json'].schema")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/urls'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/urls/{code}/stats'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/runs'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/runs'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/runs'].post.responses['403']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail").exists())
                .andExpect(jsonPath("$.components.schemas.CreateShortUrlRequest.properties.url.description")
                        .value(org.hamcrest.Matchers.containsString("does not fetch it")))
                .andExpect(jsonPath("$.paths['/{code}'].get.responses['302']").exists());
    }

    @Test
    void servesSwaggerUiOnlyInTheLocalProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
