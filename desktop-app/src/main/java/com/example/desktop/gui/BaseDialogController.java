package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.DialogActionResult;
import com.example.desktop.model.ToastNotificationType;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.Map;

/**
 * Shared desktop dialog context and feedback helpers for modal forms.
 */
public abstract class BaseDialogController implements AppContextAware {

    @FXML
    protected Label formMessageLabel;

    @FXML
    protected ToastHostController toastHostViewController;

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

        if (toastHostViewController != null) {
            toastHostViewController.setUseAppNotifications(false);
            toastHostViewController.setTopOffset(16);
            toastHostViewController.setContext(appModel, vaultManager, hostServices, stage, navigator);
        }

        clearFormMessage();
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

    protected boolean handleDialogActionResult(DialogActionResult result,
                                               Map<String, Label> fieldErrorLabels,
                                               boolean closeOnSuccess) {
        clearDialogFeedback(fieldErrorLabels);
        if (result == null) {
            return false;
        }

        if (result.hasFieldErrors()) {
            for (Map.Entry<String, String> fieldError : result.fieldErrors().entrySet()) {
                Label errorLabel = fieldErrorLabels == null ? null : fieldErrorLabels.get(fieldError.getKey());
                if (errorLabel != null) {
                    showFieldMessage(errorLabel, fieldError.getValue());
                }
            }
        }

        if (result.hasFormMessage()) {
            showFormMessage(result.formMessage());
        }
        if (result.hasLocalToast()) {
            showDialogToast(result.localToastType(), result.localToastMessage());
        }

        if (result.success()) {
            if (closeOnSuccess) {
                closeDialog();
            }
            if (result.hasMainToast()) {
                showMainToast(result.mainToastType(), result.mainToastMessage());
            }
        }
        return result.success();
    }

    protected void clearDialogFeedback(Map<String, Label> fieldErrorLabels) {
        clearFormMessage();
        if (fieldErrorLabels == null) {
            return;
        }
        for (Label errorLabel : fieldErrorLabels.values()) {
            clearFieldMessage(errorLabel);
        }
    }

    protected void showFormMessage(String message) {
        if (formMessageLabel == null) {
            return;
        }
        formMessageLabel.setText(message);
        formMessageLabel.setVisible(true);
        formMessageLabel.setManaged(true);
    }

    protected void clearFormMessage() {
        if (formMessageLabel == null) {
            return;
        }
        formMessageLabel.setText("");
        formMessageLabel.setVisible(false);
        formMessageLabel.setManaged(false);
    }

    protected void clearFieldMessage(Label errorLabel) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    protected void showFieldMessage(Label errorLabel, String message) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    protected void showDialogToast(ToastNotificationType type, String message) {
        if (toastHostViewController != null) {
            toastHostViewController.showLocalToast(message, type);
        }
    }

    protected void showMainToast(ToastNotificationType type, String message) {
        if (appModel == null || message == null || message.isBlank()) {
            return;
        }
        switch (type) {
            case SUCCESS -> appModel.showSuccessMessage(message);
            case ERROR -> appModel.showErrorMessage(message);
            case INFO -> appModel.showInfoMessage(message);
        }
    }
}
