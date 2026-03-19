package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.ItemLockOptions;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

/**
 * Shared desktop dialog context for item editors.
 */
public abstract class BaseItemDialogController implements AppContextAware {

    protected AppModel appModel;
    protected VaultManager vaultManager;
    protected Stage dialogStage;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;
        this.dialogStage = stage;
    }

    protected void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    protected void bindWindowTitle(String key) {
        if (dialogStage == null) {
            return;
        }
        dialogStage.titleProperty().unbind();
        dialogStage.titleProperty().bind(appModel.textBinding(key));
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
}
