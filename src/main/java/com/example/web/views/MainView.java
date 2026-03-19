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

@PageTitle("TimeVault - AI Digital Memory")
@Route("")
public class MainView extends VerticalLayout {

    private final VaultItemService vaultItemService;
    private final Div itemsGrid = new Div();
    private final Div currentView = new Div();

    // Navigation state
    private String currentViewState = "home"; // "home" or "vault"

    @Autowired
    public MainView(VaultItemService vaultItemService) {
        this.vaultItemService = vaultItemService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);
        addClassName("main-view");

        itemsGrid.addClassName("vault-grid");
        itemsGrid.setWidthFull();
        currentView.setSizeFull();

        // Create main container with proper centering
        VerticalLayout main = new VerticalLayout();
        main.setSizeFull();
        main.setPadding(false);
        main.setSpacing(false);
        main.setMargin(false);
        main.setAlignItems(Alignment.CENTER);

        // Header spans full width
        Component header = createHeader();
        main.add(header);
        main.setHorizontalComponentAlignment(Alignment.STRETCH, header);

        // Navigation tabs
        Component navTabs = createNavigationTabs();
        main.add(navTabs);
        main.setHorizontalComponentAlignment(Alignment.CENTER, navTabs);

        // Content wrapper with max width and centered
        VerticalLayout contentWrapper = new VerticalLayout();
        contentWrapper.setMaxWidth("1200px");
        contentWrapper.setWidth("100%");
        contentWrapper.setPadding(true);
        contentWrapper.setMargin(false);
        contentWrapper.setSpacing(true);

        // Add current view container
        contentWrapper.add(currentView);

        // Initialize with home view
        showHomeView();

        main.add(contentWrapper);
        main.setHorizontalComponentAlignment(Alignment.CENTER, contentWrapper);
        main.setFlexGrow(1, contentWrapper);

