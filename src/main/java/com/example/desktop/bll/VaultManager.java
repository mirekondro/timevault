package com.example.desktop.bll;

import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.UserDAO;
import com.example.desktop.dao.VaultItemDAO;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.DialogFieldIds;
import com.example.desktop.model.ImageAssetData;
import com.example.desktop.model.ItemLockOptions;
import com.example.desktop.model.ProtectedItemData;
import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.ToastNotificationType;
import com.example.desktop.model.UnlockedItemSession;
import com.example.desktop.model.VaultItemFx;
import com.example.desktop.security.ProtectedItemCrypto;
import com.example.shared.model.UserSession;
import com.example.shared.model.VaultUser;
import com.example.shared.security.AccountValidator;
import com.example.shared.security.PasswordHasher;
import com.example.shared.service.GeminiService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final VaultItemDAO vaultItemDAO;
    private final UserDAO userDAO;
    private final SchemaInitializer schemaInitializer;
    private final GeminiService geminiService;
    private final ProtectedItemCrypto protectedItemCrypto = new ProtectedItemCrypto();

    public VaultManager(VaultItemDAO vaultItemDAO,
                        UserDAO userDAO,
                        SchemaInitializer schemaInitializer,
                        GeminiService geminiService) {
        this.vaultItemDAO = vaultItemDAO;
        this.userDAO = userDAO;
        this.schemaInitializer = schemaInitializer;
        this.geminiService = geminiService;
    }

    public void initialize(AppModel appModel) {
        appModel.setBusy(true);
        try {
            schemaInitializer.initializeSchema();
            appModel.clearVault();
        } catch (SQLException exception) {
            appModel.showErrorKey("status.db.init.error", safeMessage(exception));
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
            appModel.showSuccessKey("status.vault.loaded", itemCount, currentUser.email());
        } catch (SQLException exception) {
            appModel.showErrorKey("status.db.load.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void register(AppModel appModel) {
        String email = AccountValidator.normalizeEmail(appModel.registerEmailInputProperty().get());
        String password = appModel.registerPasswordInputProperty().get();
        String confirmPassword = appModel.registerConfirmPasswordInputProperty().get();

        if (!AccountValidator.isValidEmail(email)) {
            appModel.showErrorKey("status.validation.email.register");
            return;
        }

        if (!AccountValidator.isValidPassword(password)) {
            appModel.showErrorKey("status.validation.password.min");
            return;
        }

        if (!password.equals(confirmPassword)) {
            appModel.showErrorKey("status.validation.password.confirm");
            return;
        }

        appModel.setBusy(true);
        try {
            if (userDAO.findByEmail(email).isPresent()) {
                appModel.showErrorKey("status.auth.email.exists");
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
            appModel.showSuccessKey("status.auth.account.created", session.email(), itemCount);
        } catch (SQLException exception) {
            appModel.showErrorKey("status.auth.create.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void login(AppModel appModel) {
        String email = AccountValidator.normalizeEmail(appModel.loginEmailInputProperty().get());
        String password = appModel.loginPasswordInputProperty().get();

        if (!AccountValidator.isValidEmail(email)) {
            appModel.showErrorKey("status.validation.email.login");
            return;
        }

        if (!AccountValidator.isValidPassword(password)) {
            appModel.showErrorKey("status.validation.password.min");
            return;
        }

        appModel.setBusy(true);
        try {
            Optional<VaultUser> existingUser = userDAO.findByEmail(email);
            if (existingUser.isEmpty() || !PasswordHasher.matches(password, existingUser.get().getPasswordHash())) {
                appModel.showErrorKey("status.auth.invalid.credentials");
                return;
            }

            UserSession session = toSession(existingUser.get());
            appModel.setCurrentUser(session);
            resetVaultFilters(appModel);
            appModel.clearLoginForm();
            appModel.clearRegisterForm();
            int itemCount = loadCurrentUserItems(appModel, session);
            appModel.showSuccessKey("status.auth.logged.in", session.email(), itemCount);
        } catch (SQLException exception) {
            appModel.showErrorKey("status.auth.login.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public void logout(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            appModel.showInfoKey("status.auth.no.current.user");
            return;
        }

        appModel.setCurrentUser(null);
        resetVaultFilters(appModel);
        appModel.clearVault();
        appModel.clearLoginForm();
        appModel.clearRegisterForm();
        appModel.showSuccessKey("status.auth.logged.out", currentUser.email());
    }

    public DialogActionResult updateProfileEmail(AppModel appModel, String newEmailInput, String currentPasswordInput) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }

        String newEmail = AccountValidator.normalizeEmail(newEmailInput);
        String currentPassword = safePasswordValue(currentPasswordInput);

        if (newEmail.isBlank()) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL, "dialog.validation.profile.email.required");
        }
        if (!AccountValidator.isValidEmail(newEmail)) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL, "status.profile.email.invalid");
        }
        if (currentPassword.isBlank()) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL_CURRENT_PASSWORD, "dialog.validation.profile.currentPassword.required");
        }

        appModel.setBusy(true);
        try {
            Optional<VaultUser> currentUserRecord = userDAO.findById(currentUser.id());
            if (currentUserRecord.isEmpty()) {
                return dialogFormError(appModel, "status.profile.account.missing");
            }

            VaultUser user = currentUserRecord.get();
            if (!PasswordHasher.matches(currentPassword, user.getPasswordHash())) {
                return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL_CURRENT_PASSWORD, "status.profile.current.password.invalid");
            }
            if (newEmail.equals(user.getEmail())) {
                return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL, "status.profile.email.same");
            }

            Optional<VaultUser> existingUser = userDAO.findByEmail(newEmail);
            if (existingUser.isPresent() && existingUser.get().getId() != user.getId()) {
                return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL, "status.profile.email.exists");
            }

            if (!userDAO.updateEmail(user.getId(), newEmail)) {
                return dialogFormError(appModel, "status.profile.account.missing");
            }

            appModel.setCurrentUser(new UserSession(user.getId(), newEmail));
            return DialogActionResult.successLocalToast(appModel.text("status.profile.email.updated", newEmail));
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.profile.email.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public DialogActionResult updateProfilePassword(AppModel appModel,
                                                    String currentPasswordInput,
                                                    String newPasswordInput,
                                                    String confirmPasswordInput) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }

        String currentPassword = safePasswordValue(currentPasswordInput);
        String newPassword = safePasswordValue(newPasswordInput);
        String confirmPassword = safePasswordValue(confirmPasswordInput);

        DialogActionResult validationResult = validateProfilePasswordInputs(appModel, currentPassword, newPassword, confirmPassword);
        if (validationResult != null) {
            return validationResult;
        }

        appModel.setBusy(true);
        try {
            Optional<VaultUser> currentUserRecord = userDAO.findById(currentUser.id());
            if (currentUserRecord.isEmpty()) {
                return dialogFormError(appModel, "status.profile.account.missing");
            }

            VaultUser user = currentUserRecord.get();
            if (!PasswordHasher.matches(currentPassword, user.getPasswordHash())) {
                return dialogFieldError(appModel, DialogFieldIds.PROFILE_PASSWORD_CURRENT, "status.profile.current.password.invalid");
            }

            String newPasswordHash = PasswordHasher.hash(newPassword);
            if (!userDAO.updatePasswordHash(user.getId(), newPasswordHash)) {
                return dialogFormError(appModel, "status.profile.account.missing");
            }

            return DialogActionResult.successLocalToast(appModel.text("status.profile.password.updated"));
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.profile.password.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public String getEmptyDetailMessage(AppModel appModel) {
        if (!appModel.isAuthenticated()) {
            return appModel.text("detail.meta.empty.unauth");
        }
        return appModel.text("detail.meta.empty.auth");
    }

    public DialogActionResult createUrl(AppModel appModel, String urlInput, String titleInput, String notesInput, ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }

        String url = sanitize(urlInput);
        if (url.isBlank()) {
            return dialogFieldError(appModel, DialogFieldIds.URL, "dialog.validation.url.required");
        }

        String title = firstNonBlank(titleInput, appModel.text("save.default.urlTitle"));
        String notes = firstNonBlank(notesInput, url);
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
        LockConfigurationResult lockResult = applyLockConfiguration(appModel, item, null, lockOptions, null);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return saveNewItem(appModel, currentUser, item, lockResult.imageRecord(), "status.save.url.saved");
    }

    public DialogActionResult updateUrl(AppModel appModel,
                                        VaultItemFx existingItem,
                                        String urlInput,
                                        String titleInput,
                                        String notesInput,
                                        ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }
        if (existingItem == null) {
            return dialogFormError(appModel, "status.edit.select");
        }

        String url = sanitize(urlInput);
        if (url.isBlank()) {
            return dialogFieldError(appModel, DialogFieldIds.URL, "dialog.validation.url.required");
        }

        String title = firstNonBlank(titleInput, appModel.text("save.default.urlTitle"));
        String notes = firstNonBlank(notesInput, url);
        VaultItemFx updatedItem = copyItem(existingItem);
        updatedItem.setTitle(title);
        updatedItem.setContent(notes);
        updatedItem.setAiContext(buildContext("URL", title, notes));
        updatedItem.setItemType(AppModel.TYPE_URL);
        updatedItem.setTags(buildTags(AppModel.TYPE_URL, url));
        updatedItem.setSourceUrl(url);
        updatedItem.setUpdatedAt(LocalDateTime.now());
        LockConfigurationResult lockResult = applyLockConfiguration(appModel, updatedItem, existingItem, lockOptions, null);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecord(), "status.edit.url.updated");
    }

    public DialogActionResult createText(AppModel appModel, String titleInput, String contentInput, ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }

        String title = sanitize(titleInput);
        String content = sanitize(contentInput);
        DialogActionResult validationResult = validateTextInputs(appModel, title, content);
        if (validationResult != null) {
            return validationResult;
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
        LockConfigurationResult lockResult = applyLockConfiguration(appModel, item, null, lockOptions, null);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return saveNewItem(appModel, currentUser, item, lockResult.imageRecord(), "status.save.text.saved");
    }

    public DialogActionResult updateText(AppModel appModel,
                                         VaultItemFx existingItem,
                                         String titleInput,
                                         String contentInput,
                                         ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }
        if (existingItem == null) {
            return dialogFormError(appModel, "status.edit.select");
        }

        String title = sanitize(titleInput);
        String content = sanitize(contentInput);
        DialogActionResult validationResult = validateTextInputs(appModel, title, content);
        if (validationResult != null) {
            return validationResult;
        }

        VaultItemFx updatedItem = copyItem(existingItem);
        updatedItem.setTitle(title);
        updatedItem.setContent(content);
        updatedItem.setAiContext(buildContext("TEXT", title, content));
        updatedItem.setItemType(AppModel.TYPE_TEXT);
        updatedItem.setTags(buildTags(AppModel.TYPE_TEXT, content));
        updatedItem.setSourceUrl(null);
        updatedItem.setUpdatedAt(LocalDateTime.now());
        LockConfigurationResult lockResult = applyLockConfiguration(appModel, updatedItem, existingItem, lockOptions, null);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecord(), "status.edit.text.updated");
    }

    public DialogActionResult createImage(AppModel appModel, String titleInput, Path selectedImagePath, ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }

        ImageAssetLoadResult imageAssetResult = readImageAsset(appModel, selectedImagePath);
        if (!imageAssetResult.success()) {
            return imageAssetResult.failureResult();
        }

        ImageAssetData imageAsset = imageAssetResult.imageAsset();
        String title = firstNonBlank(titleInput, imageAsset.fileName());
        String aiContext = analyzeImageContext(imageAsset);
        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(imageAsset.fileName());
        item.setAiContext(aiContext);
        item.setItemType(AppModel.TYPE_IMAGE);
        item.setTags(buildTags(AppModel.TYPE_IMAGE, title + " " + imageAsset.fileName()));
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());
        item.setImageMimeType(imageAsset.mimeType());
        item.setImageByteCount(imageAsset.size());
        item.setCachedImageBytes(imageAsset.bytes());

        LockConfigurationResult lockResult = applyLockConfiguration(appModel, item, null, lockOptions, imageAsset);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return saveNewItem(appModel, currentUser, item, lockResult.imageRecord(), "status.save.image.saved");
    }

    public DialogActionResult updateImage(AppModel appModel,
                                          VaultItemFx existingItem,
                                          String titleInput,
                                          Path replacementImagePath,
                                          ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }
        if (existingItem == null) {
            return dialogFormError(appModel, "status.edit.select");
        }

        ImageAssetLoadResult imageAssetResult = resolveImageAssetForUpdate(appModel, currentUser, existingItem, replacementImagePath);
        if (!imageAssetResult.success()) {
            return imageAssetResult.failureResult();
        }

        ImageAssetData imageAsset = imageAssetResult.imageAsset();
        String title = firstNonBlank(titleInput, appModel.getResolvedTitle(existingItem));
        String aiContext = replacementImagePath == null
                ? firstNonBlank(appModel.getResolvedContext(existingItem), analyzeImageContext(imageAsset))
                : analyzeImageContext(imageAsset);

        VaultItemFx updatedItem = copyItem(existingItem);
        updatedItem.setTitle(title);
        updatedItem.setContent(imageAsset.fileName());
        updatedItem.setAiContext(aiContext);
        updatedItem.setItemType(AppModel.TYPE_IMAGE);
        updatedItem.setTags(buildTags(AppModel.TYPE_IMAGE, title + " " + imageAsset.fileName()));
        updatedItem.setSourceUrl(null);
        updatedItem.setUpdatedAt(LocalDateTime.now());
        updatedItem.setImageMimeType(imageAsset.mimeType());
        updatedItem.setImageByteCount(imageAsset.size());
        updatedItem.setCachedImageBytes(imageAsset.bytes());

        LockConfigurationResult lockResult = applyLockConfiguration(appModel, updatedItem, existingItem, lockOptions, imageAsset);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecord(), "status.edit.image.updated");
    }

    public boolean unlockItem(AppModel appModel, VaultItemFx item, String rawPassword) {
        if (item == null) {
            appModel.showErrorKey("status.lock.unlock.select");
            return false;
        }
        if (!item.isLocked()) {
            return true;
        }
        if (item.isUnlockedInSession()) {
            return true;
        }
        String password = safePasswordValue(rawPassword);
        if (password.isBlank()) {
            appModel.showErrorKey("dialog.validation.unlock.password.required");
            return false;
        }

        try {
            VaultItemFx unlockedItem = unlockItemInSession(appModel, item, password);
            appModel.showSuccessKey("status.lock.unlock.success", unlockedItem.getId());
            return true;
        } catch (SQLException exception) {
            appModel.showErrorKey("status.lock.unlock.error", safeMessage(exception));
            return false;
        } catch (IllegalArgumentException exception) {
            appModel.showErrorKey("status.lock.unlock.invalid");
            return false;
        } catch (IllegalStateException exception) {
            appModel.showErrorKey("status.lock.unlock.error", safeMessage(exception));
            return false;
        }
    }

    public DialogActionResult unlockItemInDialog(AppModel appModel, VaultItemFx item, String rawPassword) {
        if (item == null) {
            return dialogFormError(appModel, "status.lock.unlock.select");
        }
        if (!item.isLocked() || item.isUnlockedInSession()) {
            return DialogActionResult.successful();
        }

        String password = safePasswordValue(rawPassword);
        if (password.isBlank()) {
            return dialogFieldError(appModel, DialogFieldIds.UNLOCK_PASSWORD, "dialog.validation.unlock.password.required");
        }

        try {
            unlockItemInSession(appModel, item, password);
            return DialogActionResult.successful();
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.lock.unlock.error", safeMessage(exception));
        } catch (IllegalArgumentException exception) {
            return dialogFieldError(appModel, DialogFieldIds.UNLOCK_PASSWORD, "status.lock.unlock.invalid");
        } catch (IllegalStateException exception) {
            return dialogFormError(appModel, "status.lock.unlock.error", safeMessage(exception));
        }
    }

    public Optional<ImageAssetData> loadImageAsset(AppModel appModel, VaultItemFx item) {
        if (item == null
                || !AppModel.TYPE_IMAGE.equalsIgnoreCase(item.getItemType())
                || appModel.isLockedItemHidden(item)
                || !item.hasStoredImage()) {
            return Optional.empty();
        }

        String fileName = firstNonBlank(appModel.getResolvedContent(item), item.getTitle());
        if (item.isUnlockedInSession() && item.getUnlockedSession() != null && item.getUnlockedSession().imageBytes().length > 0) {
            return Optional.of(new ImageAssetData(fileName, item.getImageMimeType(), item.getUnlockedSession().imageBytes()));
        }

        byte[] cachedImageBytes = item.getCachedImageBytes();
        if (cachedImageBytes.length > 0) {
            return Optional.of(new ImageAssetData(fileName, item.getImageMimeType(), cachedImageBytes));
        }

        try {
            Optional<StoredImageRecord> storedImageRecord = loadStoredImageRecord(item.getOwnerId(), item.getId());
            if (storedImageRecord.isEmpty() || storedImageRecord.get().imageData().length == 0) {
                return Optional.empty();
            }

            item.setCachedImageBytes(storedImageRecord.get().imageData());
            item.setImageMimeType(firstNonBlank(storedImageRecord.get().mimeType(), item.getImageMimeType()));
            item.setImageByteCount(Math.max(item.getImageByteCount(), storedImageRecord.get().byteCount()));
            return Optional.of(new ImageAssetData(fileName, item.getImageMimeType(), item.getCachedImageBytes()));
        } catch (SQLException exception) {
            appModel.showErrorKey("status.image.load.error", safeMessage(exception));
            return Optional.empty();
        }
    }

    public boolean deleteSelected(AppModel appModel) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return false;
        }

        VaultItemFx selectedItem = appModel.getSelectedItem();
        if (selectedItem == null) {
            appModel.showErrorKey("status.delete.select");
            return false;
        }
        if (selectedItem.isLocked() && !selectedItem.isUnlockedInSession()) {
            appModel.showErrorKey("status.lock.unlock.required");
            return false;
        }

        appModel.setBusy(true);
        try {
            boolean deleted = vaultItemDAO.deleteById(currentUser.id(), selectedItem.getId());
            if (!deleted) {
                appModel.showErrorKey("status.delete.missing");
                return false;
            }
            appModel.removeItem(selectedItem.getId());
            appModel.showSuccessKey("status.delete.deleted", selectedItem.getId());
            return true;
        } catch (SQLException exception) {
            appModel.showErrorKey("status.delete.error", safeMessage(exception));
            return false;
        } finally {
            appModel.setBusy(false);
        }
    }

    public DialogActionResult deleteItem(AppModel appModel, VaultItemFx item) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }
        if (item == null) {
            return dialogFormError(appModel, "status.delete.select");
        }
        if (item.isLocked() && !item.isUnlockedInSession()) {
            return dialogFormError(appModel, "status.lock.unlock.required");
        }

        appModel.setBusy(true);
        try {
            boolean deleted = vaultItemDAO.deleteById(currentUser.id(), item.getId());
            if (!deleted) {
                return dialogFormError(appModel, "status.delete.missing");
            }
            appModel.removeItem(item.getId());
            return DialogActionResult.successMainToast(appModel.text("status.delete.deleted", item.getId()));
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.delete.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    private DialogActionResult saveNewItem(AppModel appModel,
                                           UserSession currentUser,
                                           VaultItemFx item,
                                           StoredImageRecord imageRecord,
                                           String successKey) {
        appModel.setBusy(true);
        try {
            VaultItemFx savedItem = vaultItemDAO.insert(currentUser.id(), item, imageRecord);
            appModel.addItem(savedItem);
            return DialogActionResult.successMainToast(appModel.text(successKey, savedItem.getId(), currentUser.id()));
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.save.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    private DialogActionResult updateExistingItem(AppModel appModel,
                                                  UserSession currentUser,
                                                  VaultItemFx item,
                                                  StoredImageRecord imageRecord,
                                                  String successKey) {
        appModel.setBusy(true);
        try {
            boolean updated = vaultItemDAO.update(currentUser.id(), item, imageRecord);
            if (!updated) {
                return dialogFormError(appModel, "status.edit.missing");
            }
            appModel.updateItem(item);
            return DialogActionResult.successMainToast(appModel.text(successKey, item.getId()));
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.edit.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    private ImageAssetLoadResult readImageAsset(AppModel appModel, Path imagePath) {
        if (imagePath == null) {
            return ImageAssetLoadResult.failed(dialogFieldError(appModel, DialogFieldIds.PATH, "dialog.validation.image.path.required"));
        }

        try {
            byte[] bytes = Files.readAllBytes(imagePath);
            if (bytes.length == 0) {
                return ImageAssetLoadResult.failed(dialogFieldError(appModel, DialogFieldIds.PATH, "status.save.image.read.error"));
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                return ImageAssetLoadResult.failed(dialogFieldError(
                        appModel,
                        DialogFieldIds.PATH,
                        "status.save.image.too.large",
                        MAX_IMAGE_BYTES / (1024 * 1024)));
            }

            String mimeType = Files.probeContentType(imagePath);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "image/png";
            }

            return ImageAssetLoadResult.success(new ImageAssetData(imagePath.getFileName().toString(), mimeType, bytes));
        } catch (IOException exception) {
            return ImageAssetLoadResult.failed(dialogFieldError(appModel, DialogFieldIds.PATH, "status.save.image.read.error"));
        }
    }

    private ImageAssetLoadResult resolveImageAssetForUpdate(AppModel appModel,
                                                            UserSession currentUser,
                                                            VaultItemFx existingItem,
                                                            Path replacementImagePath) {
        if (replacementImagePath != null) {
            return readImageAsset(appModel, replacementImagePath);
        }

        String fileName = firstNonBlank(appModel.getResolvedContent(existingItem), existingItem.getTitle());
        if (existingItem.isUnlockedInSession() && existingItem.getUnlockedSession() != null
                && existingItem.getUnlockedSession().imageBytes().length > 0) {
            return ImageAssetLoadResult.success(new ImageAssetData(
                    fileName,
                    existingItem.getImageMimeType(),
                    existingItem.getUnlockedSession().imageBytes()));
        }

        byte[] cachedImageBytes = existingItem.getCachedImageBytes();
        if (cachedImageBytes.length > 0) {
            return ImageAssetLoadResult.success(new ImageAssetData(fileName, existingItem.getImageMimeType(), cachedImageBytes));
        }

        try {
            Optional<StoredImageRecord> storedImageRecord = loadStoredImageRecord(currentUser.id(), existingItem.getId());
            if (storedImageRecord.isEmpty() || storedImageRecord.get().imageData().length == 0) {
                return ImageAssetLoadResult.failed(dialogFormError(appModel, "status.image.load.missing"));
            }
            existingItem.setCachedImageBytes(storedImageRecord.get().imageData());
            existingItem.setImageMimeType(firstNonBlank(storedImageRecord.get().mimeType(), existingItem.getImageMimeType()));
            existingItem.setImageByteCount(Math.max(existingItem.getImageByteCount(), storedImageRecord.get().byteCount()));
            return ImageAssetLoadResult.success(new ImageAssetData(
                    fileName,
                    existingItem.getImageMimeType(),
                    existingItem.getCachedImageBytes()));
        } catch (SQLException exception) {
            return ImageAssetLoadResult.failed(dialogFormError(appModel, "status.image.load.error", safeMessage(exception)));
        }
    }

    private DialogActionResult validateTextInputs(AppModel appModel, String title, String content) {
        DialogActionResult result = null;
        if (title.isBlank()) {
            result = addFieldError(result, appModel, DialogFieldIds.TITLE, "dialog.validation.text.title.required");
        }
        if (content.isBlank()) {
            result = addFieldError(result, appModel, DialogFieldIds.CONTENT, "dialog.validation.text.content.required");
        }
        return result;
    }

    private DialogActionResult validateProfilePasswordInputs(AppModel appModel,
                                                             String currentPassword,
                                                             String newPassword,
                                                             String confirmPassword) {
        DialogActionResult result = null;
        if (currentPassword.isBlank()) {
            result = addFieldError(result, appModel, DialogFieldIds.PROFILE_PASSWORD_CURRENT, "dialog.validation.profile.currentPassword.required");
        }
        if (newPassword.isBlank()) {
            result = addFieldError(result, appModel, DialogFieldIds.PROFILE_PASSWORD_NEW, "dialog.validation.profile.newPassword.required");
        }
        if (confirmPassword.isBlank()) {
            result = addFieldError(result, appModel, DialogFieldIds.PROFILE_PASSWORD_CONFIRM, "dialog.validation.profile.confirmPassword.required");
        }
        if (result != null) {
            return result;
        }
        if (!AccountValidator.isValidPassword(newPassword)) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_PASSWORD_NEW, "status.profile.password.min");
        }
        if (!newPassword.equals(confirmPassword)) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_PASSWORD_CONFIRM, "status.profile.password.confirm");
        }
        return null;
    }

    private DialogActionResult validateLockInputs(AppModel appModel, String password, String confirmPassword) {
        DialogActionResult result = null;
        if (password.isBlank()) {
            result = addFieldError(result, appModel, DialogFieldIds.LOCK_PASSWORD, "dialog.validation.lock.password.required");
        }
        if (confirmPassword.isBlank()) {
            result = addFieldError(result, appModel, DialogFieldIds.LOCK_CONFIRM, "dialog.validation.lock.confirm.required");
        }
        if (result != null) {
            return result;
        }
        if (!AccountValidator.isValidPassword(password)) {
            return dialogFieldError(appModel, DialogFieldIds.LOCK_PASSWORD, "status.lock.password.min");
        }
        if (!password.equals(confirmPassword)) {
            return dialogFieldError(appModel, DialogFieldIds.LOCK_CONFIRM, "status.lock.password.confirm");
        }
        return null;
    }

    private DialogActionResult dialogFieldError(AppModel appModel, String fieldId, String key, Object... args) {
        return DialogActionResult.fieldError(fieldId, appModel.text(key, args));
    }

    private DialogActionResult addFieldError(DialogActionResult currentResult,
                                             AppModel appModel,
                                             String fieldId,
                                             String key,
                                             Object... args) {
        DialogActionResult baseResult = currentResult == null ? DialogActionResult.failure() : currentResult;
        return baseResult.withFieldError(fieldId, appModel.text(key, args));
    }

    private DialogActionResult dialogFormError(AppModel appModel, String key, Object... args) {
        String message = appModel.text(key, args);
        return DialogActionResult.failure()
                .withFormMessage(message)
                .withLocalToast(ToastNotificationType.ERROR, message);
    }

    private DialogActionResult authRequiredDialogResult(AppModel appModel) {
        return dialogFormError(appModel, "status.auth.required");
    }

    private String analyzeImageContext(ImageAssetData imageAsset) {
        return geminiService.analyzeImage(imageAsset.bytes(), imageAsset.mimeType(), imageAsset.fileName());
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

    private LockConfigurationResult applyLockConfiguration(AppModel appModel,
                                                           VaultItemFx item,
                                                           VaultItemFx existingItem,
                                                           ItemLockOptions lockOptions,
                                                           ImageAssetData imageAsset) {
        ItemLockOptions safeLockOptions = lockOptions == null
                ? new ItemLockOptions(false, "", "")
                : lockOptions;

        if (!safeLockOptions.enabled()) {
            if (existingItem != null && existingItem.isLocked() && !existingItem.isUnlockedInSession()) {
                return LockConfigurationResult.failed(dialogFormError(appModel, "status.lock.unlock.required"));
            }
            clearLockState(item);
            return LockConfigurationResult.success(buildPlainImageRecord(item, imageAsset));
        }

        ProtectedItemData protectedItemData = new ProtectedItemData(
                item.getTitle(),
                item.getContent(),
                item.getAiContext(),
                item.getTags(),
                item.getSourceUrl());

        String password = safePasswordValue(safeLockOptions.password());
        String confirmPassword = safePasswordValue(safeLockOptions.confirmPassword());

        if (existingItem != null && existingItem.isLocked() && password.isBlank() && confirmPassword.isBlank()) {
            if (!existingItem.isUnlockedInSession()) {
                return LockConfigurationResult.failed(dialogFormError(appModel, "status.lock.unlock.required"));
            }
            ProtectedItemCrypto.LockedItemEnvelope envelope = protectedItemCrypto.relockWithExistingSession(
                    protectedItemData,
                    imageAsset == null ? null : imageAsset.bytes(),
                    existingItem);
            applyProtectedEnvelope(item, envelope);
            return LockConfigurationResult.success(buildProtectedImageRecord(item, envelope.encryptedImageData()));
        }

        DialogActionResult validationResult = validateLockInputs(appModel, password, confirmPassword);
        if (validationResult != null) {
            return LockConfigurationResult.failed(validationResult);
        }

        byte[] rawImageBytes = imageAsset == null ? new byte[0] : imageAsset.bytes();
        ProtectedItemCrypto.LockedItemEnvelope envelope = protectedItemCrypto.createNewLock(protectedItemData, rawImageBytes, password);
        applyProtectedEnvelope(item, envelope);
        return LockConfigurationResult.success(buildProtectedImageRecord(item, envelope.encryptedImageData()));
    }

    private StoredImageRecord buildPlainImageRecord(VaultItemFx item, ImageAssetData imageAsset) {
        if (item == null || imageAsset == null || imageAsset.bytes().length == 0) {
            return null;
        }
        item.setImageMimeType(imageAsset.mimeType());
        item.setImageByteCount(imageAsset.size());
        item.setCachedImageBytes(imageAsset.bytes());
        return new StoredImageRecord(imageAsset.mimeType(), imageAsset.size(), imageAsset.bytes(), null);
    }

    private StoredImageRecord buildProtectedImageRecord(VaultItemFx item, byte[] encryptedImageData) {
        if (item == null || encryptedImageData == null || encryptedImageData.length == 0) {
            return null;
        }
        item.clearCachedImageBytes();
        return new StoredImageRecord(item.getImageMimeType(), item.getImageByteCount(), null, encryptedImageData);
    }

    private void applyProtectedEnvelope(VaultItemFx item, ProtectedItemCrypto.LockedItemEnvelope envelope) {
        item.setTitle(VaultItemFx.LOCKED_TITLE_PLACEHOLDER);
        item.setContent(null);
        item.setAiContext(null);
        item.setTags(null);
        item.setSourceUrl(null);
        item.setLocked(true);
        item.setLockPasswordHash(envelope.passwordHash());
        item.setLockSalt(envelope.lockSalt());
        item.setLockPayload(envelope.encryptedPayload());
        item.setUnlockedSession(envelope.unlockedSession());
        item.clearCachedImageBytes();
    }

    private void clearLockState(VaultItemFx item) {
        item.setLocked(false);
        item.setLockPasswordHash("");
        item.setLockSalt("");
        item.setLockPayload("");
        item.clearUnlockedSession();
    }

    private VaultItemFx unlockItemInSession(AppModel appModel, VaultItemFx item, String password) throws SQLException {
        byte[] protectedImageData = loadStoredImageRecord(item.getOwnerId(), item.getId())
                .map(StoredImageRecord::protectedImageData)
                .orElseGet(() -> new byte[0]);
        UnlockedItemSession unlockedSession = protectedItemCrypto.unlock(item, protectedImageData, password);
        VaultItemFx unlockedItem = copyItem(item);
        unlockedItem.setUnlockedSession(unlockedSession);
        appModel.updateItem(unlockedItem);
        return unlockedItem;
    }

    private Optional<StoredImageRecord> loadStoredImageRecord(long userId, long itemId) throws SQLException {
        return vaultItemDAO.findStoredImageByItemId(userId, itemId);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private UserSession currentDialogUser(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            appModel.clearVault();
        }
        return currentUser;
    }

    private UserSession requireAuthenticatedUser(AppModel appModel) {
        UserSession currentUser = appModel.getCurrentUser();
        if (currentUser == null) {
            appModel.clearVault();
            appModel.showErrorKey("status.auth.required");
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
        appModel.resetArchiveFilters();
    }

    private VaultItemFx copyItem(VaultItemFx source) {
        VaultItemFx copy = new VaultItemFx();
        copy.setId(source.getId());
        copy.setOwnerId(source.getOwnerId());
        copy.setTitle(source.getTitle());
        copy.setContent(source.getContent());
        copy.setAiContext(source.getAiContext());
        copy.setItemType(source.getItemType());
        copy.setTags(source.getTags());
        copy.setSourceUrl(source.getSourceUrl());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setLocked(source.isLocked());
        copy.setLockPasswordHash(source.getLockPasswordHash());
        copy.setLockSalt(source.getLockSalt());
        copy.setLockPayload(source.getLockPayload());
        copy.setUnlockedSession(source.getUnlockedSession() == null ? null : source.getUnlockedSession().copy());
        copy.setImageMimeType(source.getImageMimeType());
        copy.setImageByteCount(source.getImageByteCount());
        copy.setCachedImageBytes(source.getCachedImageBytes());
        return copy;
    }

    private String safePasswordValue(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record ImageAssetLoadResult(ImageAssetData imageAsset, DialogActionResult failureResult) {

        private static ImageAssetLoadResult success(ImageAssetData imageAsset) {
            return new ImageAssetLoadResult(imageAsset, null);
        }

        private static ImageAssetLoadResult failed(DialogActionResult failureResult) {
            return new ImageAssetLoadResult(null, failureResult);
        }

        private boolean success() {
            return imageAsset != null;
        }
    }

    private record LockConfigurationResult(boolean success, StoredImageRecord imageRecord, DialogActionResult failureResult) {

        private static LockConfigurationResult success(StoredImageRecord imageRecord) {
            return new LockConfigurationResult(true, imageRecord, null);
        }

        private static LockConfigurationResult failed(DialogActionResult failureResult) {
            return new LockConfigurationResult(false, null, failureResult);
        }
    }
}
