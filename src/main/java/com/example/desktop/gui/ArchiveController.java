package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controller for the archive table pane.
 */
public class ArchiveController implements AppContextAware {

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
        if (AppModel.TYPE_ALL.equals(selectedType)) {
            appModel.showInfoKey("status.archive.chooseType");
            return;
        }

        switch (selectedType) {
            case AppModel.TYPE_URL -> openDialog(
                    "/com/example/desktop/gui/url-dialog.fxml",
                    UrlDialogController.class,
                    UrlDialogController::prepareForCreate);
            case AppModel.TYPE_TEXT -> openDialog(
                    "/com/example/desktop/gui/text-dialog.fxml",
                    TextDialogController.class,
                    TextDialogController::prepareForCreate);
            case AppModel.TYPE_IMAGE -> openDialog(
                    "/com/example/desktop/gui/image-dialog.fxml",
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
                    UrlDialogController.class,
                    controller -> controller.prepareForEdit(selectedItem));
            case AppModel.TYPE_TEXT -> openDialog(
                    "/com/example/desktop/gui/text-dialog.fxml",
                    TextDialogController.class,
                    controller -> controller.prepareForEdit(selectedItem));
            case AppModel.TYPE_IMAGE -> openDialog(
                    "/com/example/desktop/gui/image-dialog.fxml",
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

        VaultItemFx selectedItem = requireUnlockedSelection("lock.prompt.header.delete");
        if (selectedItem == null) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(ownerStage);
        confirmation.initModality(Modality.WINDOW_MODAL);
        confirmation.setTitle(appModel.text("archive.delete.title"));
        confirmation.setHeaderText(appModel.text("archive.delete.header", appModel.getItemTitle(selectedItem)));
        confirmation.setContentText(appModel.text("archive.delete.content"));

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            vaultManager.deleteSelected(appModel);
        }
    }

    private void configureColumns() {
        itemsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
        addButton.disableProperty().bind(appModel.busyProperty().or(appModel.authenticatedProperty().not()));
        editButton.disableProperty().bind(appModel.busyProperty()
                .or(appModel.authenticatedProperty().not())
                .or(appModel.selectedItemProperty().isNull()));
        deleteButton.disableProperty().bind(appModel.busyProperty()
                .or(appModel.authenticatedProperty().not())
                .or(appModel.selectedItemProperty().isNull()));
    }

    private VaultItemFx requireUnlockedSelection(String promptHeaderKey) {
        VaultItemFx selectedItem = appModel.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        if (!selectedItem.isLocked() || selectedItem.isUnlockedInSession()) {
            return selectedItem;
        }

        Optional<String> password = promptForItemPassword(promptHeaderKey, selectedItem);
        if (password.isEmpty()) {
            return null;
        }

        return vaultManager.unlockItem(appModel, selectedItem, password.get())
                ? appModel.getSelectedItem()
                : null;
    }

    private Optional<String> promptForItemPassword(String headerKey, VaultItemFx item) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(ownerStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(appModel.text("lock.prompt.title"));
        dialog.setHeaderText(appModel.text(headerKey, appModel.getItemTitle(item)));

        ButtonType continueButtonType = new ButtonType(
                appModel.text("lock.prompt.submit"),
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(continueButtonType, ButtonType.CANCEL);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(appModel.text("lock.prompt.password.prompt"));

        Label helperLabel = new Label(appModel.text("lock.prompt.copy"));
        helperLabel.setWrapText(true);

        VBox content = new VBox(10, helperLabel, passwordField);
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/desktop/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        dialog.setResultConverter(buttonType ->
                buttonType == continueButtonType ? passwordField.getText() : null);

        return dialog.showAndWait().filter(value -> value != null && !value.isBlank());
    }

    private <T extends AppContextAware> void openDialog(String fxmlPath,
                                                        Class<T> controllerType,
                                                        Consumer<T> initializer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            T controller = controllerType.cast(loader.getController());

            Stage dialogStage = new Stage();
            dialogStage.initOwner(ownerStage);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setResizable(false);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/desktop/styles.css").toExternalForm());
            dialogStage.setScene(scene);

            controller.setContext(appModel, vaultManager, hostServices, dialogStage, navigator);
            initializer.accept(controller);
            dialogStage.showAndWait();
        } catch (IOException exception) {
            appModel.showErrorKey("status.dialog.open.error", exception.getMessage());
        }
    }
}