        add(main);
        setHorizontalComponentAlignment(Alignment.STRETCH, main);
    }

    private Component createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("glass-header");
        header.setWidthFull();
        header.getStyle().set("width", "100vw");
        header.getStyle().set("margin-left", "calc(-50vw + 50%)");
        header.getStyle().set("margin-right", "calc(-50vw + 50%)");
        header.setPadding(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        HorizontalLayout logoArea = new HorizontalLayout();
        logoArea.setAlignItems(FlexComponent.Alignment.CENTER);
        logoArea.setSpacing(true);

        Icon vaultIcon = VaadinIcon.CUBE.create();
        vaultIcon.setColor("#A78BFA"); // Neon purple icon
        vaultIcon.setSize("32px");

        H1 title = new H1("TimeVault");
        title.addClassName("logo-text");

        logoArea.add(vaultIcon, title);

        TextField searchField = new TextField();
        searchField.setPlaceholder("Search your digital mind...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addClassName("header-search");
        searchField.setClearButtonVisible(true);

        searchField.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().trim().isEmpty()) {
                updateItemsList(vaultItemService.searchComprehensive(e.getValue().trim()));
            } else {
                loadRecentItems();
            }
        });

        header.add(logoArea, searchField);
        return header;
    }

    private Component createNavigationTabs() {
        Tab homeTab = new Tab(VaadinIcon.HOME.create(), new Span("Home"));
        Tab vaultTab = new Tab(VaadinIcon.ARCHIVES.create(), new Span("Your Vault"));

        Tabs navTabs = new Tabs(homeTab, vaultTab);
        navTabs.addClassName("nav-tabs");
        navTabs.addThemeVariants(TabsVariant.LUMO_CENTERED);

        navTabs.addSelectedChangeListener(event -> {
            Tab selectedTab = navTabs.getSelectedTab();
            if (selectedTab == homeTab) {
                showHomeView();
            } else if (selectedTab == vaultTab) {
                showVaultView();
            }
        });

        return navTabs;
    }

    private void showHomeView() {
        currentViewState = "home";
        currentView.removeAll();

        VerticalLayout homeContent = new VerticalLayout();
        homeContent.setSpacing(true);
        homeContent.setPadding(false);
        homeContent.setAlignItems(Alignment.CENTER);

        // Add hero section
        homeContent.add(createHeroSection());

        // Add input section
        homeContent.add(createContentSection());

        // Add recent items (limited to 3)
        homeContent.add(createRecentItemsPreview());

        currentView.add(homeContent);
    }

    private void showVaultView() {
        currentViewState = "vault";
        currentView.removeAll();

        VerticalLayout vaultContent = new VerticalLayout();
        vaultContent.setSpacing(true);
        vaultContent.setPadding(false);

        // Add vault header
        H2 vaultTitle = new H2("Your Digital Vault");
        vaultTitle.addClassName("section-title");
        vaultContent.add(vaultTitle);

        // Add search functionality specific to vault
        TextField vaultSearch = new TextField();
        vaultSearch.setPlaceholder("Search your vault...");
        vaultSearch.setPrefixComponent(VaadinIcon.SEARCH.create());
        vaultSearch.addClassName("vault-search");
        vaultSearch.setWidthFull();
        vaultSearch.setMaxWidth("600px");
        vaultSearch.setClearButtonVisible(true);

        vaultSearch.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().trim().isEmpty()) {
                updateItemsList(vaultItemService.searchComprehensive(e.getValue().trim()));
            } else {
                loadAllItems();
            }
        });

        vaultContent.add(vaultSearch);
        vaultContent.setHorizontalComponentAlignment(Alignment.CENTER, vaultSearch);

        // Add all items grid
        itemsGrid.removeAll();
        loadAllItems();
        vaultContent.add(itemsGrid);

        currentView.add(vaultContent);
    }

    private String fetchWebpageContent(String url) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (TimeVault/1.0)")
                .timeout(java.time.Duration.ofSeconds(15))
                .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Extract text content from HTML
                String content = response.body();
                // Remove HTML tags for cleaner analysis
                content = content.replaceAll("<script[^>]*>.*?</script>", "")
                                .replaceAll("<style[^>]*>.*?</style>", "")
                                .replaceAll("<[^>]+>", " ")
                                .replaceAll("\\s+", " ")
                                .trim();

                // Limit content size
                return content.length() > 5000 ? content.substring(0, 5000) + "..." : content;
            } else {
                return "Unable to fetch content from: " + url;
            }
        } catch (Exception e) {
            return "Error fetching content: " + e.getMessage() + " URL: " + url;
        }
    }

    private Component createHeroSection() {
        VerticalLayout hero = new VerticalLayout();
        hero.addClassName("hero-section");
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        H2 heroTitle = new H2("Capture your digital mind.");
        heroTitle.addClassName("hero-title");

        Paragraph heroSubtitle = new Paragraph("Powered by Gemini 2.0. Smart auto-tagging, vision analysis, and contextual memory.");
        heroSubtitle.addClassName("hero-subtitle");

        hero.add(heroTitle, heroSubtitle);
        return hero;
    }

    private Component createContentSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("modern-card");
        section.setWidthFull();
        section.setAlignItems(FlexComponent.Alignment.CENTER);

        Tab urlTab = new Tab(VaadinIcon.LINK.create(), new Span("Web URL"));
        Tab imageTab = new Tab(VaadinIcon.MAGIC.create(), new Span("Vision AI"));
        Tab textTab = new Tab(VaadinIcon.TEXT_LABEL.create(), new Span("Smart Note"));

        Tabs tabs = new Tabs(urlTab, imageTab, textTab);
        tabs.addThemeVariants(TabsVariant.LUMO_EQUAL_WIDTH_TABS, TabsVariant.LUMO_CENTERED);
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
        panel.setAlignItems(FlexComponent.Alignment.CENTER);
        panel.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);

        TextField urlField = new TextField();
        urlField.setPlaceholder("Paste any link (e.g. https://github.com/...)");
        urlField.setPrefixComponent(VaadinIcon.LINK.create());
        urlField.setWidthFull();
        urlField.addClassName("modern-input");

        Button saveButton = new Button("Extract & Save to Vault", VaadinIcon.MAGIC.create());
        saveButton.addClassName("modern-button");
        saveButton.setWidthFull();

        saveButton.addClickListener(e -> {
            if (urlField.getValue().trim().isEmpty()) {
                showNeonNotification("Please enter a valid URL", false);
            } else {
                try {
                    String url = urlField.getValue().trim();
                    // Add protocol if missing
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url;
                    }

                    showNeonNotification("Fetching webpage content...", true);

                    // Fetch webpage content for better analysis
                    String pageContent = fetchWebpageContent(url);

                    // Save with AI-generated title and context
                    vaultItemService.saveUrl(url, pageContent);
                    showNeonNotification("Webpage saved & analyzed by AI!", true);
                    urlField.clear();
                    loadRecentItems();
                } catch (Exception ex) {
                    showNeonNotification("Error: " + ex.getMessage(), false);
                }
            }
        });

        panel.add(new Paragraph("AI will automatically fetch the webpage, read the content, and generate a contextual summary."), urlField, saveButton);
        return panel;
    }

    private VerticalLayout createImagePanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.addClassName("input-panel");
        panel.setAlignItems(FlexComponent.Alignment.CENTER);
        panel.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);

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
                showNeonNotification("Image processed by Gemini Vision!", true);
                loadRecentItems();
            } catch (Exception ex) {
                showNeonNotification("Error: " + ex.getMessage(), false);
            }
        });

        panel.add(new Paragraph("Upload an image. The AI Vision model will deeply analyze its contents and context."), upload);
        return panel;
    }

    private VerticalLayout createTextPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.addClassName("input-panel");
        panel.setAlignItems(FlexComponent.Alignment.CENTER);
        panel.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);

        TextArea textArea = new TextArea();
        textArea.setPlaceholder("Dump your thoughts, meeting notes, or raw text here...");
        textArea.setWidthFull();
        textArea.setMinHeight("150px");
        textArea.addClassName("modern-input");

        Button saveButton = new Button("Synthesize Note", VaadinIcon.MAGIC.create());
        saveButton.addClassName("modern-button");
        saveButton.setWidthFull();

        saveButton.addClickListener(e -> {
            if (textArea.getValue().trim().isEmpty()) {
                showNeonNotification("Text area is empty", false);
            } else {
                try {
                    String content = textArea.getValue().trim();
                    vaultItemService.saveText("Note: " + content.substring(0, Math.min(content.length(), 25)), content);
                    showNeonNotification("Note synthesized & saved!", true);
                    textArea.clear();
                    loadRecentItems();
                } catch (Exception ex) {
                    showNeonNotification("Error: " + ex.getMessage(), false);
                }
            }
        });

        panel.add(new Paragraph("AI will extract key points, generate tags, and create a permanent memory of your text."), textArea, saveButton);
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
        sectionTitle.getStyle().set("color", "white");
        sectionTitle.getStyle().set("font-weight", "800");
        sectionTitle.getStyle().set("margin", "0");

        Button refreshBtn = new Button("Sync", VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshBtn.getStyle().set("color", "#A78BFA");
        refreshBtn.addClickListener(e -> loadRecentItems());

        sectionHeader.add(sectionTitle, refreshBtn);

        loadRecentItems();

        section.add(sectionHeader, itemsGrid);
        return section;
    }

    private Component createRecentItemsPreview() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(false);
        section.setMaxWidth("1000px");
        section.setWidthFull();

        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Recent Memories");
        title.addClassName("section-title");
        title.getStyle().set("color", "white");
        title.getStyle().set("font-weight", "800");
        title.getStyle().set("margin", "0");

        Button viewAllButton = new Button("View All", VaadinIcon.ARROW_RIGHT.create());
        viewAllButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        viewAllButton.getStyle().set("color", "#A78BFA");
        viewAllButton.addClickListener(e -> showVaultView());

        headerLayout.add(title, viewAllButton);
        section.add(headerLayout);

        // Create preview grid for 3 recent items
        Div previewGrid = new Div();
        previewGrid.addClassName("vault-grid-preview");

        try {
            List<VaultItem> recentItems = vaultItemService.findTop3Recent();
            if (recentItems.isEmpty()) {
                Div emptyState = new Div();
                emptyState.addClassName("empty-state-preview");
                emptyState.add(new H4("No memories yet..."));
                emptyState.add(new Paragraph("Start saving URLs, images, and notes above to build your digital vault."));
                previewGrid.add(emptyState);
            } else {
                for (VaultItem item : recentItems) {
                    previewGrid.add(createVaultItemCard(item));
                }
            }
        } catch (Exception e) {
            showNeonNotification("Error loading recent items: " + e.getMessage(), false);
        }

        section.add(previewGrid);
        return section;
    }

    private void loadAllItems() {
        try {
            updateItemsList(vaultItemService.findAll());
        } catch (Exception e) {
            showNeonNotification("Error loading items: " + e.getMessage(), false);
        }
    }

    private void loadRecentItems() {
        if (currentViewState.equals("home")) {
            // Refresh the home view
            showHomeView();
        } else {
            // Refresh the vault view
            try {
                updateItemsList(vaultItemService.findRecent());
            } catch (Exception e) {
                showNeonNotification("Error loading items: " + e.getMessage(), false);
            }
        }
    }

    private void updateItemsList(List<VaultItem> items) {
        itemsGrid.removeAll();

        if (items.isEmpty()) {
            Div emptyState = new Div();
            emptyState.addClassName("empty-state");
            Icon cube = VaadinIcon.CUBE.create();
            cube.setSize("48px");
            cube.setColor("#64748b");
            emptyState.add(cube);
            emptyState.add(new H3("The vault is empty."));
            emptyState.add(new Paragraph("Feed the AI your first link, image, or text snippet above."));
            itemsGrid.add(emptyState);
        } else {
            // Apply staggered animation delay to cards
            int delay = 0;
            for (VaultItem item : items) {
                Component card = createVaultItemCard(item);
                card.getElement().getStyle().set("animation-delay", delay + "ms");
                itemsGrid.add(card);
                delay += 100; // 100ms delay for cascade effect
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

        String type = vaultItem.getItemType() != null ? vaultItem.getItemType().toUpperCase() : "UNKNOWN";
        String iconColor = switch (type) {
            case "URL" -> "#60A5FA"; // Blue
            case "IMAGE" -> "#F472B6"; // Pink
            case "TEXT" -> "#34D399"; // Emerald
            default -> "#94A3B8"; // Slate
        };

        Span typeBadge = new Span(type);
        typeBadge.addClassName("type-badge");
        typeBadge.getStyle().set("background-color", iconColor + "20");
        typeBadge.getStyle().set("color", iconColor);
        typeBadge.getStyle().set("border", "1px solid " + iconColor + "50");

        Button deleteBtn = new Button(VaadinIcon.TRASH.create());
        deleteBtn.addClassName("delete-btn");
        deleteBtn.addClickListener(e -> {
            vaultItemService.delete(vaultItem.getId());
            loadRecentItems();
            showNeonNotification("Memory erased.", true);
        });

        cardHeader.add(typeBadge, deleteBtn);

        String safeTitle = vaultItem.getTitle() != null && !vaultItem.getTitle().trim().isEmpty()
                ? vaultItem.getTitle() : "Untitled Memory";
        H4 title = new H4(safeTitle);
        title.addClassName("card-title");

        // AI Context Box
        Div contextWrapper = new Div();
        contextWrapper.addClassName("card-context-wrapper");

        Span aiLabel = new Span("✨ AI Summary");
        aiLabel.addClassName("ai-label");

        String safeContext = vaultItem.getAiContext() != null && !vaultItem.getAiContext().trim().isEmpty()
                ? vaultItem.getAiContext() : "Awaiting AI analysis...";
        Paragraph context = new Paragraph(safeContext);
        context.addClassName("card-context");

        contextWrapper.add(aiLabel, context);

        HorizontalLayout cardFooter = new HorizontalLayout();
        cardFooter.addClassName("card-footer");
        cardFooter.setWidthFull();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        String dateString = vaultItem.getCreatedAt() != null ? vaultItem.getCreatedAt().format(formatter) : "Unknown Date";
        Span dateStr = new Span(dateString);
        dateStr.addClassName("card-date");

        cardFooter.add(dateStr);

        card.add(cardHeader, title, contextWrapper, cardFooter);
        return card;
    }

    private void showNeonNotification(String text, boolean success) {
        Notification notification = new Notification(text, 3000, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(success ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
        notification.open();
    }
}