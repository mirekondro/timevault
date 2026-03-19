package com.example.web.views;

import com.example.shared.model.VaultItem;
import com.example.shared.service.VaultItemService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;
import java.util.List;

@PageTitle("TimeVault - Your Digital Memory")
@Route("")
public class MainView extends VerticalLayout {

    private final VaultItemService vaultItemService;

    // BEZPEČNÁ INICIALIZACE: Vytvoříme Div rovnou, aby na něj vyhledávání mohlo bezpečně sahat
    private final Div itemsGrid = new Div();

    @Autowired
    public MainView(VaultItemService vaultItemService) {
        this.vaultItemService = vaultItemService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("main-view");

        itemsGrid.addClassName("vault-grid");
        itemsGrid.setWidthFull();

        VerticalLayout main = new VerticalLayout();
        main.setSizeFull();
        main.setPadding(false);
        main.setSpacing(false);

        main.add(createHeader());
        main.add(createHeroSection());

        VerticalLayout contentWrapper = new VerticalLayout();
        contentWrapper.setMaxWidth("1200px");
        contentWrapper.setWidth("100%");
        contentWrapper.setPadding(true);
        contentWrapper.setMargin(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setAlignSelf(Alignment.CENTER);

        contentWrapper.add(
                createContentSection(),
                createRecentItemsSection()
        );

        main.add(contentWrapper);
        main.setFlexGrow(1, contentWrapper);

        add(main);
    }

    private Component createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("glass-header");
        header.setWidthFull();
        header.setPadding(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        HorizontalLayout logoArea = new HorizontalLayout();
        logoArea.setAlignItems(FlexComponent.Alignment.CENTER);
        logoArea.setSpacing(true);

        Icon vaultIcon = VaadinIcon.CUBE.create();
        vaultIcon.addClassName("logo-icon");

        H1 title = new H1("TimeVault");
        title.addClassName("logo-text");

        logoArea.add(vaultIcon, title);

        TextField searchField = new TextField();
        searchField.setPlaceholder("Search your vault...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addClassName("header-search");
        searchField.setClearButtonVisible(true);

        searchField.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().trim().isEmpty()) {
                updateItemsList(vaultItemService.search(e.getValue().trim()));
            } else {
                loadRecentItems();
            }
        });

