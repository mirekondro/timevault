package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.DialogFieldIds;
import com.example.desktop.model.ItemLockOptions;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.util.Map;

/**
 * Shared desktop dialog context for item editors.
 */
public abstract class BaseItemDialogController extends BaseDialogController {

    @FXML
    protected Label lockPasswordErrorLabel;

    @FXML
    protected Label confirmLockPasswordErrorLabel;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);
    }

    protected void configureLockControls(CheckBox lockToggle,
                                         PasswordField passwordField,
                                         PasswordField confirmPasswordField,
                                         Label hintLabel) {
        if (lockToggle == null || passwordField == null || confirmPasswordField == null) {
            return;
        }

        appModel.bindText(lockToggle, "dialog.lock.toggle");
        appModel.bindPrompt(passwordField, "dialog.lock.password.prompt");
        appModel.bindPrompt(confirmPasswordField, "dialog.lock.confirm.prompt");

        passwordField.disableProperty().bind(lockToggle.selectedProperty().not());
        confirmPasswordField.disableProperty().bind(lockToggle.selectedProperty().not());

        lockToggle.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (!isSelected) {
                passwordField.clear();
                confirmPasswordField.clear();
                clearFieldMessage(lockPasswordErrorLabel);
                clearFieldMessage(confirmLockPasswordErrorLabel);
            }
        });

        if (hintLabel != null) {
            hintLabel.textProperty().bind(appModel.textBinding("dialog.lock.edit.hint"));
            hintLabel.visibleProperty().bind(lockToggle.selectedProperty());
            hintLabel.managedProperty().bind(hintLabel.visibleProperty());
        }
    }

    protected void prepareLockStateForCreate(CheckBox lockToggle,
                                             PasswordField passwordField,
                                             PasswordField confirmPasswordField) {
        lockToggle.setSelected(false);
        passwordField.clear();
        confirmPasswordField.clear();
    }

    protected void prepareLockStateForEdit(CheckBox lockToggle,
                                           PasswordField passwordField,
                                           PasswordField confirmPasswordField,
                                           VaultItemFx item) {
        lockToggle.setSelected(item != null && item.isLocked());
        passwordField.clear();
        confirmPasswordField.clear();
    }

    protected ItemLockOptions readLockOptions(CheckBox lockToggle,
                                              PasswordField passwordField,
                                              PasswordField confirmPasswordField) {
        return new ItemLockOptions(
                lockToggle != null && lockToggle.isSelected(),
                passwordField == null ? "" : passwordField.getText(),
                confirmPasswordField == null ? "" : confirmPasswordField.getText());
    }

    protected void addLockFieldErrorLabels(Map<String, Label> fieldErrorLabels) {
        if (fieldErrorLabels == null) {
            return;
        }
        fieldErrorLabels.put(DialogFieldIds.LOCK_PASSWORD, lockPasswordErrorLabel);
        fieldErrorLabels.put(DialogFieldIds.LOCK_CONFIRM, confirmLockPasswordErrorLabel);
    }
}
