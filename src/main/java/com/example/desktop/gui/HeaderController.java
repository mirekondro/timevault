package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Fixed header controller for the main desktop scene.
 */
public class HeaderController implements AppContextAware {

    @FXML
    private Label currentUserLabel;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> typeFilterComboBox;

    @FXML
    private Button reloadButton;

    @FXML
    private Button logoutButton;

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

        searchField.textProperty().bindBidirectional(appModel.searchTextProperty());
        typeFilterComboBox.setItems(appModel.getTypeOptions());
        typeFilterComboBox.valueProperty().bindBidirectional(appModel.selectedTypeProperty());
        searchField.disableProperty().bind(appModel.authenticatedProperty().not());
        typeFilterComboBox.disableProperty().bind(appModel.authenticatedProperty().not());
        reloadButton.disableProperty().bind(appModel.busyProperty().or(appModel.authenticatedProperty().not()));
        currentUserLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            if (appModel.getCurrentUser() == null) {
                return "No user";
            }
            return appModel.getCurrentUser().email();
        }, appModel.currentUserProperty()));

        logoutButton.disableProperty().bind(appModel.busyProperty());
    }

    @FXML
    private void handleReload() {
        vaultManager.loadVault(appModel);
    }

    @FXML
    private void handleLogout() {
        vaultManager.logout(appModel);
        navigator.showAuthScene();
    }
}
