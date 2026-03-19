package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

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

    private AppModel appModel;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;

        itemsTableView.setItems(appModel.getFilteredItems());
        appModel.bindText(archiveTitleLabel, "archive.title");
        titleColumn.textProperty().bind(appModel.textBinding("archive.column.title"));
        typeColumn.textProperty().bind(appModel.textBinding("archive.column.type"));
        createdColumn.textProperty().bind(appModel.textBinding("archive.column.created"));
        previewColumn.textProperty().bind(appModel.textBinding("archive.column.preview"));

        configureColumns();
        configurePlaceholder(appModel);

        visibleCountLabel.textProperty().bind(Bindings.createStringBinding(
                () -> vaultManager.getArchiveSummary(appModel),
                appModel.localeProperty(),
                appModel.getFilteredItems(),
                appModel.authenticatedProperty()));

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

    private void configurePlaceholder(AppModel appModel) {
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
}
