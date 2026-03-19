package com.example.desktop.gui;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Root controller that wires shared desktop context into child controllers.
 */
public class MainController {

    @FXML
    private TopBarController topBarViewController;

    @FXML
    private SaveController saveViewController;

    @FXML
    private ArchiveController archiveViewController;

    @FXML
    private DetailController detailViewController;

    @FXML
    private Label statusLabel;

    public void setContext(AppModel appModel, VaultManager vaultManager, HostServices hostServices, Stage stage) {
        statusLabel.textProperty().bind(appModel.statusMessageProperty());

        initializeChild(topBarViewController, appModel, vaultManager, hostServices, stage);
        initializeChild(saveViewController, appModel, vaultManager, hostServices, stage);
        initializeChild(archiveViewController, appModel, vaultManager, hostServices, stage);
        initializeChild(detailViewController, appModel, vaultManager, hostServices, stage);

        vaultManager.initialize(appModel);
    }

    private void initializeChild(AppContextAware controller,
                                 AppModel appModel,
                                 VaultManager vaultManager,
                                 HostServices hostServices,
                                 Stage stage) {
        controller.setContext(appModel, vaultManager, hostServices, stage);
    }
}
