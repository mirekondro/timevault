package com.example.desktop.bll;

import com.example.desktop.dao.AppInitializer;
import com.example.desktop.dao.UserDAO;
import com.example.desktop.dao.VaultItemDAO;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.DialogFieldIds;
import com.example.desktop.model.GalleryImageFx;
import com.example.desktop.model.ImageAssetData;
import com.example.desktop.model.ItemLockOptions;
import com.example.desktop.model.ProtectedItemData;
import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.ToastNotificationType;
import com.example.desktop.model.UnlockedItemSession;
import com.example.desktop.model.VaultItemFx;
import com.example.desktop.security.ProtectedItemCrypto;
import com.example.shared.model.VaultItem;
import com.example.shared.model.UserSession;
import com.example.shared.security.AccountValidator;
import com.example.shared.service.GeminiService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Business logic layer for the desktop application.
 */
public class VaultManager {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_FETCHED_URL_CONTENT = 8000;
    private static final String INVALID_URL_MARKER = "invalid-url";
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern META_DESCRIPTION_PATTERN = Pattern.compile(
            "(?is)<meta[^>]+name\\s*=\\s*['\"]description['\"][^>]+content\\s*=\\s*['\"](.*?)['\"][^>]*>|" +
                    "<meta[^>]+content\\s*=\\s*['\"](.*?)['\"][^>]+name\\s*=\\s*['\"]description['\"][^>]*>");

    private final VaultItemDAO vaultItemDAO;
    private final UserDAO userDAO;
    private final AppInitializer appInitializer;
    private final GeminiService geminiService;
    private final ProtectedItemCrypto protectedItemCrypto = new ProtectedItemCrypto();

    public VaultManager(VaultItemDAO vaultItemDAO,
                        UserDAO userDAO,
                        AppInitializer appInitializer,
                        GeminiService geminiService) {
        this.vaultItemDAO = vaultItemDAO;
        this.userDAO = userDAO;
        this.appInitializer = appInitializer;
        this.geminiService = geminiService;
    }

    public void initialize(AppModel appModel) {
        appModel.setBusy(true);
        try {
            appInitializer.initialize();
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
            UserSession session = userDAO.register(email, password);
            appModel.setCurrentUser(session);
            resetVaultFilters(appModel);
            appModel.clearRegisterForm();
            appModel.clearLoginForm();
            int itemCount = loadCurrentUserItems(appModel, session);
            appModel.showSuccessKey("status.auth.account.created", session.email(), itemCount);
        } catch (IllegalArgumentException exception) {
            handleRegisterError(appModel, exception);
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
            UserSession session = userDAO.authenticate(email, password);
            appModel.setCurrentUser(session);
            resetVaultFilters(appModel);
            appModel.clearLoginForm();
            appModel.clearRegisterForm();
            int itemCount = loadCurrentUserItems(appModel, session);
            appModel.showSuccessKey("status.auth.logged.in", session.email(), itemCount);
        } catch (IllegalArgumentException exception) {
            handleLoginError(appModel, exception);
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

        try {
            userDAO.logout();
        } catch (SQLException ignored) {
            // Local logout should still complete even if the backend is unavailable.
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
            UserSession updatedSession = userDAO.updateEmail(currentUser.id(), newEmail, currentPassword);
            appModel.setCurrentUser(updatedSession);
            return DialogActionResult.successLocalToast(appModel.text("status.profile.email.updated", newEmail));
        } catch (IllegalArgumentException exception) {
            return mapProfileEmailValidationError(appModel, exception);
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
            userDAO.updatePassword(currentUser.id(), currentPassword, newPassword, confirmPassword);
            return DialogActionResult.successLocalToast(appModel.text("status.profile.password.updated"));
        } catch (IllegalArgumentException exception) {
            return mapProfilePasswordValidationError(appModel, exception);
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

    public DialogActionResult createUrl(AppModel appModel,
                                        String urlInput,
                                        String titleInput,
                                        String summaryInput,
                                        String contentInput,
                                        String tagsInput,
                                        ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }

        String url = normalizeUrlInput(urlInput);
        if (url == null) {
            return dialogFieldError(appModel, DialogFieldIds.URL, invalidUrlKey(urlInput));
        }

        String title = firstNonBlank(titleInput, appModel.text("save.default.urlTitle"));
        String content = sanitize(contentInput);
        String aiContext = firstNonBlank(summaryInput, buildContext("URL", title, firstNonBlank(content, url)));
        String tags = resolveUrlTags(tagsInput, url, content, aiContext);
        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(content);
        item.setAiContext(aiContext);
        item.setItemType(AppModel.TYPE_URL);
        item.setTags(tags);
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
                                        String summaryInput,
                                        String contentInput,
                                        String tagsInput,
                                        ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }
        if (existingItem == null) {
            return dialogFormError(appModel, "status.edit.select");
        }

        String url = normalizeUrlInput(urlInput);
        if (url == null) {
            return dialogFieldError(appModel, DialogFieldIds.URL, invalidUrlKey(urlInput));
        }

        String title = firstNonBlank(titleInput, appModel.text("save.default.urlTitle"));
        String content = sanitize(contentInput);
        String aiContext = firstNonBlank(summaryInput, buildContext("URL", title, firstNonBlank(content, url)));
        String tags = resolveUrlTags(tagsInput, url, content, aiContext);
        VaultItemFx updatedItem = copyItem(existingItem);
        updatedItem.setTitle(title);
        updatedItem.setContent(content);
        updatedItem.setAiContext(aiContext);
        updatedItem.setItemType(AppModel.TYPE_URL);
        updatedItem.setTags(tags);
        updatedItem.setSourceUrl(url);
        updatedItem.setUpdatedAt(LocalDateTime.now());
        LockConfigurationResult lockResult = applyLockConfiguration(appModel, updatedItem, existingItem, lockOptions, null);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecord(), "status.edit.url.updated");
    }

