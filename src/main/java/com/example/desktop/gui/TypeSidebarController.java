package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;

/**
 * Compact left rail for switching between item types.
 */
public class TypeSidebarController implements AppContextAware {

    @FXML
    private Label sidebarTitleLabel;

    @FXML
    private ToggleButton allTypeButton;

    @FXML
    private ToggleButton urlTypeButton;

    @FXML
    private ToggleButton textTypeButton;

    @FXML
    private ToggleButton imageTypeButton;

    private final ToggleGroup toggleGroup = new ToggleGroup();
    private AppModel appModel;
    private boolean updatingSelection;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;

        appModel.bindText(sidebarTitleLabel, "archive.sidebar.title");
        configureTypeButton(allTypeButton, AppModel.TYPE_ALL, "type.all");
        configureTypeButton(urlTypeButton, AppModel.TYPE_URL, "type.url");
        configureTypeButton(textTypeButton, AppModel.TYPE_TEXT, "type.text");
        configureTypeButton(imageTypeButton, AppModel.TYPE_IMAGE, "type.image");

        appModel.selectedTypeProperty().addListener((obs, oldValue, newValue) -> updateSelectedButton(newValue));
        updateSelectedButton(appModel.selectedTypeProperty().get());
    }

    private void configureTypeButton(ToggleButton button, String typeCode, String key) {
        button.setUserData(typeCode);
        button.setToggleGroup(toggleGroup);
        appModel.bindText(button, key);
        button.disableProperty().bind(appModel.authenticatedProperty().not());
        button.setOnAction(event -> {
            if (!updatingSelection) {
                appModel.selectedTypeProperty().set(typeCode);
            }
        });
    }

    private void updateSelectedButton(String typeCode) {
        updatingSelection = true;
        try {
            ToggleButton button = switch (typeCode == null ? AppModel.TYPE_ALL : typeCode) {
                case AppModel.TYPE_URL -> urlTypeButton;
                case AppModel.TYPE_TEXT -> textTypeButton;
                case AppModel.TYPE_IMAGE -> imageTypeButton;
                case AppModel.TYPE_ALL -> allTypeButton;
                default -> allTypeButton;
            };
            toggleGroup.selectToggle(button);
        } finally {
            updatingSelection = false;
        }
    }
}
