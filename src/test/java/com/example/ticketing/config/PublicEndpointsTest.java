package com.example.ticketing.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testControllerIsGoneAndNotPublic() throws Exception {
        mockMvc.perform(delete("/api/test/incident/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test/hash").param("password", "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void analyticsTestEndpointIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/analytics/test")).andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerIsNotPublicByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginStaysPublic() throws Exception {
        mockMvc.perform(get("/api/auth/ping")).andExpect(status().isOk());
    }
}
