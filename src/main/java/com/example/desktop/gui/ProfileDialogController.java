package com.example.desktop.gui;

import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.DialogFieldIds;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialog controller for editing account email and password.
 */
public class ProfileDialogController extends BaseDialogController {

    @FXML
    private Label dialogTitleLabel;

    @FXML
    private Label dialogCopyLabel;

    @FXML
    private Label emailSectionTitleLabel;

    @FXML
    private Label emailSectionCopyLabel;

    @FXML
    private Label currentEmailCaptionLabel;

    @FXML
    private Label currentEmailValueLabel;

    @FXML
    private TextField newEmailField;

    @FXML
    private Label newEmailErrorLabel;

    @FXML
    private PasswordField emailCurrentPasswordField;

    @FXML
    private Label emailCurrentPasswordErrorLabel;

    @FXML
    private Button updateEmailButton;

    @FXML
    private Label passwordSectionTitleLabel;

    @FXML
    private Label passwordSectionCopyLabel;

    @FXML
    private PasswordField passwordCurrentPasswordField;

    @FXML
    private Label passwordCurrentErrorLabel;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private Label newPasswordErrorLabel;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label confirmPasswordErrorLabel;

    @FXML
    private Button updatePasswordButton;

    @FXML
    private Button closeButton;

    @Override
    public void setContext(AppModel appModel,
                           com.example.desktop.bll.VaultManager vaultManager,
                           javafx.application.HostServices hostServices,
                           javafx.stage.Stage stage,
                           com.example.desktop.DesktopNavigator navigator) {
        super.setContext(appModel, vaultManager, hostServices, stage, navigator);
        bindWindowTitle("dialog.profile.window");

        appModel.bindText(dialogTitleLabel, "dialog.profile.title");
        appModel.bindText(dialogCopyLabel, "dialog.profile.copy");
        appModel.bindText(emailSectionTitleLabel, "dialog.profile.email.title");
        appModel.bindText(emailSectionCopyLabel, "dialog.profile.email.copy");
        appModel.bindText(currentEmailCaptionLabel, "dialog.profile.email.current");
        appModel.bindPrompt(newEmailField, "dialog.profile.email.prompt");
        appModel.bindPrompt(emailCurrentPasswordField, "dialog.profile.email.password.prompt");
        appModel.bindText(updateEmailButton, "dialog.profile.email.submit");
        appModel.bindText(passwordSectionTitleLabel, "dialog.profile.password.title");
        appModel.bindText(passwordSectionCopyLabel, "dialog.profile.password.copy");
        appModel.bindPrompt(passwordCurrentPasswordField, "dialog.profile.password.current.prompt");
        appModel.bindPrompt(newPasswordField, "dialog.profile.password.new.prompt");
        appModel.bindPrompt(confirmPasswordField, "dialog.profile.password.confirm.prompt");
        appModel.bindText(updatePasswordButton, "dialog.profile.password.submit");
        appModel.bindText(closeButton, "dialog.common.close");

        currentEmailValueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> appModel.getCurrentUser() == null ? appModel.text("header.menu.noUser") : appModel.getCurrentUser().email(),
                appModel.currentUserProperty(),
                appModel.localeProperty()));

        updateEmailButton.disableProperty().bind(appModel.busyProperty());
        updatePasswordButton.disableProperty().bind(appModel.busyProperty());
        closeButton.disableProperty().bind(appModel.busyProperty());

        Platform.runLater(newEmailField::requestFocus);
    }

    @FXML
    private void handleUpdateEmail() {
        DialogActionResult result = vaultManager.updateProfileEmail(
                appModel,
                newEmailField.getText(),
                emailCurrentPasswordField.getText());
        if (handleDialogActionResult(result, fieldErrorLabels(), false)) {
            newEmailField.clear();
            emailCurrentPasswordField.clear();
        }
    }

    @FXML
    private void handleUpdatePassword() {
        DialogActionResult result = vaultManager.updateProfilePassword(
                appModel,
                passwordCurrentPasswordField.getText(),
                newPasswordField.getText(),
                confirmPasswordField.getText());
        if (handleDialogActionResult(result, fieldErrorLabels(), false)) {
            passwordCurrentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
        }
    }

    @FXML
    private void handleClose() {
        closeDialog();
    }

    private Map<String, Label> fieldErrorLabels() {
        Map<String, Label> fieldErrorLabels = new LinkedHashMap<>();
        fieldErrorLabels.put(DialogFieldIds.PROFILE_EMAIL, newEmailErrorLabel);
        fieldErrorLabels.put(DialogFieldIds.PROFILE_EMAIL_CURRENT_PASSWORD, emailCurrentPasswordErrorLabel);
        fieldErrorLabels.put(DialogFieldIds.PROFILE_PASSWORD_CURRENT, passwordCurrentErrorLabel);
        fieldErrorLabels.put(DialogFieldIds.PROFILE_PASSWORD_NEW, newPasswordErrorLabel);
        fieldErrorLabels.put(DialogFieldIds.PROFILE_PASSWORD_CONFIRM, confirmPasswordErrorLabel);
        return fieldErrorLabels;
    }
}
