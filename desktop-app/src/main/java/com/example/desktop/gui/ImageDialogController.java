package com.example.desktop.gui;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.DialogFieldIds;
import com.example.desktop.model.GalleryImageFx;
import com.example.desktop.model.ToastNotificationType;
import com.example.desktop.model.VaultItemFx;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Add/edit dialog for gallery-based image items.
 */
public class ImageDialogController extends BaseItemDialogController {

    private static final long MAX_DIALOG_IMAGE_BYTES = 10L * 1024L * 1024L;

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private Label gallerySectionLabel;

    @FXML
    private Label galleryCountLabel;

    @FXML
    private TextField titleField;

    @FXML
    private TextArea notesArea;

    @FXML
    private TextArea summaryArea;

    @FXML
    private TextField tagsField;

    @FXML
    private ListView<GalleryImageFx> galleryListView;

    @FXML
    private Label pathErrorLabel;

    @FXML
    private Button addImagesButton;

    @FXML
    private Button removeImageButton;

    @FXML
    private Button moveUpButton;

    @FXML
    private Button moveDownButton;

    @FXML
    private Button analyzeButton;

    @FXML
    private ImageView previewImageView;

    @FXML
    private Label previewMetaLabel;

    @FXML
    private Label previewPlaceholderLabel;

    @FXML
    private TextArea selectedImageContextArea;

    @FXML
    private CheckBox lockToggle;

    @FXML
    private PasswordField lockPasswordField;

    @FXML
    private PasswordField confirmLockPasswordField;

    @FXML
    private Label lockHintLabel;

    @FXML
    private Button primaryButton;

    @FXML
    private Button cancelButton;

    private final ObservableList<GalleryImageFx> workingImages = FXCollections.observableArrayList();
    private boolean editMode;
    private VaultItemFx editingItem;

    @Override
    public void setContext(com.example.desktop.model.AppModel appModel,
                           com.example.desktop.bll.VaultManager vaultManager,
                           javafx.application.HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);

        appModel.bindPrompt(titleField, "save.image.title.prompt");
        appModel.bindPrompt(notesArea, "dialog.image.notes.prompt");
        appModel.bindPrompt(summaryArea, "dialog.image.summary.prompt");
        appModel.bindPrompt(tagsField, "dialog.image.tags.prompt");

        appModel.bindText(gallerySectionLabel, "dialog.image.gallery.section");
        appModel.bindText(addImagesButton, "dialog.image.files.add");
        appModel.bindText(removeImageButton, "dialog.image.files.remove");
        appModel.bindText(moveUpButton, "dialog.image.files.moveUp");
        appModel.bindText(moveDownButton, "dialog.image.files.moveDown");
        appModel.bindText(analyzeButton, "dialog.image.analyze.button");
        appModel.bindText(cancelButton, "dialog.common.cancel");

        configureLockControls(lockToggle, lockPasswordField, confirmLockPasswordField, lockHintLabel);

