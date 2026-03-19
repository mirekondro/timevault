package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
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
    private Label sectionTitleLabel;

    @FXML
    private Label sectionCopyLabel;

    @FXML
    private TabPane saveTabPane;

    @FXML
    private Tab urlTab;

    @FXML
    private Label urlCopyLabel;

    @FXML
    private TextField urlField;

    @FXML
    private TextField urlTitleField;

    @FXML
    private TextArea urlNotesArea;

    @FXML
    private Tab textTab;

    @FXML
    private Label textCopyLabel;

    @FXML
    private TextField textTitleField;

    @FXML
    private TextArea textContentArea;

    @FXML
    private Tab imageTab;

    @FXML
    private Label imageCopyLabel;

    @FXML
    private TextField imageTitleField;

    @FXML
    private TextField imagePathField;

    @FXML
    private Button saveUrlButton;

    @FXML
    private Button saveTextButton;

    @FXML
    private Button browseImageButton;

    @FXML
    private Button saveImageButton;

    private AppModel appModel;
    private VaultManager vaultManager;
    private Stage stage;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
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

        appModel.bindText(sectionTitleLabel, "save.section.title");
        appModel.bindText(sectionCopyLabel, "save.section.copy");
        appModel.bindText(urlTab, "save.tab.url");
        appModel.bindText(textTab, "save.tab.text");
        appModel.bindText(imageTab, "save.tab.image");
        appModel.bindText(urlCopyLabel, "save.url.copy");
        appModel.bindPrompt(urlField, "save.url.prompt");
        appModel.bindPrompt(urlTitleField, "save.url.title.prompt");
        appModel.bindPrompt(urlNotesArea, "save.url.notes.prompt");
        appModel.bindText(saveUrlButton, "save.url.button");
        appModel.bindText(textCopyLabel, "save.text.copy");
        appModel.bindPrompt(textTitleField, "save.text.title.prompt");
        appModel.bindPrompt(textContentArea, "save.text.content.prompt");
        appModel.bindText(saveTextButton, "save.text.button");
        appModel.bindText(imageCopyLabel, "save.image.copy");
        appModel.bindPrompt(imageTitleField, "save.image.title.prompt");
        appModel.bindPrompt(imagePathField, "save.image.path.prompt");
        appModel.bindText(browseImageButton, "save.image.browse");
        appModel.bindText(saveImageButton, "save.image.button");

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
        chooser.setTitle(appModel.text("fileChooser.image.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                appModel.text("fileChooser.image.filter"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        File selectedFile = chooser.showOpenDialog(stage);
        if (selectedFile != null) {
            appModel.imagePathInputProperty().set(selectedFile.getAbsolutePath());
            if (appModel.imageTitleInputProperty().get().isBlank()) {
                appModel.imageTitleInputProperty().set(selectedFile.getName());
            }
            appModel.showSuccessKey("status.save.image.selected", selectedFile.getName());
        }
    }

    @FXML
    private void handleSaveImage() {
        vaultManager.saveImage(appModel);
    }
}
