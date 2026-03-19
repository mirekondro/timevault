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
            appModel.setStatusMessage("Database is ready. Register or log in to open your vault.");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not initialize database: " + safeMessage(exception));
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
            appModel.setStatusMessage("Loaded " + itemCount + " item(s) for " + currentUser.email() + ".");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not load vault items: " + safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void register(AppModel appModel) {
        String email = AccountValidator.normalizeEmail(appModel.registerEmailInputProperty().get());
        String password = appModel.registerPasswordInputProperty().get();
        String confirmPassword = appModel.registerConfirmPasswordInputProperty().get();

        if (!AccountValidator.isValidEmail(email)) {
            appModel.setStatusMessage("Enter a valid email address before registering.");
            return;
        }

        if (!AccountValidator.isValidPassword(password)) {
            appModel.setStatusMessage("Passwords must be at least 8 characters long.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            appModel.setStatusMessage("The password confirmation does not match.");
            return;
        }

        appModel.setBusy(true);
        try {
            if (userDAO.findByEmail(email).isPresent()) {
                appModel.setStatusMessage("That email is already registered. Try logging in instead.");
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
            appModel.setStatusMessage("Account created for " + session.email() + ". Loaded " + itemCount + " item(s).");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not create account: " + safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void login(AppModel appModel) {
        String email = AccountValidator.normalizeEmail(appModel.loginEmailInputProperty().get());
        String password = appModel.loginPasswordInputProperty().get();

        if (!AccountValidator.isValidEmail(email)) {
            appModel.setStatusMessage("Enter a valid email address before logging in.");
            return;
        }

        if (!AccountValidator.isValidPassword(password)) {
            appModel.setStatusMessage("Passwords must be at least 8 characters long.");
            return;
        }

        appModel.setBusy(true);
        try {
            Optional<VaultUser> existingUser = userDAO.findByEmail(email);
            if (existingUser.isEmpty() || !PasswordHasher.matches(password, existingUser.get().getPasswordHash())) {
                appModel.setStatusMessage("Email or password is incorrect.");
                return;
            }

            UserSession session = toSession(existingUser.get());
            appModel.setCurrentUser(session);
            resetVaultFilters(appModel);
            appModel.clearLoginForm();
            appModel.clearRegisterForm();
            int itemCount = loadCurrentUserItems(appModel, session);
            appModel.setStatusMessage("Signed in as " + session.email() + ". Loaded " + itemCount + " item(s).");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not log in: " + safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void logout(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            appModel.setStatusMessage("No user is currently signed in.");
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
        appModel.setStatusMessage("Signed out of " + currentUser.email() + ".");
    }

    public String getSessionSummary(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            return "Not signed in";
        }
        return "Signed in as " + currentUser.email();
    }

    public String getSessionMeta(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            return "Create an account to keep every upload attached to your own user id.";
        }
        return "Account #" + currentUser.id() + " owns every saved item in this session.";
    }

    public String getEmptyDetailMessage(AppModel appModel) {
        if (!appModel.isAuthenticated()) {
            return "Log in to load your own vault items.";
        }
        return "Search or save an item to see its details.";
    }

    public String getArchiveSummary(AppModel appModel) {
        if (!appModel.isAuthenticated()) {
            return "Log in to see your saved items.";
        }
        return appModel.getFilteredItems().size() + " visible item(s)";
    }

    public void saveUrl(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return;
        }

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
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, currentUser, item, "Saved URL item");
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
            appModel.setStatusMessage("Add both a title and content for a text item.");
            return;
        }

        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(content);
        item.setAiContext(buildContext("TEXT", title, content));
        item.setItemType("TEXT");
        item.setTags(buildTags("TEXT", content));
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, currentUser, item, "Saved text item");
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
            appModel.setStatusMessage("Choose an image and give it a title before saving.");
            return;
        }

        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(path);
        item.setAiContext(buildContext("IMAGE", title, path));
        item.setItemType("IMAGE");
        item.setTags(buildTags("IMAGE", title));
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());

        saveItem(appModel, currentUser, item, "Saved image item");
        appModel.clearImageForm();
    }

    public void deleteSelected(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return;
        }

        VaultItemFx selectedItem = appModel.getSelectedItem();
        if (selectedItem == null) {
            appModel.setStatusMessage("Select an item before trying to delete.");
            return;
        }

        appModel.setBusy(true);
        try {
            boolean deleted = vaultItemDAO.deleteById(currentUser.id(), selectedItem.getId());
            if (!deleted) {
                appModel.setStatusMessage("That item no longer exists for your account.");
                return;
            }
            appModel.removeItem(selectedItem.getId());
            appModel.setStatusMessage("Deleted item #" + selectedItem.getId() + ".");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not delete item: " + safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    private void saveItem(AppModel appModel, UserSession currentUser, VaultItemFx item, String successPrefix) {
        appModel.setBusy(true);
        try {
            VaultItemFx savedItem = vaultItemDAO.insert(currentUser.id(), item);
            appModel.addItem(savedItem);
            appModel.setStatusMessage(successPrefix + " #" + savedItem.getId() + " for account #" + currentUser.id() + ".");
        } catch (SQLException exception) {
            appModel.setStatusMessage("Could not save item: " + safeMessage(exception));
        } finally {
            appModel.setBusy(false);
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

    private UserSession requireAuthenticatedUser(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            appModel.clearVault();
            appModel.setStatusMessage("Register or log in before accessing the vault.");
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
        appModel.selectedTypeProperty().set("ALL");
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
