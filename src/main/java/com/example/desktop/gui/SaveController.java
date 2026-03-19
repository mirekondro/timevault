package com.example.desktop.gui;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * Controller for the save tabs.
 */
public class SaveController implements AppContextAware {

    @FXML
    private Label authHintLabel;

    @FXML
    private TabPane saveTabPane;

    @FXML
    private TextField urlField;

    @FXML
    private TextField urlTitleField;

    @FXML
    private TextArea urlNotesArea;

    @FXML
    private TextField textTitleField;

    @FXML
    private TextArea textContentArea;

    @FXML
    private TextField imageTitleField;

    @FXML
    private TextField imagePathField;

    @FXML
    private Button saveUrlButton;

    @FXML
    private Button saveTextButton;

    @FXML
    private Button saveImageButton;

    private AppModel appModel;
    private VaultManager vaultManager;
    private Stage stage;

    @Override
    public void setContext(AppModel appModel, VaultManager vaultManager, HostServices hostServices, Stage stage) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;
        this.stage = stage;

        urlField.textProperty().bindBidirectional(appModel.urlInputProperty());
        urlTitleField.textProperty().bindBidirectional(appModel.urlTitleInputProperty());
        urlNotesArea.textProperty().bindBidirectional(appModel.urlNotesInputProperty());

        textTitleField.textProperty().bindBidirectional(appModel.textTitleInputProperty());
        textContentArea.textProperty().bindBidirectional(appModel.textContentInputProperty());

        imageTitleField.textProperty().bindBidirectional(appModel.imageTitleInputProperty());
        imagePathField.textProperty().bindBidirectional(appModel.imagePathInputProperty());

        authHintLabel.visibleProperty().bind(appModel.authenticatedProperty().not());
        authHintLabel.managedProperty().bind(authHintLabel.visibleProperty());
        authHintLabel.textProperty().bind(Bindings.createStringBinding(
                () -> appModel.isAuthenticated()
                        ? ""
                        : "Log in to save URLs, notes, and images into your own account.",
                appModel.authenticatedProperty()));

        saveTabPane.disableProperty().bind(appModel.busyProperty().or(appModel.authenticatedProperty().not()));
        saveUrlButton.disableProperty().bind(appModel.busyProperty().or(appModel.authenticatedProperty().not()));
        saveTextButton.disableProperty().bind(appModel.busyProperty().or(appModel.authenticatedProperty().not()));
        saveImageButton.disableProperty().bind(appModel.busyProperty().or(appModel.authenticatedProperty().not()));
    }

    @FXML
    private void handleSaveUrl() {
        vaultManager.saveUrl(appModel);
    }

    @FXML
    private void handleSaveText() {
        vaultManager.saveText(appModel);
    }

    @FXML
    private void handleBrowseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        File selectedFile = chooser.showOpenDialog(stage);
        if (selectedFile != null) {
            appModel.imagePathInputProperty().set(selectedFile.getAbsolutePath());
            if (appModel.imageTitleInputProperty().get().isBlank()) {
                appModel.imageTitleInputProperty().set(selectedFile.getName());
            }
            appModel.setStatusMessage("Selected image " + selectedFile.getName() + ".");
        }
    }

    @FXML
    private void handleSaveImage() {
        vaultManager.saveImage(appModel);
    }
}
