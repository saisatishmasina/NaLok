package com.shaktee.nalok.notes.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteKind;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    Page<Note> findByArchived(boolean archived, Pageable pageable);

    Page<Note> findByKind(NoteKind kind, Pageable pageable);

    Page<Note> findByKindAndArchived(NoteKind kind, boolean archived, Pageable pageable);
}
