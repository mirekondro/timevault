package com.example.desktop.gui;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.DialogFieldIds;
import com.example.desktop.model.ToastNotificationType;
import com.example.desktop.model.VaultItemFx;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Add/edit dialog for URL items.
 */
public class UrlDialogController extends BaseItemDialogController {

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private Label urlLabel;

    @FXML
    private TextField urlField;

    @FXML
    private Label urlErrorLabel;

    @FXML
    private Label captureModeLabel;

    @FXML
    private RadioButton linkOnlyRadio;

    @FXML
    private RadioButton archiveContentRadio;

    @FXML
    private ToggleGroup captureModeToggleGroup;

    @FXML
    private Label captureModeCopyLabel;

    @FXML
    private Label titleLabel;

    @FXML
    private TextField titleField;

    @FXML
    private Label tagsLabel;

    @FXML
    private TextField tagsField;

    @FXML
    private Label summaryLabel;

    @FXML
    private TextArea summaryArea;

    @FXML
    private Label contentLabel;

    @FXML
    private TextArea contentArea;

    @FXML
    private CheckBox lockToggle;

    @FXML
    private PasswordField lockPasswordField;

    @FXML
    private PasswordField confirmLockPasswordField;

    @FXML
    private Label lockHintLabel;

    @FXML
    private Button analyzeButton;

    @FXML
    private Button primaryButton;

    @FXML
    private Button cancelButton;

    private boolean editMode;
    private VaultItemFx editingItem;

    @Override
    public void setContext(com.example.desktop.model.AppModel appModel,
                           VaultManager vaultManager,
                           javafx.application.HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);

        appModel.bindText(urlLabel, "dialog.url.field.url");
        appModel.bindPrompt(urlField, "save.url.prompt");
        appModel.bindText(captureModeLabel, "dialog.url.mode.label");
        appModel.bindText(linkOnlyRadio, "dialog.url.mode.link.title");
        appModel.bindText(archiveContentRadio, "dialog.url.mode.archive.title");
        appModel.bindText(titleLabel, "dialog.url.field.title");
        appModel.bindPrompt(titleField, "save.url.title.prompt");
        appModel.bindText(tagsLabel, "dialog.url.field.tags");
        appModel.bindPrompt(tagsField, "save.url.tags.prompt");
        appModel.bindText(summaryLabel, "dialog.url.field.summary");
        appModel.bindPrompt(summaryArea, "save.url.summary.prompt");
        appModel.bindText(contentLabel, "dialog.url.field.content");
        appModel.bindText(cancelButton, "dialog.common.cancel");
        configureLockControls(lockToggle, lockPasswordField, confirmLockPasswordField, lockHintLabel);

