package com.example.desktop.model;

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

import java.util.Collection;
import java.util.List;

/**
 * Central observable state for the desktop application.
 * Controllers bind to this model instead of calling each other.
 */
public class AppModel {

    private final ObservableList<String> typeOptions = FXCollections.observableArrayList("ALL", "URL", "TEXT", "IMAGE");
    private final ObservableList<VaultItemFx> allItems = FXCollections.observableArrayList();
    private final FilteredList<VaultItemFx> filteredItems = new FilteredList<>(allItems);

    private final ObjectProperty<VaultItemFx> selectedItem = new SimpleObjectProperty<>();
    private final StringProperty searchText = new SimpleStringProperty("");
    private final StringProperty selectedType = new SimpleStringProperty("ALL");
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private final IntegerProperty totalCount = new SimpleIntegerProperty();
    private final IntegerProperty urlCount = new SimpleIntegerProperty();
    private final IntegerProperty textCount = new SimpleIntegerProperty();
    private final IntegerProperty imageCount = new SimpleIntegerProperty();

    private final StringProperty urlInput = new SimpleStringProperty("");
    private final StringProperty urlTitleInput = new SimpleStringProperty("");
    private final StringProperty urlNotesInput = new SimpleStringProperty("");
    private final StringProperty textTitleInput = new SimpleStringProperty("");
    private final StringProperty textContentInput = new SimpleStringProperty("");
    private final StringProperty imageTitleInput = new SimpleStringProperty("");
    private final StringProperty imagePathInput = new SimpleStringProperty("");

    public AppModel() {
        searchText.addListener((obs, oldValue, newValue) -> updateFilter());
        selectedType.addListener((obs, oldValue, newValue) -> updateFilter());
        allItems.addListener((ListChangeListener<VaultItemFx>) change -> updateCounts());

        updateFilter();
        updateCounts();
    }

    public ObservableList<String> getTypeOptions() {
        return typeOptions;
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

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public StringProperty selectedTypeProperty() {
        return selectedType;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public void setStatusMessage(String message) {
        statusMessage.set(message);
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public void setBusy(boolean value) {
        busy.set(value);
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

    private void updateFilter() {
        String query = searchText.get() == null ? "" : searchText.get().trim().toLowerCase();
        String type = selectedType.get() == null ? "ALL" : selectedType.get().trim().toUpperCase();

        filteredItems.setPredicate(item -> matchesType(item, type) && matchesQuery(item, query));

        if (selectedItem.get() != null && !filteredItems.contains(selectedItem.get())) {
            selectedItem.set(filteredItems.isEmpty() ? null : filteredItems.getFirst());
        } else if (selectedItem.get() == null && !filteredItems.isEmpty()) {
            selectedItem.set(filteredItems.getFirst());
        }
    }

    private boolean matchesType(VaultItemFx item, String type) {
        return "ALL".equals(type) || type.equalsIgnoreCase(item.getItemType());
    }

    private boolean matchesQuery(VaultItemFx item, String query) {
        if (query.isBlank()) {
            return true;
        }
        return List.of(item.getTitle(), item.getContent(), item.getAiContext(), item.getTags(), item.getSourceUrl(), item.getItemType())
                .stream()
                .filter(value -> value != null)
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains(query));
    }

    private void updateCounts() {
        totalCount.set(allItems.size());
        urlCount.set((int) allItems.stream().filter(item -> "URL".equalsIgnoreCase(item.getItemType())).count());
        textCount.set((int) allItems.stream().filter(item -> "TEXT".equalsIgnoreCase(item.getItemType())).count());
        imageCount.set((int) allItems.stream().filter(item -> "IMAGE".equalsIgnoreCase(item.getItemType())).count());
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
}
