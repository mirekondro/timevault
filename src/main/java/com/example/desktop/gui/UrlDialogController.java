package com.example.desktop.gui;

import com.example.desktop.model.VaultItemFx;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * Add/edit dialog for URL items.
 */
public class UrlDialogController extends BaseItemDialogController {

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private TextField urlField;

    @FXML
    private TextField titleField;

    @FXML
    private TextArea notesArea;

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

    @Override
    public void setContext(com.example.desktop.model.AppModel appModel,
                           com.example.desktop.bll.VaultManager vaultManager,
                           javafx.application.HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);
        appModel.bindPrompt(urlField, "save.url.prompt");
        appModel.bindPrompt(titleField, "save.url.title.prompt");
        appModel.bindPrompt(notesArea, "save.url.notes.prompt");
        appModel.bindText(cancelButton, "dialog.common.cancel");
        configureLockControls(lockToggle, lockPasswordField, confirmLockPasswordField, lockHintLabel);
    }

    public void prepareForCreate() {
        editMode = false;
        editingItem = null;
        urlField.clear();
        titleField.clear();
        notesArea.clear();
        prepareLockStateForCreate(lockToggle, lockPasswordField, confirmLockPasswordField);
        refreshModeText();
    }

    public void prepareForEdit(VaultItemFx item) {
        editMode = true;
        editingItem = item;
        urlField.setText(appModel.getResolvedSourceUrl(item));
        titleField.setText(appModel.getResolvedTitle(item));
        notesArea.setText(appModel.getResolvedContent(item));
        prepareLockStateForEdit(lockToggle, lockPasswordField, confirmLockPasswordField, item);
        refreshModeText();
    }

    @FXML
    private void handleSave() {
        boolean saved = editMode
                ? vaultManager.updateUrl(
                        appModel,
                        editingItem,
                        urlField.getText(),
                        titleField.getText(),
                        notesArea.getText(),
                        readLockOptions(lockToggle, lockPasswordField, confirmLockPasswordField))
                : vaultManager.createUrl(
                        appModel,
                        urlField.getText(),
                        titleField.getText(),
                        notesArea.getText(),
                        readLockOptions(lockToggle, lockPasswordField, confirmLockPasswordField));
        if (saved) {
            closeDialog();
        }
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
        primaryButton.textProperty().unbind();
        primaryButton.textProperty().bind(appModel.textBinding(editMode ? "dialog.url.submit.edit" : "dialog.url.submit.create"));
    }
}
