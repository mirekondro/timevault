package com.example.desktop;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.dao.ConnectionManager;
import com.example.desktop.dao.DatabaseConfig;
import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.SqlUserDAO;
import com.example.desktop.dao.SqlVaultItemDAO;
import com.example.desktop.gui.AuthController;
import com.example.desktop.gui.MainController;
import com.example.desktop.model.AppModel;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX entry point for the layered desktop TimeVault app.
 */
public class DesktopApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        DatabaseConfig databaseConfig = new DatabaseConfig();
        ConnectionManager connectionManager = new ConnectionManager(databaseConfig);
        SchemaInitializer schemaInitializer = new SchemaInitializer(connectionManager, databaseConfig);
        SqlVaultItemDAO vaultItemDAO = new SqlVaultItemDAO(connectionManager);
        SqlUserDAO userDAO = new SqlUserDAO(connectionManager);
        VaultManager vaultManager = new VaultManager(vaultItemDAO, userDAO, schemaInitializer);
        AppModel appModel = new AppModel();

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double targetWidth = Math.min(1380, Math.max(900, visualBounds.getWidth() - 80));
        double targetHeight = Math.min(920, Math.max(680, visualBounds.getHeight() - 80));

        vaultManager.initialize(appModel);

        FXMLLoader authLoader = new FXMLLoader(getClass().getResource("/com/example/desktop/gui/auth-view.fxml"));
        Parent authRoot = authLoader.load();
        AuthController authController = authLoader.getController();

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/example/desktop/gui/main-view.fxml"));
        Parent mainRoot = mainLoader.load();
        MainController mainController = mainLoader.getController();

        Scene authScene = new Scene(authRoot, targetWidth, targetHeight);
        Scene mainScene = new Scene(mainRoot, targetWidth, targetHeight);
        String stylesheet = getClass().getResource("/desktop/styles.css").toExternalForm();
        authScene.getStylesheets().add(stylesheet);
        mainScene.getStylesheets().add(stylesheet);

        DesktopNavigator navigator = new DesktopNavigator(stage, authScene, mainScene, appModel);
        navigator.setOnShowAuth(authController::showLoginView);

        authController.setContext(appModel, vaultManager, getHostServices(), stage, navigator);
        mainController.setContext(appModel, vaultManager, getHostServices(), stage, navigator);

        stage.setMinWidth(Math.min(900, visualBounds.getWidth()));
        stage.setMinHeight(Math.min(680, visualBounds.getHeight()));
        navigator.showAuthScene();
        stage.centerOnScreen();
        stage.show();
    }
}