        if (captureModeToggleGroup != null) {
            captureModeToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
                if (newToggle == null) {
                    captureModeToggleGroup.selectToggle(linkOnlyRadio);
                    return;
                }
                updateCaptureModeText();
            });
        }
        appModel.localeProperty().addListener((obs, oldLocale, newLocale) -> updateCaptureModeText());

        urlField.disableProperty().bind(appModel.busyProperty());
        titleField.disableProperty().bind(appModel.busyProperty());
        tagsField.disableProperty().bind(appModel.busyProperty());
        summaryArea.disableProperty().bind(appModel.busyProperty());
        contentArea.disableProperty().bind(appModel.busyProperty());
        analyzeButton.disableProperty().bind(appModel.busyProperty());
        primaryButton.disableProperty().bind(appModel.busyProperty());
        cancelButton.disableProperty().bind(appModel.busyProperty());
        linkOnlyRadio.disableProperty().bind(appModel.busyProperty());
        archiveContentRadio.disableProperty().bind(appModel.busyProperty());
        lockToggle.disableProperty().bind(appModel.busyProperty());

        updateCaptureModeText();
    }

    public void prepareForCreate() {
        editMode = false;
        editingItem = null;
        urlField.clear();
        titleField.clear();
        tagsField.clear();
        summaryArea.clear();
        contentArea.clear();
        if (captureModeToggleGroup != null) {
            captureModeToggleGroup.selectToggle(linkOnlyRadio);
        }
        prepareLockStateForCreate(lockToggle, lockPasswordField, confirmLockPasswordField);
        clearDialogFeedback(fieldErrorLabels());
        refreshModeText();
        updateCaptureModeText();
    }

    public void prepareForEdit(VaultItemFx item) {
        editMode = true;
        editingItem = item;
        urlField.setText(appModel.getResolvedSourceUrl(item));
        titleField.setText(appModel.getResolvedTitle(item));
        tagsField.setText(appModel.getResolvedTags(item));
        summaryArea.setText(appModel.getResolvedContext(item));
        contentArea.setText(appModel.getResolvedContent(item));
        if (captureModeToggleGroup != null) {
            captureModeToggleGroup.selectToggle(contentArea.getText().isBlank() ? linkOnlyRadio : archiveContentRadio);
        }
        prepareLockStateForEdit(lockToggle, lockPasswordField, confirmLockPasswordField, item);
        clearDialogFeedback(fieldErrorLabels());
        refreshModeText();
        updateCaptureModeText();
    }

    @FXML
    private void handleAnalyze() {
        clearDialogFeedback(fieldErrorLabels());
        if (urlField.getText() == null || urlField.getText().isBlank()) {
            String message = appModel.text("dialog.validation.url.required");
            showFieldMessage(urlErrorLabel, message);
            showDialogToast(ToastNotificationType.ERROR, message);
            return;
        }

        showDialogToast(ToastNotificationType.INFO, appModel.text("status.url.analyze.started"));
        appModel.setBusy(true);

        Task<VaultManager.UrlAnalysisResult> analysisTask = new Task<>() {
            @Override
            protected VaultManager.UrlAnalysisResult call() throws Exception {
                return vaultManager.analyzeUrl(urlField.getText(), isArchiveContentMode());
            }
        };

        analysisTask.setOnSucceeded(event -> {
            appModel.setBusy(false);
            applyAnalysisResult(analysisTask.getValue());
            showDialogToast(ToastNotificationType.SUCCESS, appModel.text("status.url.analyze.success"));
        });

        analysisTask.setOnFailed(event -> {
            appModel.setBusy(false);
            handleAnalysisFailure(analysisTask.getException());
        });

        Thread thread = new Thread(analysisTask, "timevault-url-analysis");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleSave() {
        DialogActionResult result = editMode
                ? vaultManager.updateUrl(
                        appModel,
                        editingItem,
                        urlField.getText(),
                        titleField.getText(),
                        summaryArea.getText(),
                        contentArea.getText(),
                        tagsField.getText(),
                        readLockOptions(lockToggle, lockPasswordField, confirmLockPasswordField))
                : vaultManager.createUrl(
                        appModel,
                        urlField.getText(),
                        titleField.getText(),
                        summaryArea.getText(),
                        contentArea.getText(),
                        tagsField.getText(),
                        readLockOptions(lockToggle, lockPasswordField, confirmLockPasswordField));
        handleDialogActionResult(result, fieldErrorLabels(), true);
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void refreshModeText() {
        bindWindowTitle(editMode ? "dialog.url.edit.window" : "dialog.url.create.window");
        dialogTitleLabel.textProperty().unbind();
        dialogTitleLabel.textProperty().bind(appModel.textBinding(editMode ? "dialog.url.edit.title" : "dialog.url.create.title"));
        dialogCopyLabel.textProperty().unbind();
        dialogCopyLabel.textProperty().bind(appModel.textBinding(editMode ? "dialog.url.edit.copy" : "dialog.url.create.copy"));
        analyzeButton.textProperty().unbind();
        analyzeButton.textProperty().bind(appModel.textBinding(editMode ? "dialog.url.analyze.edit" : "dialog.url.analyze.create"));
        primaryButton.textProperty().unbind();
        primaryButton.textProperty().bind(appModel.textBinding(editMode ? "dialog.url.submit.edit" : "dialog.url.submit.create"));
    }

    private void updateCaptureModeText() {
        if (contentArea == null || captureModeCopyLabel == null) {
            return;
        }
        boolean archiveContentMode = isArchiveContentMode();
        captureModeCopyLabel.setText(appModel.text(
                archiveContentMode ? "dialog.url.mode.archive.copy" : "dialog.url.mode.link.copy"));
        contentArea.setPromptText(appModel.text(
                archiveContentMode ? "save.url.content.prompt.archive" : "save.url.content.prompt.link"));
    }

    private boolean isArchiveContentMode() {
        return archiveContentRadio != null && archiveContentRadio.isSelected();
    }

    private void applyAnalysisResult(VaultManager.UrlAnalysisResult result) {
        if (result == null) {
            return;
        }
        urlField.setText(result.normalizedUrl());
        titleField.setText(result.title().isBlank() ? appModel.text("save.default.urlTitle") : result.title());
        summaryArea.setText(result.aiContext());
        tagsField.setText(result.tags());
        if (isArchiveContentMode()) {
            contentArea.setText(result.archivedContent());
        }
    }

    private void handleAnalysisFailure(Throwable throwable) {
        clearFormMessage();
        clearFieldMessage(urlErrorLabel);

        Throwable cause = throwable == null ? null : throwable;
        String message;
        if (cause instanceof IllegalArgumentException && "invalid-url".equals(cause.getMessage())) {
            message = appModel.text("dialog.validation.url.invalid");
            showFieldMessage(urlErrorLabel, message);
            showDialogToast(ToastNotificationType.ERROR, message);
            return;
        }

        String detail = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? (cause == null ? "Unknown error" : cause.getClass().getSimpleName())
                : cause.getMessage();
        message = appModel.text("status.url.analyze.error", detail);
        showFormMessage(message);
        showDialogToast(ToastNotificationType.ERROR, message);
    }

    private Map<String, Label> fieldErrorLabels() {
        Map<String, Label> fieldErrorLabels = new LinkedHashMap<>();
        fieldErrorLabels.put(DialogFieldIds.URL, urlErrorLabel);
        addLockFieldErrorLabels(fieldErrorLabels);
        return fieldErrorLabels;
    }
}
