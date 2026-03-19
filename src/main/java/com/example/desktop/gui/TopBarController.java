package com.example.desktop.gui;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for search, filters, and desktop summary cards.
 */
public class TopBarController implements AppContextAware {

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> typeFilterComboBox;

    @FXML
    private Button reloadButton;

    @FXML
    private Label totalCountValue;

    @FXML
    private Label urlCountValue;

    @FXML
    private Label textCountValue;

    @FXML
    private Label imageCountValue;

    private AppModel appModel;
    private VaultManager vaultManager;

    @Override
    public void setContext(AppModel appModel, VaultManager vaultManager, HostServices hostServices, Stage stage) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;

        searchField.textProperty().bindBidirectional(appModel.searchTextProperty());
        typeFilterComboBox.setItems(appModel.getTypeOptions());
        typeFilterComboBox.valueProperty().bindBidirectional(appModel.selectedTypeProperty());

        totalCountValue.textProperty().bind(appModel.totalCountProperty().asString());
        urlCountValue.textProperty().bind(appModel.urlCountProperty().asString());
        textCountValue.textProperty().bind(appModel.textCountProperty().asString());
        imageCountValue.textProperty().bind(appModel.imageCountProperty().asString());

        reloadButton.disableProperty().bind(appModel.busyProperty());
    }

    @FXML
    private void handleReload() {
        vaultManager.loadVault(appModel);
    }
}
