package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.GalleryImageFx;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Controller for the selected item details pane.
 */
public class DetailController implements AppContextAware {

    @FXML
    private Label detailTitleLabel;

    @FXML
    private Label detailMetaLabel;

    @FXML
    private VBox trashBox;

    @FXML
    private Label trashTitleLabel;

    @FXML
    private Label trashCopyLabel;

    @FXML
    private VBox unlockBox;

    @FXML
    private Label unlockTitleLabel;

    @FXML
    private Label unlockCopyLabel;

    @FXML
    private PasswordField unlockPasswordField;

    @FXML
    private Button unlockButton;

    @FXML
    private VBox detailContentBox;

    @FXML
    private Hyperlink sourceLink;

    @FXML
    private VBox imageBox;

    @FXML
    private Label imageSectionLabel;

    @FXML
    private ImageView detailImageView;

    @FXML
    private Label imageMetaLabel;

    @FXML
    private ListView<GalleryImageFx> detailGalleryListView;

    @FXML
    private Label selectedImageSectionLabel;

    @FXML
    private TextArea selectedImageContextArea;

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

    private final ObservableList<GalleryImageFx> detailGalleryImages = FXCollections.observableArrayList();
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
        appModel.bindText(imageSectionLabel, "detail.section.image");
        appModel.bindText(selectedImageSectionLabel, "detail.section.image.analysis");
        appModel.bindText(trashTitleLabel, "detail.trash.title");
        appModel.bindText(trashCopyLabel, "detail.trash.copy");
        appModel.bindText(unlockTitleLabel, "detail.locked.title");
        appModel.bindText(unlockCopyLabel, "detail.locked.copy");
        appModel.bindPrompt(unlockPasswordField, "detail.unlock.prompt");
        appModel.bindText(unlockButton, "detail.unlock.button");

