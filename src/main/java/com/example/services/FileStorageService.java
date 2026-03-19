package com.example.services;

import com.example.entities.Archive;
import com.example.support.TimeVaultPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileStorageService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final TimeVaultPaths paths;

    public FileStorageService(TimeVaultPaths paths) {
        this.paths = paths;
        try {
            Files.createDirectories(paths.baseDir());
            Files.createDirectories(paths.filesDir());
            Files.createDirectories(paths.exportsDir());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare TimeVault folders", exception);
        }
    }

    public String storeSnapshot(String title, String rawContent) {
        return writeStringFile("snapshots", title, ".html", rawContent);
    }

    public String storeImage(Path sourceFile) {
        try {
            String extension = extractExtension(sourceFile.getFileName().toString());
            Path targetDirectory = paths.filesDir().resolve("images");
            Files.createDirectories(targetDirectory);
            Path target = targetDirectory.resolve(timestampedName(sourceFile.getFileName().toString(), extension));
            Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store image", exception);
        }
    }

    public Path exportArchive(Archive archive) {
        String fileName = timestampedName(archive.getTitle(), ".txt");
        Path target = paths.exportsDir().resolve(fileName);
        StringBuilder builder = new StringBuilder();
        builder.append("Title: ").append(archive.getTitle()).append(System.lineSeparator());
        builder.append("Type: ").append(archive.getType().displayName()).append(System.lineSeparator());
        builder.append("Created: ").append(archive.createdAtLabel()).append(System.lineSeparator());
        builder.append("Source: ").append(archive.getSourcePlatform()).append(System.lineSeparator());
        if (archive.getUrl() != null && !archive.getUrl().isBlank()) {
            builder.append("URL: ").append(archive.getUrl()).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("AI Context").append(System.lineSeparator());
        builder.append(archive.getAiContext()).append(System.lineSeparator());
        builder.append(System.lineSeparator()).append("Content").append(System.lineSeparator());
        builder.append(archive.getContent()).append(System.lineSeparator());
        try {
            Files.writeString(target, builder.toString(), StandardCharsets.UTF_8);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to export archive", exception);
        }
    }

    public void deleteStoredArtifact(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ignored) {
        }
    }

    private String writeStringFile(String folder, String title, String extension, String content) {
        try {
            Path targetDirectory = paths.filesDir().resolve(folder);
            Files.createDirectories(targetDirectory);
            Path target = targetDirectory.resolve(timestampedName(title, extension));
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
            return target.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store archive artifact", exception);
        }
    }

    private String timestampedName(String title, String extension) {
        String safeTitle = sanitizeFileName(title);
        return FILE_STAMP.format(LocalDateTime.now()) + "-" + safeTitle + extension;
    }

    private String sanitizeFileName(String input) {
        String safe = input == null ? "capture" : input.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        safe = safe.replaceAll("^-+", "").replaceAll("-+$", "");
        return safe.isBlank() ? "capture" : safe.substring(0, Math.min(safe.length(), 40));
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : ".png";
    }
}
