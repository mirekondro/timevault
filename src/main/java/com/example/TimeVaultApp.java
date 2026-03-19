package com.example;

import com.example.gui.MainView;
import com.example.repositories.ArchiveRepository;
import com.example.repositories.CapsuleRepository;
import com.example.repositories.DatabaseManager;
import com.example.repositories.TagRepository;
import com.example.services.AiContextService;
import com.example.services.ArchiveService;
import com.example.services.CapsuleService;
import com.example.services.FetchService;
import com.example.services.FileStorageService;
import com.example.services.RescueService;
import com.example.support.TimeVaultPaths;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimeVaultApp extends javafx.application.Application {

    private ExecutorService executor;

    @Override
    public void start(Stage stage) {
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "timevault-worker");
            thread.setDaemon(true);
            return thread;
        });

        TimeVaultPaths paths = TimeVaultPaths.defaultPaths();
        DatabaseManager databaseManager = new DatabaseManager(paths.databasePath());
        ArchiveRepository archiveRepository = new ArchiveRepository(databaseManager);
        TagRepository tagRepository = new TagRepository(databaseManager);
        CapsuleRepository capsuleRepository = new CapsuleRepository(databaseManager);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        FileStorageService fileStorageService = new FileStorageService(paths);
        FetchService fetchService = new FetchService(httpClient);
        AiContextService aiContextService = new AiContextService(httpClient);
        ArchiveService archiveService = new ArchiveService(
                archiveRepository,
                tagRepository,
                fetchService,
                aiContextService,
                fileStorageService
        );
        RescueService rescueService = new RescueService(httpClient, fetchService, aiContextService, archiveService);
        CapsuleService capsuleService = new CapsuleService(httpClient, capsuleRepository, aiContextService);

        MainView root = new MainView(stage, archiveService, rescueService, capsuleService, executor, paths);
        Scene scene = new Scene(root, 1440, 920);
        scene.getStylesheets().add(TimeVaultApp.class.getResource("/styles.css").toExternalForm());

        stage.setTitle("TimeVault - The Archive of Tomorrow");
        stage.setMinWidth(1280);
        stage.setMinHeight(820);
        stage.setScene(scene);
        stage.show();

        Platform.runLater(root::initialize);
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