        detailGalleryListView.setItems(detailGalleryImages);
        detailGalleryListView.setCellFactory(listView -> new GalleryImageListCell(appModel));
        detailGalleryListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldImage, newImage) -> updateSelectedImage(newImage));

        sourceLink.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            VaultItemFx item = appModel.getSelectedItem();
            return item == null
                    || appModel.isDeletedItem(item)
                    || appModel.getResolvedSourceUrl(item).isBlank()
                    || appModel.isLockedItemHidden(item);
        }, appModel.selectedItemProperty()));
        unlockButton.disableProperty().bind(unlockPasswordField.textProperty().isEmpty()
                .or(Bindings.createBooleanBinding(() -> {
                    VaultItemFx item = appModel.getSelectedItem();
                    return item == null
                            || appModel.isDeletedItem(item)
                            || !item.isLocked()
                            || item.isUnlockedInSession();
                }, appModel.selectedItemProperty())));

        appModel.selectedItemProperty().addListener((obs, oldItem, newItem) -> updateDetails(newItem));
        appModel.currentUserProperty().addListener((obs, oldUser, newUser) -> updateDetails(appModel.getSelectedItem()));
        appModel.localeProperty().addListener((obs, oldLocale, newLocale) -> updateDetails(appModel.getSelectedItem()));
        updateDetails(appModel.getSelectedItem());
    }

    @FXML
    private void handleOpenSource() {
        VaultItemFx item = appModel.getSelectedItem();
        String sourceUrl = appModel.getResolvedSourceUrl(item);
        if (item != null && !sourceUrl.isBlank()) {
            hostServices.showDocument(sourceUrl);
        }
    }

    @FXML
    private void handleUnlock() {
        VaultItemFx item = appModel.getSelectedItem();
        if (vaultManager.unlockItem(appModel, item, unlockPasswordField.getText())) {
            unlockPasswordField.clear();
        }
    }

    private void updateDetails(VaultItemFx item) {
        tagsPane.getChildren().clear();
        unlockPasswordField.clear();

        if (item == null) {
            detailTitleLabel.setText(appModel.text("detail.title.empty"));
            detailMetaLabel.setText(vaultManager.getEmptyDetailMessage(appModel));
            sourceLink.setText("");
            sourceLink.setVisible(false);
            sourceLink.setManaged(false);
            clearImagePreview();
            contextArea.setText("");
            contentArea.setText("");
            trashBox.setVisible(false);
            trashBox.setManaged(false);
            unlockBox.setVisible(false);
            unlockBox.setManaged(false);
            detailContentBox.setVisible(true);
            detailContentBox.setManaged(true);
            addTag(appModel.getNoTagsText());
            return;
        }

        detailTitleLabel.setText(appModel.getItemTitle(item));
        if (appModel.isDeletedItem(item)) {
            detailMetaLabel.setText(appModel.text("detail.trash.meta", appModel.formatTimestamp(item.getDeletedAt())));
            sourceLink.setText("");
            sourceLink.setVisible(false);
            sourceLink.setManaged(false);
            clearImagePreview();
            contextArea.setText("");
            contentArea.setText("");
            trashBox.setVisible(true);
            trashBox.setManaged(true);
            unlockBox.setVisible(false);
            unlockBox.setManaged(false);
            detailContentBox.setVisible(false);
            detailContentBox.setManaged(false);
            return;
        }

        detailMetaLabel.setText(appModel.getItemDetailMeta(item));
        trashBox.setVisible(false);
        trashBox.setManaged(false);
        if (appModel.isLockedItemHidden(item)) {
            sourceLink.setText("");
            sourceLink.setVisible(false);
            sourceLink.setManaged(false);
            clearImagePreview();
            contextArea.setText("");
            contentArea.setText("");
            unlockBox.setVisible(true);
            unlockBox.setManaged(true);
            detailContentBox.setVisible(false);
            detailContentBox.setManaged(false);
            return;
        }

        String sourceUrl = appModel.getResolvedSourceUrl(item);
        sourceLink.setText(sourceUrl);
        sourceLink.setVisible(!sourceUrl.isBlank());
        sourceLink.setManaged(sourceLink.isVisible());
        updateImagePreview(item);
        unlockBox.setVisible(false);
        unlockBox.setManaged(false);
        detailContentBox.setVisible(true);
        detailContentBox.setManaged(true);
        contextArea.setText(appModel.getItemContext(item));
        contentArea.setText(appModel.getItemContent(item));

        String tags = appModel.getResolvedTags(item);
        if (tags.isBlank()) {
            addTag(appModel.getNoTagsText());
        } else {
            Arrays.stream(tags.split(","))
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

    private void updateImagePreview(VaultItemFx item) {
        if (item == null || !AppModel.TYPE_IMAGE.equalsIgnoreCase(item.getItemType())) {
            clearImagePreview();
            return;
        }

        imageBox.setVisible(true);
        imageBox.setManaged(true);

        List<GalleryImageFx> galleryImages = vaultManager.loadImageGallery(appModel, item);
        detailGalleryImages.setAll(galleryImages);
        if (detailGalleryImages.isEmpty()) {
            detailImageView.setImage(null);
            imageMetaLabel.setText(appModel.text("detail.image.unavailable"));
            selectedImageContextArea.setText(appModel.text("detail.image.analysis.none"));
            return;
        }

        GalleryImageFx preferredImage = detailGalleryListView.getSelectionModel().getSelectedItem();
        int preferredIndex = resolvePreferredIndex(preferredImage, detailGalleryImages);
        detailGalleryListView.getSelectionModel().select(preferredIndex);
    }

    private int resolvePreferredIndex(GalleryImageFx preferredImage, List<GalleryImageFx> galleryImages) {
        if (preferredImage == null || galleryImages == null || galleryImages.isEmpty()) {
            return 0;
        }
        for (int index = 0; index < galleryImages.size(); index++) {
            GalleryImageFx candidate = galleryImages.get(index);
            if (matchesImage(preferredImage, candidate)) {
                return index;
            }
        }
        return 0;
    }

    private boolean matchesImage(GalleryImageFx left, GalleryImageFx right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() > 0L && right.getId() > 0L) {
            return left.getId() == right.getId();
        }
        return left.getDisplayOrder() == right.getDisplayOrder()
                && java.util.Objects.equals(left.getFileName(), right.getFileName());
    }

    private void updateSelectedImage(GalleryImageFx selectedImage) {
        if (selectedImage == null) {
            detailImageView.setImage(null);
            imageMetaLabel.setText("");
            selectedImageContextArea.setText("");
            return;
        }

        if (selectedImage.hasCachedBytes()) {
            detailImageView.setImage(new Image(new ByteArrayInputStream(selectedImage.getCachedImageBytes())));
        } else {
            detailImageView.setImage(null);
        }

        imageMetaLabel.setText(formatImageMeta(selectedImage));
        selectedImageContextArea.setText(selectedImage.getAiContext() == null || selectedImage.getAiContext().isBlank()
                ? appModel.text("detail.image.analysis.none")
                : selectedImage.getAiContext());
    }

    private void clearImagePreview() {
        detailGalleryImages.clear();
        detailGalleryListView.getSelectionModel().clearSelection();
        detailImageView.setImage(null);
        imageMetaLabel.setText("");
        selectedImageContextArea.setText("");
        imageBox.setVisible(false);
        imageBox.setManaged(false);
    }

    private String formatImageMeta(GalleryImageFx image) {
        String mimeType = image.getMimeType() == null || image.getMimeType().isBlank()
                ? "image"
                : image.getMimeType().toLowerCase(Locale.ROOT);
        String sizeLabel = formatByteCount(image.getByteCount());
        String fileName = image.getFileName() == null || image.getFileName().isBlank()
                ? appModel.text("item.untitled")
                : image.getFileName();
        int selectedIndex = Math.max(detailGalleryListView.getSelectionModel().getSelectedIndex(), 0) + 1;
        if (detailGalleryImages.size() > 1) {
            return appModel.text("detail.image.meta.gallery", selectedIndex, detailGalleryImages.size(), fileName, sizeLabel, mimeType);
        }
        return appModel.text("detail.image.meta.single", fileName, sizeLabel, mimeType);
    }

    private String formatByteCount(long byteCount) {
        if (byteCount <= 0) {
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
