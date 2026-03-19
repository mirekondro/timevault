package com.example.desktop.model;

import com.example.shared.model.UserSession;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputControl;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import javafx.util.Duration;

/**
 * Central observable state for the desktop application.
 * Controllers bind to this model instead of calling each other.
 */
public class AppModel {

    public static final String TYPE_ALL = "ALL";
    public static final String TYPE_URL = "URL";
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_IMAGE = "IMAGE";

    private static final String BUNDLE_BASE_NAME = "i18n.i18n";

    private final ObservableList<String> typeOptions = FXCollections.observableArrayList(
            TYPE_ALL, TYPE_URL, TYPE_TEXT, TYPE_IMAGE);
    private final ObservableList<LanguageOption> languageOptions = FXCollections.observableArrayList(
            new LanguageOption("en", Locale.ENGLISH, "language.english", true),
            new LanguageOption("da", Locale.forLanguageTag("da"), "language.danish", true)
    );
    private final ObservableList<LanguageOption> readOnlyLanguageOptions =
            FXCollections.unmodifiableObservableList(languageOptions);
    private final ObservableList<ToastNotification> notifications = FXCollections.observableArrayList();
    private final ObservableList<ToastNotification> readOnlyNotifications =
            FXCollections.unmodifiableObservableList(notifications);

    private final ObservableList<VaultItemFx> allItems = FXCollections.observableArrayList();
    private final FilteredList<VaultItemFx> filteredItems = new FilteredList<>(allItems);

    private final ObjectProperty<VaultItemFx> selectedItem = new SimpleObjectProperty<>();
    private final ObjectProperty<UserSession> currentUser = new SimpleObjectProperty<>();
    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(Locale.ENGLISH);
    private final StringProperty searchText = new SimpleStringProperty("");
    private final StringProperty selectedType = new SimpleStringProperty(TYPE_ALL);
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty authenticated = new SimpleBooleanProperty(false);

    private final IntegerProperty totalCount = new SimpleIntegerProperty();
    private final IntegerProperty urlCount = new SimpleIntegerProperty();
    private final IntegerProperty textCount = new SimpleIntegerProperty();
    private final IntegerProperty imageCount = new SimpleIntegerProperty();

    private final StringProperty loginEmailInput = new SimpleStringProperty("");
    private final StringProperty loginPasswordInput = new SimpleStringProperty("");
    private final StringProperty registerEmailInput = new SimpleStringProperty("");
    private final StringProperty registerPasswordInput = new SimpleStringProperty("");
    private final StringProperty registerConfirmPasswordInput = new SimpleStringProperty("");

    private final StringProperty urlInput = new SimpleStringProperty("");
    private final StringProperty urlTitleInput = new SimpleStringProperty("");
    private final StringProperty urlNotesInput = new SimpleStringProperty("");
    private final StringProperty textTitleInput = new SimpleStringProperty("");
    private final StringProperty textContentInput = new SimpleStringProperty("");
    private final StringProperty imageTitleInput = new SimpleStringProperty("");
    private final StringProperty imagePathInput = new SimpleStringProperty("");
    private long nextNotificationId = 1L;

    public AppModel() {
        searchText.addListener((obs, oldValue, newValue) -> updateFilter());
        selectedType.addListener((obs, oldValue, newValue) -> updateFilter());
        allItems.addListener((ListChangeListener<VaultItemFx>) change -> updateCounts());
        currentUser.addListener((obs, oldUser, newUser) -> authenticated.set(newUser != null));

        updateFilter();
        updateCounts();
    }

    public ObservableList<String> getTypeOptions() {
        return typeOptions;
    }

    public ObservableList<LanguageOption> getLanguageOptions() {
        return readOnlyLanguageOptions;
    }

    public ObservableList<ToastNotification> getNotifications() {
        return readOnlyNotifications;
    }

    public ObservableList<VaultItemFx> getFilteredItems() {
        return filteredItems;
    }

    public void setItems(Collection<VaultItemFx> items) {
        Long selectedId = selectedItem.get() == null ? null : selectedItem.get().getId();
        allItems.setAll(items);
        restoreSelection(selectedId);
    }

    public void addItem(VaultItemFx item) {
        allItems.add(0, item);
        selectedItem.set(item);
        updateCounts();
        updateFilter();
    }

    public void removeItem(long itemId) {
        allItems.removeIf(item -> item.getId() == itemId);
        if (selectedItem.get() != null && selectedItem.get().getId() == itemId) {
            selectedItem.set(filteredItems.isEmpty() ? null : filteredItems.getFirst());
        }
        updateCounts();
        updateFilter();
    }

    public ObjectProperty<VaultItemFx> selectedItemProperty() {
        return selectedItem;
    }

