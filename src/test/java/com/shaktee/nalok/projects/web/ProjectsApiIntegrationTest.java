package com.shaktee.nalok.projects.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.shaktee.nalok.projects.model.ProjectSection;
import com.shaktee.nalok.projects.repository.ProjectRepository;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;

/**
 * Projects HTTP contract on the disposable test database. Verifies DTO
 * projection (notably the ACIVE->ACTIVE boundary mapping), summaries,
 * sections, problem-detail errors and the F06 create/edit write contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectSectionService projectSectionService;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void persistedAciveStatusIsPresentedAsActive() throws Exception {
        UUID id = projectService.createProject("Boundary status", "api mapping").getId();
        projectService.startProject(id); // persisted as misspelled ACIVE

        mockMvc.perform(get("/api/projects/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/projects")
                        .param("status", "ACTIVE")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void unknownStatusFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryCountsNewProject() throws Exception {
        projectService.createProject("Summary subject", null);

        MvcResult result = mockMvc.perform(get("/api/projects/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.active").isNumber())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"total\"");
    }

    @Test
    void sectionsListedForKnownProjectAndErroredForUnknown() throws Exception {
        UUID projectId = projectService.createProject("Sections source", null).getId();
        projectSectionService.createSection(projectId, "Reading", null, 0);

        mockMvc.perform(get("/api/projects/{id}/sections", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Reading"))
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].position").value(0));

        mockMvc.perform(get("/api/projects/{id}/sections", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownProjectIsNotFoundError() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void listEndpointReturnsStablePaginationEnvelope() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ---------- F06: create/edit write contract ----------

    @Test
    void creatingPersistsAPlannedProjectAndReturns201Envelope() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Fresh start","description":"written through the API"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Fresh start"))
                .andExpect(jsonPath("$.description").value("written through the API"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andReturn();

        // PERSISTENCE, not just response shape: the row survived to the DB.
        String id = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id");
        var persisted = projectRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Fresh start");
        assertThat(persisted.getStatus().name()).isEqualTo("PLANNED");
    }

    @Test
    void creatingWithBlankOrTooLongTitleIsRejected() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Project title is required"));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Project title cannot exceed 200 characters"));
    }

    @Test
    void creatingWithMalformedBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void editingUpdatesTitleAndDescriptionInOneCall() throws Exception {
        UUID id = projectService.createProject("Editable", "before").getId();
        long snapshotVersion = projectService.getProject(id).getVersion();

        mockMvc.perform(put("/api/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Editable v2","description":"after","version":%d}
                                """.formatted(snapshotVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Editable v2"))
                .andExpect(jsonPath("$.description").value("after"));

        var persisted = projectRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Editable v2");
        assertThat(persisted.getDescription()).isEqualTo("after");
        // Editing own fields does NOT touch lifecycle state or timestamps.
        assertThat(persisted.getStatus().name()).isEqualTo("PLANNED");
        assertThat(persisted.getCompletedAt()).isNull();
    }

    @Test
    void editingAUnknownProjectIsNotFoundError() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ghost\",\"version\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void editingWithMissingTitleIsRejected() throws Exception {
        UUID id = projectService.createProject("Guarded edit", null).getId();

        mockMvc.perform(put("/api/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"kept\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Project title is required"));

        // A rejected edit must leave the project untouched (atomic intent).
        var persisted = projectRepository.findById(id).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Guarded edit");
        assertThat(persisted.getDescription()).isNull();
    }

    // ---------- F07: lifecycle + sections write contract ----------

    @Test
    void lifecycleEndpointsFollowModelStateRules() throws Exception {
        UUID id = projectService.createProject("Lifecycle run", null).getId();

        // Planned → Active → Paused → Active → Completed → Archive.
        mockMvc.perform(post("/api/projects/{id}/start", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/projects/{id}/pause", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mockMvc.perform(post("/api/projects/{id}/start", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/projects/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Model rule: only ACTIVE projects can complete → 409 conflict.
        mockMvc.perform(post("/api/projects/{id}/complete", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operation conflicts with current state"));

        // Archiving a completed project is allowed; DOUBLE archive → 409.
        mockMvc.perform(post("/api/projects/{id}/archive", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/projects/{id}/archive", id))
                .andExpect(status().isConflict());

        // Persisted state matches every reported transition.
        assertThat(projectRepository.findById(id).orElseThrow().getStatus().name())
                .isEqualTo("ARCHIVED");
    }

    @Test
    void createdSectionIsAppendedAndListedPositionOrdered() throws Exception {
        UUID projectId = projectService.createProject("Sectioned", null).getId();

        MvcResult first = mockMvc.perform(post("/api/projects/{id}/sections", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Setup\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Integer firstPosition = com.jayway.jsonpath.JsonPath.read(
                first.getResponse().getContentAsString(), "$.position");
        assertThat(firstPosition).isZero();

        MvcResult second = mockMvc.perform(post("/api/projects/{id}/sections", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Research\",\"description\":\"sources\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andReturn();

        // LISTED in position order: the second-created section comes second.
        mockMvc.perform(get("/api/projects/{id}/sections", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Setup"))
                .andExpect(jsonPath("$[1].title").value("Research"))
                .andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[1].projectId").value(projectId.toString()));

        // The DB actually holds the appended ordering, not just the envelope.
        String secondId = com.jayway.jsonpath.JsonPath.read(
                second.getResponse().getContentAsString(), "$.id");
        var persisted = projectSectionService.getSection(UUID.fromString(secondId));
        assertThat(persisted.getPosition()).isEqualTo(1);
    }

    @Test
    void sectionEditingIsAtomicAndValidated() throws Exception {
        UUID projectId = projectService.createProject("Section edit host", null).getId();
        MvcResult created = mockMvc.perform(post("/api/projects/{id}/sections", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Draft section\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sectionId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");
        int snapshotVersion = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(put("/api/projects/sections/{sectionId}", sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Final section\",\"description\":\"v2\",\"version\":"
                                + snapshotVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final section"))
                .andExpect(jsonPath("$.description").value("v2"))
                .andExpect(jsonPath("$.position").value(0));

        // Blank titles are rejected and the section survives untouched.
        mockMvc.perform(put("/api/projects/sections/{sectionId}", sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"version\":" + snapshotVersion + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Section title is required"));
        assertThat(projectSectionService.getSection(UUID.fromString(sectionId)).getTitle())
                .isEqualTo("Final section");

        // Editing an unknown section is a NotFound problem, not a 500.
        mockMvc.perform(put("/api/projects/sections/{sectionId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ghost\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleVersionEditsAreRejectedInsteadOfOverwritingNewerWork() throws Exception {
        UUID projectId = projectService.createProject("Stale edit target", null).getId();
        long initialVersion = projectService.getProject(projectId).getVersion();

        MvcResult firstEdit = mockMvc.perform(put("/api/projects/{id}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Newer truth\",\"version\":" + initialVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(initialVersion + 1))
                .andReturn();

        // A STALE replay of the same form (old version) must NOT overwrite
        // the newer title — it is a conflict, and the row is untouched.
        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"STALE OVERWRITE\",\"version\":" + initialVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent update"))
                .andExpect(jsonPath("$.detail")
                        .value("The record changed concurrently; reload and try again"));
        assertThat(projectService.getProject(projectId).getTitle()).isEqualTo("Newer truth");

        // Editing against the CURRENT version still succeeds.
        int currentVersion = com.jayway.jsonpath.JsonPath.read(
                firstEdit.getResponse().getContentAsString(), "$.version");
        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Newer truth v2\",\"version\":" + currentVersion + "}"))
                .andExpect(status().isOk());
        assertThat(projectService.getProject(projectId).getTitle()).isEqualTo("Newer truth v2");

        // The same rule protects section edits.
        MvcResult section = mockMvc.perform(post("/api/projects/{id}/sections", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Guarded section\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String sectionId = com.jayway.jsonpath.JsonPath.read(
                section.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/projects/sections/{sectionId}", sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Section v2\",\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/projects/sections/{sectionId}", sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"STALE SECTION OVERWRITE\",\"version\":0}"))
                .andExpect(status().isConflict());
        assertThat(projectSectionService.getSection(UUID.fromString(sectionId)).getTitle())
                .isEqualTo("Section v2");
    }

    @Test
    void concurrentSectionAppendsGetDistinctOrderedPositions() throws Exception {
        UUID projectId = projectService.createProject("Append race", null).getId();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch allReady = new CountDownLatch(threads);
        try {
            List<Future<Exception>> outcomes = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                outcomes.add(pool.submit(() -> {
                    allReady.countDown();
                    // Fire all appends at the same instant after the gate.
                    if (!allReady.await(10, TimeUnit.SECONDS)) {
                        return new IllegalStateException("race gate timed out");
                    }
                    try {
                        projectSectionService.appendSection(projectId, "Parallel " + UUID.randomUUID(), null);
                        return null;
                    } catch (Exception failure) {
                        return failure;
                    }
                }));
            }
            for (Future<Exception> outcome : outcomes) {
                assertThat(outcome.get(15, TimeUnit.SECONDS)).isNull();
            }
        } finally {
            pool.shutdownNow();
        }

        // Every append persisted and every position is DISTINCT and ordered
        // (positions are exactly 0..threads-1, each taken exactly once).
        List<ProjectSection> persisted = projectSectionService.listSections(projectId);
        assertThat(persisted).hasSize(threads);
        assertThat(persisted.stream().map(ProjectSection::getPosition).distinct().count())
                .isEqualTo(threads);
        assertThat(persisted.stream().mapToInt(ProjectSection::getPosition).max())
                .hasValue(threads - 1);
    }
}