        header.add(logoArea, searchField);
        return header;
    }

    private Component createHeroSection() {
        VerticalLayout hero = new VerticalLayout();
        hero.addClassName("hero-section");
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        H2 heroTitle = new H2("Capture your digital mind.");
        heroTitle.addClassName("hero-title");

        Paragraph heroSubtitle = new Paragraph("AI-powered context and smart auto-tagging for everything you save.");
        heroSubtitle.addClassName("hero-subtitle");

        hero.add(heroTitle, heroSubtitle);
        return hero;
    }

    private Component createContentSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("modern-card");
        section.setWidthFull();

        Tab urlTab = new Tab(VaadinIcon.LINK.create(), new Span("URL"));
        Tab imageTab = new Tab(VaadinIcon.PICTURE.create(), new Span("Image"));
        Tab textTab = new Tab(VaadinIcon.TEXT_LABEL.create(), new Span("Text"));

        Tabs tabs = new Tabs(urlTab, imageTab, textTab);
        tabs.addThemeVariants(TabsVariant.LUMO_EQUAL_WIDTH_TABS);
        tabs.setWidthFull();
        tabs.addClassName("modern-tabs");

        VerticalLayout urlPanel = createUrlPanel();
        VerticalLayout imagePanel = createImagePanel();
        VerticalLayout textPanel = createTextPanel();

        imagePanel.setVisible(false);
        textPanel.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            urlPanel.setVisible(tabs.getSelectedTab() == urlTab);
            imagePanel.setVisible(tabs.getSelectedTab() == imageTab);
            textPanel.setVisible(tabs.getSelectedTab() == textTab);
        });

        section.add(tabs, urlPanel, imagePanel, textPanel);
        return section;
    }

    private VerticalLayout createUrlPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.addClassName("input-panel");

        TextField urlField = new TextField();
        urlField.setPlaceholder("https://example.com/article");
        urlField.setPrefixComponent(VaadinIcon.LINK.create());
        urlField.setWidthFull();
        urlField.addClassName("modern-input");

        Button saveButton = new Button("Save to Vault", VaadinIcon.PLUS.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClassName("modern-button");
        saveButton.setWidthFull();

        saveButton.addClickListener(e -> {
            if (urlField.getValue().trim().isEmpty()) {
                Notification.show("Please enter a URL").addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                try {
                    String url = urlField.getValue().trim();
                    String title = "URL: " + url.substring(0, Math.min(url.length(), 50));
                    vaultItemService.saveUrl(url, title, url);
                    Notification.show("Saved with AI context!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    urlField.clear();
                    loadRecentItems();
                } catch (Exception ex) {
                    Notification.show("Error: " + ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });

        panel.add(new Paragraph("Paste any URL to generate an AI summary automatically."), urlField, saveButton);
        return panel;
    }

    private VerticalLayout createImagePanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.addClassName("input-panel");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setWidthFull();
        upload.setAcceptedFileTypes("image/*");
        upload.setMaxFiles(1);
        upload.addClassName("modern-upload");

        upload.addSucceededListener(event -> {
            try {
                byte[] fileData = buffer.getInputStream().readAllBytes();
                vaultItemService.saveImage("Image: " + event.getFileName(), fileData, event.getMIMEType(), event.getFileName());
                Notification.show("Image analyzed & saved!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadRecentItems();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        panel.add(new Paragraph("Upload an image. Gemini Vision will analyze and describe it."), upload);
        return panel;
    }

    private VerticalLayout createTextPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.addClassName("input-panel");

        TextArea textArea = new TextArea();
        textArea.setPlaceholder("Type or paste your text here...");
        textArea.setWidthFull();
        textArea.setMinHeight("150px");
        textArea.addClassName("modern-input");

        Button saveButton = new Button("Save Note", VaadinIcon.PLUS.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClassName("modern-button");
        saveButton.setWidthFull();

        saveButton.addClickListener(e -> {
            if (textArea.getValue().trim().isEmpty()) {
                Notification.show("Please enter some text").addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                try {
                    String content = textArea.getValue().trim();
                    vaultItemService.saveText("Note: " + content.substring(0, Math.min(content.length(), 30)), content);
                    Notification.show("Text saved with AI context!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    textArea.clear();
                    loadRecentItems();
                } catch (Exception ex) {
                    Notification.show("Error: " + ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });

        panel.add(new Paragraph("Save notes or quotes. AI will extract the key points."), textArea, saveButton);
        return panel;
    }

    private Component createRecentItemsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setPadding(false);
        section.getStyle().set("margin-top", "2rem");

        HorizontalLayout sectionHeader = new HorizontalLayout();
        sectionHeader.setWidthFull();
        sectionHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        sectionHeader.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 sectionTitle = new H3("Your Vault");
        sectionTitle.addClassName("section-title");

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshBtn.addClickListener(e -> loadRecentItems());

        sectionHeader.add(sectionTitle, refreshBtn);

        loadRecentItems();

        section.add(sectionHeader, itemsGrid);
        return section;
    }

    private void loadRecentItems() {
        try {
            updateItemsList(vaultItemService.findRecent());
        } catch (Exception e) {
            Notification.show("Error loading items: " + e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateItemsList(List<VaultItem> items) {
        itemsGrid.removeAll();

        if (items.isEmpty()) {
            Div emptyState = new Div();
            emptyState.addClassName("empty-state");
            emptyState.add(new H3("It's quiet here..."));
            emptyState.add(new Paragraph("Save your first item above to start building your vault."));
            itemsGrid.add(emptyState);
        } else {
            for (VaultItem item : items) {
                itemsGrid.add(createVaultItemCard(item));
            }
        }
    }

    private Component createVaultItemCard(VaultItem vaultItem) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("vault-card");
        card.setSpacing(false);

        HorizontalLayout cardHeader = new HorizontalLayout();
        cardHeader.setWidthFull();
        cardHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        cardHeader.setAlignItems(FlexComponent.Alignment.CENTER);

        // BEZPEČNÁ KONTROLA NULL: Pokud databáze vrátí null u typu, aplikace nespadne
        String type = vaultItem.getItemType() != null ? vaultItem.getItemType() : "UNKNOWN";
        String emoji = switch (type) {
            case "URL" -> "🔗";
            case "IMAGE" -> "🖼️";
            case "TEXT" -> "📝";
            default -> "📄";
        };

        Span typeIcon = new Span(emoji);
        typeIcon.addClassName("type-icon");

        Button deleteBtn = new Button(VaadinIcon.TRASH.create());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
        deleteBtn.addClickListener(e -> {
            vaultItemService.delete(vaultItem.getId());
            Notification.show("Item deleted");
            loadRecentItems();
        });

        cardHeader.add(typeIcon, deleteBtn);

        H4 itemTitle = new H4(vaultItem.getTitle() != null ? vaultItem.getTitle() : "Untitled");
        itemTitle.addClassName("item-title");

        Paragraph itemContext = new Paragraph(vaultItem.getAiContext() != null ?
            vaultItem.getAiContext() : "No AI context available");
        itemContext.addClassName("item-context");

        Span itemMeta = new Span();
        itemMeta.addClassName("item-meta");
        if (vaultItem.getCreatedAt() != null) {
            itemMeta.setText(vaultItem.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        }

        card.add(cardHeader, itemTitle, itemContext, itemMeta);
        return card;
    }
}
