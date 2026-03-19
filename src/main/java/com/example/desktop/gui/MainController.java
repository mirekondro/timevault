package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
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
    private HeaderController headerViewController;

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

    @FXML
    private Label footerCreditLabel;

    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        statusLabel.textProperty().bind(appModel.statusMessageProperty());
        appModel.bindText(footerCreditLabel, "footer.credit");

        initializeChild(headerViewController, appModel, vaultManager, hostServices, stage, navigator);
        initializeChild(topBarViewController, appModel, vaultManager, hostServices, stage, navigator);
        initializeChild(saveViewController, appModel, vaultManager, hostServices, stage, navigator);
        initializeChild(archiveViewController, appModel, vaultManager, hostServices, stage, navigator);
        initializeChild(detailViewController, appModel, vaultManager, hostServices, stage, navigator);
    }

    private void initializeChild(AppContextAware controller,
                                 AppModel appModel,
                                 VaultManager vaultManager,
                                 HostServices hostServices,
                                 Stage stage,
                                 DesktopNavigator navigator) {
        controller.setContext(appModel, vaultManager, hostServices, stage, navigator);
    }
}
