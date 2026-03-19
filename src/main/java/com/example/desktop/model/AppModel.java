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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public static final String SEARCH_COLUMN_ALL = "ALL";
    public static final String SEARCH_COLUMN_TITLE = "TITLE";
    public static final String SEARCH_COLUMN_TYPE = "TYPE";
    public static final String SEARCH_COLUMN_CREATED = "CREATED";
    public static final String SEARCH_COLUMN_PREVIEW = "PREVIEW";

    private static final String BUNDLE_BASE_NAME = "i18n.i18n";

    private final ObservableList<String> typeOptions = FXCollections.observableArrayList(
            TYPE_ALL, TYPE_URL, TYPE_TEXT, TYPE_IMAGE);
    private final ObservableList<String> searchColumnOptions = FXCollections.observableArrayList(
            SEARCH_COLUMN_ALL, SEARCH_COLUMN_TITLE, SEARCH_COLUMN_TYPE, SEARCH_COLUMN_CREATED, SEARCH_COLUMN_PREVIEW);
    private final ObservableList<String> readOnlySearchColumnOptions =
            FXCollections.unmodifiableObservableList(searchColumnOptions);
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
    private final Map<String, SearchState> searchStateByType = new LinkedHashMap<>();

    private final ObjectProperty<VaultItemFx> selectedItem = new SimpleObjectProperty<>();
    private final ObjectProperty<UserSession> currentUser = new SimpleObjectProperty<>();
    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(Locale.ENGLISH);
    private final StringProperty searchText = new SimpleStringProperty("");
    private final StringProperty selectedSearchColumn = new SimpleStringProperty(SEARCH_COLUMN_ALL);
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

    private long nextNotificationId = 1L;
    private boolean syncingSearchState;

    public AppModel() {
        initializeSearchStates();
        searchText.addListener((obs, oldValue, newValue) -> {
            if (syncingSearchState) {
                return;
            }
            getSearchState(selectedType.get()).searchTextProperty().set(newValue == null ? "" : newValue);
            updateFilter();
        });
        selectedSearchColumn.addListener((obs, oldValue, newValue) -> {
            if (syncingSearchState) {
                return;
            }
            getSearchState(selectedType.get()).searchColumnProperty().set(normalizeSearchColumn(newValue));
            updateFilter();
        });
        selectedType.addListener((obs, oldValue, newValue) -> {
            loadSearchState(newValue);
            updateFilter();
        });
        locale.addListener((obs, oldValue, newValue) -> updateFilter());
        allItems.addListener((ListChangeListener<VaultItemFx>) change -> updateCounts());
        currentUser.addListener((obs, oldUser, newUser) -> authenticated.set(newUser != null));

        loadSearchState(TYPE_ALL);
        updateFilter();
        updateCounts();
    }

    public ObservableList<String> getTypeOptions() {
        return typeOptions;
    }

    public ObservableList<LanguageOption> getLanguageOptions() {
        return readOnlyLanguageOptions;
    }

    public ObservableList<String> getSearchColumnOptions() {
        return readOnlySearchColumnOptions;
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

    public void updateItem(VaultItemFx updatedItem) {
        if (updatedItem == null) {
            return;
        }

        Long selectedId = selectedItem.get() == null ? updatedItem.getId() : selectedItem.get().getId();
        for (int index = 0; index < allItems.size(); index++) {
            if (allItems.get(index).getId() == updatedItem.getId()) {
                allItems.set(index, updatedItem);
                break;
            }
        }
        updateCounts();
        updateFilter();
        restoreSelection(selectedId);
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

    public StringProperty selectedSearchColumnProperty() {
        return selectedSearchColumn;
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

    public void clearLoginForm() {
        loginEmailInput.set("");
        loginPasswordInput.set("");
    }

    public void clearRegisterForm() {
        registerEmailInput.set("");
        registerPasswordInput.set("");
        registerConfirmPasswordInput.set("");
    }

    public void clearVault() {
        allItems.clear();
        selectedItem.set(null);
        updateCounts();
        updateFilter();
    }

    public void resetArchiveFilters() {
        initializeSearchStates();
        selectedType.set(TYPE_ALL);
        loadSearchState(TYPE_ALL);
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
        return switch (normalizeTypeCode(typeCode)) {
            case TYPE_ALL -> text("type.all");
            case TYPE_URL -> text("type.url");
            case TYPE_TEXT -> text("type.text");
            case TYPE_IMAGE -> text("type.image");
            default -> typeCode == null ? "" : typeCode;
        };
    }

    public String getSearchColumnLabel(String searchColumn) {
        return switch (normalizeSearchColumn(searchColumn)) {
            case SEARCH_COLUMN_TITLE -> text("archive.column.title");
            case SEARCH_COLUMN_TYPE -> text("archive.column.type");
            case SEARCH_COLUMN_CREATED -> text("archive.column.created");
            case SEARCH_COLUMN_PREVIEW -> text("archive.column.preview");
            case SEARCH_COLUMN_ALL -> text("type.all");
            default -> searchColumn == null ? "" : searchColumn;
        };
    }

    public String getLanguageDisplayName(LanguageOption option) {
        return text(option.labelKey());
    }

    public boolean isLockedItemHidden(VaultItemFx item) {
        return item != null && item.isLocked() && !item.isUnlockedInSession();
    }

    public String getResolvedTitle(VaultItemFx item) {
        ProtectedItemData data = resolveItemData(item);
        return data == null ? "" : firstNonBlank(data.title(), "");
    }

    public String getResolvedContent(VaultItemFx item) {
        ProtectedItemData data = resolveItemData(item);
        return data == null ? "" : firstNonBlank(data.content(), "");
    }

    public String getResolvedContext(VaultItemFx item) {
        ProtectedItemData data = resolveItemData(item);
        return data == null ? "" : firstNonBlank(data.aiContext(), "");
    }

    public String getResolvedTags(VaultItemFx item) {
        ProtectedItemData data = resolveItemData(item);
        return data == null ? "" : firstNonBlank(data.tags(), "");
    }

    public String getResolvedSourceUrl(VaultItemFx item) {
        ProtectedItemData data = resolveItemData(item);
        return data == null ? "" : firstNonBlank(data.sourceUrl(), "");
    }

    public String getItemTitle(VaultItemFx item) {
        if (isLockedItemHidden(item)) {
            return text("item.locked");
        }
        String title = getResolvedTitle(item);
        if (item == null || title.isBlank()) {
            return text("item.untitled");
        }
        return title;
    }

    public String getItemSnippet(VaultItemFx item) {
        if (isLockedItemHidden(item)) {
            return text("item.locked");
        }
        String source = getSearchablePreview(item);
        return source.length() > 180 ? source.substring(0, 180) + "..." : source;
    }

    public String getItemContext(VaultItemFx item) {
        if (isLockedItemHidden(item)) {
            return text("detail.locked.copy");
        }
        return firstNonBlank(
                getResolvedContext(item),
                text("item.context.none"));
    }

    public String getItemContent(VaultItemFx item) {
        if (isLockedItemHidden(item)) {
            return text("detail.locked.copy");
        }
        return firstNonBlank(
                getResolvedContent(item),
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

    public boolean shouldShowArchiveSummary() {
        return isAuthenticated() && (hasActiveArchiveSearch() || !TYPE_ALL.equals(normalizeTypeCode(selectedType.get())));
    }

    public String getArchiveSummaryText() {
        if (!isAuthenticated()) {
            return text("archive.login.required");
        }

        int count = filteredItems.size();
        String typeCode = normalizeTypeCode(selectedType.get());
        String searchColumn = normalizeSearchColumn(selectedSearchColumn.get());
        String query = searchText.get() == null ? "" : searchText.get().trim();

        boolean hasTypeFilter = !TYPE_ALL.equals(typeCode);
        boolean hasQuery = !query.isBlank();
        boolean hasSpecificColumn = !SEARCH_COLUMN_ALL.equals(searchColumn);

        if (hasTypeFilter && hasQuery && hasSpecificColumn) {
            return text("archive.summary.type.column.query",
                    count,
                    asSentenceLabel(getTypeLabel(typeCode)),
                    getSearchColumnLabel(searchColumn),
                    query);
        }
        if (hasTypeFilter && hasQuery) {
            return text("archive.summary.type.query",
                    count,
                    asSentenceLabel(getTypeLabel(typeCode)),
                    query);
        }
        if (hasTypeFilter) {
            return text("archive.summary.type",
                    count,
                    asSentenceLabel(getTypeLabel(typeCode)));
        }
        if (hasQuery && hasSpecificColumn) {
            return text("archive.summary.column.query",
                    count,
                    getSearchColumnLabel(searchColumn),
                    query);
        }
        if (hasQuery) {
            return text("archive.summary.query", count, query);
        }
        return text("archive.visible", count);
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
        String type = normalizeTypeCode(selectedType.get());
        String searchColumn = normalizeSearchColumn(selectedSearchColumn.get());

        filteredItems.setPredicate(item -> matchesType(item, type) && matchesQuery(item, query, searchColumn));

        if (selectedItem.get() != null && !filteredItems.contains(selectedItem.get())) {
            selectedItem.set(filteredItems.isEmpty() ? null : filteredItems.getFirst());
        } else if (selectedItem.get() == null && !filteredItems.isEmpty()) {
            selectedItem.set(filteredItems.getFirst());
        }
    }

    private boolean matchesType(VaultItemFx item, String type) {
        return TYPE_ALL.equals(type) || type.equalsIgnoreCase(item.getItemType());
    }

    private boolean matchesQuery(VaultItemFx item, String query, String searchColumn) {
        if (query.isBlank()) {
            return true;
        }
        return getSearchValues(item, searchColumn).stream()
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(query));
    }

    private List<String> getSearchValues(VaultItemFx item, String searchColumn) {
        if (isLockedItemHidden(item)) {
            return switch (normalizeSearchColumn(searchColumn)) {
                case SEARCH_COLUMN_TITLE -> List.of(text("item.locked"));
                case SEARCH_COLUMN_TYPE -> List.of(getTypeLabel(item == null ? null : item.getItemType()));
                case SEARCH_COLUMN_CREATED -> List.of(formatTimestamp(item == null ? null : item.getCreatedAt()));
                case SEARCH_COLUMN_PREVIEW -> List.of(text("item.locked"));
                case SEARCH_COLUMN_ALL -> List.of(
                        text("item.locked"),
                        getTypeLabel(item == null ? null : item.getItemType()),
                        formatTimestamp(item == null ? null : item.getCreatedAt()));
                default -> List.of();
            };
        }

        return switch (normalizeSearchColumn(searchColumn)) {
            case SEARCH_COLUMN_TITLE -> List.of(getItemTitle(item));
            case SEARCH_COLUMN_TYPE -> List.of(getTypeLabel(item == null ? null : item.getItemType()));
            case SEARCH_COLUMN_CREATED -> List.of(formatTimestamp(item == null ? null : item.getCreatedAt()));
            case SEARCH_COLUMN_PREVIEW -> List.of(getSearchablePreview(item));
            case SEARCH_COLUMN_ALL -> List.of(
                    getItemTitle(item),
                    getTypeLabel(item == null ? null : item.getItemType()),
                    formatTimestamp(item == null ? null : item.getCreatedAt()),
                    getSearchablePreview(item));
            default -> List.of();
        };
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

    private void initializeSearchStates() {
        searchStateByType.clear();
        for (String typeCode : typeOptions) {
            searchStateByType.put(normalizeTypeCode(typeCode), new SearchState());
        }
    }

    private SearchState getSearchState(String typeCode) {
        return searchStateByType.computeIfAbsent(normalizeTypeCode(typeCode), key -> new SearchState());
    }

    private void loadSearchState(String typeCode) {
        SearchState searchState = getSearchState(typeCode);
        syncingSearchState = true;
        try {
            selectedSearchColumn.set(searchState.searchColumnProperty().get());
            searchText.set(searchState.searchTextProperty().get());
        } finally {
            syncingSearchState = false;
        }
    }

    private String getSearchablePreview(VaultItemFx item) {
        if (isLockedItemHidden(item)) {
            return text("item.locked");
        }
        return firstNonBlank(
                getResolvedContext(item),
                getResolvedContent(item),
                text("item.preview.none"));
    }

    private ProtectedItemData resolveItemData(VaultItemFx item) {
        if (item == null) {
            return null;
        }
        if (item.isUnlockedInSession() && item.getUnlockedSession() != null) {
            return item.getUnlockedSession().data();
        }
        return new ProtectedItemData(
                item.getTitle(),
                item.getContent(),
                item.getAiContext(),
                item.getTags(),
                item.getSourceUrl());
    }

    private boolean hasActiveArchiveSearch() {
        return searchText.get() != null && !searchText.get().trim().isBlank();
    }

    private String asSentenceLabel(String value) {
        return value == null ? "" : value.toLowerCase(getLocale());
    }

    private String normalizeTypeCode(String typeCode) {
        return typeCode == null || typeCode.isBlank() ? TYPE_ALL : typeCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSearchColumn(String searchColumn) {
        if (searchColumn == null || searchColumn.isBlank()) {
            return SEARCH_COLUMN_ALL;
        }

        String normalized = searchColumn.trim().toUpperCase(Locale.ROOT);
        if (searchColumnOptions.contains(normalized)) {
            return normalized;
        }
        return SEARCH_COLUMN_ALL;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static final class SearchState {

        private final StringProperty searchText = new SimpleStringProperty("");
        private final StringProperty searchColumn = new SimpleStringProperty(SEARCH_COLUMN_ALL);

        private StringProperty searchTextProperty() {
            return searchText;
        }

        private StringProperty searchColumnProperty() {
            return searchColumn;
        }
    }
}
