package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

import java.util.Arrays;

/**
 * Controller for the selected item details pane.
 */
public class DetailController implements AppContextAware {

    @FXML
    private Label detailTitleLabel;

    @FXML
    private Label detailMetaLabel;

    @FXML
    private Hyperlink sourceLink;

    @FXML
    private FlowPane tagsPane;

    @FXML
    private TextArea contextArea;

    @FXML
    private Label contextSectionLabel;

    @FXML
    private TextArea contentArea;

    @FXML
    private Label tagsSectionLabel;

    @FXML
    private Label contentSectionLabel;

    @FXML
    private Button deleteButton;

    private AppModel appModel;
    private VaultManager vaultManager;
    private HostServices hostServices;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;
        this.hostServices = hostServices;

        appModel.bindText(contextSectionLabel, "detail.section.context");
        appModel.bindText(tagsSectionLabel, "detail.section.tags");
        appModel.bindText(contentSectionLabel, "detail.section.content");
        appModel.bindText(deleteButton, "detail.button.delete");

        deleteButton.disableProperty().bind(appModel.selectedItemProperty().isNull().or(appModel.busyProperty()));
        sourceLink.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            VaultItemFx item = appModel.getSelectedItem();
            return item == null || item.getSourceUrl() == null || item.getSourceUrl().isBlank();
        }, appModel.selectedItemProperty()));

        appModel.selectedItemProperty().addListener((obs, oldItem, newItem) -> updateDetails(newItem));
        appModel.currentUserProperty().addListener((obs, oldUser, newUser) -> updateDetails(appModel.getSelectedItem()));
        appModel.localeProperty().addListener((obs, oldLocale, newLocale) -> updateDetails(appModel.getSelectedItem()));
        updateDetails(appModel.getSelectedItem());
    }

    @FXML
    private void handleOpenSource() {
        VaultItemFx item = appModel.getSelectedItem();
        if (item != null && item.getSourceUrl() != null && !item.getSourceUrl().isBlank()) {
            hostServices.showDocument(item.getSourceUrl());
        }
    }

    @FXML
    private void handleDelete() {
        vaultManager.deleteSelected(appModel);
    }

    private void updateDetails(VaultItemFx item) {
        tagsPane.getChildren().clear();

        if (item == null) {
            detailTitleLabel.setText(appModel.text("detail.title.empty"));
            detailMetaLabel.setText(vaultManager.getEmptyDetailMessage(appModel));
            sourceLink.setText("");
            sourceLink.setVisible(false);
            sourceLink.setManaged(false);
            contextArea.setText("");
            contentArea.setText("");
            addTag(appModel.getNoTagsText());
            return;
        }

        detailTitleLabel.setText(appModel.getItemTitle(item));
        detailMetaLabel.setText(appModel.getItemDetailMeta(item));
        sourceLink.setText(item.getSourceUrl());
        sourceLink.setVisible(item.getSourceUrl() != null && !item.getSourceUrl().isBlank());
        sourceLink.setManaged(sourceLink.isVisible());
        contextArea.setText(appModel.getItemContext(item));
        contentArea.setText(appModel.getItemContent(item));

        if (item.getTags() == null || item.getTags().isBlank()) {
            addTag(appModel.getNoTagsText());
        } else {
            Arrays.stream(item.getTags().split(","))
                    .map(String::trim)
                    .filter(tag -> !tag.isBlank())
                    .forEach(this::addTag);
        }
    }

    private void addTag(String text) {
        Label tagLabel = new Label(text);
        tagLabel.getStyleClass().add("tag-chip");
        tagsPane.getChildren().add(tagLabel);
    }
}
