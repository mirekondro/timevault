package com.example.gui;

import com.example.entities.Archive;
import com.example.entities.ArchiveDraft;
import com.example.entities.ArchiveType;
import com.example.entities.DailyCapsule;
import com.example.entities.DashboardStats;
import com.example.services.ArchiveService;
import com.example.services.CapsuleService;
import com.example.services.RescueResult;
import com.example.services.RescueService;
import com.example.support.TimeVaultPaths;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MainView extends BorderPane {

    private final Stage stage;
    private final ArchiveService archiveService;
    private final RescueService rescueService;
    private final CapsuleService capsuleService;
    private final ExecutorService executor;
    private final TimeVaultPaths paths;

    private final StackPane contentHolder = new StackPane();
    private final Map<Section, Button> navButtons = new EnumMap<>(Section.class);
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(220));

    private final TextField searchField = new TextField();
    private final ComboBox<String> filterBox = new ComboBox<>();
    private final Label statusLabel = new Label("Booting TimeVault...");

    private final TextField quickCaptureField = new TextField();
    private final Label totalSavedValue = new Label("0");
    private final Label savedThisWeekValue = new Label("0");
    private final Label rescuedLinksValue = new Label("0");
    private final TilePane recentGrid = new TilePane();

    private final TilePane archiveGrid = new TilePane();
    private final Label archiveResultLabel = new Label("Archive is loading...");
    private final Label detailTitleLabel = new Label("Select a memory from the archive");
    private final Label detailMetaLabel = new Label("The full record, context note, and export tools appear here.");
    private final Label detailSourceLabel = new Label();
    private final Label detailAiContextLabel = new Label();
    private final FlowPane detailTagPane = new FlowPane();
    private final TextArea detailContentArea = readOnlyArea(18);
    private final ImageView detailImageView = new ImageView();
    private final HBox detailButtonRow = new HBox(10);
    private final Button exportButton = new Button("Export");
    private final Button deleteButton = new Button("Delete");
    private final Button rescueButton = new Button("Rescue Link");
    private final ObjectProperty<Archive> selectedArchive = new SimpleObjectProperty<>();

    private final TextField rescueUrlField = new TextField();
    private final Button rescueLookupButton = new Button("Search Wayback");
    private final Button rescueSaveButton = new Button("Save to Vault");
    private final ProgressIndicator rescueProgress = new ProgressIndicator();
    private final Label rescueTitleLabel = new Label("Paste a dead link and TimeVault will look for a Wayback snapshot.");
    private final Label rescueMetaLabel = new Label("No rescue result yet.");
    private final TextArea rescueContentArea = readOnlyArea(10);
    private final TextArea rescueAiArea = readOnlyArea(8);
    private final ObjectProperty<RescueResult> rescueResult = new SimpleObjectProperty<>();

    private final VBox capsuleList = new VBox(16);

    private Node dashboardView;
    private Node archiveView;
    private Node rescueView;
    private Node capsuleView;
    private Section currentSection = Section.DASHBOARD;

    public MainView(
            Stage stage,
            ArchiveService archiveService,
            RescueService rescueService,
            CapsuleService capsuleService,
            ExecutorService executor,
            TimeVaultPaths paths
    ) {
        this.stage = stage;
        this.archiveService = archiveService;
        this.rescueService = rescueService;
        this.capsuleService = capsuleService;
        this.executor = executor;
        this.paths = paths;

        getStyleClass().add("app-root");
        setLeft(buildSidebar());
        setTop(buildTopBar());
        setCenter(contentHolder);
        setBottom(buildStatusBar());

        dashboardView = buildDashboardView();
        archiveView = buildArchiveView();
        rescueView = buildRescueView();
        capsuleView = buildCapsuleView();
        contentHolder.getChildren().setAll(dashboardView);

        configureInteractions();
    }

    public void initialize() {
        showSection(Section.DASHBOARD);
        reloadEverything();
        setStatus("Vault ready. SQL Server connected, artifacts stored in " + paths.baseDir());
    }

    private void configureInteractions() {
        filterBox.getItems().addAll("All Types", "URL", "Image", "Text", "Event");
        filterBox.getSelectionModel().selectFirst();
        filterBox.setOnAction(event -> {
            if (currentSection == Section.ARCHIVE) {
                loadArchiveView();
            }
        });

        searchField.setPromptText("Search saved URLs, memes, notes, and tags");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (currentSection == Section.ARCHIVE) {
                searchDebounce.playFromStart();
            }
        });
        searchField.setOnAction(event -> {
            showSection(Section.ARCHIVE);
            loadArchiveView();
        });
        searchDebounce.setOnFinished(event -> loadArchiveView());

        quickCaptureField.setPromptText("Paste a URL or type a moment worth saving");
        quickCaptureField.setOnAction(event -> saveQuickText());

        exportButton.setOnAction(event -> {
            Archive archive = selectedArchive.get();
            if (archive == null) {
                return;
            }
            Path exportPath = archiveService.exportArchive(archive);
            setStatus("Exported \"" + archive.getTitle() + "\" to " + exportPath);
        });

        deleteButton.setOnAction(event -> {
            Archive archive = selectedArchive.get();
            if (archive == null) {
                return;
            }
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete \"" + archive.getTitle() + "\" from the vault?", ButtonType.YES, ButtonType.CANCEL);
            alert.initOwner(stage);
            alert.setHeaderText("Delete archive item");
            alert.showAndWait().filter(ButtonType.YES::equals).ifPresent(ignore -> {
                archiveService.deleteArchive(archive);
                selectedArchive.set(null);
                clearArchiveDetails();
                reloadEverything();
                setStatus("Deleted \"" + archive.getTitle() + "\" from the vault.");
            });
        });

        rescueButton.setOnAction(event -> {
            Archive archive = selectedArchive.get();
            if (archive == null || archive.getUrl() == null || archive.getUrl().isBlank()) {
                return;
            }
            rescueUrlField.setText(archive.getUrl());
            showSection(Section.RESCUE);
            setStatus("Loaded the archive URL into Dead Link Rescue.");
        });

        rescueLookupButton.setOnAction(event -> runRescueLookup());
        rescueSaveButton.setOnAction(event -> saveRescueResult());
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(14);
        sidebar.setPadding(new Insets(26));
        sidebar.setPrefWidth(220);
        sidebar.getStyleClass().add("sidebar");

        Label title = new Label("TimeVault");
        title.getStyleClass().add("sidebar-title");
        Label subtitle = new Label("The Archive of Tomorrow");
        subtitle.getStyleClass().add("sidebar-subtitle");

        VBox navBox = new VBox(10,
                navButton("Dashboard", Section.DASHBOARD),
                navButton("Archive", Section.ARCHIVE),
                navButton("Dead Rescue", Section.RESCUE),
                navButton("Denmark Today", Section.CAPSULES)
        );

        Button saveStudioButton = pillButton("Open Save Studio");
        saveStudioButton.getStyleClass().add("accent-button");
        saveStudioButton.setMaxWidth(Double.MAX_VALUE);
        saveStudioButton.setOnAction(event -> openSaveDialog());
        installLift(saveStudioButton);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("Local-first archive.\nZero server required.");
        footer.getStyleClass().add("muted-copy");

        sidebar.getChildren().addAll(title, subtitle, new Separator(), navBox, spacer, saveStudioButton, footer);
        return sidebar;
    }

    private Node buildTopBar() {
        FlowPane bar = new FlowPane(14, 14);
        bar.setPadding(new Insets(22, 28, 18, 28));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");

        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(420);
        searchField.setMinWidth(260);
        filterBox.setPrefWidth(150);

        Button searchButton = pillButton("Search");
        searchButton.setOnAction(event -> {
            showSection(Section.ARCHIVE);
            loadArchiveView();
        });
        installLift(searchButton);

        Button newCaptureButton = pillButton("New Capture");
        newCaptureButton.getStyleClass().add("accent-button");
        newCaptureButton.setOnAction(event -> openSaveDialog());
        installLift(newCaptureButton);

        bar.getChildren().addAll(searchField, filterBox, searchButton, newCaptureButton);
        return bar;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(16);
        bar.setPadding(new Insets(14, 28, 20, 28));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");

        statusLabel.getStyleClass().add("status-label");
        Label storageLabel = new Label("Artifacts: " + paths.baseDir());
        storageLabel.getStyleClass().add("muted-copy");
        storageLabel.setContentDisplay(ContentDisplay.RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(statusLabel, spacer, storageLabel);
        return bar;
    }

    private Node buildDashboardView() {
        VBox page = pageBox();

        VBox hero = sectionCard();
        Label kicker = new Label("Hack Esbjerg 2026");
        kicker.getStyleClass().add("eyebrow");
        Label headline = new Label("Save the internet before it disappears.");
        headline.getStyleClass().add("hero-title");
        Label body = new Label("Capture a URL, a note, or an image. TimeVault stores the artifact, writes a context note, and turns today into something future historians can actually feel.");
        body.setWrapText(true);
        body.getStyleClass().add("muted-copy");

        FlowPane quickBar = new FlowPane(12, 12);
        quickBar.setAlignment(Pos.CENTER_LEFT);
        quickCaptureField.setPrefWidth(420);
        quickCaptureField.setMinWidth(260);
        Button saveUrlButton = pillButton("Save URL");
        Button saveTextButton = pillButton("Save Text");
        Button openStudioButton = pillButton("Preview in Studio");
        openStudioButton.getStyleClass().add("ghost-button");
        saveUrlButton.setOnAction(event -> saveQuickUrl());
        saveTextButton.setOnAction(event -> saveQuickText());
        openStudioButton.setOnAction(event -> openSaveDialog());
        installLift(saveUrlButton);
        installLift(saveTextButton);
        installLift(openStudioButton);
        quickBar.getChildren().addAll(quickCaptureField, saveUrlButton, saveTextButton, openStudioButton);

        hero.getChildren().addAll(kicker, headline, body, quickBar);

        FlowPane statsRow = new FlowPane(16, 16,
                statCard("Total Saved", totalSavedValue, "Everything in the local vault"),
                statCard("This Week", savedThisWeekValue, "Fresh captures from the last 7 days"),
                statCard("Rescued Links", rescuedLinksValue, "Recovered through Wayback")
        );

        VBox recentSection = sectionCard();
        Label recentTitle = sectionTitle("Recent Saves");
        recentGrid.setHgap(16);
        recentGrid.setVgap(16);
        recentGrid.setPrefColumns(3);
        recentSection.getChildren().addAll(recentTitle, recentGrid);

        page.getChildren().addAll(hero, statsRow, recentSection);
        return pageScroll(page);
    }

    private Node buildArchiveView() {
        VBox leftPane = sectionCard();
        leftPane.setPadding(new Insets(22));
        leftPane.setMinWidth(320);
        Label archiveTitle = sectionTitle("Archive View");
        Label archiveHint = new Label("Live search and type filters update this wall as you browse.");
        archiveHint.getStyleClass().add("muted-copy");
        archiveGrid.setHgap(16);
        archiveGrid.setVgap(16);
        archiveGrid.setPrefColumns(2);
        ScrollPane gridScroll = pageScroll(archiveGrid);
        gridScroll.setFitToWidth(true);
        VBox.setVgrow(gridScroll, Priority.ALWAYS);
        leftPane.getChildren().addAll(archiveTitle, archiveHint, archiveResultLabel, gridScroll);

        VBox rightPane = sectionCard();
        rightPane.setPadding(new Insets(22));
        rightPane.setSpacing(16);
        rightPane.setMinWidth(320);
        detailTitleLabel.getStyleClass().add("detail-title");
        detailMetaLabel.getStyleClass().add("muted-copy");
        detailSourceLabel.getStyleClass().add("muted-copy");
        detailAiContextLabel.getStyleClass().add("context-box");
        detailAiContextLabel.setWrapText(true);
        detailTagPane.setHgap(8);
        detailTagPane.setVgap(8);
        detailImageView.setFitWidth(320);
        detailImageView.setPreserveRatio(true);
        detailImageView.setSmooth(true);
        detailButtonRow.getChildren().addAll(exportButton, deleteButton, rescueButton);
        detailButtonRow.setAlignment(Pos.CENTER_LEFT);
        installLift(exportButton);
        installLift(deleteButton);
        installLift(rescueButton);
        rightPane.getChildren().addAll(detailTitleLabel, detailMetaLabel, detailSourceLabel, detailImageView, detailAiContextLabel, detailTagPane, detailContentArea, detailButtonRow);
        VBox.setVgrow(detailContentArea, Priority.ALWAYS);
        clearArchiveDetails();

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setDividerPositions(0.56);
        return splitPane;
    }

    private Node buildRescueView() {
        VBox page = pageBox();

        VBox hero = sectionCard();
        Label title = sectionTitle("Dead Link Rescue");
        Label body = new Label("Paste a broken URL and TimeVault will ask the Wayback Machine for the closest surviving snapshot.");
        body.setWrapText(true);
        body.getStyleClass().add("muted-copy");
        FlowPane row = new FlowPane(12, 12);
        rescueUrlField.setPrefWidth(460);
        rescueUrlField.setMinWidth(260);
        rescueUrlField.setPromptText("https://example.com");
        rescueProgress.setVisible(false);
        rescueProgress.setMaxSize(30, 30);
        installLift(rescueLookupButton);
        row.getChildren().addAll(rescueUrlField, rescueLookupButton, rescueProgress);
        hero.getChildren().addAll(title, body, row);

        VBox resultCard = sectionCard();
        rescueTitleLabel.getStyleClass().add("detail-title");
        rescueMetaLabel.getStyleClass().add("muted-copy");
        rescueSaveButton.setDisable(true);
        installLift(rescueSaveButton);
        resultCard.getChildren().addAll(rescueTitleLabel, rescueMetaLabel, rescueContentArea, rescueAiArea, rescueSaveButton);

        page.getChildren().addAll(hero, resultCard);
        return pageScroll(page);
    }

    private Node buildCapsuleView() {
        VBox page = pageBox();

        VBox hero = sectionCard();
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label title = sectionTitle("Denmark Today");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button captureButton = pillButton("Capture Today");
        captureButton.getStyleClass().add("accent-button");
        captureButton.setOnAction(event -> captureToday());
        installLift(captureButton);
        row.getChildren().addAll(title, spacer, captureButton);

        Label body = new Label("Daily vibe capsules blend Danish headlines with an AI summary so the archive remembers how a day felt, not just what it contained.");
        body.setWrapText(true);
        body.getStyleClass().add("muted-copy");
        hero.getChildren().addAll(row, body);

        VBox timelineCard = sectionCard();
        timelineCard.getChildren().addAll(sectionTitle("Timeline"), capsuleList);
        page.getChildren().addAll(hero, timelineCard);
        return pageScroll(page);
    }

    private void reloadEverything() {
        loadDashboard();
        loadArchiveView();
        loadCapsules();
    }

    private void loadDashboard() {
        runAsync("Loading dashboard...", () -> new DashboardPayload(archiveService.getDashboardStats(), archiveService.recentArchives(6)), payload -> {
            DashboardStats stats = payload.stats();
            totalSavedValue.setText(Long.toString(stats.totalSaved()));
            savedThisWeekValue.setText(Long.toString(stats.savedThisWeek()));
            rescuedLinksValue.setText(Long.toString(stats.rescuedLinks()));
            recentGrid.getChildren().setAll(cardsFor(payload.recent()));
            if (payload.recent().isEmpty()) {
                recentGrid.getChildren().setAll(emptyState("Nothing saved yet. Capture your first internet moment."));
            }
        });
    }

    private void loadArchiveView() {
        runAsync("Refreshing archive view...", () -> archiveService.searchArchives(searchField.getText(), selectedFilter()), archives -> {
            archiveResultLabel.setText(archives.size() + " item" + (archives.size() == 1 ? "" : "s") + " in view");
            archiveGrid.getChildren().setAll(cardsFor(archives));
            if (archives.isEmpty()) {
                archiveGrid.getChildren().setAll(emptyState("No saved items match this search yet."));
            }
            Archive current = selectedArchive.get();
            if (current != null) {
                archives.stream()
                        .filter(archive -> archive.getId().equals(current.getId()))
                        .findFirst()
                        .ifPresent(this::selectArchive);
            }
        });
    }

    private void loadCapsules() {
        runAsync("Loading Denmark Today timeline...", capsuleService::listCapsules, capsules -> {
            capsuleList.getChildren().clear();
            if (capsules.isEmpty()) {
                capsuleList.getChildren().add(emptyState("No daily capsules yet. Capture today to start the timeline."));
                return;
            }
            capsules.forEach(capsule -> capsuleList.getChildren().add(capsuleCard(capsule)));
        });
    }

    private void saveQuickUrl() {
        String value = quickCaptureField.getText().trim();
        if (value.isBlank()) {
            setStatus("Add a URL first.");
            return;
        }
        runAsync("Saving URL capture...", () -> archiveService.saveDraft(archiveService.createUrlDraft(value)), archive -> {
            quickCaptureField.clear();
            afterArchiveSaved(archive, "Saved URL capture");
        });
    }

    private void saveQuickText() {
        String value = quickCaptureField.getText().trim();
        if (value.isBlank()) {
            setStatus("Add text first.");
            return;
        }
        runAsync("Saving text capture...", () -> archiveService.saveDraft(archiveService.createTextDraft(null, value)), archive -> {
            quickCaptureField.clear();
            afterArchiveSaved(archive, "Saved text capture");
        });
    }

    private void runRescueLookup() {
        String value = rescueUrlField.getText().trim();
        if (value.isBlank()) {
            setStatus("Paste a dead URL to rescue.");
            return;
        }
        rescueProgress.setVisible(true);
        runAsync("Searching the Wayback Machine...", () -> rescueService.rescue(value), result -> {
            rescueProgress.setVisible(false);
            rescueResult.set(result);
            rescueSaveButton.setDisable(false);
            rescueTitleLabel.setText(result.draft().getTitle());
            rescueMetaLabel.setText("Closest snapshot: " + result.snapshotDateTime() + "\n" + result.snapshotUrl());
            rescueContentArea.setText(result.draft().getContent());
            rescueAiArea.setText(result.draft().getAiContext());
            setStatus("Found a surviving snapshot on the Wayback Machine.");
        }, () -> rescueProgress.setVisible(false));
    }

    private void saveRescueResult() {
        RescueResult result = rescueResult.get();
        if (result == null) {
            return;
        }
        runAsync("Saving rescued page...", () -> rescueService.saveRescue(result), archive -> {
            rescueSaveButton.setDisable(true);
            afterArchiveSaved(archive, "Rescued page saved");
            showSection(Section.ARCHIVE);
        });
    }

    private void captureToday() {
        runAsync("Capturing today's Danish mood...", capsuleService::captureToday, capsule -> {
            loadCapsules();
            setStatus("Captured Denmark Today for " + capsule.getCapsuleDate());
            showSection(Section.CAPSULES);
        });
    }

    private void afterArchiveSaved(Archive archive, String action) {
        reloadEverything();
        showSection(Section.ARCHIVE);
        selectArchive(archive);
        setStatus(action + ": \"" + archive.getTitle() + "\"");
    }

    private void selectArchive(Archive archive) {
        selectedArchive.set(archive);
        detailTitleLabel.setText(archive.getTitle());
        detailMetaLabel.setText(archive.getType().displayName() + "  |  " + archive.createdAtLabel());
        detailSourceLabel.setText((archive.getSourcePlatform() == null ? "" : archive.getSourcePlatform())
                + (archive.getUrl() == null || archive.getUrl().isBlank() ? "" : "  |  " + archive.getUrl()));
        detailAiContextLabel.setText(archive.getAiContext());
        detailContentArea.setText(archive.getContent());
        detailTagPane.getChildren().setAll(archive.getTags().stream().map(this::tagChip).toList());
        rescueButton.setDisable(!archive.isRescuable());
        exportButton.setDisable(false);
        deleteButton.setDisable(false);

        if (archive.getType() == ArchiveType.IMAGE && archive.getFilePath() != null && Files.exists(Path.of(archive.getFilePath()))) {
            detailImageView.setVisible(true);
            detailImageView.setManaged(true);
            detailImageView.setImage(new Image(Path.of(archive.getFilePath()).toUri().toString(), true));
        } else {
            detailImageView.setVisible(false);
            detailImageView.setManaged(false);
            detailImageView.setImage(null);
        }
    }

    private void clearArchiveDetails() {
        detailTitleLabel.setText("Select a memory from the archive");
        detailMetaLabel.setText("The full record, context note, and export tools appear here.");
        detailSourceLabel.setText("");
        detailAiContextLabel.setText("AI context appears here after you pick an item.");
        detailContentArea.clear();
        detailTagPane.getChildren().clear();
        detailImageView.setVisible(false);
        detailImageView.setManaged(false);
        detailImageView.setImage(null);
        exportButton.setDisable(true);
        deleteButton.setDisable(true);
        rescueButton.setDisable(true);
    }

    private List<Node> cardsFor(List<Archive> archives) {
        return archives.stream().map(this::archiveCard).map(Node.class::cast).toList();
    }

    private VBox archiveCard(Archive archive) {
        VBox card = sectionCard();
        card.setPrefWidth(280);
        Label title = new Label(archive.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        Label meta = new Label(archive.getType().displayName() + "  |  " + archive.createdAtLabel());
        meta.getStyleClass().add("muted-copy");
        Label excerpt = new Label(archive.excerpt(180));
        excerpt.setWrapText(true);
        excerpt.getStyleClass().add("card-copy");
        Label context = new Label(firstSentence(archive.getAiContext()));
        context.setWrapText(true);
        context.getStyleClass().add("muted-copy");
        card.getChildren().addAll(title, meta, excerpt, context);
        installLift(card);
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> {
            showSection(Section.ARCHIVE);
            selectArchive(archive);
        });
        return card;
    }

    private VBox capsuleCard(DailyCapsule capsule) {
        VBox card = sectionCard();
        Label date = new Label(capsule.getCapsuleDate().toString());
        date.getStyleClass().add("eyebrow");
        Label headline = new Label(capsule.getHeadline());
        headline.getStyleClass().add("card-title");
        headline.setWrapText(true);
        Label vibe = new Label(capsule.getVibeSummary());
        vibe.setWrapText(true);
        Label topics = new Label("Topics: " + capsule.getTrendingTopics());
        topics.setWrapText(true);
        topics.getStyleClass().add("muted-copy");
        card.getChildren().addAll(date, headline, vibe, topics);
        return card;
    }

    private void openSaveDialog() {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Save to TimeVault");

        ObjectProperty<ArchiveDraft> draft = new SimpleObjectProperty<>();
        ObjectProperty<Path> imagePath = new SimpleObjectProperty<>();

        Label previewTitle = new Label("Generate a preview");
        previewTitle.getStyleClass().add("detail-title");
        Label previewMeta = new Label("Pick a tab, generate the capture, then save it.");
        previewMeta.getStyleClass().add("muted-copy");
        TextArea previewContent = readOnlyArea(12);
        TextArea previewAi = readOnlyArea(8);
        FlowPane previewTags = new FlowPane(8, 8);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setMaxSize(34, 34);
        Button saveButton = pillButton("Save to Vault");
        saveButton.getStyleClass().add("accent-button");
        saveButton.setDisable(true);
        installLift(saveButton);

        draft.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                previewTitle.setText("Generate a preview");
                previewMeta.setText("Pick a tab, generate the capture, then save it.");
                previewContent.clear();
                previewAi.clear();
                previewTags.getChildren().clear();
                saveButton.setDisable(true);
                return;
            }
            previewTitle.setText(newValue.getTitle());
            previewMeta.setText(newValue.getType().displayName() + "  |  " + newValue.getSourcePlatform());
            previewContent.setText(newValue.getContent());
            previewAi.setText(newValue.getAiContext());
            previewTags.getChildren().setAll(newValue.getTags().stream().map(this::tagChip).toList());
            saveButton.setDisable(false);
        });

        TextField urlField = new TextField();
        urlField.setPromptText("https://example.com");
        Button generateUrlButton = pillButton("Generate Preview");
        installLift(generateUrlButton);
        generateUrlButton.setOnAction(event -> {
            progress.setVisible(true);
            runAsync("Generating URL preview...", () -> archiveService.createUrlDraft(urlField.getText()), draft::set, () -> progress.setVisible(false));
        });
        VBox urlTabBox = new VBox(12, sectionLabel("URL"), urlField, generateUrlButton);
        urlTabBox.setPadding(new Insets(18));

        TextField noteTitleField = new TextField();
        noteTitleField.setPromptText("Optional title");
        TextArea noteBodyField = new TextArea();
        noteBodyField.setPromptText("What do you want future historians to remember?");
        noteBodyField.setPrefRowCount(10);
        Button generateTextButton = pillButton("Generate Preview");
        installLift(generateTextButton);
        generateTextButton.setOnAction(event -> {
            progress.setVisible(true);
            runAsync("Generating text preview...", () -> archiveService.createTextDraft(noteTitleField.getText(), noteBodyField.getText()), draft::set, () -> progress.setVisible(false));
        });
        VBox textTabBox = new VBox(12, sectionLabel("Text"), noteTitleField, noteBodyField, generateTextButton);
        textTabBox.setPadding(new Insets(18));

        Label imagePathLabel = new Label("No image selected");
        imagePathLabel.getStyleClass().add("muted-copy");
        Button browseButton = pillButton("Choose Image");
        installLift(browseButton);
        browseButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
            java.io.File file = chooser.showOpenDialog(dialog);
            if (file != null) {
                imagePath.set(file.toPath());
                imagePathLabel.setText(file.toPath().toString());
            }
        });
        TextArea imageNoteField = new TextArea();
        imageNoteField.setPromptText("Optional note about this image");
        imageNoteField.setPrefRowCount(8);
        Button generateImageButton = pillButton("Generate Preview");
        installLift(generateImageButton);
        generateImageButton.setOnAction(event -> {
            if (imagePath.get() == null) {
                setStatus("Choose an image first.");
                return;
            }
            progress.setVisible(true);
            runAsync("Generating image preview...", () -> archiveService.createImageDraft(imagePath.get(), imageNoteField.getText()), draft::set, () -> progress.setVisible(false));
        });
        VBox imageTabBox = new VBox(12, sectionLabel("Image"), browseButton, imagePathLabel, imageNoteField, generateImageButton);
        imageTabBox.setPadding(new Insets(18));

        TabPane tabs = new TabPane(
                tab("URL", urlTabBox),
                tab("Text", textTabBox),
                tab("Image", imageTabBox)
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox previewBox = sectionCard();
        previewBox.getChildren().addAll(previewTitle, previewMeta, previewContent, previewAi, previewTags, progress, saveButton);
        VBox.setVgrow(previewContent, Priority.ALWAYS);

        saveButton.setOnAction(event -> {
            ArchiveDraft current = draft.get();
            if (current == null) {
                return;
            }
            progress.setVisible(true);
            runAsync("Saving capture...", () -> archiveService.saveDraft(current), archive -> {
                progress.setVisible(false);
                dialog.close();
                afterArchiveSaved(archive, "Saved in TimeVault");
            }, () -> progress.setVisible(false));
        });

        SplitPane splitPane = new SplitPane(tabs, previewBox);
        splitPane.setDividerPositions(0.47);
        tabs.setMinWidth(0);
        previewBox.setMinWidth(0);

        Scene scene = new Scene(splitPane, 900, 620);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setMinWidth(760);
        dialog.setMinHeight(560);
        dialog.setResizable(true);
        dialog.show();
    }

    private void showSection(Section section) {
        currentSection = section;
        contentHolder.getChildren().setAll(switch (section) {
            case DASHBOARD -> dashboardView;
            case ARCHIVE -> archiveView;
            case RESCUE -> rescueView;
            case CAPSULES -> capsuleView;
        });
        navButtons.forEach((key, button) -> {
            button.getStyleClass().remove("nav-button-active");
            if (key == section) {
                button.getStyleClass().add("nav-button-active");
            }
        });
    }

    private Button navButton(String text, Section section) {
        Button button = pillButton(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> showSection(section));
        navButtons.put(section, button);
        installLift(button);
        return button;
    }

    private Button pillButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("pill-button");
        return button;
    }

    private VBox statCard(String title, Label valueLabel, String subtitle) {
        VBox card = sectionCard();
        card.setPrefWidth(220);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("muted-copy");
        valueLabel.getStyleClass().add("stat-value");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setWrapText(true);
        subtitleLabel.getStyleClass().add("muted-copy");
        card.getChildren().addAll(titleLabel, valueLabel, subtitleLabel);
        return card;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("eyebrow");
        return label;
    }

    private VBox sectionCard() {
        VBox box = new VBox(12);
        box.getStyleClass().add("vault-card");
        return box;
    }

    private VBox pageBox() {
        VBox box = new VBox(20);
        box.setPadding(new Insets(28));
        return box;
    }

    private ScrollPane pageScroll(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("page-scroll");
        return scrollPane;
    }

    private TextArea readOnlyArea(int rows) {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setFocusTraversable(false);
        area.setPrefRowCount(rows);
        area.getStyleClass().add("readonly-area");
        return area;
    }

    private Label tagChip(String tag) {
        Label label = new Label(tag);
        label.getStyleClass().add("tag-chip");
        return label;
    }

    private Node emptyState(String message) {
        VBox box = sectionCard();
        Label label = new Label(message);
        label.setWrapText(true);
        label.getStyleClass().add("muted-copy");
        box.getChildren().add(label);
        return box;
    }

    private Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private void installLift(Node node) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(120), node);
        grow.setToX(1.015);
        grow.setToY(1.015);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(120), node);
        shrink.setToX(1.0);
        shrink.setToY(1.0);
        node.setOnMouseEntered(event -> {
            shrink.stop();
            grow.playFromStart();
        });
        node.setOnMouseExited(event -> {
            grow.stop();
            shrink.playFromStart();
        });
    }

    private ArchiveType selectedFilter() {
        return switch (filterBox.getValue()) {
            case "URL" -> ArchiveType.URL;
            case "Image" -> ArchiveType.IMAGE;
            case "Text" -> ArchiveType.TEXT;
            case "Event" -> ArchiveType.EVENT;
            default -> null;
        };
    }

    private String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.split("(?<=[.!?])\\s+");
        return parts.length == 0 ? text : parts[0];
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private <T> void runAsync(String loadingMessage, Supplier<T> supplier, Consumer<T> onSuccess) {
        runAsync(loadingMessage, supplier, onSuccess, () -> {
        });
    }

    private <T> void runAsync(String loadingMessage, Supplier<T> supplier, Consumer<T> onSuccess, Runnable onFinally) {
        setStatus(loadingMessage);
        CompletableFuture.supplyAsync(supplier, executor)
                .whenComplete((result, throwable) -> Platform.runLater(() -> {
                    onFinally.run();
                    if (throwable != null) {
                        handleAsyncError(throwable);
                        return;
                    }
                    onSuccess.accept(result);
                }));
    }

    private void handleAsyncError(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        setStatus("Action failed: " + cause.getMessage());
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setHeaderText("TimeVault hit a problem");
        alert.setContentText(cause.getMessage());
        alert.showAndWait();
    }

    private record DashboardPayload(DashboardStats stats, List<Archive> recent) {
    }

    private enum Section {
        DASHBOARD,
        ARCHIVE,
        RESCUE,
        CAPSULES
    }
}
