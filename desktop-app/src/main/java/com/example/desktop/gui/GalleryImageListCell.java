package com.example.desktop.gui;

import com.example.desktop.model.AppModel;
import com.example.desktop.model.GalleryImageFx;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * Shared thumbnail list cell for gallery images in dialogs and detail views.
 */
public final class GalleryImageListCell extends ListCell<GalleryImageFx> {

    private final AppModel appModel;
    private final ImageView previewView = new ImageView();
    private final Label titleLabel = new Label();
    private final Label metaLabel = new Label();
    private final HBox root;

    public GalleryImageListCell(AppModel appModel) {
        this.appModel = appModel;

        previewView.setFitWidth(64);
        previewView.setFitHeight(52);
        previewView.setPreserveRatio(true);
        previewView.setSmooth(true);

        StackPane previewShell = new StackPane(previewView);
        previewShell.getStyleClass().add("gallery-thumb-shell");

        titleLabel.getStyleClass().add("gallery-cell-title");
        metaLabel.getStyleClass().add("gallery-cell-meta");

        VBox textBox = new VBox(4, titleLabel, metaLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        root = new HBox(10, previewShell, textBox);
        root.getStyleClass().add("gallery-list-row");

        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    protected void updateItem(GalleryImageFx item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        String fileName = item.getFileName() == null || item.getFileName().isBlank()
                ? appModel.text("item.untitled")
                : item.getFileName();
        titleLabel.setText(fileName);
        metaLabel.setText(formatMeta(item));

        if (item.hasCachedBytes()) {
            previewView.setImage(new Image(new ByteArrayInputStream(item.getCachedImageBytes())));
        } else {
            previewView.setImage(null);
        }

        setGraphic(root);
    }

    private String formatMeta(GalleryImageFx item) {
        String mimeType = item.getMimeType() == null || item.getMimeType().isBlank()
                ? "image"
                : item.getMimeType().toLowerCase(Locale.ROOT);
        return formatByteCount(item.getByteCount()) + " | " + mimeType;
    }

    private String formatByteCount(long byteCount) {
        if (byteCount <= 0L) {
            return "0 B";
        }
        if (byteCount >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", byteCount / (1024d * 1024d));
        }
        if (byteCount >= 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", byteCount / 1024d);
        }
        return byteCount + " B";
    }
}
