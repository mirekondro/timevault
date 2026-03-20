package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Controller for the archive table pane.
 */
public class ArchiveController implements AppContextAware {

    private static final DialogWindowSize FORM_DIALOG_SIZE = new DialogWindowSize(760, 620, 640, 520);
    private static final DialogWindowSize DELETE_DIALOG_SIZE = new DialogWindowSize(700, 340, 580, 300);
    private static final DialogWindowSize RESTORE_DIALOG_SIZE = new DialogWindowSize(700, 340, 580, 300);
    private static final DialogWindowSize UNLOCK_DIALOG_SIZE = new DialogWindowSize(680, 320, 560, 280);

    @FXML
    private Label archiveTitleLabel;

    @FXML
    private Label visibleCountLabel;

    @FXML
    private TableView<VaultItemFx> itemsTableView;

    @FXML
    private TableColumn<VaultItemFx, String> titleColumn;

    @FXML
    private TableColumn<VaultItemFx, String> typeColumn;

    @FXML
    private TableColumn<VaultItemFx, String> createdColumn;

    @FXML
    private TableColumn<VaultItemFx, String> previewColumn;

    @FXML
    private Button addButton;

    @FXML
    private Button editButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button restoreButton;

    private AppModel appModel;
    private VaultManager vaultManager;
    private HostServices hostServices;
    private Stage ownerStage;
    private DesktopNavigator navigator;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;
        this.hostServices = hostServices;
        this.ownerStage = stage;
        this.navigator = navigator;

        itemsTableView.setItems(appModel.getFilteredItems());
        appModel.bindText(archiveTitleLabel, "archive.title");
        titleColumn.textProperty().bind(appModel.textBinding("archive.column.title"));
        typeColumn.textProperty().bind(appModel.textBinding("archive.column.type"));
        createdColumn.textProperty().bind(appModel.textBinding("archive.column.created"));
        previewColumn.textProperty().bind(appModel.textBinding("archive.column.preview"));
        appModel.bindText(addButton, "archive.action.add");
        appModel.bindText(editButton, "archive.action.edit");
        appModel.bindText(deleteButton, "archive.action.delete");
        appModel.bindText(restoreButton, "archive.action.restore");

        configureColumns();
        configurePlaceholder();
        configureActions();

