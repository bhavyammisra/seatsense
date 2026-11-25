package com.seatsense.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SpaRoutesTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void everyPageOpensWithoutLoginAndGetsTheApp() throws Exception {
        for (String page : SpaRoutes.PAGES) {
            mvc.perform(get(page)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void builtAssetsNeedNoLogin() throws Exception {
        // No frontend build in tests, so the file is missing: 404, not a login error
        mvc.perform(get("/assets/index.js")).andExpect(status().isNotFound());
    }

    @Test
    void pagePathsOpenOnlyForReading() throws Exception {
        mvc.perform(post("/kiosk")).andExpect(status().isForbidden());
    }
}
