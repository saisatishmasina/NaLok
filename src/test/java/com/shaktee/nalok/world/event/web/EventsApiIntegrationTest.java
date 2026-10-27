package com.shaktee.nalok.world.event.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;

/**
 * World-history HTTP contract: read-only, bounded, filterable by kind and
 * by a WHOLE target reference (type + id); half-references and unknown
 * parameter values are 400s. There is deliberately no write endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudySessionService studySessionService;

    @Test
    void eventsListIsBoundedNewestFirstAndFilterable() throws Exception {
        StudySession session = studySessionService.createSession("API trail", null, null);
        studySessionService.startSession(session.getId());

        // Envelope with a stable shape; the newest event is the start.
        mockMvc.perform(get("/api/events")
                        .param("targetType", "STUDY_SESSION")
                        .param("targetId", session.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].kind").value("STUDY_SESSION_STARTED"))
                .andExpect(jsonPath("$.content[1].kind").value("STUDY_SESSION_CREATED"))
                .andExpect(jsonPath("$.content[0].description").isString())
                .andExpect(jsonPath("$.content[0].occurredAt").isString())
                .andExpect(jsonPath("$.content[0].target.referenceId")
                        .value(session.getId().toString()));

        // Kind filter narrows to exactly that fact.
        mockMvc.perform(get("/api/events")
                        .param("kind", "STUDY_SESSION_STARTED")
                        .param("targetType", "STUDY_SESSION")
                        .param("targetId", session.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Standalone events (the runner's WORLD_STARTED) have no target.
        mockMvc.perform(get("/api/events").param("kind", "WORLD_STARTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].target").doesNotExist())
                .andExpect(jsonPath("$.content[0].description").isString());
    }

    @Test
    void halfReferenceAndUnknownValuesAreRejected() throws Exception {
        // A target reference requires type AND id.
        mockMvc.perform(get("/api/events").param("targetType", "STUDY_SESSION"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events")
                        .param("targetId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        // Unknown enum values are parameter errors, not 500s.
        mockMvc.perform(get("/api/events").param("kind", "NOT_A_KIND"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events").param("targetType", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
    }
}
