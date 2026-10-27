package com.shaktee.nalok.notes.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.shaktee.nalok.notes.model.NoteProjectLink;
import com.shaktee.nalok.notes.model.NoteSectionLink;
import com.shaktee.nalok.notes.model.NoteSessionLink;
import com.shaktee.nalok.notes.model.NoteSourceLink;

/**
 * All associations of one note, grouped by target type. linkedAt says when
 * the association was made — never authorship.
 */
public record NoteLinksDto(
        List<ProjectLink> projects,
        List<SectionLink> sections,
        List<SessionLink> sessions,
        List<SourceLink> sources) {

    public record ProjectLink(UUID projectId, Instant linkedAt) {

        static ProjectLink of(NoteProjectLink link) {
            return new ProjectLink(link.getProject().getId(), link.getLinkedAt());
        }
    }

    public record SectionLink(UUID sectionId, Instant linkedAt) {

        static SectionLink of(NoteSectionLink link) {
            return new SectionLink(link.getSection().getId(), link.getLinkedAt());
        }
    }

    public record SessionLink(UUID sessionId, Instant linkedAt) {

        static SessionLink of(NoteSessionLink link) {
            return new SessionLink(link.getSession().getId(), link.getLinkedAt());
        }
    }

    public record SourceLink(UUID sourceId, Instant linkedAt) {

        static SourceLink of(NoteSourceLink link) {
            return new SourceLink(link.getSource().getId(), link.getLinkedAt());
        }
    }

    public static NoteLinksDto of(List<NoteProjectLink> projects,
            List<NoteSectionLink> sections, List<NoteSessionLink> sessions,
            List<NoteSourceLink> sources) {
        return new NoteLinksDto(
                projects.stream().map(ProjectLink::of).toList(),
                sections.stream().map(SectionLink::of).toList(),
                sessions.stream().map(SessionLink::of).toList(),
                sources.stream().map(SourceLink::of).toList());
    }
}
