package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Controller for desktop summary cards.
 */
public class TopBarController implements AppContextAware {

    @FXML
    private Label totalCountValue;

    @FXML
    private Label urlCountValue;

    @FXML
    private Label textCountValue;

    @FXML
    private Label imageCountValue;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        totalCountValue.textProperty().bind(appModel.totalCountProperty().asString());
        urlCountValue.textProperty().bind(appModel.urlCountProperty().asString());
        textCountValue.textProperty().bind(appModel.textCountProperty().asString());
        imageCountValue.textProperty().bind(appModel.imageCountProperty().asString());
    }
}
