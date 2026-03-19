package com.example.desktop;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.dao.ConnectionManager;
import com.example.desktop.dao.DatabaseConfig;
import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.SqlUserDAO;
import com.example.desktop.dao.SqlVaultItemDAO;
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

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/desktop/gui/main-view.fxml"));
        Parent root = loader.load();

        MainController controller = loader.getController();
        controller.setContext(appModel, vaultManager, getHostServices(), stage);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double targetWidth = Math.min(1380, Math.max(900, visualBounds.getWidth() - 80));
        double targetHeight = Math.min(920, Math.max(680, visualBounds.getHeight() - 80));

        Scene scene = new Scene(root, targetWidth, targetHeight);
        scene.getStylesheets().add(getClass().getResource("/desktop/styles.css").toExternalForm());

        stage.setTitle("TimeVault Desktop");
        stage.setMinWidth(Math.min(900, visualBounds.getWidth()));
        stage.setMinHeight(Math.min(680, visualBounds.getHeight()));
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}
