package com.example.desktop.gui;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for login, registration, and session actions.
 */
public class AuthController implements AppContextAware {

    @FXML
    private VBox authFormsBox;

    @FXML
    private TabPane authTabPane;

    @FXML
    private VBox sessionBox;

    @FXML
    private Label sessionSummaryLabel;

    @FXML
    private Label sessionMetaLabel;

    @FXML
    private TextField loginEmailField;

    @FXML
    private PasswordField loginPasswordField;

    @FXML
    private Button loginButton;

    @FXML
    private TextField registerEmailField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField registerConfirmPasswordField;

    @FXML
    private Button registerButton;

    @FXML
    private Button logoutButton;

    private AppModel appModel;
    private VaultManager vaultManager;

    @Override
    public void setContext(AppModel appModel, VaultManager vaultManager, HostServices hostServices, Stage stage) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;

        loginEmailField.textProperty().bindBidirectional(appModel.loginEmailInputProperty());
        loginPasswordField.textProperty().bindBidirectional(appModel.loginPasswordInputProperty());
        registerEmailField.textProperty().bindBidirectional(appModel.registerEmailInputProperty());
        registerPasswordField.textProperty().bindBidirectional(appModel.registerPasswordInputProperty());
        registerConfirmPasswordField.textProperty().bindBidirectional(appModel.registerConfirmPasswordInputProperty());

        authFormsBox.visibleProperty().bind(appModel.authenticatedProperty().not());
        authFormsBox.managedProperty().bind(authFormsBox.visibleProperty());
        sessionBox.visibleProperty().bind(appModel.authenticatedProperty());
        sessionBox.managedProperty().bind(sessionBox.visibleProperty());

        loginButton.disableProperty().bind(appModel.busyProperty());
        registerButton.disableProperty().bind(appModel.busyProperty());
        logoutButton.disableProperty().bind(appModel.busyProperty());
        authTabPane.disableProperty().bind(appModel.busyProperty());

        sessionSummaryLabel.textProperty().bind(Bindings.createStringBinding(
                () -> vaultManager.getSessionSummary(appModel),
                appModel.currentUserProperty()));
        sessionMetaLabel.textProperty().bind(Bindings.createStringBinding(
                () -> vaultManager.getSessionMeta(appModel),
                appModel.currentUserProperty()));
    }

    @FXML
    private void handleLogin() {
        vaultManager.login(appModel);
    }

    @FXML
    private void handleRegister() {
        vaultManager.register(appModel);
    }

    @FXML
    private void handleLogout() {
        vaultManager.logout(appModel);
    }
}
