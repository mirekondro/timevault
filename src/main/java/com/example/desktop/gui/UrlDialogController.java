package com.example.desktop.gui;

import com.example.desktop.model.VaultItemFx;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
    }

    public void prepareForCreate() {
        editMode = false;
        editingItem = null;
        urlField.clear();
        titleField.clear();
        notesArea.clear();
        refreshModeText();
    }

    public void prepareForEdit(VaultItemFx item) {
        editMode = true;
        editingItem = item;
        urlField.setText(item.getSourceUrl());
        titleField.setText(item.getTitle());
        notesArea.setText(item.getContent());
        refreshModeText();
    }

    @FXML
    private void handleSave() {
        boolean saved = editMode
                ? vaultManager.updateUrl(appModel, editingItem, urlField.getText(), titleField.getText(), notesArea.getText())
                : vaultManager.createUrl(appModel, urlField.getText(), titleField.getText(), notesArea.getText());
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
