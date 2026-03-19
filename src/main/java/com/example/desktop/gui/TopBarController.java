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
    private Label totalTitleLabel;

    @FXML
    private Label totalCountValue;

    @FXML
    private Label totalCopyLabel;

    @FXML
    private Label urlTitleLabel;

    @FXML
    private Label urlCountValue;

    @FXML
    private Label urlCopyLabel;

    @FXML
    private Label textTitleLabel;

    @FXML
    private Label textCountValue;

    @FXML
    private Label textCopyLabel;

    @FXML
    private Label imageTitleLabel;

    @FXML
    private Label imageCountValue;

    @FXML
    private Label imageCopyLabel;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        appModel.bindText(totalTitleLabel, "stats.total.title");
        appModel.bindText(totalCopyLabel, "stats.total.copy");
        appModel.bindText(urlTitleLabel, "stats.url.title");
        appModel.bindText(urlCopyLabel, "stats.url.copy");
        appModel.bindText(textTitleLabel, "stats.text.title");
        appModel.bindText(textCopyLabel, "stats.text.copy");
        appModel.bindText(imageTitleLabel, "stats.image.title");
        appModel.bindText(imageCopyLabel, "stats.image.copy");

        totalCountValue.textProperty().bind(appModel.totalCountProperty().asString());
        urlCountValue.textProperty().bind(appModel.urlCountProperty().asString());
        textCountValue.textProperty().bind(appModel.textCountProperty().asString());
        imageCountValue.textProperty().bind(appModel.imageCountProperty().asString());
    }
}
