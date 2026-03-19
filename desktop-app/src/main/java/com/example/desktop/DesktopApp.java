package com.example.desktop;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.config.DesktopApplicationConfig;
import com.example.desktop.dao.ConnectionManager;
import com.example.desktop.dao.DatabaseConfig;
import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.SqlUserDAO;
import com.example.desktop.dao.SqlVaultItemDAO;
import com.example.desktop.gui.AuthController;
import com.example.desktop.gui.MainController;
import com.example.desktop.model.AppModel;
import com.example.shared.service.GeminiService;
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
        DesktopApplicationConfig applicationConfig = new DesktopApplicationConfig();
        DatabaseConfig databaseConfig = new DatabaseConfig();
        ConnectionManager connectionManager = new ConnectionManager(databaseConfig);
        SchemaInitializer schemaInitializer = new SchemaInitializer(connectionManager, databaseConfig);
        SqlVaultItemDAO vaultItemDAO = new SqlVaultItemDAO(connectionManager);
        SqlUserDAO userDAO = new SqlUserDAO(connectionManager);
        GeminiService geminiService = new GeminiService(applicationConfig.geminiApiKey(), applicationConfig.geminiModel());
        VaultManager vaultManager = new VaultManager(vaultItemDAO, userDAO, schemaInitializer, geminiService);
        AppModel appModel = new AppModel();

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double authWidth = Math.min(640, visualBounds.getWidth());
        double authHeight = Math.min(760, visualBounds.getHeight());
        double authMinWidth = Math.min(560, visualBounds.getWidth());
        double authMinHeight = Math.min(680, visualBounds.getHeight());
        double mainWidth = Math.min(1380, Math.max(1100, visualBounds.getWidth() - 40));
        double mainHeight = Math.min(920, Math.max(760, visualBounds.getHeight() - 40));
        double mainMinWidth = Math.min(900, visualBounds.getWidth());
        double mainMinHeight = Math.min(720, visualBounds.getHeight());

        vaultManager.initialize(appModel);

        FXMLLoader authLoader = new FXMLLoader(getClass().getResource("/com/example/desktop/gui/auth-view.fxml"));
        Parent authRoot = authLoader.load();
        AuthController authController = authLoader.getController();

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/example/desktop/gui/main-view.fxml"));
        Parent mainRoot = mainLoader.load();
        MainController mainController = mainLoader.getController();

        Scene authScene = new Scene(authRoot, authWidth, authHeight);
        Scene mainScene = new Scene(mainRoot, mainWidth, mainHeight);
        String stylesheet = getClass().getResource("/desktop/styles.css").toExternalForm();
        authScene.getStylesheets().add(stylesheet);
        mainScene.getStylesheets().add(stylesheet);

        DesktopNavigator navigator = new DesktopNavigator(
                stage,
                authScene,
                mainScene,
                appModel,
                authWidth,
                authHeight,
                authMinWidth,
                authMinHeight,
                mainMinWidth,
                mainMinHeight
        );
        navigator.setOnShowAuth(authController::showLoginView);

        authController.setContext(appModel, vaultManager, getHostServices(), stage, navigator);
        mainController.setContext(appModel, vaultManager, getHostServices(), stage, navigator);

        navigator.showAuthScene();
        stage.show();
    }
}
