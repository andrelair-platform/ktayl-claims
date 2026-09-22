package com.andrelair.ktayl.claims.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L1 — the info endpoint answers with the service identity. */
@WebMvcTest(InfoController.class)
class InfoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void infoReturnsServiceIdentity() throws Exception {
        mvc.perform(get("/api/claims/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ktayl-claims-acl"));
    }
}
