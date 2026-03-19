package com.example.desktop.gui;

import com.example.desktop.model.AppModel;
import com.example.desktop.model.VaultItemFx;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

/**
 * Styled list cell for vault items.
 */
public class VaultItemCell extends ListCell<VaultItemFx> {

    private final AppModel appModel;

    public VaultItemCell(AppModel appModel) {
        this.appModel = appModel;
        appModel.localeProperty().addListener((obs, oldLocale, newLocale) -> refreshLocalizedContent());
    }

    @Override
    protected void updateItem(VaultItemFx item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        Label typeLabel = new Label(appModel.getTypeLabel(item.getItemType()));
        typeLabel.getStyleClass().add("eyebrow");

        Label titleLabel = new Label(appModel.getItemTitle(item));
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setWrapText(true);

        Label metaLabel = new Label(appModel.formatTimestamp(item.getCreatedAt()));
        metaLabel.getStyleClass().add("muted-copy");
        metaLabel.setWrapText(true);

        Label snippetLabel = new Label(appModel.getItemSnippet(item));
        snippetLabel.getStyleClass().add("card-copy");
        snippetLabel.setWrapText(true);

        VBox card = new VBox(6, typeLabel, titleLabel, metaLabel, snippetLabel);
        card.getStyleClass().add("vault-card");
        card.setPadding(new Insets(14));

        setText(null);
        setGraphic(card);
    }

    private void refreshLocalizedContent() {
        VaultItemFx item = getItem();
        if (item != null && !isEmpty()) {
            updateItem(item, false);
        }
    }
}
