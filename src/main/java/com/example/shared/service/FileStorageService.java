package com.example.shared.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * SHARED SERVICE - File Storage
 *
 * Handles file uploads and storage for images
 */
@Service
public class FileStorageService {

    @Value("${timevault.storage.path:${user.home}/.timevault/uploads}")
    private String storagePath;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(storagePath);
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    /**
     * Store a file and return the stored path
     */
    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);

        // Generate unique filename with date prefix
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        Path dateDir = rootLocation.resolve(datePrefix);
        Files.createDirectories(dateDir);

        Path destinationFile = dateDir.resolve(uniqueFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Return relative path
        return datePrefix + "/" + uniqueFilename;
    }

    /**
     * Store file from byte array
     */
    public String store(byte[] data, String originalFilename) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String extension = getExtension(originalFilename);

        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        Path dateDir = rootLocation.resolve(datePrefix);
        Files.createDirectories(dateDir);

        Path destinationFile = dateDir.resolve(uniqueFilename);
        Files.write(destinationFile, data);

        return datePrefix + "/" + uniqueFilename;
    }

    /**
     * Load file as byte array
     */
    public byte[] load(String relativePath) throws IOException {
        Path file = rootLocation.resolve(relativePath);
        return Files.readAllBytes(file);
    }

    /**
     * Get full path to a stored file
     */
    public Path getPath(String relativePath) {
        return rootLocation.resolve(relativePath);
    }

    /**
     * Delete a stored file
     */
    public boolean delete(String relativePath) {
        try {
            Path file = rootLocation.resolve(relativePath);
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if a file exists
     */
    public boolean exists(String relativePath) {
        Path file = rootLocation.resolve(relativePath);
        return Files.exists(file);
    }

    /**
     * Get MIME type of a file
     */
    public String getMimeType(String relativePath) {
        try {
            Path file = rootLocation.resolve(relativePath);
            String mimeType = Files.probeContentType(file);
            return mimeType != null ? mimeType : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }
}

