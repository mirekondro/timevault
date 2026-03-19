package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
    private Label authEyebrowLabel;

    @FXML
    private Label authTitleLabel;

    @FXML
    private Label authCopyLabel;

    @FXML
    private VBox loginPane;

    @FXML
    private VBox registerPane;

    @FXML
    private Label loginTitleLabel;

    @FXML
    private TextField loginEmailField;

    @FXML
    private PasswordField loginPasswordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label loginSwitchLabel;

    @FXML
    private Hyperlink showRegisterLink;

    @FXML
    private Label registerTitleLabel;

    @FXML
    private TextField registerEmailField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField registerConfirmPasswordField;

    @FXML
    private Button registerButton;

    @FXML
    private Label registerSwitchLabel;

    @FXML
    private Hyperlink showLoginLink;

    @FXML
    private ToastHostController toastHostViewController;

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

        appModel.bindText(authEyebrowLabel, "auth.eyebrow");
        appModel.bindText(authTitleLabel, "auth.title");
        authCopyLabel.textProperty().bind(Bindings.createStringBinding(
                () -> appModel.text(registerPane.isVisible() ? "auth.register.copy" : "auth.login.copy"),
                appModel.localeProperty(),
                registerPane.visibleProperty()));
        appModel.bindText(loginTitleLabel, "auth.login.title");
        appModel.bindPrompt(loginEmailField, "auth.login.email.prompt");
        appModel.bindPrompt(loginPasswordField, "auth.login.password.prompt");
        appModel.bindText(loginButton, "auth.login.button");
        appModel.bindText(loginSwitchLabel, "auth.login.switch.copy");
        appModel.bindText(showRegisterLink, "auth.login.switch.link");
        appModel.bindText(registerTitleLabel, "auth.register.title");
        appModel.bindPrompt(registerEmailField, "auth.register.email.prompt");
        appModel.bindPrompt(registerPasswordField, "auth.register.password.prompt");
        appModel.bindPrompt(registerConfirmPasswordField, "auth.register.confirm.prompt");
        appModel.bindText(registerButton, "auth.register.button");
        appModel.bindText(registerSwitchLabel, "auth.register.switch.copy");
        appModel.bindText(showLoginLink, "auth.register.switch.link");

        toastHostViewController.setTopOffset(26);
        loginButton.defaultButtonProperty().bind(loginPane.visibleProperty());
        registerButton.defaultButtonProperty().bind(registerPane.visibleProperty());
        loginEmailField.setOnAction(event -> handleLogin());
        loginPasswordField.setOnAction(event -> handleLogin());
        registerEmailField.setOnAction(event -> handleRegister());
        registerPasswordField.setOnAction(event -> handleRegister());
        registerConfirmPasswordField.setOnAction(event -> handleRegister());
        loginButton.disableProperty().bind(appModel.busyProperty());
        registerButton.disableProperty().bind(appModel.busyProperty());
        showLoginView();

        toastHostViewController.setContext(appModel, vaultManager, hostServices, stage, navigator);
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
