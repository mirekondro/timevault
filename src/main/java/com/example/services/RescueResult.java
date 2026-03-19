package com.example.services;

import com.example.entities.ArchiveDraft;

import java.time.LocalDateTime;

public record RescueResult(
        String originalUrl,
        String snapshotUrl,
        LocalDateTime snapshotDateTime,
        ArchiveDraft draft
) {
}
