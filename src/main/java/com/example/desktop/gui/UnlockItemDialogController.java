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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom modal for unlocking a protected item before another action continues.
 */
public class UnlockItemDialogController extends BaseDialogController {

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private Label helperLabel;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label passwordErrorLabel;

    @FXML
    private Button cancelButton;

    @FXML
    private Button primaryButton;

    private VaultItemFx item;
    private String headerKey;

    @Override
    public void setContext(com.example.desktop.model.AppModel appModel,
                           com.example.desktop.bll.VaultManager vaultManager,
                           HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);
        bindWindowTitle("lock.prompt.title");
        appModel.bindText(dialogTitleLabel, "lock.prompt.title");
        appModel.bindText(helperLabel, "lock.prompt.copy");
        appModel.bindPrompt(passwordField, "lock.prompt.password.prompt");
        appModel.bindText(cancelButton, "dialog.common.cancel");
        appModel.bindText(primaryButton, "lock.prompt.submit");

        cancelButton.disableProperty().bind(appModel.busyProperty());
        primaryButton.disableProperty().bind(appModel.busyProperty());
    }

    public void prepare(VaultItemFx item, String headerKey) {
        this.item = item;
        this.headerKey = headerKey;

        passwordField.clear();
        clearDialogFeedback(fieldErrorLabels());
        dialogCopyLabel.textProperty().unbind();
        dialogCopyLabel.textProperty().bind(Bindings.createStringBinding(
                () -> item == null || headerKey == null
                        ? ""
                        : appModel.text(headerKey, appModel.getItemTitle(item)),
                appModel.localeProperty()));

        Platform.runLater(passwordField::requestFocus);
    }

    @FXML
    private void handleContinue() {
        DialogActionResult result = vaultManager.unlockItemInDialog(appModel, item, passwordField.getText());
        handleDialogActionResult(result, fieldErrorLabels(), true);
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private Map<String, Label> fieldErrorLabels() {
        Map<String, Label> fieldErrorLabels = new LinkedHashMap<>();
        fieldErrorLabels.put(DialogFieldIds.UNLOCK_PASSWORD, passwordErrorLabel);
        return fieldErrorLabels;
    }
}
