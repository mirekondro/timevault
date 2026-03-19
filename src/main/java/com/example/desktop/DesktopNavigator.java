package com.example.desktop;

import com.example.desktop.model.AppModel;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Handles scene navigation between authentication and the main desktop shell.
 */
public class DesktopNavigator {

    private final Stage stage;
    private final Scene authScene;
    private final Scene mainScene;
    private final AppModel appModel;
    private Runnable onShowAuth = () -> { };
    private Runnable onShowMain = () -> { };

    public DesktopNavigator(Stage stage, Scene authScene, Scene mainScene, AppModel appModel) {
        this.stage = stage;
        this.authScene = authScene;
        this.mainScene = mainScene;
        this.appModel = appModel;
    }

    public void setOnShowAuth(Runnable onShowAuth) {
        this.onShowAuth = onShowAuth == null ? () -> { } : onShowAuth;
    }

    public void setOnShowMain(Runnable onShowMain) {
        this.onShowMain = onShowMain == null ? () -> { } : onShowMain;
    }

    public void showAuthScene() {
        onShowAuth.run();
        stage.titleProperty().unbind();
        stage.titleProperty().bind(appModel.textBinding(appModel.getCurrentSceneTitleKey(true)));
        stage.setScene(authScene);
    }

    public void showMainScene() {
        onShowMain.run();
        stage.titleProperty().unbind();
        stage.titleProperty().bind(appModel.textBinding(appModel.getCurrentSceneTitleKey(false)));
        stage.setScene(mainScene);
    }
}
