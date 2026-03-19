package com.example.desktop.bll;

import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.VaultItemDAO;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Business logic layer for the desktop application.
 */
public class VaultManager {

    private final VaultItemDAO vaultItemDAO;
    private final SchemaInitializer schemaInitializer;

    public VaultManager(VaultItemDAO vaultItemDAO, SchemaInitializer schemaInitializer) {
        this.vaultItemDAO = vaultItemDAO;
        this.schemaInitializer = schemaInitializer;
    }

    public void initialize(AppModel appModel) {
        appModel.setBusy(true);
        try {
            schemaInitializer.initializeSchema();
            loadVault(appModel);
            appModel.setStatusMessage("Desktop vault is ready.");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not initialize database: " + safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void loadVault(AppModel appModel) {
        try {
            appModel.setItems(vaultItemDAO.findAll());
            appModel.setStatusMessage("Loaded " + appModel.totalCountProperty().get() + " item(s) from the vault.");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not load vault items: " + safeMessage(exception));
        }
    }

    public void saveUrl(AppModel appModel) {
        String url = appModel.urlInputProperty().get().trim();
        if (url.isBlank()) {
            appModel.setStatusMessage("Enter a URL before saving.");
            return;
        }

        String title = firstNonBlank(appModel.urlTitleInputProperty().get(), "Saved URL");
        String notes = firstNonBlank(appModel.urlNotesInputProperty().get(), url);
        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(notes);
        item.setAiContext(buildContext("URL", title, notes));
        item.setItemType("URL");
        item.setTags(buildTags("URL", url));
        item.setSourceUrl(url);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, item, "Saved URL item");
        appModel.clearUrlForm();
    }

    public void saveText(AppModel appModel) {
        String title = appModel.textTitleInputProperty().get().trim();
        String content = appModel.textContentInputProperty().get().trim();
        if (title.isBlank() || content.isBlank()) {
            appModel.setStatusMessage("Add both a title and content for a text item.");
            return;
        }

        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(content);
        item.setAiContext(buildContext("TEXT", title, content));
        item.setItemType("TEXT");
        item.setTags(buildTags("TEXT", content));
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, item, "Saved text item");
        appModel.clearTextForm();
    }

    public void saveImage(AppModel appModel) {
        String title = appModel.imageTitleInputProperty().get().trim();
        String path = appModel.imagePathInputProperty().get().trim();
        if (title.isBlank() || path.isBlank()) {
            appModel.setStatusMessage("Choose an image and give it a title before saving.");
            return;
        }

        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(path);
        item.setAiContext(buildContext("IMAGE", title, path));
        item.setItemType("IMAGE");
        item.setTags(buildTags("IMAGE", title));
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, item, "Saved image item");
        appModel.clearImageForm();
    }

    public void deleteSelected(AppModel appModel) {
        VaultItemFx selectedItem = appModel.getSelectedItem();
        if (selectedItem == null) {
            appModel.setStatusMessage("Select an item before trying to delete.");
            return;
        }

        try {
            vaultItemDAO.deleteById(selectedItem.getId());
            appModel.removeItem(selectedItem.getId());
            appModel.setStatusMessage("Deleted item #" + selectedItem.getId() + ".");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not delete item: " + safeMessage(exception));
        }
    }

    private void saveItem(AppModel appModel, VaultItemFx item, String successPrefix) {
        try {
            VaultItemFx savedItem = vaultItemDAO.insert(item);
            appModel.addItem(savedItem);
            appModel.setStatusMessage(successPrefix + " #" + savedItem.getId() + ".");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not save item: " + safeMessage(exception));
        }
    }

    private String buildTags(String type, String content) {
        StringBuilder tags = new StringBuilder(type).append(", ").append(LocalDate.now());

        if ("URL".equalsIgnoreCase(type)) {
            String lowerContent = content.toLowerCase(Locale.ROOT);
            if (lowerContent.contains("github.com")) {
                tags.append(", GitHub");
            } else if (lowerContent.contains("medium.com")) {
                tags.append(", Medium");
            } else if (lowerContent.contains("twitter.com") || lowerContent.contains("x.com")) {
                tags.append(", Twitter");
            } else if (lowerContent.contains("youtube.com")) {
                tags.append(", YouTube");
            } else if (lowerContent.contains("reddit.com")) {
                tags.append(", Reddit");
            }
        }

        return tags.toString();
    }

    private String buildContext(String itemType, String title, String content) {
        String summary = content.length() > 140 ? content.substring(0, 140) + "..." : content;
        return "Saved on " + LocalDate.now() + " as a " + itemType.toLowerCase(Locale.ROOT) + " entry."
                + " This capture matters because \"" + title + "\" preserves a small piece of the digital moment."
                + " A future reader would see \"" + summary + "\" as evidence of how people stored and described things in this version of TimeVault.";
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
