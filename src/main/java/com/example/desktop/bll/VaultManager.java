package com.example.desktop.bll;

import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.UserDAO;
import com.example.desktop.dao.VaultItemDAO;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;
import com.example.shared.model.UserSession;
import com.example.shared.model.VaultUser;
import com.example.shared.security.AccountValidator;
import com.example.shared.security.PasswordHasher;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Business logic layer for the desktop application.
 */
public class VaultManager {

    private final VaultItemDAO vaultItemDAO;
    private final UserDAO userDAO;
    private final SchemaInitializer schemaInitializer;

    public VaultManager(VaultItemDAO vaultItemDAO, UserDAO userDAO, SchemaInitializer schemaInitializer) {
        this.vaultItemDAO = vaultItemDAO;
        this.userDAO = userDAO;
        this.schemaInitializer = schemaInitializer;
    }

    public void initialize(AppModel appModel) {
        appModel.setBusy(true);
        try {
            schemaInitializer.initializeSchema();
            appModel.clearVault();
            appModel.setStatusKey("status.db.ready");
        } catch (SQLException exception) {
            appModel.setStatusKey("status.db.init.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void loadVault(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return;
        }

        appModel.setBusy(true);
        try {
            int itemCount = loadCurrentUserItems(appModel, currentUser);
            appModel.setStatusKey("status.vault.loaded", itemCount, currentUser.email());
        } catch (SQLException exception) {
            appModel.setStatusKey("status.db.load.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void register(AppModel appModel) {
        String email = AccountValidator.normalizeEmail(appModel.registerEmailInputProperty().get());
        String password = appModel.registerPasswordInputProperty().get();
        String confirmPassword = appModel.registerConfirmPasswordInputProperty().get();

        if (!AccountValidator.isValidEmail(email)) {
            appModel.setStatusKey("status.validation.email.register");
            return;
        }

        if (!AccountValidator.isValidPassword(password)) {
            appModel.setStatusKey("status.validation.password.min");
            return;
        }

        if (!password.equals(confirmPassword)) {
            appModel.setStatusKey("status.validation.password.confirm");
            return;
        }

        appModel.setBusy(true);
        try {
            if (userDAO.findByEmail(email).isPresent()) {
                appModel.setStatusKey("status.auth.email.exists");
                return;
            }

            VaultUser newUser = new VaultUser(email, PasswordHasher.hash(password));
            VaultUser savedUser = userDAO.insert(newUser);
            UserSession session = toSession(savedUser);

            appModel.setCurrentUser(session);
            resetVaultFilters(appModel);
            appModel.clearRegisterForm();
            appModel.clearLoginForm();
            int itemCount = loadCurrentUserItems(appModel, session);
            appModel.setStatusKey("status.auth.account.created", session.email(), itemCount);
        } catch (SQLException exception) {
            appModel.setStatusKey("status.auth.create.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void login(AppModel appModel) {
        String email = AccountValidator.normalizeEmail(appModel.loginEmailInputProperty().get());
        String password = appModel.loginPasswordInputProperty().get();

        if (!AccountValidator.isValidEmail(email)) {
            appModel.setStatusKey("status.validation.email.login");
            return;
        }

        if (!AccountValidator.isValidPassword(password)) {
            appModel.setStatusKey("status.validation.password.min");
            return;
        }

        appModel.setBusy(true);
        try {
            Optional<VaultUser> existingUser = userDAO.findByEmail(email);
            if (existingUser.isEmpty() || !PasswordHasher.matches(password, existingUser.get().getPasswordHash())) {
                appModel.setStatusKey("status.auth.invalid.credentials");
                return;
            }

            UserSession session = toSession(existingUser.get());
            appModel.setCurrentUser(session);
            resetVaultFilters(appModel);
            appModel.clearLoginForm();
            appModel.clearRegisterForm();
            int itemCount = loadCurrentUserItems(appModel, session);
            appModel.setStatusKey("status.auth.logged.in", session.email(), itemCount);
        } catch (SQLException exception) {
            appModel.setStatusKey("status.auth.login.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void logout(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            appModel.setStatusKey("status.auth.no.current.user");
            return;
        }

        appModel.setCurrentUser(null);
        resetVaultFilters(appModel);
        appModel.clearVault();
        appModel.clearLoginForm();
        appModel.clearRegisterForm();
        appModel.clearUrlForm();
        appModel.clearTextForm();
        appModel.clearImageForm();
        appModel.setStatusKey("status.auth.logged.out", currentUser.email());
    }

    public String getEmptyDetailMessage(AppModel appModel) {
        if (!appModel.isAuthenticated()) {
            return appModel.text("detail.meta.empty.unauth");
        }
        return appModel.text("detail.meta.empty.auth");
    }

    public String getArchiveSummary(AppModel appModel) {
        if (!appModel.isAuthenticated()) {
            return appModel.text("archive.login.required");
        }
        return appModel.getVisibleArchiveSummary(appModel.getFilteredItems().size());
    }

    public void saveUrl(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return;
        }

        String url = appModel.urlInputProperty().get().trim();
        if (url.isBlank()) {
            appModel.setStatusKey("status.save.url.missing");
            return;
        }

        String title = firstNonBlank(appModel.urlTitleInputProperty().get(), appModel.text("save.default.urlTitle"));
        String notes = firstNonBlank(appModel.urlNotesInputProperty().get(), url);
        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(notes);
        item.setAiContext(buildContext("URL", title, notes));
        item.setItemType(AppModel.TYPE_URL);
        item.setTags(buildTags(AppModel.TYPE_URL, url));
        item.setSourceUrl(url);
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, currentUser, item, "status.save.url.saved");
        appModel.clearUrlForm();
    }

    public void saveText(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return;
        }

        String title = appModel.textTitleInputProperty().get().trim();
        String content = appModel.textContentInputProperty().get().trim();
        if (title.isBlank() || content.isBlank()) {
            appModel.setStatusKey("status.save.text.missing");
            return;
        }

        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(content);
        item.setAiContext(buildContext("TEXT", title, content));
        item.setItemType(AppModel.TYPE_TEXT);
        item.setTags(buildTags(AppModel.TYPE_TEXT, content));
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, currentUser, item, "status.save.text.saved");
        appModel.clearTextForm();
    }

    public void saveImage(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return;
        }

        String title = appModel.imageTitleInputProperty().get().trim();
        String path = appModel.imagePathInputProperty().get().trim();
        if (title.isBlank() || path.isBlank()) {
            appModel.setStatusKey("status.save.image.missing");
            return;
        }

        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(path);
        item.setAiContext(buildContext("IMAGE", title, path));
        item.setItemType(AppModel.TYPE_IMAGE);
        item.setTags(buildTags(AppModel.TYPE_IMAGE, title));
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, currentUser, item, "status.save.image.saved");
        appModel.clearImageForm();
    }

