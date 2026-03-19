package com.example.desktop.bll;

import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.UserDAO;
import com.example.desktop.dao.VaultItemDAO;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.ImageAssetData;
import com.example.desktop.model.ItemLockOptions;
import com.example.desktop.model.ProtectedItemData;
import com.example.desktop.model.StoredImageRecord;
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

    public String getEmptyDetailMessage(AppModel appModel) {
        if (!appModel.isAuthenticated()) {
            return appModel.text("detail.meta.empty.unauth");
        }
        return appModel.text("detail.meta.empty.auth");
    }

    public boolean createUrl(AppModel appModel, String urlInput, String titleInput, String notesInput, ItemLockOptions lockOptions) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return false;
        }

        String url = sanitize(urlInput);
        if (url.isBlank()) {
            appModel.showErrorKey("status.save.url.missing");
            return false;
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
            return false;
        }

        return saveNewItem(appModel, currentUser, item, lockResult.imageRecord(), "status.save.url.saved");
    }

    public boolean updateUrl(AppModel appModel,
                             VaultItemFx existingItem,
                             String urlInput,
                             String titleInput,
                             String notesInput,
                             ItemLockOptions lockOptions) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return false;
        }
        if (existingItem == null) {
            appModel.showErrorKey("status.edit.select");
            return false;
        }

        String url = sanitize(urlInput);
        if (url.isBlank()) {
            appModel.showErrorKey("status.save.url.missing");
            return false;
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
            return false;
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecord(), "status.edit.url.updated");
    }

    public boolean createText(AppModel appModel, String titleInput, String contentInput, ItemLockOptions lockOptions) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return false;
        }

        String title = sanitize(titleInput);
        String content = sanitize(contentInput);
        if (title.isBlank() || content.isBlank()) {
            appModel.showErrorKey("status.save.text.missing");
            return false;
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
            return false;
        }

        return saveNewItem(appModel, currentUser, item, lockResult.imageRecord(), "status.save.text.saved");
    }

    public boolean updateText(AppModel appModel,
                              VaultItemFx existingItem,
                              String titleInput,
                              String contentInput,
                              ItemLockOptions lockOptions) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return false;
        }
        if (existingItem == null) {
            appModel.showErrorKey("status.edit.select");
            return false;
        }

        String title = sanitize(titleInput);
        String content = sanitize(contentInput);
        if (title.isBlank() || content.isBlank()) {
            appModel.showErrorKey("status.save.text.missing");
            return false;
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
            return false;
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecord(), "status.edit.text.updated");
    }

    public boolean createImage(AppModel appModel, String titleInput, Path selectedImagePath, ItemLockOptions lockOptions) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return false;
        }

        Optional<ImageAssetData> imageAsset = readImageAsset(appModel, selectedImagePath);
        if (imageAsset.isEmpty()) {
            return false;
        }

        String title = firstNonBlank(titleInput, imageAsset.get().fileName());
        String aiContext = analyzeImageContext(imageAsset.get());
        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(imageAsset.get().fileName());
        item.setAiContext(aiContext);
        item.setItemType(AppModel.TYPE_IMAGE);
        item.setTags(buildTags(AppModel.TYPE_IMAGE, title + " " + imageAsset.get().fileName()));
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());
        item.setImageMimeType(imageAsset.get().mimeType());
        item.setImageByteCount(imageAsset.get().size());
        item.setCachedImageBytes(imageAsset.get().bytes());

        LockConfigurationResult lockResult = applyLockConfiguration(appModel, item, null, lockOptions, imageAsset.get());
        if (!lockResult.success()) {
            return false;
        }

        return saveNewItem(appModel, currentUser, item, lockResult.imageRecord(), "status.save.image.saved");
    }

    public boolean updateImage(AppModel appModel,
                               VaultItemFx existingItem,
                               String titleInput,
                               Path replacementImagePath,
                               ItemLockOptions lockOptions) {
        UserSession currentUser = requireAuthenticatedUser(appModel);
        if (currentUser == null) {
            return false;
        }
        if (existingItem == null) {
            appModel.showErrorKey("status.edit.select");
            return false;
        }

        Optional<ImageAssetData> imageAsset = resolveImageAssetForUpdate(appModel, currentUser, existingItem, replacementImagePath);
        if (imageAsset.isEmpty()) {
            return false;
        }

        String title = firstNonBlank(titleInput, appModel.getResolvedTitle(existingItem));
        String aiContext = replacementImagePath == null
                ? firstNonBlank(appModel.getResolvedContext(existingItem), analyzeImageContext(imageAsset.get()))
                : analyzeImageContext(imageAsset.get());

        VaultItemFx updatedItem = copyItem(existingItem);
        updatedItem.setTitle(title);
        updatedItem.setContent(imageAsset.get().fileName());
        updatedItem.setAiContext(aiContext);
        updatedItem.setItemType(AppModel.TYPE_IMAGE);
        updatedItem.setTags(buildTags(AppModel.TYPE_IMAGE, title + " " + imageAsset.get().fileName()));
        updatedItem.setSourceUrl(null);
        updatedItem.setUpdatedAt(LocalDateTime.now());
        updatedItem.setImageMimeType(imageAsset.get().mimeType());
        updatedItem.setImageByteCount(imageAsset.get().size());
        updatedItem.setCachedImageBytes(imageAsset.get().bytes());

        LockConfigurationResult lockResult = applyLockConfiguration(appModel, updatedItem, existingItem, lockOptions, imageAsset.get());
        if (!lockResult.success()) {
            return false;
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
        if (rawPassword == null || rawPassword.isBlank()) {
            appModel.showErrorKey("status.lock.password.required");
            return false;
        }

        try {
            byte[] protectedImageData = loadStoredImageRecord(item.getOwnerId(), item.getId())
                    .map(StoredImageRecord::protectedImageData)
                    .orElseGet(() -> new byte[0]);
            UnlockedItemSession unlockedSession = protectedItemCrypto.unlock(item, protectedImageData, rawPassword);
            VaultItemFx unlockedItem = copyItem(item);
            unlockedItem.setUnlockedSession(unlockedSession);
            appModel.updateItem(unlockedItem);
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

    private boolean saveNewItem(AppModel appModel,
                                UserSession currentUser,
                                VaultItemFx item,
                                StoredImageRecord imageRecord,
                                String successKey) {
        appModel.setBusy(true);
        try {
            VaultItemFx savedItem = vaultItemDAO.insert(currentUser.id(), item, imageRecord);
            appModel.addItem(savedItem);
            appModel.showSuccessKey(successKey, savedItem.getId(), currentUser.id());
            return true;
        } catch (SQLException exception) {
            appModel.showErrorKey("status.save.error", safeMessage(exception));
            return false;
        } finally {
            appModel.setBusy(false);
        }
    }

    private boolean updateExistingItem(AppModel appModel,
                                       UserSession currentUser,
                                       VaultItemFx item,
                                       StoredImageRecord imageRecord,
                                       String successKey) {
        appModel.setBusy(true);
        try {
            boolean updated = vaultItemDAO.update(currentUser.id(), item, imageRecord);
            if (!updated) {
                appModel.showErrorKey("status.edit.missing");
                return false;
            }
            appModel.updateItem(item);
            appModel.showSuccessKey(successKey, item.getId());
            return true;
        } catch (SQLException exception) {
            appModel.showErrorKey("status.edit.error", safeMessage(exception));
            return false;
        } finally {
            appModel.setBusy(false);
        }
    }

    private Optional<ImageAssetData> readImageAsset(AppModel appModel, Path imagePath) {
        if (imagePath == null) {
            appModel.showErrorKey("status.save.image.missing");
            return Optional.empty();
        }

        try {
            byte[] bytes = Files.readAllBytes(imagePath);
            if (bytes.length == 0) {
                appModel.showErrorKey("status.save.image.read.error");
                return Optional.empty();
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                appModel.showErrorKey("status.save.image.too.large", MAX_IMAGE_BYTES / (1024 * 1024));
                return Optional.empty();
            }

            String mimeType = Files.probeContentType(imagePath);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "image/png";
            }

            return Optional.of(new ImageAssetData(imagePath.getFileName().toString(), mimeType, bytes));
        } catch (IOException exception) {
            appModel.showErrorKey("status.save.image.read.error");
            return Optional.empty();
        }
    }

    private Optional<ImageAssetData> resolveImageAssetForUpdate(AppModel appModel,
                                                                UserSession currentUser,
                                                                VaultItemFx existingItem,
                                                                Path replacementImagePath) {
        if (replacementImagePath != null) {
            return readImageAsset(appModel, replacementImagePath);
        }

        String fileName = firstNonBlank(appModel.getResolvedContent(existingItem), existingItem.getTitle());
        if (existingItem.isUnlockedInSession() && existingItem.getUnlockedSession() != null
                && existingItem.getUnlockedSession().imageBytes().length > 0) {
            return Optional.of(new ImageAssetData(fileName, existingItem.getImageMimeType(), existingItem.getUnlockedSession().imageBytes()));
        }

        byte[] cachedImageBytes = existingItem.getCachedImageBytes();
        if (cachedImageBytes.length > 0) {
            return Optional.of(new ImageAssetData(fileName, existingItem.getImageMimeType(), cachedImageBytes));
        }

        try {
            Optional<StoredImageRecord> storedImageRecord = loadStoredImageRecord(currentUser.id(), existingItem.getId());
            if (storedImageRecord.isEmpty() || storedImageRecord.get().imageData().length == 0) {
                appModel.showErrorKey("status.image.load.missing");
                return Optional.empty();
            }
            existingItem.setCachedImageBytes(storedImageRecord.get().imageData());
            existingItem.setImageMimeType(firstNonBlank(storedImageRecord.get().mimeType(), existingItem.getImageMimeType()));
            existingItem.setImageByteCount(Math.max(existingItem.getImageByteCount(), storedImageRecord.get().byteCount()));
            return Optional.of(new ImageAssetData(fileName, existingItem.getImageMimeType(), existingItem.getCachedImageBytes()));
        } catch (SQLException exception) {
            appModel.showErrorKey("status.image.load.error", safeMessage(exception));
            return Optional.empty();
        }
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
                appModel.showErrorKey("status.lock.unlock.required");
                return LockConfigurationResult.failed();
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
                appModel.showErrorKey("status.lock.unlock.required");
                return LockConfigurationResult.failed();
            }
            ProtectedItemCrypto.LockedItemEnvelope envelope = protectedItemCrypto.relockWithExistingSession(
                    protectedItemData,
                    imageAsset == null ? null : imageAsset.bytes(),
                    existingItem);
            applyProtectedEnvelope(item, envelope);
            return LockConfigurationResult.success(buildProtectedImageRecord(item, envelope.encryptedImageData()));
        }

        if (password.isBlank() || confirmPassword.isBlank()) {
            appModel.showErrorKey("status.lock.password.required");
            return LockConfigurationResult.failed();
        }
        if (!AccountValidator.isValidPassword(password)) {
            appModel.showErrorKey("status.lock.password.min");
            return LockConfigurationResult.failed();
        }
        if (!password.equals(confirmPassword)) {
            appModel.showErrorKey("status.lock.password.confirm");
            return LockConfigurationResult.failed();
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

    private Optional<StoredImageRecord> loadStoredImageRecord(long userId, long itemId) throws SQLException {
        return vaultItemDAO.findStoredImageByItemId(userId, itemId);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
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

    private record LockConfigurationResult(boolean success, StoredImageRecord imageRecord) {

        private static LockConfigurationResult success(StoredImageRecord imageRecord) {
            return new LockConfigurationResult(true, imageRecord);
        }

        private static LockConfigurationResult failed() {
            return new LockConfigurationResult(false, null);
        }
    }
}
