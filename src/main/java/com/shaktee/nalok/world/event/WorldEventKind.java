package com.shaktee.nalok.world.event;

/**
 * What a persisted world event records. Values are stable API facts — the
 * UI filters and renders them by name. Lifecycle events are written by the
 * study service in the SAME transaction as the state change they describe
 * (E01a); a rolled-back change leaves no event behind.
 */
public enum WorldEventKind {
    WORLD_STARTED,
    STUDY_SESSION_CREATED,
    STUDY_SESSION_STARTED,
    STUDY_SESSION_AUTO_PAUSED,
    STUDY_SESSION_PAUSED,
    STUDY_SESSION_RESUMED,
    STUDY_SESSION_FINISHED,
    STUDY_SESSION_RECOVERED
}
