package vn.sportbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void registerLoginAndAccessProtectedEndpoint() throws Exception {
        String body = """
                {"email":"newuser@test.vn","password":"123456","fullName":"Người Dùng Mới","phone":"0911222333"}
                """;
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@test.vn\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(login);
        String token = node.get("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newuser@test.vn"));

        mvc.perform(get("/api/bookings/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordReturns401AndValidationReturns400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.vn\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\"1\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void publicEndpointsAreOpen() throws Exception {
        mvc.perform(get("/api/venues")).andExpect(status().isOk());
        mvc.perform(get("/api/meta")).andExpect(status().isOk());
        mvc.perform(get("/api/matches")).andExpect(status().isOk());
    }
}
