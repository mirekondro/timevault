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
    private final double authStageWidth;
    private final double authStageHeight;
    private final double authMinWidth;
    private final double authMinHeight;
    private final double mainMinWidth;
    private final double mainMinHeight;
    private Runnable onShowAuth = () -> { };
    private Runnable onShowMain = () -> { };

    public DesktopNavigator(Stage stage,
                            Scene authScene,
                            Scene mainScene,
                            AppModel appModel,
                            double authStageWidth,
                            double authStageHeight,
                            double authMinWidth,
                            double authMinHeight,
                            double mainMinWidth,
                            double mainMinHeight) {
        this.stage = stage;
        this.authScene = authScene;
        this.mainScene = mainScene;
        this.appModel = appModel;
        this.authStageWidth = authStageWidth;
        this.authStageHeight = authStageHeight;
        this.authMinWidth = authMinWidth;
        this.authMinHeight = authMinHeight;
        this.mainMinWidth = mainMinWidth;
        this.mainMinHeight = mainMinHeight;
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
        stage.setMaximized(false);
        stage.setMinWidth(authMinWidth);
        stage.setMinHeight(authMinHeight);
        stage.setScene(authScene);
        stage.setWidth(authStageWidth);
        stage.setHeight(authStageHeight);
        stage.centerOnScreen();
    }

    public void showMainScene() {
        onShowMain.run();
        stage.titleProperty().unbind();
        stage.titleProperty().bind(appModel.textBinding(appModel.getCurrentSceneTitleKey(false)));
        stage.setMinWidth(mainMinWidth);
        stage.setMinHeight(mainMinHeight);
        stage.setScene(mainScene);
        stage.setMaximized(true);
    }
}
