package com.codelry.demo.sessionapi;

import com.codelry.demo.sessionapi.controller.SessionController;
import com.codelry.demo.sessionapi.model.Session;
import com.codelry.demo.sessionapi.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createSession_ShouldReturnCreatedWithSessionId() throws Exception {
        Session mockSession = new Session();
        when(sessionService.createSession()).thenReturn(mockSession);

        mockMvc.perform(post("/v1/api/session"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists());
    }

    @Test
    void createSession_WhenRedisError_ShouldReturnInternalServerError() throws Exception {
        when(sessionService.createSession()).thenThrow(new RuntimeException("Unable to create session - Redis service unavailable"));

        mockMvc.perform(post("/v1/api/session"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));
    }

    @Test
    void getSession_WithValidUUID_ShouldReturnSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Session mockSession = new Session();
        when(sessionService.getSession(sessionId)).thenReturn(Optional.of(mockSession));

        mockMvc.perform(get("/v1/api/session/" + sessionId))
                .andExpect(status().isOk());
    }

    @Test
    void getSession_WithInvalidUUID_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/v1/api/session/invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void getSession_WhenRedisError_ShouldReturnInternalServerError() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.getSession(sessionId)).thenThrow(new RuntimeException("Unable to retrieve session - Redis service unavailable"));

        mockMvc.perform(get("/v1/api/session/" + sessionId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));
    }

    @Test
    void getSession_WithNonExistentSession_ShouldReturnNotFound() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.getSession(sessionId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/api/session/" + sessionId))
                .andExpect(status().isNotFound());
    }
}
