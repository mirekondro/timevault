package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for login, registration, and session actions.
 */
public class AuthController implements AppContextAware {

    @FXML
    private VBox loginPane;

    @FXML
    private VBox registerPane;

    @FXML
    private TextField loginEmailField;

    @FXML
    private PasswordField loginPasswordField;

    @FXML
    private Button loginButton;

    @FXML
    private Hyperlink showRegisterLink;

    @FXML
    private TextField registerEmailField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField registerConfirmPasswordField;

    @FXML
    private Button registerButton;

    @FXML
    private Hyperlink showLoginLink;

    @FXML
    private Label authStatusLabel;

    private AppModel appModel;
    private VaultManager vaultManager;
    private DesktopNavigator navigator;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;
        this.navigator = navigator;

        loginEmailField.textProperty().bindBidirectional(appModel.loginEmailInputProperty());
        loginPasswordField.textProperty().bindBidirectional(appModel.loginPasswordInputProperty());
        registerEmailField.textProperty().bindBidirectional(appModel.registerEmailInputProperty());
        registerPasswordField.textProperty().bindBidirectional(appModel.registerPasswordInputProperty());
        registerConfirmPasswordField.textProperty().bindBidirectional(appModel.registerConfirmPasswordInputProperty());

        authStatusLabel.textProperty().bind(appModel.statusMessageProperty());
        loginButton.disableProperty().bind(appModel.busyProperty());
        registerButton.disableProperty().bind(appModel.busyProperty());
        showLoginView();
    }

    @FXML
    private void handleLogin() {
        vaultManager.login(appModel);
        if (appModel.isAuthenticated()) {
            navigator.showMainScene();
        }
    }

    @FXML
    private void handleRegister() {
        vaultManager.register(appModel);
        if (appModel.isAuthenticated()) {
            navigator.showMainScene();
        }
    }

    @FXML
    private void handleShowRegister() {
        showRegisterView();
    }

    @FXML
    private void handleShowLogin() {
        showLoginView();
    }

    public void showLoginView() {
        loginPane.setVisible(true);
        loginPane.setManaged(true);
        registerPane.setVisible(false);
        registerPane.setManaged(false);
        Platform.runLater(loginEmailField::requestFocus);
    }

    public void showRegisterView() {
        registerPane.setVisible(true);
        registerPane.setManaged(true);
        loginPane.setVisible(false);
        loginPane.setManaged(false);
        Platform.runLater(registerEmailField::requestFocus);
    }
}
