package com.example.desktop.gui;

import com.example.desktop.model.VaultItemFx;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * Add/edit dialog for text items.
 */
public class TextDialogController extends BaseItemDialogController {

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private TextField titleField;

    @FXML
    private TextArea contentArea;

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
        appModel.bindPrompt(titleField, "save.text.title.prompt");
        appModel.bindPrompt(contentArea, "save.text.content.prompt");
        appModel.bindText(cancelButton, "dialog.common.cancel");
    }

    public void prepareForCreate() {
        editMode = false;
        editingItem = null;
        titleField.clear();
        contentArea.clear();
        refreshModeText();
    }

    public void prepareForEdit(VaultItemFx item) {
        editMode = true;
        editingItem = item;
        titleField.setText(item.getTitle());
        contentArea.setText(item.getContent());
        refreshModeText();
    }

    @FXML
    private void handleSave() {
        boolean saved = editMode
                ? vaultManager.updateText(appModel, editingItem, titleField.getText(), contentArea.getText())
                : vaultManager.createText(appModel, titleField.getText(), contentArea.getText());
        if (saved) {
            closeDialog();
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void refreshModeText() {
        bindWindowTitle(editMode ? "dialog.text.edit.window" : "dialog.text.create.window");
        dialogTitleLabel.textProperty().unbind();
        dialogTitleLabel.textProperty().bind(appModel.textBinding(editMode ? "dialog.text.edit.title" : "dialog.text.create.title"));
        dialogCopyLabel.textProperty().unbind();
        dialogCopyLabel.textProperty().bind(appModel.textBinding(editMode ? "dialog.text.edit.copy" : "dialog.text.create.copy"));
        primaryButton.textProperty().unbind();
        primaryButton.textProperty().bind(appModel.textBinding(editMode ? "dialog.text.submit.edit" : "dialog.text.submit.create"));
    }
}
