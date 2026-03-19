package com.example.desktop.gui;

import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.DialogFieldIds;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom confirmation modal for deleting a vault item.
 */
public class DeleteItemDialogController extends BaseDialogController {

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogHeaderLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private VBox unlockSection;

    @FXML
    private Label unlockHelperLabel;

    @FXML
    private PasswordField unlockPasswordField;

    @FXML
    private Label unlockPasswordErrorLabel;

    @FXML
    private Button cancelButton;

    @FXML
    private Button deleteButton;

    private VaultItemFx item;
    private boolean passwordRequired;

    @Override
    public void setContext(com.example.desktop.model.AppModel appModel,
                           com.example.desktop.bll.VaultManager vaultManager,
                           HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);
        bindWindowTitle("archive.delete.title");
        appModel.bindText(dialogTitleLabel, "archive.delete.title");
        appModel.bindText(dialogCopyLabel, "archive.delete.content");
        appModel.bindText(unlockHelperLabel, "lock.prompt.copy");
        appModel.bindPrompt(unlockPasswordField, "lock.prompt.password.prompt");
        appModel.bindText(cancelButton, "dialog.common.cancel");
        appModel.bindText(deleteButton, "archive.action.delete");

        cancelButton.disableProperty().bind(appModel.busyProperty());
        deleteButton.disableProperty().bind(appModel.busyProperty());
    }

    public void prepareForItem(VaultItemFx item) {
        this.item = item;
        this.passwordRequired = item != null && item.isLocked() && !item.isUnlockedInSession();

        unlockPasswordField.clear();
        clearDialogFeedback(fieldErrorLabels());
        dialogHeaderLabel.textProperty().unbind();
        dialogHeaderLabel.textProperty().bind(Bindings.createStringBinding(
                () -> item == null
                        ? appModel.text("archive.delete.title")
                        : appModel.text("archive.delete.header", appModel.getItemTitle(item)),
                appModel.localeProperty()));

        unlockSection.setVisible(passwordRequired);
        unlockSection.setManaged(passwordRequired);

        Platform.runLater(() -> {
            if (passwordRequired) {
                unlockPasswordField.requestFocus();
            } else {
                cancelButton.requestFocus();
            }
        });
    }

    @FXML
    private void handleDelete() {
        VaultItemFx itemToDelete = item;

        if (passwordRequired) {
            DialogActionResult unlockResult = vaultManager.unlockItemInDialog(appModel, itemToDelete, unlockPasswordField.getText());
            if (!handleDialogActionResult(unlockResult, fieldErrorLabels(), false)) {
                return;
            }

            VaultItemFx refreshedSelection = appModel.getSelectedItem();
            if (itemToDelete != null
                    && refreshedSelection != null
                    && refreshedSelection.getId() == itemToDelete.getId()) {
                itemToDelete = refreshedSelection;
                item = refreshedSelection;
            }

            passwordRequired = false;
            unlockPasswordField.clear();
            unlockSection.setVisible(false);
            unlockSection.setManaged(false);
        }

        DialogActionResult deleteResult = vaultManager.deleteItem(appModel, itemToDelete);
        handleDialogActionResult(deleteResult, fieldErrorLabels(), true);
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private Map<String, Label> fieldErrorLabels() {
        Map<String, Label> fieldErrorLabels = new LinkedHashMap<>();
        fieldErrorLabels.put(DialogFieldIds.UNLOCK_PASSWORD, unlockPasswordErrorLabel);
        return fieldErrorLabels;
    }
}
