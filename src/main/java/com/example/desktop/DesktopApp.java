package com.example.desktop;

import com.example.desktop.bll.VaultManager;
import com.example.desktop.dao.ConnectionManager;
import com.example.desktop.dao.DatabaseConfig;
import com.example.desktop.dao.SchemaInitializer;
import com.example.desktop.dao.SqlVaultItemDAO;
import com.example.desktop.gui.MainController;
import com.example.desktop.model.AppModel;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
        VaultManager vaultManager = new VaultManager(vaultItemDAO, schemaInitializer);
        AppModel appModel = new AppModel();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/desktop/gui/main-view.fxml"));
        Parent root = loader.load();

        MainController controller = loader.getController();
        controller.setContext(appModel, vaultManager, getHostServices(), stage);

        Scene scene = new Scene(root, 1380, 920);
        scene.getStylesheets().add(getClass().getResource("/desktop/styles.css").toExternalForm());

        stage.setTitle("TimeVault Desktop");
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();
    }
}
