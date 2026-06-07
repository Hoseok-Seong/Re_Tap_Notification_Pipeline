package com.retap.fcmmock.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retap.fcmmock.FcmMockServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = FcmMockServerApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "fcm-mock.response-delay-ms=0",
        "fcm-mock.failure-rate-percent=0"
})
class FcmMockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sendReturnsFcmLikeMessageName() throws Exception {
        mockMvc.perform(post("/v1/projects/retap-test/messages:send")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": {
                                    "token": "mock-token-1",
                                    "notification": {
                                      "title": "title",
                                      "body": "body"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", startsWith("projects/retap-test/messages/")));
    }

    @Test
    void metricsReturnsCountersAndConfiguredValues() throws Exception {
        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").exists())
                .andExpect(jsonPath("$.successRequests").exists())
                .andExpect(jsonPath("$.failureRequests").exists())
                .andExpect(jsonPath("$.responseDelayMs").value(0))
                .andExpect(jsonPath("$.failureRatePercent").value(0.0));
    }
}
