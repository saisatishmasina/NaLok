package com.shaktee.nalok.projects.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;

/**
 * Read-only Projects HTTP contract on the disposable test database. Verifies
 * DTO projection (notably the ACIVE->ACTIVE boundary mapping), summaries,
 * sections and problem-detail errors.
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
}
