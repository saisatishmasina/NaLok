package com.shaktee.nalok.notes.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Closed read projection for bounded revision-history pages: only the
 * metadata columns are selected, so paging through a heavily revised note
 * never loads the stored bodies. Full content remains available per
 * revision through the detail path.
 */
public interface NoteRevisionSummary {

    UUID getId();

    long getRevisionNumber();

    String getTitle();

    NoteKind getKind();

    Instant getRecordedAt();
}
