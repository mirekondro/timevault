package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.ImageAssetData;
import com.example.desktop.model.VaultItemFx;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
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
        appModel.bindText(unlockTitleLabel, "detail.locked.title");
        appModel.bindText(unlockCopyLabel, "detail.locked.copy");
        appModel.bindPrompt(unlockPasswordField, "detail.unlock.prompt");
        appModel.bindText(unlockButton, "detail.unlock.button");

        sourceLink.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            VaultItemFx item = appModel.getSelectedItem();
            return item == null || appModel.getResolvedSourceUrl(item).isBlank() || appModel.isLockedItemHidden(item);
        }, appModel.selectedItemProperty()));
        unlockButton.disableProperty().bind(unlockPasswordField.textProperty().isEmpty()
                .or(Bindings.createBooleanBinding(() -> {
                    VaultItemFx item = appModel.getSelectedItem();
                    return item == null || !item.isLocked() || item.isUnlockedInSession();
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
            unlockBox.setVisible(false);
            unlockBox.setManaged(false);
            detailContentBox.setVisible(true);
            detailContentBox.setManaged(true);
            addTag(appModel.getNoTagsText());
            return;
        }

        detailTitleLabel.setText(appModel.getItemTitle(item));
        detailMetaLabel.setText(appModel.getItemDetailMeta(item));
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

        vaultManager.loadImageAsset(appModel, item).ifPresentOrElse(imageAsset -> {
            detailImageView.setImage(new Image(new ByteArrayInputStream(imageAsset.bytes())));
            imageMetaLabel.setText(formatImageMeta(item, imageAsset));
        }, () -> {
            detailImageView.setImage(null);
            imageMetaLabel.setText(appModel.text("detail.image.unavailable"));
        });
    }

    private void clearImagePreview() {
        detailImageView.setImage(null);
        imageMetaLabel.setText("");
        imageBox.setVisible(false);
        imageBox.setManaged(false);
    }

    private String formatImageMeta(VaultItemFx item, ImageAssetData imageAsset) {
        String mimeType = imageAsset.mimeType() == null || imageAsset.mimeType().isBlank()
                ? item.getImageMimeType()
                : imageAsset.mimeType();
        String sizeLabel = formatByteCount(Math.max(item.getImageByteCount(), imageAsset.size()));
        return appModel.text("detail.image.meta", sizeLabel, mimeType.toLowerCase(Locale.ROOT));
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
