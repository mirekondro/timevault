package com.example.desktop.gui;

import com.example.desktop.model.VaultItemFx;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

/**
 * Styled list cell for vault items.
 */
public class VaultItemCell extends ListCell<VaultItemFx> {

    @Override
    protected void updateItem(VaultItemFx item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        Label typeLabel = new Label(item.getItemType());
        typeLabel.getStyleClass().add("eyebrow");

        Label titleLabel = new Label(item.getTitle().isBlank() ? "Untitled item" : item.getTitle());
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setWrapText(true);

        Label metaLabel = new Label(item.getFormattedCreatedAt());
        metaLabel.getStyleClass().add("muted-copy");
        metaLabel.setWrapText(true);

        Label snippetLabel = new Label(item.getDisplaySnippet());
        snippetLabel.getStyleClass().add("card-copy");
        snippetLabel.setWrapText(true);

        VBox card = new VBox(6, typeLabel, titleLabel, metaLabel, snippetLabel);
        card.getStyleClass().add("vault-card");
        card.setPadding(new Insets(14));

        setText(null);
        setGraphic(card);
    }
}