    public void deleteSelected(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return;
        }

        VaultItemFx selectedItem = appModel.getSelectedItem();
        if (selectedItem == null) {
            appModel.setStatusKey("status.delete.select");
            return;
        }

        appModel.setBusy(true);
        try {
            boolean deleted = vaultItemDAO.deleteById(currentUser.id(), selectedItem.getId());
            if (!deleted) {
                appModel.setStatusKey("status.delete.missing");
                return;
            }
            appModel.removeItem(selectedItem.getId());
            appModel.setStatusKey("status.delete.deleted", selectedItem.getId());
        } catch (SQLException exception) {
            appModel.setStatusKey("status.delete.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    private void saveItem(AppModel appModel, UserSession currentUser, VaultItemFx item, String successKey) {
        appModel.setBusy(true);
        try {
            VaultItemFx savedItem = vaultItemDAO.insert(currentUser.id(), item);
            appModel.addItem(savedItem);
            appModel.setStatusKey(successKey, savedItem.getId(), currentUser.id());
        } catch (SQLException exception) {
            appModel.setStatusKey("status.save.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    private String buildTags(String type, String content) {
        StringBuilder tags = new StringBuilder(type).append(", ").append(LocalDate.now());

        if (AppModel.TYPE_URL.equalsIgnoreCase(type)) {
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

    private UserSession requireAuthenticatedUser(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            appModel.clearVault();
            appModel.setStatusKey("status.auth.required");
            return null;
        }
        return currentUser;
    }

    private int loadCurrentUserItems(AppModel appModel, UserSession session) throws SQLException {
        List<VaultItemFx> items = vaultItemDAO.findAllByUserId(session.id());
        appModel.setItems(items);
        return items.size();
    }

    private UserSession toSession(VaultUser user) {
        return new UserSession(user.getId(), user.getEmail());
    }

    private void resetVaultFilters(AppModel appModel) {
        appModel.searchTextProperty().set("");
        appModel.selectedTypeProperty().set(AppModel.TYPE_ALL);
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
