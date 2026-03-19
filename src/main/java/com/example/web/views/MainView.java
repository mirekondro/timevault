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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * WEB VERSION - Main View
 *
 * This is YOUR part - the web frontend using Vaadin
 * Uses shared backend services (VaultItemService)
 */
@PageTitle("TimeVault - Your Digital Memory")
@Route("")
public class MainView extends VerticalLayout {

    private final VaultItemService vaultItemService;
    private VerticalLayout itemsList;

    @Autowired
    public MainView(VaultItemService vaultItemService) {
        this.vaultItemService = vaultItemService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("main-view");

        add(createHeader());
        add(createHeroSection());
        add(createContentSection());
        add(createRecentItemsSection());
    }

    private Component createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.addClassName("header");
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        // Logo and title
        HorizontalLayout logo = new HorizontalLayout();
        logo.setAlignItems(FlexComponent.Alignment.CENTER);
        logo.setSpacing(true);
        Icon vaultIcon = VaadinIcon.ARCHIVES.create();
        vaultIcon.addClassName("logo-icon");
        H1 title = new H1("TimeVault");
        title.addClassName("logo-text");
        logo.add(vaultIcon, title);

        // Search bar
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search your vault...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addClassName("header-search");
        searchField.setWidth("400px");

        searchField.addValueChangeListener(e -> {
            if (!e.getValue().isEmpty()) {
                List<VaultItem> results = vaultItemService.search(e.getValue());
                updateItemsList(results);
            } else {
                loadRecentItems();
            }
        });

        // Action buttons
        Button browseBtn = new Button("Browse All", VaadinIcon.GRID.create());
        browseBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        browseBtn.addClassName("header-button");

