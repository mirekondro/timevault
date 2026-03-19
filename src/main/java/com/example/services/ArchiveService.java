package com.example.services;

import com.example.entities.Archive;
import com.example.entities.ArchiveDraft;
import com.example.entities.ArchiveType;
import com.example.entities.DashboardStats;
import com.example.repositories.ArchiveRepository;
import com.example.repositories.TagRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class ArchiveService {

    private final ArchiveRepository archiveRepository;
    private final TagRepository tagRepository;
    private final FetchService fetchService;
    private final AiContextService aiContextService;
    private final FileStorageService fileStorageService;

    public ArchiveService(
            ArchiveRepository archiveRepository,
            TagRepository tagRepository,
            FetchService fetchService,
            AiContextService aiContextService,
            FileStorageService fileStorageService
    ) {
        this.archiveRepository = archiveRepository;
        this.tagRepository = tagRepository;
        this.fetchService = fetchService;
        this.aiContextService = aiContextService;
        this.fileStorageService = fileStorageService;
    }

    public ArchiveDraft createUrlDraft(String url) {
        FetchedContent fetchedContent = fetchService.fetchPage(url);
        return composeDraft(
                ArchiveType.URL,
                fetchedContent.finalUrl(),
                fetchedContent.title(),
                fetchedContent.content(),
                fetchedContent.sourcePlatform(),
                fetchedContent.rawContent(),
                null,
                LocalDateTime.now()
        );
    }

    public ArchiveDraft createTextDraft(String title, String text) {
        String safeContent = text == null ? "" : text.trim();
        String safeTitle = (title == null || title.isBlank()) ? deriveTitleFromText(safeContent) : title.trim();
        return composeDraft(
                ArchiveType.TEXT,
                null,
                safeTitle,
                safeContent,
                "Local note",
                null,
                null,
                LocalDateTime.now()
        );
    }

    public ArchiveDraft createImageDraft(Path sourceFile, String note) {
        String fileName = sourceFile.getFileName().toString();
        String title = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String content = note == null || note.isBlank()
                ? "Image archived locally from " + fileName + "."
                : note.trim();
        return composeDraft(
                ArchiveType.IMAGE,
                null,
                title,
                content,
                "Local upload",
                null,
                sourceFile,
                LocalDateTime.now()
        );
    }

    public ArchiveDraft composeDraft(
            ArchiveType type,
            String url,
            String title,
            String content,
            String sourcePlatform,
            String rawPayload,
            Path sourceFile,
            LocalDateTime createdAt
    ) {
        ArchiveDraft draft = new ArchiveDraft();
        draft.setType(type);
        draft.setUrl(url);
        draft.setTitle(title);
        draft.setContent(content == null || content.isBlank() ? "No content captured yet." : content.trim());
        draft.setSourcePlatform(sourcePlatform);
        draft.setRawPayload(rawPayload);
        draft.setSourceFile(sourceFile);
        draft.setCreatedAt(createdAt == null ? LocalDateTime.now() : createdAt);
        draft.setAiContext(aiContextService.generateArchiveContext(draft.getCreatedAt(), draft.getTitle(), draft.getContent()));
        draft.setTags(suggestTags(type, url, title, draft.getContent(), sourcePlatform, draft.getCreatedAt()));
        return draft;
    }

    public Archive saveDraft(ArchiveDraft draft) {
        String filePath = null;
        if (draft.getType() == ArchiveType.URL && draft.getRawPayload() != null && !draft.getRawPayload().isBlank()) {
            filePath = fileStorageService.storeSnapshot(draft.getTitle(), draft.getRawPayload());
        } else if (draft.getType() == ArchiveType.IMAGE && draft.getSourceFile() != null) {
            filePath = fileStorageService.storeImage(draft.getSourceFile());
        }

        Archive archive = new Archive(
                null,
                draft.getType(),
                draft.getUrl(),
                draft.getTitle(),
                draft.getContent(),
                filePath,
                draft.getAiContext(),
                draft.getSourcePlatform(),
                draft.getCreatedAt(),
                draft.getTags()
        );

        Archive saved = archiveRepository.save(archive);
        tagRepository.replaceTags(saved.getId(), draft.getTags());
        saved.setTags(draft.getTags());
        return saved;
    }

    public List<Archive> searchArchives(String query, ArchiveType type) {
        List<Archive> archives = archiveRepository.search(query, type);
        attachTags(archives);
        return archives;
    }

    public List<Archive> recentArchives(int limit) {
        List<Archive> archives = archiveRepository.findRecent(limit);
        attachTags(archives);
        return archives;
    }

    public Optional<Archive> findById(long id) {
        Optional<Archive> archive = archiveRepository.findById(id);
        archive.ifPresent(this::attachTags);
        return archive;
    }

    public DashboardStats getDashboardStats() {
        return new DashboardStats(
                archiveRepository.countAll(),
                archiveRepository.countSince(LocalDateTime.now().minusDays(7)),
                archiveRepository.countRescued()
        );
    }

    public void deleteArchive(Archive archive) {
        archiveRepository.delete(archive.getId());
        fileStorageService.deleteStoredArtifact(archive.getFilePath());
    }

    public java.nio.file.Path exportArchive(Archive archive) {
        return fileStorageService.exportArchive(archive);
    }

    private void attachTags(Archive archive) {
        archive.setTags(tagRepository.findTagsByArchiveId(archive.getId()));
    }

    private void attachTags(List<Archive> archives) {
        archives.forEach(this::attachTags);
    }

    private List<String> suggestTags(
            ArchiveType type,
            String url,
            String title,
            String content,
            String sourcePlatform,
            LocalDateTime createdAt
    ) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(type.databaseValue());
        tags.add(String.valueOf(createdAt.getYear()));
        tags.add(createdAt.toLocalDate().toString());

        if (sourcePlatform != null && !sourcePlatform.isBlank()) {
            tags.add(cleanTag(sourcePlatform));
        }

        if (url != null && !url.isBlank()) {
            String domain = url.replaceFirst("^https?://", "").split("/")[0];
            tags.add(cleanTag(domain));
        }

        List<String> keywords = aiContextService.extractKeywords(title + " " + content, 4);
        keywords.forEach(keyword -> tags.add(cleanTag(keyword)));

        return tags.stream()
                .filter(tag -> !tag.isBlank())
                .limit(8)
                .toList();
    }

    private String cleanTag(String tag) {
        return tag == null ? "" : tag.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9.-]+", "-").replaceAll("^-+|-+$", "");
    }

    private String deriveTitleFromText(String text) {
        String safe = text == null ? "" : text.trim();
        if (safe.isBlank()) {
            return "Untitled note";
        }
        String singleLine = safe.replaceAll("\\s+", " ");
        return singleLine.substring(0, Math.min(singleLine.length(), 48));
    }
}
