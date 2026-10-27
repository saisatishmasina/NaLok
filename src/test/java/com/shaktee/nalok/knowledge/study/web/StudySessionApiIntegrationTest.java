package com.shaktee.nalok.knowledge.study.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;

/**
 * HTTP contract for study operations, backed by the real services on the
 * disposable test database. Verifies statuses, DTO shapes and error
 * problem-details — transitions themselves are covered by service tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudySessionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudySessionService studySessionService;

    @Test
    void createdSessionIsReturnedAsDto() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "HTTP create", "summary": "via API"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("HTTP create"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.context").doesNotExist())
                .andExpect(jsonPath("$.activeSecondsTotal").value(0))
                .andReturn();
        assertThat(result).isNotNull();
    }

    @Test
    void blankTitleIsRejectedWithProblemDetail() throws Exception {
        mockMvc.perform(post("/api/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void missingContextTargetIsRejectedAsNotFound() throws Exception {
        mockMvc.perform(post("/api/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Dangling context",
                                 "context": {"entityType": "PROJECT", "referenceId": "%s"}}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Context target not found"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void unknownSessionIsNotFoundError() throws Exception {
        mockMvc.perform(get("/api/study/sessions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void lifecycleRunsThroughHttpAndEndingBeyondDoneIsConflict() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "HTTP lifecycle"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(post("/api/study/sessions/{id}/start", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.activeSecondsTotal").isNumber());

        mockMvc.perform(post("/api/study/sessions/{id}/pause", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.pausedAt").isNotEmpty());

        mockMvc.perform(post("/api/study/sessions/{id}/resume", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/study/sessions/{id}/finish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // Finishing an already-completed session conflicts with state.
        mockMvc.perform(post("/api/study/sessions/{id}/finish", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operation conflicts with current state"));
    }

    @Test
    void currentEndpointTracksTheFocusedSession() throws Exception {
        // Preparation: the shared disposable database may hold an ACTIVE
        // session from other test classes; recover it so "no current" is
        // observable deterministically.
        MvcResult current = mockMvc.perform(get("/api/study/sessions/current"))
                .andReturn();
        if (current.getResponse().getStatus() == 200) {
            UUID activeId = extractId(current.getResponse().getContentAsString());
            mockMvc.perform(post("/api/study/sessions/{id}/recover-interrupted", activeId))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/study/sessions/current"))
                .andExpect(status().isNoContent());

        // Starting one session makes it THE current one; starting another
        // auto-pauses the previous (D06) so uniqueness holds globally.
        MvcResult first = mockMvc.perform(post("/api/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Focus one"}
                                """))
                .andReturn();
        UUID firstId = extractId(first.getResponse().getContentAsString());
        mockMvc.perform(post("/api/study/sessions/{id}/start", firstId)).andExpect(status().isOk());

        MvcResult second = mockMvc.perform(post("/api/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Focus two"}
                                """))
                .andReturn();
        UUID secondId = extractId(second.getResponse().getContentAsString());
        mockMvc.perform(post("/api/study/sessions/{id}/start", secondId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/study/sessions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secondId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/study/sessions/{id}", firstId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // Hygiene: finish the focused session.
        mockMvc.perform(post("/api/study/sessions/{id}/pause", secondId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/study/sessions/{id}/finish", secondId))
                .andExpect(status().isOk());
    }

    @Test
    void historyEndpointReturnsStablePaginationEnvelope() throws Exception {
        mockMvc.perform(get("/api/study/sessions")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/study/sessions")
                        .param("status", "COMPLETED")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void summaryEndpointEchoesZoneAndBucketsAreConsistent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/study/summary")
                        .param("zone", "America/New_York"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value("America/New_York"))
                .andExpect(jsonPath("$.weekStart").isNotEmpty())
                .andExpect(jsonPath("$.weekEnd").isNotEmpty())
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.totalSeconds").isNumber())
                .andExpect(jsonPath("$.days.length()").value(7))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Every bucket carries a date member; the zone echo was asserted above.
        assertThat(body).contains("\"date\"");
    }

    @Test
    void summaryEndpointRejectsUnknownZone() throws Exception {
        mockMvc.perform(get("/api/study/summary")
                        .param("zone", "Mars/Olympus"))
                .andExpect(status().isBadRequest());
    }

    // ---------- F08: session workbench lists by context ----------

    @Test
    void sessionsCanBeListedByContextAndIncompletePairsAreRejected() throws Exception {
        // A REAL section so context targets exist (create path validates it).
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Section workbench project"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projectId = firstId(project.getResponse().getContentAsString());

        MvcResult section = mockMvc.perform(post("/api/projects/{id}/sections", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Workbench section"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID sectionId = firstId(section.getResponse().getContentAsString());

        UUID one = createSession(sectionId, "Workbench session one");
        UUID two = createSession(sectionId, "Workbench session two");
        // A session pointing at the PROJECT must NOT leak into the section list.
        createSession(projectId, "Project-tagged other", "PROJECT");

        // Both section-bound sessions are returned (newest-first is the
        // repository sort; both may share a createdAt instant, so the exact
        // order is not asserted — membership is the contract here).
        MvcResult listed = mockMvc.perform(get("/api/study/sessions/by-context")
                        .param("entityType", "PROJECT_SECTION")
                        .param("referenceId", sectionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        String body = listed.getResponse().getContentAsString();
        assertThat(body).contains(one.toString());
        assertThat(body).contains(two.toString());
        assertThat(body).doesNotContain("Project-tagged other");

        // Unknown reference: an EMPTY envelope, not an error — the page is
        // honest about "nothing here yet".
        mockMvc.perform(get("/api/study/sessions/by-context")
                        .param("entityType", "PROJECT_SECTION")
                        .param("referenceId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Incomplete reference (missing referenceId) is a bad request.
        mockMvc.perform(get("/api/study/sessions/by-context")
                        .param("entityType", "PROJECT_SECTION"))
                .andExpect(status().isBadRequest());
    }

    private UUID createSession(UUID referenceId, String title) throws Exception {
        return createSession(referenceId, title, "PROJECT_SECTION");
    }

    private UUID createSession(UUID referenceId, String title, String entityType) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s",
                                 "context": {"entityType": "%S", "referenceId": "%s"}}
                                """.formatted(title, entityType, referenceId)))
                .andExpect(status().isCreated())
                .andReturn();
        return firstId(created.getResponse().getContentAsString());
    }

    /**
     * FIRST occurrence of "id" only — a greedy match would trip over the
     * "projectId" key that follows in project-adjacent DTOs.
     */
    private UUID firstId(String body) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("no id in response: " + body);
        }
        return UUID.fromString(matcher.group(1));
    }

    private UUID extractId(String body) {
        return UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }
}
