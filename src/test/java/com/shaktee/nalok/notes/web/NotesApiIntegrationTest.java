package com.shaktee.nalok.notes.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.shaktee.nalok.knowledge.study.service.StudySessionService;
import com.shaktee.nalok.notes.service.NoteService;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;

/**
 * Notes HTTP contract on the disposable test database: DTO projection,
 * expected-version conflict answers, validation errors as problem details,
 * association endpoints and context retrieval.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotesApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectSectionService projectSectionService;

    @Autowired
    private StudySessionService studySessionService;

    @Test
    void createGetAndUpdateRoundTripWithVersionAndRevisions() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"API note","body":"Intégration 🔍\\nline two","kind":"THOUGHT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("API note"))
                .andExpect(jsonPath("$.kind").value("THOUGHT"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.latestRevisionNumber").value(1))
                .andExpect(jsonPath("$.version").isNumber())
                .andReturn();
        UUID id = readId(created);

        mockMvc.perform(get("/api/notes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Intégration 🔍\nline two"));

        // Content-changing PUT records revision 2; older revision stays.
        mockMvc.perform(put("/api/notes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"API note","body":"Body v2","expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Body v2"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.latestRevisionNumber").value(2));

        mockMvc.perform(get("/api/notes/{id}/revisions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].revisionNumber").value(2))
                .andExpect(jsonPath("$[0].body").value("Body v2"))
                .andExpect(jsonPath("$[1].revisionNumber").value(1))
                .andExpect(jsonPath("$[1].body").value("Intégration 🔍\nline two"));
    }

    @Test
    void staleExpectedVersionAnswers409AndKeepsServerContent() throws Exception {
        UUID id = noteService.createNote("Conflict subject", "Server writing", null)
                .getId();

        mockMvc.perform(put("/api/notes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Stale","body":"Lost draft","expectedVersion":9}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent update"));

        mockMvc.perform(get("/api/notes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Server writing"));
    }

    @Test
    void validationAndUnknownResourceErrors() throws Exception {
        // Blank title → 400 problem detail.
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        // Missing expectedVersion on edit → 400.
        UUID id = noteService.createNote("Edit me", null, null).getId();
        mockMvc.perform(put("/api/notes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No version sent"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/notes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notes/{id}/revisions/{rev}", id, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAnswersSummariesWithoutBodiesAndSupportsFilters() throws Exception {
        noteService.createNote("Summary subject", "The body stays hidden", null);

        mockMvc.perform(get("/api/notes").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").isNotEmpty())
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.totalElements").isNumber());

        MvcResult questions = mockMvc.perform(get("/api/notes")
                        .param("kind", "QUESTION")
                        .param("archived", "false")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        // The QUESTION filter answers only QUESTION summaries (or none).
        assertThat(questions.getResponse().getContentAsString())
                .doesNotContain("\"kind\":\"NOTE\"")
                .doesNotContain("\"kind\":\"DECISION\"");
    }

    @Test
    void archiveAndRestoreThroughTheApi() throws Exception {
        UUID id = noteService.createNote("API archive", "Kept", null).getId();

        mockMvc.perform(post("/api/notes/{id}/archive", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.body").value("Kept"))
                .andExpect(jsonPath("$.latestRevisionNumber").value(1));

        mockMvc.perform(get("/api/notes").param("archived", "true").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));

        mockMvc.perform(post("/api/notes/{id}/restore", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void linkEndpointsAttachListAndUnlinkWithContextRetrieval() throws Exception {
        UUID noteId = noteService.createNote("Linked via API", "Body", null).getId();
        UUID projectId = projectService.createProject("API project", null).getId();
        UUID sectionId = projectSectionService.createSection(
                projectId, "API section", null, 0).getId();
        UUID sessionId = studySessionService.createSession("API session", null, null)
                .getId();

        mockMvc.perform(post("/api/notes/{id}/links/projects/{pid}", noteId, projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.projects[0].linkedAt").isString());
        mockMvc.perform(post("/api/notes/{id}/links/sections/{sid}", noteId, sectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].sectionId").value(sectionId.toString()));
        mockMvc.perform(post("/api/notes/{id}/links/sessions/{sid}", noteId, sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].sessionId").value(sessionId.toString()));

        // Duplicate attach stays a 200 with a single entry.
        mockMvc.perform(post("/api/notes/{id}/links/projects/{pid}", noteId, projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(1));

        // Project-wide retrieval includes the section-linked note, once.
        mockMvc.perform(get("/api/notes/for-project/{pid}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(noteId.toString()))
                .andExpect(jsonPath("$[0].body").doesNotExist());
        mockMvc.perform(get("/api/notes/for-section/{sid}", sectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId.toString()));
        mockMvc.perform(get("/api/notes/for-session/{sid}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId.toString()));

        // Unknown targets answer 404.
        mockMvc.perform(post("/api/notes/{id}/links/projects/{pid}", noteId,
                        UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notes/for-project/{pid}", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        // Unlink → 204, then gone.
        mockMvc.perform(delete("/api/notes/{id}/links/sections/{sid}", noteId, sectionId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notes/{id}/links", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(0))
                .andExpect(jsonPath("$.projects.length()").value(1));
    }

    private UUID readId(MvcResult result) throws Exception {
        // Deterministic UUID extraction without a JSON library dependency.
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"")
                .matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return UUID.fromString(matcher.group(1));
    }
}
