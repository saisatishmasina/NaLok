package com.shaktee.nalok.sources.web;

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

import com.shaktee.nalok.notes.service.NoteService;
import com.shaktee.nalok.sources.service.SavedSourceService;

/**
 * Saved-sources HTTP contract on the disposable test database: URL identity
 * (verbatim storage, duplicate → 409, scheme validation → 400),
 * expected-version conflict answers, archive/restore, and the note↔source
 * association endpoints from both directions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SourcesApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SavedSourceService sourceService;

    @Autowired
    private NoteService noteService;

    @Test
    void createGetAndUpdateRoundTripKeepsUrlVerbatim() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Spring guide","url":"https://Example.org/Guide?x=1","description":"reference"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://Example.org/Guide?x=1"))
                .andExpect(jsonPath("$.title").value("Spring guide"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.version").isNumber())
                .andReturn();
        UUID id = readId(created);

        mockMvc.perform(get("/api/sources/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://Example.org/Guide?x=1"));

        // Changed edit bumps the version; URL stays immutable.
        mockMvc.perform(put("/api/sources/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Edited","description":"new why","expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited"))
                .andExpect(jsonPath("$.url").value("https://Example.org/Guide?x=1"))
                .andExpect(jsonPath("$.version").value(1));

        // Stale expectedVersion → 409 problem detail, same as notes.
        mockMvc.perform(put("/api/sources/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Stale","expectedVersion":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent update"));

        // Duplicate URL → conflict with the saved state.
        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Second","url":"https://Example.org/Guide?x=1"}
                                """))
                .andExpect(status().isConflict());

        // Validation errors are 400 problem details.
        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No url"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ftp","url":"ftp://example.org/x"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/sources/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSummariesArchiveFilterAndRestoreWorkOverHttp() throws Exception {
        UUID active = createViaApi("List kept", "https://list.test/kept");
        UUID gone = createViaApi("List gone", "https://list.test/gone");

        mockMvc.perform(get("/api/sources")
                        .param("archived", "false").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + gone + "')].url")
                        .value("https://list.test/gone"))
                // Summaries carry url but never the description.
                .andExpect(jsonPath("$.content[0].description").doesNotExist());

        mockMvc.perform(post("/api/sources/{id}/archive", gone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(get("/api/sources")
                        .param("archived", "false").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + gone + "')]")
                        .isEmpty());
        mockMvc.perform(get("/api/sources")
                        .param("archived", "true").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + gone + "')].id")
                        .value(gone.toString()));

        mockMvc.perform(post("/api/sources/{id}/restore", gone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        assertThatActive(active);
    }

    @Test
    void noteSourceAssociationWorksFromBothDirections() throws Exception {
        UUID noteId = noteService.createNote("Source-backed", "Body", null).getId();
        UUID sourceId = createViaApi("Linked spec", "https://link.test/spec");

        // Attach via the notes side; idempotent duplicate attach answers 200.
        mockMvc.perform(post("/api/notes/{id}/links/sources/{sid}", noteId, sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].sourceId").value(sourceId.toString()));
        mockMvc.perform(post("/api/notes/{id}/links/sources/{sid}", noteId, sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(1));

        // Source side: notes list + bounded page.
        mockMvc.perform(get("/api/sources/{id}/notes", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(noteId.toString()))
                .andExpect(jsonPath("$[0].body").doesNotExist());
        mockMvc.perform(get("/api/sources/{id}/notes/page", sourceId)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Unknown targets answer 404 from both directions.
        mockMvc.perform(post("/api/notes/{id}/links/sources/{sid}",
                        noteId, UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/sources/{id}/notes/page", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        // Unlink is 204 and leaves note and source intact.
        mockMvc.perform(delete("/api/notes/{id}/links/sources/{sid}", noteId, sourceId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/sources/{id}/notes", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/notes/{id}", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Body"));
    }

    private UUID createViaApi(String title, String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private void assertThatActive(UUID id) throws Exception {
        mockMvc.perform(get("/api/sources/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    private static final Pattern ID_FIELD = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private UUID readId(MvcResult result) throws java.io.UnsupportedEncodingException {
        Matcher matcher = ID_FIELD.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return UUID.fromString(matcher.group(1));
    }
}