    public UrlAnalysisResult analyzeUrl(String urlInput, boolean archiveContent) throws IOException {
        String normalizedUrl = normalizeUrlInput(urlInput);
        if (normalizedUrl == null) {
            throw new IllegalArgumentException(INVALID_URL_MARKER);
        }

        String pageContent = fetchWebpageContent(normalizedUrl);
        GeminiService.UrlSummaryResult summaryResult = geminiService.generateUrlSummaryResult(normalizedUrl, pageContent);
        VaultItem analyzedItem = summaryResult.item();
        String title = sanitize(analyzedItem.getTitle());
        String aiContext = firstNonBlank(
                analyzedItem.getAiContext(),
                buildContext("URL", firstNonBlank(title, normalizedUrl), firstNonBlank(pageContent, normalizedUrl)));
        String archivedContent = archiveContent ? pageContent : "";
        String tags = buildUrlTags(normalizedUrl, firstNonBlank(pageContent, archivedContent), aiContext);
        return new UrlAnalysisResult(normalizedUrl, title, aiContext, archivedContent, tags, summaryResult.aiGenerated());
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
        item.setGalleryImages(List.of(createGalleryImage(imageAsset, aiContext, 0)));
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
        updatedItem.setGalleryImages(List.of(createGalleryImage(imageAsset, aiContext, 0)));
        updatedItem.setImageMimeType(imageAsset.mimeType());
        updatedItem.setImageByteCount(imageAsset.size());
        updatedItem.setCachedImageBytes(imageAsset.bytes());

        LockConfigurationResult lockResult = applyLockConfiguration(appModel, updatedItem, existingItem, lockOptions, imageAsset);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecord(), "status.edit.image.updated");
    }

    public ImageGalleryAnalysisResult analyzeImageGallery(String titleInput,
                                                          String notesInput,
                                                          List<GalleryImageFx> galleryImages,
                                                          boolean refreshExistingAnalyses) {
        List<GalleryImageFx> normalizedImages = normalizeGalleryImages(galleryImages);
        if (normalizedImages.isEmpty()) {
            throw new IllegalArgumentException("No images selected.");
        }

        String title = firstNonBlank(sanitize(titleInput), defaultGalleryTitle(normalizedImages));
        String notes = sanitize(notesInput);

        List<GalleryImageFx> analyzedImages = analyzeGalleryImages(normalizedImages, refreshExistingAnalyses);
        String combinedSummary = buildImageGallerySummary(title, notes, analyzedImages);
        String tags = buildImageGalleryTags(title, notes, combinedSummary, analyzedImages);
        return new ImageGalleryAnalysisResult(title, combinedSummary, tags, analyzedImages);
    }

    public DialogActionResult createImage(AppModel appModel,
                                          String titleInput,
                                          String notesInput,
                                          String summaryInput,
                                          String tagsInput,
                                          List<GalleryImageFx> galleryImages,
                                          ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }

        List<GalleryImageFx> normalizedImages = normalizeGalleryImages(galleryImages);
        DialogActionResult validationResult = validateImageInputs(appModel, normalizedImages);
        if (validationResult != null) {
            return validationResult;
        }

        String title = firstNonBlank(sanitize(titleInput), defaultGalleryTitle(normalizedImages));
        String notes = sanitize(notesInput);
        List<GalleryImageFx> analyzedImages = ensureGalleryAnalysis(title, notes, normalizedImages);
        String aiContext = firstNonBlank(summaryInput, buildImageGallerySummary(title, notes, analyzedImages));
        String tags = firstNonBlank(normalizeManualTags(tagsInput), buildImageGalleryTags(title, notes, aiContext, analyzedImages));

        VaultItemFx item = new VaultItemFx();
        item.setTitle(title);
        item.setContent(notes);
        item.setAiContext(aiContext);
        item.setItemType(AppModel.TYPE_IMAGE);
        item.setTags(tags);
        item.setSourceUrl(null);
        item.setOwnerId(currentUser.id());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());
        item.setGalleryImages(analyzedImages);

        GalleryLockConfigurationResult lockResult = applyGalleryLockConfiguration(appModel, item, null, lockOptions, analyzedImages);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return saveNewItem(appModel, currentUser, item, lockResult.imageRecords(), "status.save.image.saved");
    }

    public DialogActionResult updateImage(AppModel appModel,
                                          VaultItemFx existingItem,
                                          String titleInput,
                                          String notesInput,
                                          String summaryInput,
                                          String tagsInput,
                                          List<GalleryImageFx> galleryImages,
                                          ItemLockOptions lockOptions) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }
        if (existingItem == null) {
            return dialogFormError(appModel, "status.edit.select");
        }

        List<GalleryImageFx> normalizedImages = normalizeGalleryImages(galleryImages);
        DialogActionResult validationResult = validateImageInputs(appModel, normalizedImages);
        if (validationResult != null) {
            return validationResult;
        }

        String title = firstNonBlank(sanitize(titleInput), appModel.getResolvedTitle(existingItem), defaultGalleryTitle(normalizedImages));
        String notes = sanitize(notesInput);
        List<GalleryImageFx> analyzedImages = ensureGalleryAnalysis(title, notes, normalizedImages);
        String aiContext = firstNonBlank(summaryInput, buildImageGallerySummary(title, notes, analyzedImages));
        String tags = firstNonBlank(normalizeManualTags(tagsInput), buildImageGalleryTags(title, notes, aiContext, analyzedImages));

        VaultItemFx updatedItem = copyItem(existingItem);
        updatedItem.setTitle(title);
        updatedItem.setContent(notes);
        updatedItem.setAiContext(aiContext);
        updatedItem.setItemType(AppModel.TYPE_IMAGE);
        updatedItem.setTags(tags);
        updatedItem.setSourceUrl(null);
        updatedItem.setUpdatedAt(LocalDateTime.now());
        updatedItem.setGalleryImages(analyzedImages);

        GalleryLockConfigurationResult lockResult = applyGalleryLockConfiguration(appModel, updatedItem, existingItem, lockOptions, analyzedImages);
        if (!lockResult.success()) {
            return lockResult.failureResult();
        }

        return updateExistingItem(appModel, currentUser, updatedItem, lockResult.imageRecords(), "status.edit.image.updated");
    }

    public List<GalleryImageFx> loadImageGallery(AppModel appModel, VaultItemFx item) {
        if (item == null
                || !AppModel.TYPE_IMAGE.equalsIgnoreCase(item.getItemType())
                || appModel.isLockedItemHidden(item)
                || !item.hasStoredImage()) {
            return List.of();
        }

        if (item.isUnlockedInSession() && item.getUnlockedSession() != null && !item.getUnlockedSession().galleryImages().isEmpty()) {
            item.setGalleryImages(item.getUnlockedSession().galleryImages());
            return item.getGalleryImages();
        }

        List<GalleryImageFx> existingImages = item.getGalleryImages();
        boolean hasAllBytes = !existingImages.isEmpty() && existingImages.stream().allMatch(GalleryImageFx::hasCachedBytes);
        if (hasAllBytes) {
            return existingImages;
        }

        try {
            List<StoredImageRecord> storedImages = loadStoredImageRecords(item.getOwnerId(), item.getId());
            if (storedImages.isEmpty()) {
                return existingImages;
            }
            List<GalleryImageFx> loadedImages = mapGalleryImages(storedImages, true);
            item.setGalleryImages(loadedImages);
            return item.getGalleryImages();
        } catch (SQLException exception) {
            appModel.showErrorKey("status.image.load.error", safeMessage(exception));
            return existingImages;
        }
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
            VaultItemFx deletedItem = copyItem(selectedItem);
            LocalDateTime now = LocalDateTime.now();
            deletedItem.setDeletedAt(now);
            deletedItem.setUpdatedAt(now);
            normalizeStoredItemState(deletedItem);
            appModel.updateItem(deletedItem);
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
            VaultItemFx deletedItem = copyItem(item);
            LocalDateTime now = LocalDateTime.now();
            deletedItem.setDeletedAt(now);
            deletedItem.setUpdatedAt(now);
            normalizeStoredItemState(deletedItem);
            appModel.updateItem(deletedItem);
            return DialogActionResult.successMainToast(appModel.text("status.delete.deleted", item.getId()));
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.delete.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    public DialogActionResult restoreItem(AppModel appModel, VaultItemFx item) {
        UserSession currentUser = currentDialogUser(appModel);
        if (currentUser == null) {
            return authRequiredDialogResult(appModel);
        }
        if (item == null) {
            return dialogFormError(appModel, "status.restore.select");
        }
        if (!item.isDeleted()) {
            return dialogFormError(appModel, "status.restore.missing");
        }
        if (item.isLocked() && !item.isUnlockedInSession()) {
            return dialogFormError(appModel, "status.lock.unlock.required");
        }

        appModel.setBusy(true);
        try {
            boolean restored = vaultItemDAO.restoreById(currentUser.id(), item.getId());
            if (!restored) {
                return dialogFormError(appModel, "status.restore.missing");
            }
            VaultItemFx restoredItem = copyItem(item);
            restoredItem.setUpdatedAt(LocalDateTime.now());
            restoredItem.setDeletedAt(null);
            normalizeStoredItemState(restoredItem);
            appModel.updateItem(restoredItem);
            return DialogActionResult.successMainToast(appModel.text("status.restore.restored", item.getId()));
        } catch (SQLException exception) {
            return dialogFormError(appModel, "status.restore.error", safeMessage(exception));
        } finally {
            appModel.setBusy(false);
        }
    }

    private DialogActionResult saveNewItem(AppModel appModel,
                                           UserSession currentUser,
                                           VaultItemFx item,
                                           StoredImageRecord imageRecord,
                                           String successKey) {
        return saveNewItem(appModel, currentUser, item, imageRecord == null ? List.of() : List.of(imageRecord), successKey);
    }

    private DialogActionResult saveNewItem(AppModel appModel,
                                           UserSession currentUser,
                                           VaultItemFx item,
                                           List<StoredImageRecord> imageRecords,
                                           String successKey) {
        appModel.setBusy(true);
        try {
            VaultItemFx savedItem = vaultItemDAO.insert(currentUser.id(), item, imageRecords);
            normalizeStoredItemState(savedItem);
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
        return updateExistingItem(appModel, currentUser, item, imageRecord == null ? List.of() : List.of(imageRecord), successKey);
    }

    private DialogActionResult updateExistingItem(AppModel appModel,
                                                  UserSession currentUser,
                                                  VaultItemFx item,
                                                  List<StoredImageRecord> imageRecords,
                                                  String successKey) {
        appModel.setBusy(true);
        try {
            normalizeStoredItemState(item);
            boolean updated = vaultItemDAO.update(currentUser.id(), item, imageRecords);
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

    private DialogActionResult validateImageInputs(AppModel appModel, List<GalleryImageFx> galleryImages) {
        if (galleryImages == null || galleryImages.isEmpty()) {
            return dialogFieldError(appModel, DialogFieldIds.PATH, "dialog.validation.image.path.required");
        }
        return null;
    }

    private List<GalleryImageFx> normalizeGalleryImages(List<GalleryImageFx> galleryImages) {
        if (galleryImages == null || galleryImages.isEmpty()) {
            return List.of();
        }

        List<GalleryImageFx> normalizedImages = new ArrayList<>();
        int displayOrder = 0;
        for (GalleryImageFx sourceImage : galleryImages) {
            if (sourceImage == null) {
                continue;
            }
            GalleryImageFx normalizedImage = sourceImage.copy();
            normalizedImage.setDisplayOrder(displayOrder++);
            if (normalizedImage.getByteCount() <= 0L && normalizedImage.hasCachedBytes()) {
                normalizedImage.setByteCount(normalizedImage.getCachedImageBytes().length);
            }
            if (normalizedImage.getByteCount() > 0L) {
                normalizedImages.add(normalizedImage);
            }
        }
        return normalizedImages;
    }

    private List<GalleryImageFx> analyzeGalleryImages(List<GalleryImageFx> galleryImages, boolean refreshExistingAnalyses) {
        List<GalleryImageFx> analyzedImages = new ArrayList<>();
        for (GalleryImageFx sourceImage : normalizeGalleryImages(galleryImages)) {
            GalleryImageFx analyzedImage = sourceImage.copy();
            if (refreshExistingAnalyses || analyzedImage.getAiContext().isBlank()) {
                analyzedImage.setAiContext(geminiService.analyzeImage(
                        analyzedImage.getCachedImageBytes(),
                        analyzedImage.getMimeType(),
                        analyzedImage.getFileName()));
            }
            analyzedImages.add(analyzedImage);
        }
        return analyzedImages;
    }

    private List<GalleryImageFx> ensureGalleryAnalysis(String title, String notes, List<GalleryImageFx> galleryImages) {
        List<GalleryImageFx> analyzedImages = analyzeGalleryImages(galleryImages, false);
        String summary = buildImageGallerySummary(title, notes, analyzedImages);
        if (summary.isBlank()) {
            return analyzeGalleryImages(galleryImages, true);
        }
        return analyzedImages;
    }

    private String buildImageGallerySummary(String title, String notes, List<GalleryImageFx> galleryImages) {
        List<String> imageAnalyses = galleryImages == null ? List.of() : galleryImages.stream()
                .map(GalleryImageFx::getAiContext)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return geminiService.generateImageGallerySummary(title, notes, imageAnalyses);
    }

    private String buildImageGalleryTags(String title,
                                         String notes,
                                         String combinedSummary,
                                         List<GalleryImageFx> galleryImages) {
        Set<String> tags = new LinkedHashSet<>();
        addTag(tags, AppModel.TYPE_IMAGE);
        addTag(tags, LocalDate.now().toString());
        addTag(tags, galleryImages == null || galleryImages.size() <= 1 ? "Single image" : galleryImages.size() + " images");

        String searchableText = (sanitize(title) + " " + sanitize(notes) + " " + sanitize(combinedSummary) + " "
                + concatenateImageSearchText(galleryImages)).toLowerCase(Locale.ROOT);

        if (containsAny(searchableText, "diagram", "chart", "graph", "table")) {
            addTag(tags, "Reference");
        }
        if (containsAny(searchableText, "document", "receipt", "invoice", "report", "form")) {
            addTag(tags, "Document");
        }
        if (containsAny(searchableText, "screenshot", "ui", "dashboard", "interface", "app")) {
            addTag(tags, "Screenshot");
        }
        if (containsAny(searchableText, "photo", "scene", "landscape", "portrait")) {
            addTag(tags, "Photo");
        }
        if (containsAny(searchableText, "whiteboard", "notes", "handwritten")) {
            addTag(tags, "Notes");
        }
        if (containsAny(searchableText, "presentation", "slide")) {
            addTag(tags, "Presentation");
        }

        return String.join(", ", tags);
    }

    private String concatenateImageSearchText(List<GalleryImageFx> galleryImages) {
        if (galleryImages == null || galleryImages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (GalleryImageFx image : galleryImages) {
            if (image == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(sanitize(image.getFileName())).append(' ')
                    .append(sanitize(image.getAiContext())).append(' ')
                    .append(sanitize(image.getMimeType()));
        }
        return builder.toString().trim();
    }

    private String defaultGalleryTitle(List<GalleryImageFx> galleryImages) {
        if (galleryImages == null || galleryImages.isEmpty()) {
            return "Image gallery";
        }
        if (galleryImages.size() == 1) {
            return extractFileStem(galleryImages.getFirst().getFileName());
        }
        return "Image gallery (" + galleryImages.size() + " images)";
    }

    private String extractFileStem(String fileName) {
        String sanitized = sanitize(fileName);
        if (sanitized.isBlank()) {
            return "Image";
        }
        int lastDot = sanitized.lastIndexOf('.');
        if (lastDot <= 0) {
            return sanitized;
        }
        return sanitized.substring(0, lastDot);
    }

    private GalleryImageFx createGalleryImage(ImageAssetData imageAsset, String aiContext, int displayOrder) {
        GalleryImageFx galleryImage = new GalleryImageFx();
        galleryImage.setFileName(imageAsset == null ? "" : imageAsset.fileName());
        galleryImage.setAiContext(aiContext);
        galleryImage.setMimeType(imageAsset == null ? "application/octet-stream" : imageAsset.mimeType());
        galleryImage.setByteCount(imageAsset == null ? 0L : imageAsset.size());
        galleryImage.setDisplayOrder(displayOrder);
        galleryImage.setCachedImageBytes(imageAsset == null ? new byte[0] : imageAsset.bytes());
        return galleryImage;
    }

    private List<GalleryImageFx> mapGalleryImages(List<StoredImageRecord> imageRecords, boolean includeBytes) {
        if (imageRecords == null || imageRecords.isEmpty()) {
            return List.of();
        }
        List<GalleryImageFx> galleryImages = new ArrayList<>();
        for (StoredImageRecord imageRecord : imageRecords) {
            if (imageRecord == null || imageRecord.byteCount() <= 0L) {
                continue;
            }
            GalleryImageFx galleryImage = new GalleryImageFx();
            galleryImage.setId(imageRecord.id() == null ? 0L : imageRecord.id());
            galleryImage.setFileName(imageRecord.fileName());
            galleryImage.setAiContext(imageRecord.aiContext());
            galleryImage.setMimeType(imageRecord.mimeType());
            galleryImage.setByteCount(imageRecord.byteCount());
            galleryImage.setDisplayOrder(imageRecord.displayOrder());
            if (includeBytes && imageRecord.imageData().length > 0) {
                galleryImage.setCachedImageBytes(imageRecord.imageData());
            }
            galleryImages.add(galleryImage);
        }
        return galleryImages;
    }

    private List<StoredImageRecord> buildPlainGalleryImageRecords(List<GalleryImageFx> galleryImages) {
        if (galleryImages == null || galleryImages.isEmpty()) {
            return List.of();
        }
        List<StoredImageRecord> imageRecords = new ArrayList<>();
        for (GalleryImageFx galleryImage : normalizeGalleryImages(galleryImages)) {
            imageRecords.add(new StoredImageRecord(
                    galleryImage.getId() > 0L ? galleryImage.getId() : null,
                    galleryImage.getFileName(),
                    galleryImage.getAiContext(),
                    galleryImage.getMimeType(),
                    galleryImage.getByteCount(),
                    galleryImage.getDisplayOrder(),
                    "",
                    galleryImage.getCachedImageBytes(),
                    new byte[0]));
        }
        return imageRecords;
    }

    private String resolveUrlTags(String tagsInput, String url, String content, String aiContext) {
        String manualTags = normalizeManualTags(tagsInput);
        if (!manualTags.isBlank()) {
            return manualTags;
        }
        return buildUrlTags(url, content, aiContext);
    }

    private String buildUrlTags(String url, String content, String aiContext) {
        Set<String> tags = new LinkedHashSet<>();
        addTag(tags, AppModel.TYPE_URL);
        addTag(tags, LocalDate.now().toString());

        String normalizedUrl = sanitize(url).toLowerCase(Locale.ROOT);
        String searchableText = (sanitize(content) + " " + sanitize(aiContext)).toLowerCase(Locale.ROOT);

        String host = extractHostLabel(normalizedUrl);
        if (!host.isBlank()) {
            addTag(tags, host);
        }

        if (normalizedUrl.contains("github.com")) {
            addTag(tags, "GitHub");
            addTag(tags, "Code");
        } else if (normalizedUrl.contains("medium.com")) {
            addTag(tags, "Medium");
            addTag(tags, "Article");
        } else if (normalizedUrl.contains("twitter.com") || normalizedUrl.contains("x.com")) {
            addTag(tags, "Twitter");
            addTag(tags, "Social");
        } else if (normalizedUrl.contains("youtube.com") || normalizedUrl.contains("youtu.be")) {
            addTag(tags, "YouTube");
            addTag(tags, "Video");
        } else if (normalizedUrl.contains("reddit.com")) {
            addTag(tags, "Reddit");
            addTag(tags, "Discussion");
        } else if (normalizedUrl.contains("stackoverflow.com")) {
            addTag(tags, "StackOverflow");
            addTag(tags, "Q&A");
        } else if (normalizedUrl.contains("linkedin.com")) {
            addTag(tags, "LinkedIn");
            addTag(tags, "Professional");
        } else if (normalizedUrl.contains("docs.google.com")) {
            addTag(tags, "Google Docs");
            addTag(tags, "Document");
        } else if (normalizedUrl.contains("figma.com")) {
            addTag(tags, "Figma");
            addTag(tags, "Design");
        }

        if (containsAny(searchableText, "tutorial", "guide", "how to", "step-by-step")) {
            addTag(tags, "Tutorial");
        }
        if (containsAny(searchableText, "documentation", "docs", "reference", "api")) {
            addTag(tags, "Documentation");
        }
        if (containsAny(searchableText, "article", "blog", "post")) {
            addTag(tags, "Article");
        }
        if (containsAny(searchableText, "research", "study", "analysis", "findings")) {
            addTag(tags, "Research");
        }
        if (containsAny(searchableText, "product", "pricing", "buy", "cart")) {
            addTag(tags, "Product");
        }
        if (containsAny(searchableText, "video", "course", "watch")) {
            addTag(tags, "Video");
        }
        if (containsAny(searchableText, "table", "chart", "graph", "diagram")) {
            addTag(tags, "Data");
        }
        if (containsAny(searchableText, "javascript", "js")) {
            addTag(tags, "JavaScript");
        }
        if (searchableText.contains("python")) {
            addTag(tags, "Python");
        }
        if (searchableText.contains("java") && !searchableText.contains("javascript")) {
            addTag(tags, "Java");
        }
        if (searchableText.contains("react")) {
            addTag(tags, "React");
        }
        if (searchableText.contains("docker")) {
            addTag(tags, "Docker");
        }
        if (searchableText.contains("sql")) {
            addTag(tags, "SQL");
        }

        return String.join(", ", tags);
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
        List<GalleryImageFx> galleryImages = imageAsset == null ? List.of() : item.getGalleryImages();
        GalleryLockConfigurationResult galleryLockResult = applyGalleryLockConfiguration(
                appModel,
                item,
                existingItem,
                lockOptions,
                galleryImages);
        if (!galleryLockResult.success()) {
            return LockConfigurationResult.failed(galleryLockResult.failureResult());
        }
        return LockConfigurationResult.success(primaryImageRecord(galleryLockResult.imageRecords()));
    }

    private GalleryLockConfigurationResult applyGalleryLockConfiguration(AppModel appModel,
                                                                        VaultItemFx item,
                                                                        VaultItemFx existingItem,
                                                                        ItemLockOptions lockOptions,
                                                                        List<GalleryImageFx> galleryImages) {
        ItemLockOptions safeLockOptions = lockOptions == null
                ? new ItemLockOptions(false, "", "")
                : lockOptions;
        List<GalleryImageFx> normalizedImages = normalizeGalleryImages(galleryImages);

        if (!safeLockOptions.enabled()) {
            if (existingItem != null && existingItem.isLocked() && !existingItem.isUnlockedInSession()) {
                return GalleryLockConfigurationResult.failed(dialogFormError(appModel, "status.lock.unlock.required"));
            }
            clearLockState(item);
            item.setGalleryImages(normalizedImages);
            return GalleryLockConfigurationResult.success(buildPlainGalleryImageRecords(normalizedImages));
        }

        ProtectedItemData protectedItemData = new ProtectedItemData(
                item.getTitle(),
                item.getContent(),
                item.getAiContext(),
                item.getTags(),
                item.getSourceUrl());
        String password = safePasswordValue(safeLockOptions.password());
        String confirmPassword = safePasswordValue(safeLockOptions.confirmPassword());
        List<StoredImageRecord> plainImageRecords = buildPlainGalleryImageRecords(normalizedImages);

        if (existingItem != null && existingItem.isLocked() && password.isBlank() && confirmPassword.isBlank()) {
            if (!existingItem.isUnlockedInSession()) {
                return GalleryLockConfigurationResult.failed(dialogFormError(appModel, "status.lock.unlock.required"));
            }
            ProtectedItemCrypto.LockedItemEnvelope envelope = protectedItemCrypto.relockWithExistingSession(
                    protectedItemData,
                    plainImageRecords,
                    existingItem);
            applyProtectedEnvelope(item, envelope);
            return GalleryLockConfigurationResult.success(envelope.encryptedImages());
        }

        DialogActionResult validationResult = validateLockInputs(appModel, password, confirmPassword);
        if (validationResult != null) {
            return GalleryLockConfigurationResult.failed(validationResult);
        }

        ProtectedItemCrypto.LockedItemEnvelope envelope = protectedItemCrypto.createNewLock(
                protectedItemData,
                plainImageRecords,
                password);
        applyProtectedEnvelope(item, envelope);
        return GalleryLockConfigurationResult.success(envelope.encryptedImages());
    }

    private StoredImageRecord buildPlainImageRecord(VaultItemFx item, ImageAssetData imageAsset) {
        if (item == null || imageAsset == null || imageAsset.bytes().length == 0) {
            return null;
        }
        item.setImageMimeType(imageAsset.mimeType());
        item.setImageByteCount(imageAsset.size());
        item.setCachedImageBytes(imageAsset.bytes());
        return primaryImageRecord(buildPlainGalleryImageRecords(item.getGalleryImages()));
    }

    private StoredImageRecord buildProtectedImageRecord(VaultItemFx item, List<StoredImageRecord> encryptedImageRecords) {
        if (item == null || encryptedImageRecords == null || encryptedImageRecords.isEmpty()) {
            return null;
        }
        item.clearCachedImageBytes();
        return primaryImageRecord(encryptedImageRecords);
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
        item.setGalleryImages(mapGalleryImages(envelope.encryptedImages(), false));
        // Once the locked item is saved, the UI should immediately return to the protected view.
        item.clearUnlockedSession();
        item.clearCachedImageBytes();
    }

    private void clearLockState(VaultItemFx item) {
        item.setLocked(false);
        item.setLockPasswordHash("");
        item.setLockSalt("");
        item.setLockPayload("");
        item.clearUnlockedSession();
    }

    private void normalizeStoredItemState(VaultItemFx item) {
        if (item == null || !item.isLocked()) {
            return;
        }
        item.clearUnlockedSession();
        item.clearCachedImageBytes();
    }

    private VaultItemFx unlockItemInSession(AppModel appModel, VaultItemFx item, String password) throws SQLException {
        List<StoredImageRecord> protectedImages = loadStoredImageRecords(item.getOwnerId(), item.getId());
        UnlockedItemSession unlockedSession = protectedItemCrypto.unlock(item, protectedImages, password);
        VaultItemFx unlockedItem = copyItem(item);
        unlockedItem.setUnlockedSession(unlockedSession);
        unlockedItem.setGalleryImages(unlockedSession.galleryImages());
        appModel.updateItem(unlockedItem);
        return unlockedItem;
    }

    private Optional<StoredImageRecord> loadStoredImageRecord(long userId, long itemId) throws SQLException {
        return loadStoredImageRecords(userId, itemId).stream().findFirst();
    }

    private List<StoredImageRecord> loadStoredImageRecords(long userId, long itemId) throws SQLException {
        return vaultItemDAO.findStoredImagesByItemId(userId, itemId);
    }

    private StoredImageRecord primaryImageRecord(List<StoredImageRecord> imageRecords) {
        if (imageRecords == null || imageRecords.isEmpty()) {
            return null;
        }
        return imageRecords.getFirst();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeUrlInput(String urlInput) {
        String url = sanitize(urlInput);
        if (url.isBlank()) {
            return null;
        }

        if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            url = "https://" + url;
        }

        try {
            URI uri = URI.create(url);
            String scheme = sanitize(uri.getScheme()).toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String invalidUrlKey(String urlInput) {
        return sanitize(urlInput).isBlank() ? "dialog.validation.url.required" : "dialog.validation.url.invalid";
    }

    private String fetchWebpageContent(String url) throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(12))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (TimeVault Desktop)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }

            String readableText = extractReadableText(response.body());
            if (readableText.isBlank()) {
                throw new IOException("No readable content found.");
            }
            return truncate(readableText, MAX_FETCHED_URL_CONTENT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("The page analysis was interrupted.", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("The page URL could not be fetched.", exception);
        }
    }

    private String extractReadableText(String html) {
        String pageTitle = decodeHtmlEntities(extractPatternGroup(html, TITLE_PATTERN));
        String description = decodeHtmlEntities(extractMetaDescription(html));
        String strippedText = html == null ? "" : html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ")
                .replaceAll("(?is)<svg[^>]*>.*?</svg>", " ")
                .replaceAll("(?is)<br\\s*/?>", ". ")
                .replaceAll("(?is)</(p|div|section|article|aside|main|header|footer|h[1-6]|li|ul|ol|table|tr|td|th|blockquote)>", ". ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("(?:\\.\\s*){2,}", ". ")
                .trim();

        StringBuilder combined = new StringBuilder();
        appendDistinctText(combined, pageTitle);
        appendDistinctText(combined, description);
        appendDistinctText(combined, decodeHtmlEntities(strippedText));
        return combined.toString().trim();
    }

    private String extractMetaDescription(String html) {
        Matcher matcher = META_DESCRIPTION_PATTERN.matcher(html == null ? "" : html);
        if (!matcher.find()) {
            return "";
        }
        return firstNonBlank(matcher.group(1), matcher.group(2));
    }

    private String extractPatternGroup(String source, Pattern pattern) {
        if (source == null || pattern == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private void appendDistinctText(StringBuilder builder, String value) {
        String cleaned = sanitize(value);
        if (cleaned.isBlank()) {
            return;
        }
        String existing = builder.toString().toLowerCase(Locale.ROOT);
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (existing.contains(normalized)) {
            return;
        }
        if (builder.length() > 0) {
            char lastCharacter = builder.charAt(builder.length() - 1);
            if (lastCharacter == '.' || lastCharacter == '!' || lastCharacter == '?') {
                builder.append(' ');
            } else {
                builder.append(". ");
            }
        }
        builder.append(cleaned);
    }

    private String decodeHtmlEntities(String value) {
        return sanitize(value)
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private String truncate(String value, int maxLength) {
        String sanitized = sanitize(value);
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength) + "...";
    }

    private String normalizeManualTags(String value) {
        String sanitized = sanitize(value);
        if (sanitized.isBlank()) {
            return "";
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : sanitized.split("[,\\n]")) {
            String cleaned = sanitize(token);
            if (!cleaned.isBlank()) {
                tokens.add(cleaned);
            }
        }
        return String.join(", ", tokens);
    }

    private void addTag(Set<String> tags, String candidate) {
        String cleaned = sanitize(candidate);
        if (cleaned.isBlank() || tags == null) {
            return;
        }
        for (String existing : tags) {
            if (existing.equalsIgnoreCase(cleaned)) {
                return;
            }
        }
        tags.add(cleaned);
    }

    private boolean containsAny(String value, String... needles) {
        String normalized = sanitize(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extractHostLabel(String url) {
        String normalizedUrl = sanitize(url);
        if (normalizedUrl.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(normalizedUrl).getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            String cleaned = host.replaceFirst("^(www\\.)", "");
            int lastDot = cleaned.lastIndexOf('.');
            if (lastDot > 0) {
                cleaned = cleaned.substring(0, lastDot);
            }
            String[] segments = cleaned.replace('-', ' ').split("\\s+");
            StringBuilder label = new StringBuilder();
            for (String segment : segments) {
                if (segment.isBlank()) {
                    continue;
                }
                if (label.length() > 0) {
                    label.append(' ');
                }
                label.append(Character.toUpperCase(segment.charAt(0)));
                if (segment.length() > 1) {
                    label.append(segment.substring(1).toLowerCase(Locale.ROOT));
                }
            }
            return label.toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
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
        return (int) items.stream().filter(item -> !item.isDeleted()).count();
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
        copy.setDeletedAt(source.getDeletedAt());
        copy.setLocked(source.isLocked());
        copy.setLockPasswordHash(source.getLockPasswordHash());
        copy.setLockSalt(source.getLockSalt());
        copy.setLockPayload(source.getLockPayload());
        copy.setUnlockedSession(source.getUnlockedSession() == null ? null : source.getUnlockedSession().copy());
        copy.setGalleryImages(source.getGalleryImages());
        copy.setImageMimeType(source.getImageMimeType());
        copy.setImageByteCount(source.getImageByteCount());
        copy.setCachedImageBytes(source.getCachedImageBytes());
        return copy;
    }

    private String safePasswordValue(String value) {
        return value == null ? "" : value;
    }

    private void handleRegisterError(AppModel appModel, IllegalArgumentException exception) {
        if (containsIgnoreCase(exception.getMessage(), "already exists")) {
            appModel.showErrorKey("status.auth.email.exists");
            return;
        }
        appModel.showErrorKey("status.auth.create.error", safeMessage(exception));
    }

    private void handleLoginError(AppModel appModel, IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (containsIgnoreCase(message, "incorrect") || containsIgnoreCase(message, "found")) {
            appModel.showErrorKey("status.auth.invalid.credentials");
            return;
        }
        appModel.showErrorKey("status.auth.login.error", safeMessage(exception));
    }

    private DialogActionResult mapProfileEmailValidationError(AppModel appModel, IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (containsIgnoreCase(message, "current password")) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL_CURRENT_PASSWORD, "status.profile.current.password.invalid");
        }
        if (containsIgnoreCase(message, "already exists")) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL, "status.profile.email.exists");
        }
        if (containsIgnoreCase(message, "current email")) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_EMAIL, "status.profile.email.same");
        }
        if (containsIgnoreCase(message, "account not found")) {
            return dialogFormError(appModel, "status.profile.account.missing");
        }
        return dialogFormError(appModel, "status.profile.email.error", safeMessage(exception));
    }

    private DialogActionResult mapProfilePasswordValidationError(AppModel appModel, IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (containsIgnoreCase(message, "current password")) {
            return dialogFieldError(appModel, DialogFieldIds.PROFILE_PASSWORD_CURRENT, "status.profile.current.password.invalid");
        }
        if (containsIgnoreCase(message, "account not found")) {
            return dialogFormError(appModel, "status.profile.account.missing");
        }
        return dialogFormError(appModel, "status.profile.password.error", safeMessage(exception));
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
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

    private record GalleryLockConfigurationResult(boolean success,
                                                  List<StoredImageRecord> imageRecords,
                                                  DialogActionResult failureResult) {

        private static GalleryLockConfigurationResult success(List<StoredImageRecord> imageRecords) {
            return new GalleryLockConfigurationResult(true, imageRecords == null ? List.of() : List.copyOf(imageRecords), null);
        }

        private static GalleryLockConfigurationResult failed(DialogActionResult failureResult) {
            return new GalleryLockConfigurationResult(false, List.of(), failureResult);
        }
    }

    public record ImageGalleryAnalysisResult(String title,
                                             String aiContext,
                                             String tags,
                                             List<GalleryImageFx> galleryImages) {
    }

    public record UrlAnalysisResult(String normalizedUrl,
                                    String title,
                                    String aiContext,
                                    String archivedContent,
                                    String tags,
                                    boolean aiGenerated) {
    }
}
