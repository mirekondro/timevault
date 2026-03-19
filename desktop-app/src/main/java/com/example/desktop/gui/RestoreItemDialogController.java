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
 * Custom confirmation modal for restoring a vault item from Trash.
 */
public class RestoreItemDialogController extends BaseDialogController {

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
    private Button restoreButton;

    private VaultItemFx item;
    private boolean passwordRequired;

    @Override
    public void setContext(com.example.desktop.model.AppModel appModel,
                           com.example.desktop.bll.VaultManager vaultManager,
                           HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);
        bindWindowTitle("archive.restore.title");
        appModel.bindText(dialogTitleLabel, "archive.restore.title");
        appModel.bindText(dialogCopyLabel, "archive.restore.content");
        appModel.bindText(unlockHelperLabel, "lock.prompt.copy");
        appModel.bindPrompt(unlockPasswordField, "lock.prompt.password.prompt");
        appModel.bindText(cancelButton, "dialog.common.cancel");
        appModel.bindText(restoreButton, "archive.action.restore");

        cancelButton.disableProperty().bind(appModel.busyProperty());
        restoreButton.disableProperty().bind(appModel.busyProperty());
    }

    public void prepareForItem(VaultItemFx item) {
        this.item = item;
        this.passwordRequired = item != null && item.isLocked() && !item.isUnlockedInSession();

        unlockPasswordField.clear();
        clearDialogFeedback(fieldErrorLabels());
        dialogHeaderLabel.textProperty().unbind();
        dialogHeaderLabel.textProperty().bind(Bindings.createStringBinding(
                () -> item == null
                        ? appModel.text("archive.restore.title")
                        : appModel.text("archive.restore.header", appModel.getItemTitle(item)),
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
    private void handleRestore() {
        VaultItemFx itemToRestore = item;

        if (passwordRequired) {
            DialogActionResult unlockResult = vaultManager.unlockItemInDialog(appModel, itemToRestore, unlockPasswordField.getText());
            if (!handleDialogActionResult(unlockResult, fieldErrorLabels(), false)) {
                return;
            }

            VaultItemFx refreshedSelection = appModel.getSelectedItem();
            if (itemToRestore != null
                    && refreshedSelection != null
                    && refreshedSelection.getId() == itemToRestore.getId()) {
                itemToRestore = refreshedSelection;
                item = refreshedSelection;
            }

            passwordRequired = false;
            unlockPasswordField.clear();
            unlockSection.setVisible(false);
            unlockSection.setManaged(false);
        }

        DialogActionResult restoreResult = vaultManager.restoreItem(appModel, itemToRestore);
        handleDialogActionResult(restoreResult, fieldErrorLabels(), true);
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