        visibleCountLabel.textProperty().bind(Bindings.createStringBinding(
                appModel::getArchiveSummaryText,
                appModel.localeProperty(),
                appModel.getFilteredItems(),
                appModel.authenticatedProperty(),
                appModel.selectedTypeProperty(),
                appModel.selectedSearchColumnProperty(),
                appModel.searchTextProperty()));
        visibleCountLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                appModel::shouldShowArchiveSummary,
                appModel.authenticatedProperty(),
                appModel.selectedTypeProperty(),
                appModel.searchTextProperty()));
        visibleCountLabel.managedProperty().bind(visibleCountLabel.visibleProperty());

        itemsTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != appModel.getSelectedItem()) {
                appModel.setSelectedItem(newItem);
            }
        });

        appModel.selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (itemsTableView.getSelectionModel().getSelectedItem() != newItem) {
                itemsTableView.getSelectionModel().select(newItem);
            }
            if (newItem != null) {
                itemsTableView.scrollTo(newItem);
            }
        });

        appModel.localeProperty().addListener((obs, oldLocale, newLocale) -> itemsTableView.refresh());
    }

    @FXML
    private void handleAdd() {
        String selectedType = appModel.selectedTypeProperty().get();
        if (AppModel.TYPE_ALL.equals(selectedType) || AppModel.TYPE_TRASH.equals(selectedType)) {
            appModel.showInfoKey("status.archive.chooseType");
            return;
        }

        switch (selectedType) {
            case AppModel.TYPE_URL -> openDialog(
                    "/com/example/desktop/gui/url-dialog.fxml",
                    FORM_DIALOG_SIZE,
                    UrlDialogController.class,
                    UrlDialogController::prepareForCreate);
            case AppModel.TYPE_TEXT -> openDialog(
                    "/com/example/desktop/gui/text-dialog.fxml",
                    FORM_DIALOG_SIZE,
                    TextDialogController.class,
                    TextDialogController::prepareForCreate);
            case AppModel.TYPE_IMAGE -> openDialog(
                    "/com/example/desktop/gui/image-dialog.fxml",
                    FORM_DIALOG_SIZE,
                    ImageDialogController.class,
                    ImageDialogController::prepareForCreate);
            default -> appModel.showInfoKey("status.archive.chooseType");
        }
    }

    @FXML
    private void handleEdit() {
        if (appModel.getSelectedItem() == null) {
            appModel.showErrorKey("status.edit.select");
            return;
        }

        VaultItemFx selectedItem = requireUnlockedSelection("lock.prompt.header.edit");
        if (selectedItem == null) {
            return;
        }

        switch (selectedItem.getItemType()) {
            case AppModel.TYPE_URL -> openDialog(
                    "/com/example/desktop/gui/url-dialog.fxml",
                    FORM_DIALOG_SIZE,
                    UrlDialogController.class,
                    controller -> controller.prepareForEdit(selectedItem));
            case AppModel.TYPE_TEXT -> openDialog(
                    "/com/example/desktop/gui/text-dialog.fxml",
                    FORM_DIALOG_SIZE,
                    TextDialogController.class,
                    controller -> controller.prepareForEdit(selectedItem));
            case AppModel.TYPE_IMAGE -> openDialog(
                    "/com/example/desktop/gui/image-dialog.fxml",
                    FORM_DIALOG_SIZE,
                    ImageDialogController.class,
                    controller -> controller.prepareForEdit(selectedItem));
            default -> appModel.showErrorKey("status.edit.unsupported");
        }
    }

    @FXML
    private void handleDelete() {
        if (appModel.getSelectedItem() == null) {
            appModel.showErrorKey("status.delete.select");
            return;
        }

        VaultItemFx selectedItem = appModel.getSelectedItem();
        openDialog(
                "/com/example/desktop/gui/delete-item-dialog.fxml",
                DELETE_DIALOG_SIZE,
                DeleteItemDialogController.class,
                controller -> controller.prepareForItem(selectedItem));
    }

    @FXML
    private void handleRestore() {
        if (appModel.getSelectedItem() == null) {
            appModel.showErrorKey("status.restore.select");
            return;
        }

        VaultItemFx selectedItem = appModel.getSelectedItem();
        openDialog(
                "/com/example/desktop/gui/restore-item-dialog.fxml",
                RESTORE_DIALOG_SIZE,
                RestoreItemDialogController.class,
                controller -> controller.prepareForItem(selectedItem));
    }

    private void configureColumns() {
        itemsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (!titleColumn.getStyleClass().contains("title-column")) {
            titleColumn.getStyleClass().add("title-column");
        }
        titleColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        titleColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                appModel.getItemTitle(cellData.getValue())));
        typeColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                appModel.getTypeLabel(cellData.getValue().getItemType())));
        createdColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                appModel.formatTimestamp(cellData.getValue().getCreatedAt())));
        previewColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                appModel.getItemSnippet(cellData.getValue())));
    }

    private void configurePlaceholder() {
        Label placeholderLabel = new Label();
        placeholderLabel.getStyleClass().add("table-placeholder");
        placeholderLabel.setWrapText(true);
        placeholderLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                    if (!appModel.isAuthenticated()) {
                        return appModel.text("archive.empty.unauth");
                    }
                    if (appModel.getFilteredItems().isEmpty()) {
                        String searchText = appModel.searchTextProperty().get();
                        if (appModel.isTrashSelected() && (searchText == null || searchText.isBlank())) {
                            return appModel.text("archive.empty.trash");
                        }
                        return appModel.text("archive.empty.filtered");
                    }
                    return "";
                },
                appModel.localeProperty(),
                appModel.authenticatedProperty(),
                appModel.getFilteredItems(),
                appModel.selectedTypeProperty(),
                appModel.searchTextProperty()));
        itemsTableView.setPlaceholder(placeholderLabel);
    }

    private void configureActions() {
        BooleanBinding trashViewSelected = Bindings.createBooleanBinding(
                appModel::isTrashSelected,
                appModel.selectedTypeProperty());

        addButton.disableProperty().bind(appModel.busyProperty()
                .or(appModel.authenticatedProperty().not())
                .or(trashViewSelected));
        editButton.disableProperty().bind(appModel.busyProperty()
                .or(appModel.authenticatedProperty().not())
                .or(appModel.selectedItemProperty().isNull())
                .or(trashViewSelected));
        deleteButton.disableProperty().bind(appModel.busyProperty()
                .or(appModel.authenticatedProperty().not())
                .or(appModel.selectedItemProperty().isNull())
                .or(trashViewSelected));
        restoreButton.disableProperty().bind(appModel.busyProperty()
                .or(appModel.authenticatedProperty().not())
                .or(appModel.selectedItemProperty().isNull())
                .or(trashViewSelected.not()));

        addButton.visibleProperty().bind(trashViewSelected.not());
        addButton.managedProperty().bind(addButton.visibleProperty());
        editButton.visibleProperty().bind(trashViewSelected.not());
        editButton.managedProperty().bind(editButton.visibleProperty());
        deleteButton.visibleProperty().bind(trashViewSelected.not());
        deleteButton.managedProperty().bind(deleteButton.visibleProperty());
        restoreButton.visibleProperty().bind(trashViewSelected);
        restoreButton.managedProperty().bind(restoreButton.visibleProperty());
    }

    private VaultItemFx requireUnlockedSelection(String promptHeaderKey) {
        VaultItemFx selectedItem = appModel.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        if (!selectedItem.isLocked() || selectedItem.isUnlockedInSession()) {
            return selectedItem;
        }

        openDialog(
                "/com/example/desktop/gui/unlock-item-dialog.fxml",
                UNLOCK_DIALOG_SIZE,
                UnlockItemDialogController.class,
                controller -> controller.prepare(selectedItem, promptHeaderKey));

        VaultItemFx refreshedItem = appModel.getSelectedItem();
        if (refreshedItem != null
                && refreshedItem.getId() == selectedItem.getId()
                && (!refreshedItem.isLocked() || refreshedItem.isUnlockedInSession())) {
            return refreshedItem;
        }
        return null;
    }

    private <T extends AppContextAware> void openDialog(String fxmlPath,
                                                        DialogWindowSize windowSize,
                                                        Class<T> controllerType,
                                                        Consumer<T> initializer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            T controller = controllerType.cast(loader.getController());

            Stage dialogStage = new Stage();
            dialogStage.initOwner(ownerStage);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setResizable(true);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/desktop/styles.css").toExternalForm());
            dialogStage.setScene(scene);

            controller.setContext(appModel, vaultManager, hostServices, dialogStage, navigator);
            initializer.accept(controller);
            applyDialogWindowSize(dialogStage, windowSize);
            dialogStage.showAndWait();
        } catch (IOException exception) {
            appModel.showErrorKey("status.dialog.open.error", exception.getMessage());
        }
    }

    private void applyDialogWindowSize(Stage dialogStage, DialogWindowSize windowSize) {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double availableWidth = Math.max(360, visualBounds.getWidth() - 80);
        double availableHeight = Math.max(300, visualBounds.getHeight() - 80);

        double dialogWidth = Math.min(windowSize.preferredWidth(), availableWidth);
        dialogWidth = Math.max(dialogWidth, Math.min(windowSize.minWidth(), availableWidth));

        double dialogHeight = Math.min(windowSize.preferredHeight(), availableHeight);
        dialogHeight = Math.max(dialogHeight, Math.min(windowSize.minHeight(), availableHeight));

        dialogStage.setMinWidth(Math.min(windowSize.minWidth(), availableWidth));
        dialogStage.setMinHeight(Math.min(windowSize.minHeight(), availableHeight));
        dialogStage.setWidth(dialogWidth);
        dialogStage.setHeight(dialogHeight);
        dialogStage.centerOnScreen();
    }

    private record DialogWindowSize(double preferredWidth,
                                    double preferredHeight,
                                    double minWidth,
                                    double minHeight) {
    }
}