        galleryListView.setItems(workingImages);
        galleryListView.setCellFactory(listView -> new GalleryImageListCell(appModel));
        galleryListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldImage, newImage) -> updateSelectedImagePreview(newImage));

        removeImageButton.disableProperty().bind(galleryListView.getSelectionModel().selectedItemProperty().isNull());
        moveUpButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> galleryListView.getSelectionModel().getSelectedIndex() <= 0,
                galleryListView.getSelectionModel().selectedIndexProperty()));
        moveDownButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    int index = galleryListView.getSelectionModel().getSelectedIndex();
                    return index < 0 || index >= workingImages.size() - 1;
                },
                galleryListView.getSelectionModel().selectedIndexProperty(),
                Bindings.size(workingImages)));
        analyzeButton.disableProperty().bind(Bindings.isEmpty(workingImages));
        primaryButton.disableProperty().bind(Bindings.isEmpty(workingImages));

        workingImages.addListener((javafx.collections.ListChangeListener<GalleryImageFx>) change -> {
            updateGalleryCount();
            if (workingImages.isEmpty()) {
                updateSelectedImagePreview(null);
            }
        });

        updateGalleryCount();
        updateSelectedImagePreview(null);
    }

    public void prepareForCreate() {
        editMode = false;
        editingItem = null;
        titleField.clear();
        notesArea.clear();
        summaryArea.clear();
        tagsField.clear();
        workingImages.clear();
        prepareLockStateForCreate(lockToggle, lockPasswordField, confirmLockPasswordField);
        clearDialogFeedback(fieldErrorLabels());
        refreshModeText();
    }

    public void prepareForEdit(VaultItemFx item) {
        editMode = true;
        editingItem = item;
        titleField.setText(appModel.getResolvedTitle(item));
        notesArea.setText(appModel.getResolvedContent(item));
        summaryArea.setText(appModel.getResolvedContext(item));
        tagsField.setText(appModel.getResolvedTags(item));
        setWorkingImages(vaultManager.loadImageGallery(appModel, item), 0);
        prepareLockStateForEdit(lockToggle, lockPasswordField, confirmLockPasswordField, item);
        clearDialogFeedback(fieldErrorLabels());
        refreshModeText();
    }

    @FXML
    private void handleAddImages() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(appModel.text("fileChooser.image.gallery.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                appModel.text("fileChooser.image.filter"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        List<File> selectedFiles = chooser.showOpenMultipleDialog(dialogStage);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        clearFieldMessage(pathErrorLabel);
        clearFormMessage();

        try {
            int nextDisplayOrder = workingImages.size();
            GalleryImageFx firstAddedImage = null;
            for (File selectedFile : selectedFiles) {
                GalleryImageFx galleryImage = loadGalleryImage(selectedFile.toPath(), nextDisplayOrder++);
                if (firstAddedImage == null) {
                    firstAddedImage = galleryImage;
                }
                workingImages.add(galleryImage);
            }

            ensureSuggestedTitle();
            resequenceWorkingImages();
            if (firstAddedImage != null) {
                galleryListView.getSelectionModel().select(firstAddedImage);
                galleryListView.scrollTo(firstAddedImage);
            }
            showDialogToast(
                    ToastNotificationType.SUCCESS,
                    appModel.text("status.save.image.selected.multiple", selectedFiles.size()));
        } catch (IllegalArgumentException exception) {
            showSelectionError(exception.getMessage());
        }
    }

    @FXML
    private void handleRemoveImage() {
        GalleryImageFx selectedImage = galleryListView.getSelectionModel().getSelectedItem();
        int selectedIndex = galleryListView.getSelectionModel().getSelectedIndex();
        if (selectedImage == null) {
            return;
        }

        workingImages.remove(selectedImage);
        resequenceWorkingImages();
        if (!workingImages.isEmpty()) {
            galleryListView.getSelectionModel().select(Math.min(selectedIndex, workingImages.size() - 1));
        }
    }

    @FXML
    private void handleMoveUp() {
        int selectedIndex = galleryListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex <= 0) {
            return;
        }
        GalleryImageFx selectedImage = workingImages.remove(selectedIndex);
        workingImages.add(selectedIndex - 1, selectedImage);
        resequenceWorkingImages();
        galleryListView.getSelectionModel().select(selectedIndex - 1);
    }

    @FXML
    private void handleMoveDown() {
        int selectedIndex = galleryListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= workingImages.size() - 1) {
            return;
        }
        GalleryImageFx selectedImage = workingImages.remove(selectedIndex);
        workingImages.add(selectedIndex + 1, selectedImage);
        resequenceWorkingImages();
        galleryListView.getSelectionModel().select(selectedIndex + 1);
    }

    @FXML
    private void handleAnalyze() {
        clearDialogFeedback(fieldErrorLabels());
        if (workingImages.isEmpty()) {
            showSelectionError(appModel.text("status.save.image.missing"));
            return;
        }

        ensureSuggestedTitle();
        int selectedIndex = galleryListView.getSelectionModel().getSelectedIndex();
        showDialogToast(ToastNotificationType.INFO, appModel.text("dialog.image.analyze.started"));
        appModel.setBusy(true);

        Task<VaultManager.ImageGalleryAnalysisResult> analysisTask = new Task<>() {
            @Override
            protected VaultManager.ImageGalleryAnalysisResult call() {
                return vaultManager.analyzeImageGallery(
                        titleField.getText(),
                        notesArea.getText(),
                        List.copyOf(workingImages),
                        true);
            }
        };

        analysisTask.setOnSucceeded(event -> {
            appModel.setBusy(false);
            applyAnalysisResult(analysisTask.getValue(), selectedIndex);
            showDialogToast(ToastNotificationType.SUCCESS, appModel.text("dialog.image.analyze.success"));
        });

        analysisTask.setOnFailed(event -> {
            appModel.setBusy(false);
            Throwable exception = analysisTask.getException();
            String message = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                    ? appModel.text("dialog.image.analyze.error")
                    : exception.getMessage();
            showFormMessage(message);
            showDialogToast(ToastNotificationType.ERROR, message);
        });

        Thread thread = new Thread(analysisTask, "timevault-image-gallery-analysis");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleSave() {
        ensureSuggestedTitle();
        DialogActionResult result = editMode
                ? vaultManager.updateImage(
                        appModel,
                        editingItem,
                        titleField.getText(),
                        notesArea.getText(),
                        summaryArea.getText(),
                        tagsField.getText(),
                        List.copyOf(workingImages),
                        readLockOptions(lockToggle, lockPasswordField, confirmLockPasswordField))
                : vaultManager.createImage(
                        appModel,
                        titleField.getText(),
                        notesArea.getText(),
                        summaryArea.getText(),
                        tagsField.getText(),
                        List.copyOf(workingImages),
                        readLockOptions(lockToggle, lockPasswordField, confirmLockPasswordField));
        handleDialogActionResult(result, fieldErrorLabels(), true);
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void refreshModeText() {
        bindWindowTitle(editMode ? "dialog.image.edit.window" : "dialog.image.create.window");
        dialogTitleLabel.textProperty().unbind();
        dialogTitleLabel.textProperty().bind(appModel.textBinding(editMode ? "dialog.image.edit.title" : "dialog.image.create.title"));
        dialogCopyLabel.textProperty().unbind();
        dialogCopyLabel.textProperty().bind(appModel.textBinding(editMode ? "dialog.image.edit.copy" : "dialog.image.create.copy"));
        primaryButton.textProperty().unbind();
        primaryButton.textProperty().bind(appModel.textBinding(editMode ? "dialog.image.submit.edit" : "dialog.image.submit.create"));
    }

    private void applyAnalysisResult(VaultManager.ImageGalleryAnalysisResult result, int preferredIndex) {
        if (result == null) {
            return;
        }
        titleField.setText(result.title());
        summaryArea.setText(result.aiContext());
        tagsField.setText(result.tags());
        setWorkingImages(result.galleryImages(), preferredIndex);
    }

    private void setWorkingImages(List<GalleryImageFx> galleryImages, int preferredIndex) {
        workingImages.setAll(galleryImages == null ? List.of() : galleryImages.stream()
                .map(GalleryImageFx::copy)
                .toList());
        resequenceWorkingImages();
        if (workingImages.isEmpty()) {
            galleryListView.getSelectionModel().clearSelection();
            updateSelectedImagePreview(null);
            return;
        }
        galleryListView.getSelectionModel().select(Math.max(0, Math.min(preferredIndex, workingImages.size() - 1)));
    }

    private void updateGalleryCount() {
        galleryCountLabel.setText(appModel.text("dialog.image.gallery.count", workingImages.size()));
    }

    private void updateSelectedImagePreview(GalleryImageFx selectedImage) {
        if (selectedImage == null) {
            previewImageView.setImage(null);
            previewMetaLabel.setText("");
            previewPlaceholderLabel.setText(appModel.text("dialog.image.preview.empty"));
            selectedImageContextArea.setText(appModel.text("dialog.image.selected.analysis.empty"));
            return;
        }

        previewPlaceholderLabel.setText("");
        if (selectedImage.hasCachedBytes()) {
            previewImageView.setImage(new Image(new ByteArrayInputStream(selectedImage.getCachedImageBytes())));
        } else {
            previewImageView.setImage(null);
        }

        String fileName = selectedImage.getFileName() == null || selectedImage.getFileName().isBlank()
                ? appModel.text("item.untitled")
                : selectedImage.getFileName();
        previewMetaLabel.setText(appModel.text(
                "dialog.image.preview.meta",
                fileName,
                formatByteCount(selectedImage.getByteCount()),
                selectedImage.getMimeType()));
        selectedImageContextArea.setText(selectedImage.getAiContext() == null || selectedImage.getAiContext().isBlank()
                ? appModel.text("dialog.image.selected.analysis.empty")
                : selectedImage.getAiContext());
    }

    private void ensureSuggestedTitle() {
        if (titleField.getText() == null || titleField.getText().isBlank()) {
            titleField.setText(buildLocalDefaultTitle());
        }
    }

    private String buildLocalDefaultTitle() {
        if (workingImages.isEmpty()) {
            return appModel.text("dialog.image.default.singleTitle");
        }
        if (workingImages.size() == 1) {
            return extractFileStem(workingImages.getFirst().getFileName());
        }
        return appModel.text("dialog.image.default.galleryTitle", workingImages.size());
    }

    private String extractFileStem(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (value.isBlank()) {
            return appModel.text("dialog.image.default.singleTitle");
        }
        int lastDot = value.lastIndexOf('.');
        return lastDot <= 0 ? value : value.substring(0, lastDot);
    }

    private GalleryImageFx loadGalleryImage(Path imagePath, int displayOrder) {
        try {
            byte[] bytes = Files.readAllBytes(imagePath);
            if (bytes.length == 0) {
                throw new IllegalArgumentException(appModel.text("status.save.image.read.error"));
            }
            if (bytes.length > MAX_DIALOG_IMAGE_BYTES) {
                throw new IllegalArgumentException(appModel.text(
                        "status.save.image.too.large",
                        MAX_DIALOG_IMAGE_BYTES / (1024L * 1024L)));
            }

            String mimeType = Files.probeContentType(imagePath);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "image/png";
            }

            GalleryImageFx galleryImage = new GalleryImageFx();
            galleryImage.setFileName(imagePath.getFileName().toString());
            galleryImage.setMimeType(mimeType);
            galleryImage.setDisplayOrder(displayOrder);
            galleryImage.setCachedImageBytes(bytes);
            galleryImage.setByteCount(bytes.length);
            return galleryImage;
        } catch (IOException exception) {
            throw new IllegalArgumentException(appModel.text("status.save.image.read.error"), exception);
        }
    }

    private void resequenceWorkingImages() {
        for (int index = 0; index < workingImages.size(); index++) {
            workingImages.get(index).setDisplayOrder(index);
        }
        galleryListView.refresh();
    }

    private void showSelectionError(String message) {
        showFieldMessage(pathErrorLabel, message);
        showDialogToast(ToastNotificationType.ERROR, message);
    }

    private String formatByteCount(long byteCount) {
        if (byteCount <= 0L) {
            return "0 B";
        }
        if (byteCount >= 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", byteCount / (1024d * 1024d));
        }
        if (byteCount >= 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", byteCount / 1024d);
        }
        return byteCount + " B";
    }

    private Map<String, Label> fieldErrorLabels() {
        Map<String, Label> fieldErrorLabels = new LinkedHashMap<>();
        fieldErrorLabels.put(DialogFieldIds.PATH, pathErrorLabel);
        addLockFieldErrorLabels(fieldErrorLabels);
        return fieldErrorLabels;
    }
}
