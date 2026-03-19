package com.example.desktop.gui;

import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.DialogFieldIds;
import com.example.desktop.model.ToastNotificationType;
import com.example.desktop.model.VaultItemFx;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Add/edit dialog for image items.
 */
public class ImageDialogController extends BaseItemDialogController {

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private TextField titleField;

    @FXML
    private TextField pathField;

    @FXML
    private Label pathErrorLabel;

    @FXML
    private Button browseButton;

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

    private boolean editMode;
    private VaultItemFx editingItem;
    private Path selectedImagePath;

    @Override
    public void setContext(com.example.desktop.model.AppModel appModel,
                           com.example.desktop.bll.VaultManager vaultManager,
                           javafx.application.HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);
        appModel.bindPrompt(titleField, "save.image.title.prompt");
        appModel.bindPrompt(pathField, "save.image.path.prompt");
        appModel.bindText(browseButton, "save.image.browse");
        appModel.bindText(cancelButton, "dialog.common.cancel");
        configureLockControls(lockToggle, lockPasswordField, confirmLockPasswordField, lockHintLabel);
    }

    public void prepareForCreate() {
        editMode = false;
        editingItem = null;
        selectedImagePath = null;
        titleField.clear();
        pathField.clear();
        prepareLockStateForCreate(lockToggle, lockPasswordField, confirmLockPasswordField);
        clearDialogFeedback(fieldErrorLabels());
        refreshModeText();
    }

    public void prepareForEdit(VaultItemFx item) {
        editMode = true;
        editingItem = item;
        selectedImagePath = null;
        titleField.setText(appModel.getResolvedTitle(item));
        pathField.setText(appModel.getResolvedContent(item));
        prepareLockStateForEdit(lockToggle, lockPasswordField, confirmLockPasswordField, item);
        clearDialogFeedback(fieldErrorLabels());
        refreshModeText();
    }

    @FXML
    private void handleBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(appModel.text("fileChooser.image.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                appModel.text("fileChooser.image.filter"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        File selectedFile = chooser.showOpenDialog(dialogStage);
        if (selectedFile != null) {
            selectedImagePath = selectedFile.toPath();
            pathField.setText(selectedFile.getAbsolutePath());
            if (titleField.getText().isBlank()) {
                titleField.setText(selectedFile.getName());
            }
            clearFieldMessage(pathErrorLabel);
            clearFormMessage();
            showDialogToast(ToastNotificationType.SUCCESS, appModel.text("status.save.image.selected", selectedFile.getName()));
        }
    }

    @FXML
    private void handleSave() {
        DialogActionResult result = editMode
                ? vaultManager.updateImage(
                        appModel,
                        editingItem,
                        titleField.getText(),
                        selectedImagePath,
                        readLockOptions(lockToggle, lockPasswordField, confirmLockPasswordField))
                : vaultManager.createImage(
                        appModel,
                        titleField.getText(),
                        selectedImagePath,
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

    private Map<String, Label> fieldErrorLabels() {
        Map<String, Label> fieldErrorLabels = new LinkedHashMap<>();
        fieldErrorLabels.put(DialogFieldIds.PATH, pathErrorLabel);
        addLockFieldErrorLabels(fieldErrorLabels);
        return fieldErrorLabels;
    }
}