        HorizontalLayout actions = new HorizontalLayout(searchField, browseBtn);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        header.add(logo, actions);
        return header;
    }

    private Component createHeroSection() {
        VerticalLayout hero = new VerticalLayout();
        hero.setWidthFull();
        hero.setPadding(true);
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.addClassName("hero-section");

        H2 heroTitle = new H2("Save Everything, Find Anything");
        heroTitle.addClassName("hero-title");

        Paragraph heroSubtitle = new Paragraph(
            "Your personal digital vault with AI-powered context and smart auto-tagging"
        );
        heroSubtitle.addClassName("hero-subtitle");

        hero.add(heroTitle, heroSubtitle);
        return hero;
    }

    private Component createContentSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setMaxWidth("900px");
        section.addClassName("content-section");
        section.setAlignSelf(Alignment.CENTER);

        // Tab selection
        Tab urlTab = new Tab(VaadinIcon.LINK.create(), new Span("URL"));
        Tab imageTab = new Tab(VaadinIcon.PICTURE.create(), new Span("Image"));
        Tab textTab = new Tab(VaadinIcon.TEXT_LABEL.create(), new Span("Text"));

        Tabs tabs = new Tabs(urlTab, imageTab, textTab);
        tabs.setWidthFull();
        tabs.addClassName("input-tabs");

        // Content panels
        VerticalLayout urlPanel = createUrlPanel();
        VerticalLayout imagePanel = createImagePanel();
        VerticalLayout textPanel = createTextPanel();

        // Show/hide panels based on selected tab
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
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
            "Paste any URL - articles, videos, tweets, or web pages. We'll fetch and save the full content."
        );
        description.addClassName("panel-description");

        TextField urlField = new TextField();
        urlField.setPlaceholder("https://example.com/article");
        urlField.setPrefixComponent(VaadinIcon.LINK.create());
        urlField.setWidthFull();
        urlField.addClassName("url-input");

        Button saveButton = new Button("Save to Vault", VaadinIcon.DOWNLOAD.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveButton.addClassName("save-button");
        saveButton.addClickListener(e -> {
            if (urlField.getValue().isEmpty()) {
                Notification.show("Please enter a URL", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                try {
                    String url = urlField.getValue();
                    String title = "URL: " + url.substring(0, Math.min(url.length(), 50));
                    String aiContext = "Saved URL content. AI context will be generated automatically.";
                    vaultItemService.saveUrl(url, title, url, aiContext);

                    Notification.show("✓ Content saved to database!", 3000, Notification.Position.BOTTOM_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    urlField.clear();
                    refreshRecentItems();
                } catch (Exception ex) {
                    Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
            createFeatureItem(VaadinIcon.MAGIC, "AI-generated context in 3 sentences"),
            createFeatureItem(VaadinIcon.TAG, "Auto-tagged by type, platform & date"),
            createFeatureItem(VaadinIcon.DOWNLOAD, "Full content saved locally")
        );

        panel.add(description, urlField, saveButton, features);
        return panel;
    }

    private VerticalLayout createImagePanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
            "Upload images, screenshots, or photos. AI will analyze and tag them automatically."
        );
        description.addClassName("panel-description");

        Upload upload = new Upload();
        upload.setWidthFull();
        upload.setAcceptedFileTypes("image/*");
        upload.setDropLabel(new Span("Drop image here or click to browse"));
        upload.addClassName("image-upload");

        upload.addSucceededListener(event -> {
            try {
                String title = "Image: " + event.getFileName();
                String aiContext = "Image saved. AI analysis pending.";
                vaultItemService.saveImage(title, event.getFileName(), aiContext);

                Notification.show("✓ Image saved!", 3000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refreshRecentItems();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
            createFeatureItem(VaadinIcon.EYE, "AI visual analysis & context"),
            createFeatureItem(VaadinIcon.TAG, "Smart auto-tagging"),
            createFeatureItem(VaadinIcon.CALENDAR, "Date & metadata preserved")
        );

        panel.add(description, upload, features);
        return panel;
    }

    private VerticalLayout createTextPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.addClassName("input-panel");

        Paragraph description = new Paragraph(
            "Save notes, quotes, thoughts, or any text. Perfect for quick captures."
        );
        description.addClassName("panel-description");

        TextArea textArea = new TextArea();
        textArea.setPlaceholder("Type or paste your text here...");
        textArea.setWidthFull();
        textArea.setMinHeight("150px");
        textArea.addClassName("text-input");

        Button saveButton = new Button("Save to Vault", VaadinIcon.DOWNLOAD.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveButton.addClassName("save-button");
        saveButton.addClickListener(e -> {
            if (textArea.getValue().isEmpty()) {
                Notification.show("Please enter some text", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                try {
                    String content = textArea.getValue();
                    String title = "Note: " + content.substring(0, Math.min(content.length(), 50));
                    String aiContext = "Text note saved. AI context generated.";
                    vaultItemService.saveText(title, content, aiContext);

                    Notification.show("✓ Text saved to database!", 3000, Notification.Position.BOTTOM_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    textArea.clear();
                    refreshRecentItems();
                } catch (Exception ex) {
                    Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });

        Div features = new Div();
        features.addClassName("features-list");
        features.add(
            createFeatureItem(VaadinIcon.LIGHTBULB, "AI context generation"),
            createFeatureItem(VaadinIcon.TAG, "Automatic categorization"),
            createFeatureItem(VaadinIcon.CLOCK, "Timestamped & searchable")
        );

        panel.add(description, textArea, saveButton, features);
        return panel;
    }

    private HorizontalLayout createFeatureItem(VaadinIcon iconName, String text) {
        HorizontalLayout item = new HorizontalLayout();
        item.setAlignItems(FlexComponent.Alignment.CENTER);
        item.addClassName("feature-item");

        Icon icon = iconName.create();
        icon.setSize("16px");
        icon.addClassName("feature-icon");

        Span label = new Span(text);
        label.addClassName("feature-text");

        item.add(icon, label);
        return item;
    }

    private Component createRecentItemsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setMaxWidth("900px");
        section.addClassName("recent-section");
        section.setAlignSelf(Alignment.CENTER);

        HorizontalLayout sectionHeader = new HorizontalLayout();
        sectionHeader.setWidthFull();
        sectionHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        sectionHeader.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 sectionTitle = new H3("Recent Saves");
        sectionTitle.addClassName("section-title");

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        refreshBtn.addClickListener(e -> refreshRecentItems());

        sectionHeader.add(sectionTitle, refreshBtn);

        // Items list
        itemsList = new VerticalLayout();
        itemsList.setWidthFull();
        itemsList.setPadding(false);
        itemsList.setSpacing(true);
        itemsList.addClassName("items-list");

        loadRecentItems();

        section.add(sectionHeader, itemsList);
        return section;
    }

    private void loadRecentItems() {
        itemsList.removeAll();

        try {
            List<VaultItem> recentItems = vaultItemService.findRecent();

            if (recentItems.isEmpty()) {
                itemsList.add(createEmptyState());
            } else {
                for (VaultItem item : recentItems) {
                    itemsList.add(createVaultItemCard(item));
                }
            }
        } catch (Exception e) {
            itemsList.add(createErrorState(e.getMessage()));
        }
    }

    private void updateItemsList(List<VaultItem> items) {
        itemsList.removeAll();
        if (items.isEmpty()) {
            itemsList.add(new Paragraph("No results found"));
        } else {
            for (VaultItem item : items) {
                itemsList.add(createVaultItemCard(item));
            }
        }
    }

    private void refreshRecentItems() {
        loadRecentItems();
    }

    private Component createEmptyState() {
        VerticalLayout empty = new VerticalLayout();
        empty.setAlignItems(FlexComponent.Alignment.CENTER);
        empty.setPadding(true);

        Icon icon = VaadinIcon.INBOX.create();
        icon.setSize("48px");
        icon.setColor("var(--lumo-contrast-30pct)");

        H4 title = new H4("Your vault is empty");
        Paragraph text = new Paragraph("Start saving URLs, images, or text to see them here.");

        empty.add(icon, title, text);
        return empty;
    }

    private Component createErrorState(String message) {
        VerticalLayout error = new VerticalLayout();
        error.setAlignItems(FlexComponent.Alignment.CENTER);
        error.setPadding(true);

        Icon icon = VaadinIcon.WARNING.create();
        icon.setSize("48px");
        icon.setColor("var(--lumo-error-color)");

        H4 title = new H4("Database Connection Error");
        Paragraph text = new Paragraph("Check your SQL Server connection. Error: " + message);

        error.add(icon, title, text);
        return error;
    }

    private Component createVaultItemCard(VaultItem vaultItem) {
        HorizontalLayout card = new HorizontalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.addClassName("vault-item");
        card.setAlignItems(FlexComponent.Alignment.START);

        // Icon
        VaadinIcon iconType = switch (vaultItem.getItemType()) {
            case "URL" -> VaadinIcon.GLOBE;
            case "IMAGE" -> VaadinIcon.PICTURE;
            case "TEXT" -> VaadinIcon.TEXT_LABEL;
            default -> VaadinIcon.FILE;
        };

        Div iconWrapper = new Div();
        iconWrapper.addClassName("item-icon-wrapper");
        Icon icon = iconType.create();
        icon.addClassName("item-icon");
        iconWrapper.add(icon);

        // Content
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("item-content");

        H4 itemTitle = new H4(vaultItem.getTitle());
        itemTitle.addClassName("item-title");

        String contextText = vaultItem.getAiContext() != null ? vaultItem.getAiContext() :
            (vaultItem.getContent() != null ?
                vaultItem.getContent().substring(0, Math.min(vaultItem.getContent().length(), 150)) + "..." :
                "No content");
        Paragraph itemContext = new Paragraph(contextText);
        itemContext.addClassName("item-context");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        String metaText = vaultItem.getItemType() + " • " +
            (vaultItem.getTags() != null ? vaultItem.getTags() : "No tags") + " • " +
            (vaultItem.getCreatedAt() != null ? vaultItem.getCreatedAt().format(formatter) : "");
        Span itemMeta = new Span(metaText);
        itemMeta.addClassName("item-meta");

        content.add(itemTitle, itemContext, itemMeta);

        // Delete button
        Button deleteBtn = new Button(VaadinIcon.TRASH.create());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
        deleteBtn.addClassName("item-action");
        deleteBtn.addClickListener(e -> {
            vaultItemService.delete(vaultItem.getId());
            Notification.show("Item deleted", 2000, Notification.Position.BOTTOM_END);
            refreshRecentItems();
        });

        card.add(iconWrapper, content, deleteBtn);
        card.expand(content);

        return card;
    }
}

