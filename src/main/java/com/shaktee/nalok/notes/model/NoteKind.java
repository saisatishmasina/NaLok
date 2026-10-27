package com.shaktee.nalok.notes.model;

/**
 * What a note IS, for retrieval and later recall. Kinds are metadata on one
 * notes system, not five separate workflows; a decision note simply explains
 * its reasoning in ordinary writing.
 */
public enum NoteKind {
    NOTE,
    THOUGHT,
    QUESTION,
    DECISION,
    CHECKPOINT
}
