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
 * WEB VERSION - Modern, Beautiful Main View
 * Beautiful TimeVault UI with Vaadin
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
        getStyle()
            .set("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
            .set("min-height", "100vh");

        // Main scroll container
        VerticalLayout main = new VerticalLayout();
        main.setSizeFull();
        main.setPadding(false);
        main.setSpacing(false);

        main.add(createHeader());
        main.add(createHeroSection());

        // Content wrapper with max width
        VerticalLayout contentWrapper = new VerticalLayout();
        contentWrapper.setMaxWidth("1200px");
        contentWrapper.setWidth("100%");
        contentWrapper.setPadding(true);
        contentWrapper.setMargin(false);
        contentWrapper.setSpacing(true);
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
        header.setWidthFull();
        header.setPadding(true);
        header.setMargin(false);
        header.setSpacing(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle()
            .set("background", "rgba(255, 255, 255, 0.1)")
            .set("backdrop-filter", "blur(10px)")
            .set("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
            .set("box-shadow", "0 8px 32px 0 rgba(31, 38, 135, 0.37)");

        // Logo area
        HorizontalLayout logoArea = new HorizontalLayout();
        logoArea.setAlignItems(FlexComponent.Alignment.CENTER);
        logoArea.setSpacing(true);

        Icon vaultIcon = VaadinIcon.ARCHIVES.create();
        vaultIcon.setSize("40px");
        vaultIcon.getStyle().set("color", "white");

        H1 title = new H1("TimeVault");
        title.getStyle()
            .set("color", "white")
            .set("margin", "0")
            .set("font-size", "28px")
            .set("font-weight", "600");

        logoArea.add(vaultIcon, title);

        // Search area
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search your vault...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("350px");
        searchField.getStyle()
            .set("background", "rgba(255, 255, 255, 0.15)")
            .set("color", "white");

        searchField.addValueChangeListener(e -> {
            if (!e.getValue().isEmpty()) {
                List<VaultItem> results = vaultItemService.search(e.getValue());
                updateItemsList(results);
            } else {
                loadRecentItems();
            }
        });

        header.add(logoArea, searchField);
        header.setFlexGrow(1, searchField);

        return header;
    }

    private Component createHeroSection() {
        VerticalLayout hero = new VerticalLayout();
        hero.setWidthFull();
        hero.setPadding(true);
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        hero.setHeight("200px");

        H2 heroTitle = new H2("Save Everything, Find Anything");
        heroTitle.getStyle()
            .set("color", "white")
            .set("text-align", "center")
            .set("font-size", "42px")
            .set("font-weight", "700")
            .set("margin", "0 0 15px 0");

        Paragraph heroSubtitle = new Paragraph("Your personal digital vault with AI-powered context and smart auto-tagging");
        heroSubtitle.getStyle()
            .set("color", "rgba(255, 255, 255, 0.9)")
            .set("text-align", "center")
            .set("font-size", "16px")
            .set("margin", "0");

        hero.add(heroTitle, heroSubtitle);
        return hero;
    }

    private Component createContentSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setPadding(true);
        section.getStyle()
            .set("background", "white")
            .set("border-radius", "16px")
            .set("box-shadow", "0 20px 60px rgba(0, 0, 0, 0.15)");

        // Tab selection
        Tab urlTab = new Tab(VaadinIcon.LINK.create(), new Span("📎 URL"));
        Tab imageTab = new Tab(VaadinIcon.PICTURE.create(), new Span("🖼️ Image"));
        Tab textTab = new Tab(VaadinIcon.TEXT_LABEL.create(), new Span("📝 Text"));

        Tabs tabs = new Tabs(urlTab, imageTab, textTab);
        tabs.setWidthFull();
        tabs.getStyle().set("margin-bottom", "30px");

        // Content panels
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
        panel.setWidthFull();
        panel.setSpacing(true);

        Paragraph description = new Paragraph("Paste any URL and we'll save the full content with AI-generated context.");
        description.getStyle()
            .set("color", "#666")
            .set("font-size", "14px")
            .set("margin", "0 0 20px 0");

        TextField urlField = new TextField();
        urlField.setPlaceholder("https://example.com/article");
        urlField.setPrefixComponent(VaadinIcon.LINK.create());
        urlField.setWidthFull();
        urlField.getStyle()
            .set("padding", "12px")
            .set("border-radius", "8px");

        Button saveButton = new Button("💾 Save to Vault");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveButton.setWidthFull();
        saveButton.getStyle()
            .set("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
            .set("color", "white")
            .set("font-weight", "600");

        saveButton.addClickListener(e -> {
            if (urlField.getValue().isEmpty()) {
                Notification.show("Please enter a URL", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                try {
                    String url = urlField.getValue();
                    String title = "URL: " + url.substring(0, Math.min(url.length(), 50));
                    vaultItemService.saveUrl(url, title, url);

                    Notification.show("✓ Saved with AI context!", 3000, Notification.Position.BOTTOM_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    urlField.clear();
                    refreshRecentItems();
                } catch (Exception ex) {
                    Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });

        panel.add(description, urlField, saveButton);
        return panel;
    }

    private VerticalLayout createImagePanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.setSpacing(true);

        Paragraph description = new Paragraph("Upload images and let AI analyze and describe them automatically.");
        description.getStyle()
            .set("color", "#666")
            .set("font-size", "14px")
            .set("margin", "0 0 20px 0");

        com.vaadin.flow.component.upload.receivers.MemoryBuffer buffer =
            new com.vaadin.flow.component.upload.receivers.MemoryBuffer();

        Upload upload = new Upload(buffer);
        upload.setWidthFull();
        upload.setAcceptedFileTypes("image/*");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(10 * 1024 * 1024);
        upload.setDropLabel(new Span("Drop image here or click to browse"));
        upload.getStyle()
            .set("border", "2px dashed #667eea")
            .set("border-radius", "8px")
            .set("padding", "30px")
            .set("text-align", "center");

        upload.addSucceededListener(event -> {
            try {
                String filename = event.getFileName();
                String mimeType = event.getMIMEType();
                byte[] imageData = buffer.getInputStream().readAllBytes();

                String title = "Image: " + filename;
                vaultItemService.saveImage(title, imageData, mimeType, filename);

                Notification.show("✓ Image analyzed & saved!", 3000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refreshRecentItems();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        panel.add(description, upload);
        return panel;
    }

    private VerticalLayout createTextPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setWidthFull();
        panel.setSpacing(true);

        Paragraph description = new Paragraph("Save notes, quotes, or any text. AI will create a smart summary.");
        description.getStyle()
            .set("color", "#666")
            .set("font-size", "14px")
            .set("margin", "0 0 20px 0");

        TextArea textArea = new TextArea();
        textArea.setPlaceholder("Type or paste your text here...");
        textArea.setWidthFull();
        textArea.setMinHeight("150px");
        textArea.getStyle()
            .set("border-radius", "8px")
            .set("padding", "12px");

        Button saveButton = new Button("💾 Save to Vault");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveButton.setWidthFull();
        saveButton.getStyle()
            .set("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
            .set("color", "white")
            .set("font-weight", "600");

        saveButton.addClickListener(e -> {
            if (textArea.getValue().isEmpty()) {
                Notification.show("Please enter some text", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                try {
                    String content = textArea.getValue();
                    String title = "Note: " + content.substring(0, Math.min(content.length(), 50));
                    vaultItemService.saveText(title, content);

                    Notification.show("✓ Text saved with AI context!", 3000, Notification.Position.BOTTOM_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    textArea.clear();
                    refreshRecentItems();
                } catch (Exception ex) {
                    Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });

        panel.add(description, textArea, saveButton);
        return panel;
    }

    private Component createRecentItemsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setPadding(true);
        section.getStyle()
            .set("background", "white")
            .set("border-radius", "16px")
            .set("box-shadow", "0 20px 60px rgba(0, 0, 0, 0.15)");

        HorizontalLayout sectionHeader = new HorizontalLayout();
        sectionHeader.setWidthFull();
        sectionHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        sectionHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        sectionHeader.setMargin(false);
        sectionHeader.setPadding(false);

        H3 sectionTitle = new H3("Recent Saves");
        sectionTitle.getStyle()
            .set("margin", "0")
            .set("color", "#333");

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshBtn.addClickListener(e -> refreshRecentItems());

        sectionHeader.add(sectionTitle, refreshBtn);

        itemsList = new VerticalLayout();
        itemsList.setWidthFull();
        itemsList.setPadding(false);
        itemsList.setSpacing(true);

        loadRecentItems();

        section.add(sectionHeader, itemsList);
        return section;
    }

    private void loadRecentItems() {
        itemsList.removeAll();

        try {
            List<VaultItem> recentItems = vaultItemService.findRecent();

            if (recentItems.isEmpty()) {
                VerticalLayout empty = new VerticalLayout();
                empty.setAlignItems(FlexComponent.Alignment.CENTER);
                empty.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
                empty.setPadding(true);
                empty.setHeight("200px");

                H3 emptyTitle = new H3("Your vault is empty");
                emptyTitle.getStyle()
                    .set("color", "#999")
                    .set("text-align", "center")
                    .set("margin", "0");

                Paragraph emptyText = new Paragraph("Start saving URLs, images, or text to see them here.");
                emptyText.getStyle()
                    .set("color", "#bbb")
                    .set("text-align", "center");

                empty.add(emptyTitle, emptyText);
                itemsList.add(empty);
            } else {
                for (VaultItem item : recentItems) {
                    itemsList.add(createVaultItemCard(item));
                }
            }
        } catch (Exception e) {
            Notification.show("Error loading items: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateItemsList(List<VaultItem> items) {
        itemsList.removeAll();
        if (items.isEmpty()) {
            Paragraph noResults = new Paragraph("No results found");
            noResults.getStyle()
                .set("color", "#999")
                .set("text-align", "center")
                .set("padding", "20px");
            itemsList.add(noResults);
        } else {
            for (VaultItem item : items) {
                itemsList.add(createVaultItemCard(item));
            }
        }
    }

    private void refreshRecentItems() {
        loadRecentItems();
    }

    private Component createVaultItemCard(VaultItem vaultItem) {
        HorizontalLayout card = new HorizontalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setAlignItems(FlexComponent.Alignment.START);
        card.setSpacing(true);
        card.getStyle()
            .set("background", "linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%)")
            .set("border-left", "4px solid #667eea")
            .set("border-radius", "8px")
            .set("transition", "transform 0.2s, box-shadow 0.2s")
            .set("cursor", "pointer");

        // Icon
        String emoji = switch (vaultItem.getItemType()) {
            case "URL" -> "📎";
            case "IMAGE" -> "🖼️";
            case "TEXT" -> "📝";
            default -> "📌";
        };

        Paragraph typeEmoji = new Paragraph(emoji);
        typeEmoji.getStyle()
            .set("font-size", "24px")
            .set("margin", "0")
            .set("min-width", "40px");

        // Content
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        H4 itemTitle = new H4(vaultItem.getTitle());
        itemTitle.getStyle()
            .set("margin", "0")
            .set("color", "#333")
            .set("font-weight", "600");

        String contextText = vaultItem.getAiContext() != null ? vaultItem.getAiContext() :
            (vaultItem.getContent() != null ?
                vaultItem.getContent().substring(0, Math.min(vaultItem.getContent().length(), 150)) + "..." :
                "No content");
        Paragraph itemContext = new Paragraph(contextText);
        itemContext.getStyle()
            .set("color", "#666")
            .set("font-size", "14px")
            .set("margin", "0")
            .set("line-height", "1.5");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        String metaText = vaultItem.getItemType() + " • " +
            (vaultItem.getTags() != null ? vaultItem.getTags() : "No tags") + " • " +
            (vaultItem.getCreatedAt() != null ? vaultItem.getCreatedAt().format(formatter) : "");
        Span itemMeta = new Span(metaText);
        itemMeta.getStyle()
            .set("color", "#999")
            .set("font-size", "12px");

        content.add(itemTitle, itemContext, itemMeta);

        // Delete button
        Button deleteBtn = new Button(VaadinIcon.TRASH.create());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
        deleteBtn.getStyle()
            .set("min-width", "44px")
            .set("min-height", "44px");
        deleteBtn.addClickListener(e -> {
            vaultItemService.delete(vaultItem.getId());
            Notification.show("Item deleted", 2000, Notification.Position.BOTTOM_END);
            refreshRecentItems();
        });

        card.add(typeEmoji, content, deleteBtn);
        card.setFlexGrow(1, content);

        return card;
    }
}

