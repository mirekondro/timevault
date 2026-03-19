package com.example.desktop.gui;

import com.example.desktop.DesktopNavigator;
import com.example.desktop.bll.VaultManager;
import com.example.desktop.model.AppModel;
import com.example.desktop.model.LanguageOption;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;

/**
 * Fixed header controller for the main desktop scene.
 */
public class HeaderController implements AppContextAware {

    @FXML
    private Label brandLabel;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> searchColumnComboBox;

    @FXML
    private Button clearSearchButton;

    @FXML
    private Button reloadButton;

    @FXML
    private Button avatarButton;

    @FXML
    private Label avatarLetterLabel;

    private AppModel appModel;
    private VaultManager vaultManager;
    private DesktopNavigator navigator;
    private HostServices hostServices;
    private Stage ownerStage;
    private ContextMenu userMenu;
    private Label menuAvatarLabel;
    private Label menuEmailLabel;
    private Label menuSubtitleLabel;
    private MenuItem profileItem;
    private MenuItem languageSwitchItem;
    private MenuItem logoutItem;

    @Override
    public void setContext(AppModel appModel,
                           VaultManager vaultManager,
                           HostServices hostServices,
                           Stage stage,
                           DesktopNavigator navigator) {
        this.appModel = appModel;
        this.vaultManager = vaultManager;
        this.navigator = navigator;
        this.hostServices = hostServices;
        this.ownerStage = stage;

        configureSearchControls();
        reloadButton.disableProperty().bind(appModel.busyProperty().or(appModel.authenticatedProperty().not()));
        avatarLetterLabel.textProperty().bind(Bindings.createStringBinding(this::resolveCurrentUserInitial,
                appModel.currentUserProperty(), appModel.localeProperty()));
        avatarButton.disableProperty().bind(appModel.busyProperty().or(appModel.currentUserProperty().isNull()));

        appModel.bindText(brandLabel, "header.brand");
        appModel.bindPrompt(searchField, "header.search.prompt");
        appModel.bindText(reloadButton, "header.reload");

        buildUserMenu();
        appModel.currentUserProperty().addListener((obs, oldUser, newUser) -> updateMenuIdentity());
        appModel.localeProperty().addListener((obs, oldLocale, newLocale) -> {
            updateMenuIdentity();
            updateLanguageAction();
            refreshSearchColumnLabels();
        });
        updateMenuIdentity();
        updateLanguageAction();
        refreshSearchColumnLabels();
    }

    @FXML
    private void handleReload() {
        vaultManager.loadVault(appModel);
    }

    @FXML
    private void handleClearSearch() {
        appModel.searchTextProperty().set("");
        appModel.applyArchiveFilterNow();
    }

    @FXML
    private void handleUserMenuToggle() {
        if (appModel.getCurrentUser() == null) {
            return;
        }

        if (userMenu.isShowing()) {
            userMenu.hide();
            return;
        }

        updateMenuIdentity();
        var buttonBounds = avatarButton.localToScreen(avatarButton.getBoundsInLocal());
        userMenu.show(avatarButton, buttonBounds.getMaxX() - 230, buttonBounds.getMaxY() + 8);
    }

    private void handleProfile() {
        userMenu.hide();
        openProfileDialog();
    }

    private void handleLogout() {
        userMenu.hide();
        vaultManager.logout(appModel);
        navigator.showAuthScene();
    }

    private void buildUserMenu() {
        menuAvatarLabel = new Label();
        menuAvatarLabel.getStyleClass().add("user-menu-avatar");
        menuAvatarLabel.setMinSize(42, 42);
        menuAvatarLabel.setPrefSize(42, 42);
        menuAvatarLabel.setMaxSize(42, 42);

        menuEmailLabel = new Label();
        menuEmailLabel.getStyleClass().add("user-menu-email");
        menuEmailLabel.setWrapText(true);

        menuSubtitleLabel = new Label();
        menuSubtitleLabel.getStyleClass().add("user-menu-subtitle");
        appModel.bindText(menuSubtitleLabel, "header.menu.account");

        VBox identityCard = new VBox(8, menuAvatarLabel, menuEmailLabel, menuSubtitleLabel);
        identityCard.getStyleClass().add("user-menu-card");

        CustomMenuItem identityItem = new CustomMenuItem(identityCard, false);

        profileItem = new MenuItem();
        appModel.bindText(profileItem, "header.menu.profile");
        profileItem.setOnAction(event -> handleProfile());

        languageSwitchItem = new MenuItem();
        languageSwitchItem.setOnAction(event -> handleLanguageSwitch());

        logoutItem = new MenuItem();
        appModel.bindText(logoutItem, "header.menu.logout");
        logoutItem.setOnAction(event -> handleLogout());

        userMenu = new ContextMenu(identityItem, new SeparatorMenuItem(), profileItem, languageSwitchItem, logoutItem);
        userMenu.getStyleClass().add("user-menu-popup");
        userMenu.setAutoHide(true);
        userMenu.setHideOnEscape(true);
        userMenu.setAutoFix(true);
    }