    public VaultItemFx getSelectedItem() {
        return selectedItem.get();
    }

    public void setSelectedItem(VaultItemFx item) {
        selectedItem.set(item);
    }

    public ObjectProperty<UserSession> currentUserProperty() {
        return currentUser;
    }

    public UserSession getCurrentUser() {
        return currentUser.get();
    }

    public void setCurrentUser(UserSession user) {
        currentUser.set(user);
    }

    public ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    public Locale getLocale() {
        return locale.get();
    }

    public void setLocale(Locale value) {
        if (value != null) {
            locale.set(value);
        }
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public StringProperty selectedTypeProperty() {
        return selectedType;
    }

    public void showSuccessKey(String key, Object... args) {
        showNotification(text(key, args), ToastNotificationType.SUCCESS);
    }

    public void showErrorKey(String key, Object... args) {
        showNotification(text(key, args), ToastNotificationType.ERROR);
    }

    public void showInfoKey(String key, Object... args) {
        showNotification(text(key, args), ToastNotificationType.INFO);
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public void setBusy(boolean value) {
        busy.set(value);
    }

    public BooleanProperty authenticatedProperty() {
        return authenticated;
    }

    public boolean isAuthenticated() {
        return authenticated.get();
    }

    public IntegerProperty totalCountProperty() {
        return totalCount;
    }

    public IntegerProperty urlCountProperty() {
        return urlCount;
    }

    public IntegerProperty textCountProperty() {
        return textCount;
    }

    public IntegerProperty imageCountProperty() {
        return imageCount;
    }

    public StringProperty urlInputProperty() {
        return urlInput;
    }

    public StringProperty loginEmailInputProperty() {
        return loginEmailInput;
    }

    public StringProperty loginPasswordInputProperty() {
        return loginPasswordInput;
    }

    public StringProperty registerEmailInputProperty() {
        return registerEmailInput;
    }

    public StringProperty registerPasswordInputProperty() {
        return registerPasswordInput;
    }

    public StringProperty registerConfirmPasswordInputProperty() {
        return registerConfirmPasswordInput;
    }

    public StringProperty urlTitleInputProperty() {
        return urlTitleInput;
    }

    public StringProperty urlNotesInputProperty() {
        return urlNotesInput;
    }

    public StringProperty textTitleInputProperty() {
        return textTitleInput;
    }

    public StringProperty textContentInputProperty() {
        return textContentInput;
    }

    public StringProperty imageTitleInputProperty() {
        return imageTitleInput;
    }

    public StringProperty imagePathInputProperty() {
        return imagePathInput;
    }

    public void clearLoginForm() {
        loginEmailInput.set("");
        loginPasswordInput.set("");
    }

    public void clearRegisterForm() {
        registerEmailInput.set("");
        registerPasswordInput.set("");
        registerConfirmPasswordInput.set("");
    }

    public void clearUrlForm() {
        urlInput.set("");
        urlTitleInput.set("");
        urlNotesInput.set("");
    }

    public void clearTextForm() {
        textTitleInput.set("");
        textContentInput.set("");
    }

    public void clearImageForm() {
        imageTitleInput.set("");
        imagePathInput.set("");
    }

    public void clearVault() {
        allItems.clear();
        selectedItem.set(null);
        updateCounts();
        updateFilter();
    }

    public String text(String key, Object... args) {
        String pattern = resolveBundle().containsKey(key) ? resolveBundle().getString(key) : "!" + key + "!";
        if (args == null || args.length == 0) {
            return pattern;
        }
        MessageFormat messageFormat = new MessageFormat(pattern, getLocale());
        return messageFormat.format(args);
    }

    public StringBinding textBinding(String key) {
        return Bindings.createStringBinding(() -> text(key), locale);
    }

    public StringBinding textBinding(String key, Supplier<Object[]> argumentSupplier, Observable... dependencies) {
        return Bindings.createStringBinding(
                () -> text(key, argumentSupplier.get()),
                combineDependencies(dependencies));
    }

    public void bindText(Labeled labeled, String key) {
        labeled.textProperty().bind(textBinding(key));
    }

    public void bindText(MenuItem menuItem, String key) {
        menuItem.textProperty().bind(textBinding(key));
    }

    public void bindText(Tab tab, String key) {
        tab.textProperty().bind(textBinding(key));
    }

    public void bindPrompt(TextInputControl control, String key) {
        control.promptTextProperty().bind(textBinding(key));
    }

    public String getTypeLabel(String typeCode) {
        return switch (typeCode == null ? "" : typeCode.toUpperCase(Locale.ROOT)) {
            case TYPE_ALL -> text("type.all");
            case TYPE_URL -> text("type.url");
            case TYPE_TEXT -> text("type.text");
            case TYPE_IMAGE -> text("type.image");
            default -> typeCode == null ? "" : typeCode;
        };
    }

    public String getLanguageDisplayName(LanguageOption option) {
        return text(option.labelKey());
    }

    public String getItemTitle(VaultItemFx item) {
        if (item == null || item.getTitle().isBlank()) {
            return text("item.untitled");
        }
        return item.getTitle();
    }

    public String getItemSnippet(VaultItemFx item) {
        String source = firstNonBlank(
                item == null ? null : item.getPreviewSource(),
                text("item.preview.none"));
        return source.length() > 180 ? source.substring(0, 180) + "..." : source;
    }

    public String getItemContext(VaultItemFx item) {
        return firstNonBlank(
                item == null ? null : item.getContextSource(),
                text("item.context.none"));
    }

    public String getItemContent(VaultItemFx item) {
        return firstNonBlank(
                item == null ? null : item.getContentSource(),
                text("item.content.none"));
    }

    public String formatTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return text("item.unknownTime");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(text("item.datetime.pattern"), getLocale());
        return timestamp.format(formatter);
    }

    public String getCurrentSceneTitleKey(boolean authScene) {
        return authScene ? "app.title.auth" : "app.title.main";
    }

    public String getVisibleArchiveSummary(int count) {
        return text("archive.visible", count);
    }

    public String getItemDetailMeta(VaultItemFx item) {
        return text("detail.meta.item",
                getTypeLabel(item.getItemType()),
                item.getOwnerId(),
                formatTimestamp(item.getCreatedAt()));
    }

    public String getNoTagsText() {
        return text("detail.tag.none");
    }

    private ResourceBundle resolveBundle() {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, getLocale());
        } catch (MissingResourceException missingResourceException) {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH);
        }
    }

    private Observable[] combineDependencies(Observable... dependencies) {
        Observable[] allDependencies = new Observable[(dependencies == null ? 0 : dependencies.length) + 1];
        allDependencies[0] = locale;
        if (dependencies != null && dependencies.length > 0) {
            System.arraycopy(dependencies, 0, allDependencies, 1, dependencies.length);
        }
        return allDependencies;
    }

    private void showNotification(String message, ToastNotificationType type) {
        Runnable enqueue = () -> {
            ToastNotification notification = new ToastNotification(nextNotificationId++, message, type);
            notifications.add(notification);

            PauseTransition delay = new PauseTransition(Duration.seconds(3));
            delay.setOnFinished(event -> notifications.remove(notification));
            delay.play();
        };

        if (Platform.isFxApplicationThread()) {
            enqueue.run();
            return;
        }
        Platform.runLater(enqueue);
    }

    private void updateFilter() {
        String query = searchText.get() == null ? "" : searchText.get().trim().toLowerCase(Locale.ROOT);
        String type = selectedType.get() == null ? TYPE_ALL : selectedType.get().trim().toUpperCase(Locale.ROOT);

        filteredItems.setPredicate(item -> matchesType(item, type) && matchesQuery(item, query));

        if (selectedItem.get() != null && !filteredItems.contains(selectedItem.get())) {
            selectedItem.set(filteredItems.isEmpty() ? null : filteredItems.getFirst());
        } else if (selectedItem.get() == null && !filteredItems.isEmpty()) {
            selectedItem.set(filteredItems.getFirst());
        }
    }

    private boolean matchesType(VaultItemFx item, String type) {
        return TYPE_ALL.equals(type) || type.equalsIgnoreCase(item.getItemType());
    }

    private boolean matchesQuery(VaultItemFx item, String query) {
        if (query.isBlank()) {
            return true;
        }
        return List.of(item.getTitle(), item.getContent(), item.getAiContext(), item.getTags(), item.getSourceUrl(), item.getItemType())
                .stream()
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(query));
    }

    private void updateCounts() {
        totalCount.set(allItems.size());
        urlCount.set((int) allItems.stream().filter(item -> TYPE_URL.equalsIgnoreCase(item.getItemType())).count());
        textCount.set((int) allItems.stream().filter(item -> TYPE_TEXT.equalsIgnoreCase(item.getItemType())).count());
        imageCount.set((int) allItems.stream().filter(item -> TYPE_IMAGE.equalsIgnoreCase(item.getItemType())).count());
    }

    private void restoreSelection(Long selectedId) {
        if (selectedId != null) {
            filteredItems.stream()
                    .filter(item -> item.getId() == selectedId)
                    .findFirst()
                    .ifPresentOrElse(selectedItem::set, this::selectFirstVisibleItem);
        } else {
            selectFirstVisibleItem();
        }
    }

    private void selectFirstVisibleItem() {
        selectedItem.set(filteredItems.isEmpty() ? null : filteredItems.getFirst());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
