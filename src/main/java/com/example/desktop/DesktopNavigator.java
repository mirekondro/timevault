package com.example.desktop;

import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Handles scene navigation between authentication and the main desktop shell.
 */
public class DesktopNavigator {

    private final Stage stage;
    private final Scene authScene;
    private final Scene mainScene;
    private Runnable onShowAuth = () -> { };
    private Runnable onShowMain = () -> { };

    public DesktopNavigator(Stage stage, Scene authScene, Scene mainScene) {
        this.stage = stage;
        this.authScene = authScene;
        this.mainScene = mainScene;
    }

    public void setOnShowAuth(Runnable onShowAuth) {
        this.onShowAuth = onShowAuth == null ? () -> { } : onShowAuth;
    }

    public void setOnShowMain(Runnable onShowMain) {
        this.onShowMain = onShowMain == null ? () -> { } : onShowMain;
    }

    public void showAuthScene() {
        onShowAuth.run();
        stage.setTitle("TimeVault Desktop - Log In");
        stage.setScene(authScene);
    }

    public void showMainScene() {
        onShowMain.run();
        stage.setTitle("TimeVault Desktop");
        stage.setScene(mainScene);
    }
}