    private void configureSearchControls() {
        searchField.textProperty().bindBidirectional(appModel.searchTextProperty());
        searchField.disableProperty().bind(appModel.authenticatedProperty().not());
        searchField.setOnAction(event -> appModel.applyArchiveFilterNow());

        searchColumnComboBox.setItems(appModel.getSearchColumnOptions());
        searchColumnComboBox.valueProperty().bindBidirectional(appModel.selectedSearchColumnProperty());
        searchColumnComboBox.disableProperty().bind(searchField.disableProperty());

        clearSearchButton.disableProperty().bind(searchField.disableProperty().or(searchField.textProperty().isEmpty()));
        clearSearchButton.visibleProperty().bind(searchField.textProperty().isNotEmpty());
        clearSearchButton.managedProperty().bind(clearSearchButton.visibleProperty());
        clearSearchButton.setFocusTraversable(false);
        clearSearchButton.toFront();
    }

    private void handleLanguageSwitch() {
        LanguageOption option = appModel.getNextLanguageOption();
        if (option == null) {
            return;
        }
        if (!option.available()) {
            appModel.showInfoKey("status.language.unavailable", appModel.getLanguageDisplayName(option));
            return;
        }

        if (!option.locale().equals(appModel.getLocale())) {
            appModel.setLocale(option.locale());
            appModel.showSuccessKey("status.language.changed", appModel.getLanguageDisplayName(option));
        }
    }

    private void updateLanguageAction() {
        if (languageSwitchItem == null) {
            return;
        }

        LanguageOption nextLanguage = appModel.getNextLanguageOption();
        languageSwitchItem.setDisable(nextLanguage == null);
        if (nextLanguage == null) {
            languageSwitchItem.setText(appModel.text("header.menu.language"));
            return;
        }
        languageSwitchItem.setText(appModel.getLanguageSwitchLabel(nextLanguage));
    }

    private void updateMenuIdentity() {
        if (menuAvatarLabel == null || menuEmailLabel == null) {
            return;
        }

        menuAvatarLabel.setText(resolveCurrentUserInitial());
        if (appModel.getCurrentUser() == null) {
            menuEmailLabel.setText(appModel.text("header.menu.noUser"));
            return;
        }
        menuEmailLabel.setText(appModel.getCurrentUser().email());
    }

    private void openProfileDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/desktop/gui/profile-dialog.fxml"));
            Parent root = loader.load();
            ProfileDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.initOwner(ownerStage);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setResizable(true);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/desktop/styles.css").toExternalForm());
            dialogStage.setScene(scene);

            controller.setContext(appModel, vaultManager, hostServices, dialogStage, navigator);
            Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
            double dialogWidth = Math.min(
                    Math.min(760, visualBounds.getWidth()),
                    Math.max(540, visualBounds.getWidth() - 80));
            double dialogHeight = Math.min(
                    Math.min(820, visualBounds.getHeight()),
                    Math.max(580, visualBounds.getHeight() - 80));
            dialogStage.setMinWidth(Math.min(540, visualBounds.getWidth()));
            dialogStage.setMinHeight(Math.min(580, visualBounds.getHeight()));
            dialogStage.setWidth(dialogWidth);
            dialogStage.setHeight(dialogHeight);
            dialogStage.centerOnScreen();
            dialogStage.showAndWait();
        } catch (IOException exception) {
            appModel.showErrorKey("status.dialog.open.error", exception.getMessage());
        }
    }

    private void refreshSearchColumnLabels() {
        if (searchColumnComboBox == null) {
            return;
        }

        searchColumnComboBox.setConverter(createSearchColumnConverter());
        searchColumnComboBox.setCellFactory(listView -> createSearchColumnCell());
        searchColumnComboBox.setButtonCell(createSearchColumnCell());

        String selectedColumn = searchColumnComboBox.getValue();
        if (selectedColumn != null) {
            searchColumnComboBox.getSelectionModel().select(selectedColumn);
        }
    }

    private StringConverter<String> createSearchColumnConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(String searchColumn) {
                return searchColumn == null ? "" : appModel.getSearchColumnLabel(searchColumn);
            }

            @Override
            public String fromString(String searchColumn) {
                return searchColumn;
            }
        };
    }

    private ListCell<String> createSearchColumnCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : appModel.getSearchColumnLabel(item));
            }
        };
    }

    private String resolveCurrentUserInitial() {
        if (appModel == null) {
            return "?";
        }
        if (appModel.getCurrentUser() == null || appModel.getCurrentUser().email().isBlank()) {
            return appModel.text("header.avatar.fallback");
        }
        return appModel.getCurrentUser().email().substring(0, 1).toUpperCase();
    }
}
