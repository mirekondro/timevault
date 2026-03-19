package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

/**
 * Controller for the archive list pane.
 */
public class ArchiveController implements AppContextAware {

    @FXML
    private Label visibleCountLabel;

    @FXML
    private ListView<VaultItemFx> itemsListView;

    private AppModel appModel;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;

        itemsListView.setItems(appModel.getFilteredItems());
        itemsListView.setCellFactory(listView -> new VaultItemCell());

        visibleCountLabel.textProperty().bind(Bindings.createStringBinding(
                () -> vaultManager.getArchiveSummary(appModel),
                appModel.getFilteredItems(),
                appModel.authenticatedProperty()));

        itemsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != appModel.getSelectedItem()) {
                appModel.setSelectedItem(newItem);
            }
        });

        appModel.selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (itemsListView.getSelectionModel().getSelectedItem() != newItem) {
                itemsListView.getSelectionModel().select(newItem);
            }
            if (newItem != null) {
                itemsListView.scrollTo(newItem);
            }
        });
    }
}
