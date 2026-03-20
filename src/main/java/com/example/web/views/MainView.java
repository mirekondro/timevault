package com.example.web.views;

import com.example.shared.model.VaultItem;
import com.example.shared.service.VaultItemService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
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

        // Force white text styling
        getStyle().set("color", "#ffffff");

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

        // Force white text on main layout
        main.getStyle().set("color", "#ffffff");

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

        // Enhanced Search Bar Container
        Div searchContainer = new Div();
        searchContainer.getStyle().set("position", "relative");
        searchContainer.getStyle().set("max-width", "450px");
        searchContainer.getStyle().set("width", "100%");

        // Search Field
        TextField searchField = new TextField();
        searchField.setPlaceholder("🔍 Search your digital memories...");
        searchField.addClassName("header-search");
        searchField.setWidthFull();
        searchField.setClearButtonVisible(true);

        // Search Button Group
        Div buttonGroup = new Div();
        buttonGroup.addClassName("search-button-group");

        // Voice Search Button
        Button voiceBtn = new Button(VaadinIcon.MICROPHONE.create());
        voiceBtn.addClassName("voice-search-btn");
        voiceBtn.setTooltipText("Voice Search");

        // Search Execute Button
        Button searchBtn = new Button(VaadinIcon.SEARCH.create());
        searchBtn.addClassName("search-execute-btn");
        searchBtn.setTooltipText("Search");

        buttonGroup.add(voiceBtn, searchBtn);

        // Voice Status Indicator
        Div voiceStatus = new Div();
        voiceStatus.addClassName("voice-status");
        voiceStatus.setText("🎤 Listening...");

        // Search Results Dropdown
        Div resultsDropdown = new Div();
        resultsDropdown.addClassName("search-results-dropdown");

        // Search Loading Indicator
        Div loadingIndicator = new Div();
        loadingIndicator.addClassName("search-loading");

        searchContainer.add(searchField, buttonGroup, voiceStatus, resultsDropdown, loadingIndicator);

        // Enhanced search functionality
        searchField.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().trim().isEmpty()) {
                updateItemsList(vaultItemService.searchComprehensive(e.getValue().trim()));
            } else {
                loadRecentItems();
            }
        });

        // Add enhanced JavaScript for voice search and advanced functionality
        searchContainer.getElement().executeJs("""
            // Enhanced Search Functionality
            const searchField = this.querySelector('.header-search input');
            const voiceBtn = this.querySelector('.voice-search-btn');
            const searchBtn = this.querySelector('.search-execute-btn');
            const voiceStatus = this.querySelector('.voice-status');
            const resultsDropdown = this.querySelector('.search-results-dropdown');
            const loadingIndicator = this.querySelector('.search-loading');
            
            let recognition = null;
            let searchTimeout = null;
            
            // Initialize Speech Recognition
            if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
                const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
                recognition = new SpeechRecognition();
                recognition.continuous = false;
                recognition.interimResults = false;
                recognition.lang = 'en-US';
                
                recognition.onstart = () => {
                    voiceBtn.classList.add('listening');
                    voiceStatus.classList.add('show');
                };
                
                recognition.onend = () => {
                    voiceBtn.classList.remove('listening');
                    voiceStatus.classList.remove('show');
                };
                
                recognition.onresult = (event) => {
                    const transcript = event.results[0][0].transcript;
                    searchField.value = transcript;
                    searchField.dispatchEvent(new Event('input', { bubbles: true }));
                    performSearch(transcript);
                };
                
                recognition.onerror = (event) => {
                    voiceBtn.classList.remove('listening');
                    voiceStatus.classList.remove('show');
                    console.error('Speech recognition error:', event.error);
                };
            } else {
                voiceBtn.style.display = 'none';
            }
            
            // Voice Search Button Click
            voiceBtn.addEventListener('click', () => {
                if (recognition) {
                    try {
                        recognition.start();
                    } catch (error) {
                        console.error('Voice recognition failed:', error);
                        showNotification('🎤 Voice search not available', 'error');
                    }
                }
            });
            
            // Search Button Click
            searchBtn.addEventListener('click', () => {
                performSearch(searchField.value);
            });
            
            // Search Field Input (with debounce for suggestions)
            searchField.addEventListener('input', (e) => {
                const query = e.target.value.trim();
                
                clearTimeout(searchTimeout);
                
                if (query.length >= 2) {
                    searchTimeout = setTimeout(() => {
                        fetchSuggestions(query);
                    }, 300);
                } else {
                    resultsDropdown.classList.remove('show');
                }
            });
            
            // Search on Enter
            searchField.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    performSearch(searchField.value);
                }
            });
            
            // Fetch Search Suggestions
            async function fetchSuggestions(query) {
                try {
                    loadingIndicator.classList.add('show');
                    
                    const response = await fetch(`/api/search/suggestions?q=${encodeURIComponent(query)}`);
                    const data = await response.json();
                    
                    loadingIndicator.classList.remove('show');
                    
                    if (data.success && data.suggestions.length > 0) {
                        displaySuggestions(data.suggestions);
                    } else {
                        resultsDropdown.classList.remove('show');
                    }
                } catch (error) {
                    loadingIndicator.classList.remove('show');
                    console.error('Suggestions error:', error);
                }
            }
            
            // Display Search Suggestions
            function displaySuggestions(suggestions) {
                resultsDropdown.innerHTML = '';
                
                suggestions.forEach(suggestion => {
                    const item = document.createElement('div');
                    item.className = 'search-result-item';
                    item.innerHTML = `
                        <div class="search-result-title">${escapeHtml(suggestion.title)}</div>
                        <div class="search-result-snippet">${escapeHtml(suggestion.snippet)}</div>
                    `;
                    
                    item.addEventListener('click', () => {
                        searchField.value = suggestion.title;
                        resultsDropdown.classList.remove('show');
                        performSearch(suggestion.title);
                    });
                    
                    resultsDropdown.appendChild(item);
                });
                
                resultsDropdown.classList.add('show');
            }
            
            // Perform Search
            function performSearch(query) {
                if (!query.trim()) return;
                
                resultsDropdown.classList.remove('show');
                showNotification(`🔍 Searching: "${query}"`, 'info');
                
                // Trigger Vaadin search
                searchField.dispatchEvent(new Event('change', { bubbles: true }));
            }
            
            // Show Notification
            function showNotification(message, type = 'info') {
                const notification = document.createElement('div');
                const bgColor = type === 'error' ? 
                    'linear-gradient(135deg, #EF4444 0%, #DC2626 50%, #B91C1C 100%)' :
                    'linear-gradient(135deg, #60A5FA 0%, #A78BFA 50%, #F472B6 100%)';
                    
                notification.style.cssText = `
                    position: fixed;
                    top: 20px;
                    right: 20px;
                    background: ${bgColor};
                    color: white;
                    padding: 15px 20px;
                    border-radius: 12px;
                    z-index: 10000;
                    font-family: 'Plus Jakarta Sans', sans-serif;
                    font-weight: 600;
                    box-shadow: 0 10px 25px rgba(0,0,0,0.3);
                `;
                notification.textContent = message;
                document.body.appendChild(notification);
                
                setTimeout(() => document.body.removeChild(notification), 3000);
            }
            
            // Utility function to escape HTML
            function escapeHtml(text) {
                const div = document.createElement('div');
                div.textContent = text || '';
                return div.innerHTML;
            }
            
            // Hide dropdown when clicking outside
            document.addEventListener('click', (e) => {
                if (!this.contains(e.target)) {
                    resultsDropdown.classList.remove('show');
                }
            });
            """);

        header.add(logoArea, searchContainer);
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
        vaultContent.setAlignItems(Alignment.CENTER);

        // Add vault header
        H2 vaultTitle = new H2("Your Digital Vault");
        vaultTitle.addClassName("section-title");
        vaultTitle.getStyle().set("color", "#ffffff");
        vaultTitle.getStyle().set("font-weight", "800");
        vaultTitle.getStyle().set("margin-bottom", "2rem");
        vaultContent.add(vaultTitle);

        // Simple Search Container with Voice Search
        Div searchContainer = new Div();
        searchContainer.getStyle().set("position", "relative");
        searchContainer.getStyle().set("max-width", "600px");
        searchContainer.getStyle().set("width", "100%");
        searchContainer.getStyle().set("margin-bottom", "2rem");

        // Search Field - same as before but with voice
        TextField vaultSearch = new TextField();
        vaultSearch.setPlaceholder("🔍 Search your vault...");
        vaultSearch.setPrefixComponent(VaadinIcon.SEARCH.create());
        vaultSearch.addClassName("vault-search");
        vaultSearch.setWidthFull();
        vaultSearch.setClearButtonVisible(true);

        // Simple Search Button Group (just voice + search)
        Div buttonGroup = new Div();
        buttonGroup.addClassName("search-button-group");

        // Voice Search Button
        Button voiceBtn = new Button(VaadinIcon.MICROPHONE.create());
        voiceBtn.addClassName("voice-search-btn");
        voiceBtn.setTooltipText("Voice Search");

        buttonGroup.add(voiceBtn);

        // Voice Status Indicator
        Div voiceStatus = new Div();
        voiceStatus.addClassName("voice-status");
        voiceStatus.setText("🎤 Listening...");

        searchContainer.add(vaultSearch, buttonGroup, voiceStatus);

        // Keep original search functionality
        vaultSearch.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().trim().isEmpty()) {
                updateItemsList(vaultItemService.searchComprehensive(e.getValue().trim()));
            } else {
                loadAllItems();
            }
        });

        // Add simple voice search JavaScript (same as landing page)
        searchContainer.getElement().executeJs("""
            const searchField = this.querySelector('.vault-search input');
            const voiceBtn = this.querySelector('.voice-search-btn');
            const voiceStatus = this.querySelector('.voice-status');
            
            let recognition = null;
            
            // Initialize Speech Recognition (same as landing page)
            if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
                const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
                recognition = new SpeechRecognition();
                recognition.continuous = false;
                recognition.interimResults = false;
                recognition.lang = 'en-US';
                
                recognition.onstart = () => {
                    voiceBtn.classList.add('listening');
                    voiceStatus.classList.add('show');
                };
                
                recognition.onend = () => {
                    voiceBtn.classList.remove('listening');
                    voiceStatus.classList.remove('show');
                };
                
                recognition.onresult = (event) => {
                    const transcript = event.results[0][0].transcript;
                    searchField.value = transcript;
                    searchField.dispatchEvent(new Event('input', { bubbles: true }));
                    searchField.dispatchEvent(new Event('change', { bubbles: true }));
                };
                
                recognition.onerror = (event) => {
                    voiceBtn.classList.remove('listening');
                    voiceStatus.classList.remove('show');
                    console.error('Voice recognition error:', event.error);
                };
            } else {
                voiceBtn.style.display = 'none';
            }
            
            // Voice Search Button Click
            voiceBtn.addEventListener('click', () => {
                if (recognition) {
                    try {
                        recognition.start();
                    } catch (error) {
                        console.error('Voice recognition failed:', error);
                    }
                }
            });
            """);

        vaultContent.add(searchContainer);

        // Add all items grid (same as before)
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

        // Only force white text color, let CSS handle background
        panel.getStyle().set("color", "#ffffff");

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

        Paragraph description = new Paragraph("AI will automatically fetch the webpage, read the content, and generate a contextual summary.");
        description.getStyle().set("color", "#ffffff");

        panel.add(description, urlField, saveButton);

        // Force white text on all components
        panel.getChildren().forEach(component -> {
            component.getElement().getStyle().set("color", "#ffffff");
        });

        return panel;
    }

    private VerticalLayout createImagePanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.addClassName("input-panel");
        panel.setAlignItems(FlexComponent.Alignment.CENTER);
        panel.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);

        // Only force white text color, let CSS handle background
        panel.getStyle().set("color", "#ffffff");

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

        Paragraph description = new Paragraph("Upload an image. The AI Vision model will deeply analyze its contents and context.");
        description.getStyle().set("color", "#ffffff");

        panel.add(description, upload);

        // Force white text on all components
        panel.getChildren().forEach(component -> {
            component.getElement().getStyle().set("color", "#ffffff");
        });

        return panel;
    }

    private VerticalLayout createTextPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.addClassName("input-panel");
        panel.setAlignItems(FlexComponent.Alignment.CENTER);
        panel.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);

        // Only force white text color, let CSS handle background
        panel.getStyle().set("color", "#ffffff");

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

        Paragraph description = new Paragraph("AI will extract key points, generate tags, and create a permanent memory of your text.");
        description.getStyle().set("color", "#ffffff");

        panel.add(description, textArea, saveButton);

        // Force white text on all components
        panel.getChildren().forEach(component -> {
            component.getElement().getStyle().set("color", "#ffffff");
        });

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
        cardFooter.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        cardFooter.setAlignItems(FlexComponent.Alignment.CENTER);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        String dateString = vaultItem.getCreatedAt() != null ? vaultItem.getCreatedAt().format(formatter) : "Unknown Date";
        Span dateStr = new Span(dateString);
        dateStr.addClassName("card-date");

        // Speak button for text-to-speech
        Button speakBtn = new Button(VaadinIcon.VOLUME_UP.create());
        speakBtn.addClassName("speak-btn");
        speakBtn.setTooltipText("Listen to AI summary");
        speakBtn.getStyle().set("color", "#A78BFA");
        speakBtn.getStyle().set("background", "transparent");
        speakBtn.getStyle().set("border", "none");
        speakBtn.getStyle().set("cursor", "pointer");
        speakBtn.getStyle().set("padding", "8px");
        speakBtn.getStyle().set("border-radius", "50%");
        speakBtn.getStyle().set("transition", "all 0.3s ease");

        // Add hover effect
        speakBtn.addClickListener(e -> {
            speakText(safeContext);
        });

        // Button group for actions
        HorizontalLayout buttonGroup = new HorizontalLayout(speakBtn);
        buttonGroup.setSpacing(false);
        buttonGroup.setAlignItems(FlexComponent.Alignment.CENTER);

        cardFooter.add(dateStr, buttonGroup);

        card.add(cardHeader, title, contextWrapper, cardFooter);

        // Make card clickable
        card.getStyle().set("cursor", "pointer");
        card.addClickListener(e -> openCardModal(vaultItem));

        return card;
    }

    /**
     * Open a modal dialog to view full vault item details
     */
    private void openCardModal(VaultItem item) {
        // Create modal dialog
        Dialog modal = new Dialog();
        modal.setModal(true);
        modal.setDraggable(false);
        modal.setResizable(false);
        modal.setWidth("800px");
        modal.setMaxWidth("90vw");
        modal.setHeight("600px");
        modal.setMaxHeight("90vh");

        // Add modal styling
        modal.addClassName("vault-modal");

        // Force dark theme on modal
        modal.getElement().getStyle().set("background", "var(--glass-bg)");
        modal.getElement().getStyle().set("backdrop-filter", "blur(20px)");
        modal.getElement().getStyle().set("border", "1px solid var(--glass-border)");
        modal.getElement().getStyle().set("border-radius", "var(--radius-xl)");
        modal.getElement().getStyle().set("color", "#ffffff");
        modal.getElement().getStyle().set("--lumo-base-color", "var(--bg-deep)");
        modal.getElement().getStyle().set("--lumo-body-text-color", "#ffffff");
        modal.getElement().getStyle().set("--lumo-contrast", "#ffffff");

        // Create modal content
        VerticalLayout modalContent = new VerticalLayout();
        modalContent.setPadding(false);
        modalContent.setSpacing(false);
        modalContent.setSizeFull();

        // Modal Header
        HorizontalLayout modalHeader = new HorizontalLayout();
        modalHeader.setWidthFull();
        modalHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        modalHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        modalHeader.getStyle().set("padding", "24px 24px 16px 24px");
        modalHeader.getStyle().set("border-bottom", "1px solid rgba(255,255,255,0.1)");

        // Item type badge
        Span typeBadge = new Span(getTypeIcon(item.getItemType()) + " " + item.getItemType());
        typeBadge.getStyle().set("background", getTypeColor(item.getItemType()));
        typeBadge.getStyle().set("color", "white");
        typeBadge.getStyle().set("padding", "6px 12px");
        typeBadge.getStyle().set("border-radius", "20px");
        typeBadge.getStyle().set("font-size", "12px");
        typeBadge.getStyle().set("font-weight", "600");

        // Close button
        Button closeBtn = new Button(VaadinIcon.CLOSE.create());
        closeBtn.addClassName("modal-close-btn");
        closeBtn.getStyle().set("background", "rgba(255,255,255,0.1)");
        closeBtn.getStyle().set("border", "none");
        closeBtn.getStyle().set("color", "#ffffff");
        closeBtn.getStyle().set("border-radius", "50%");
        closeBtn.getStyle().set("width", "40px");
        closeBtn.getStyle().set("height", "40px");
        closeBtn.getStyle().set("cursor", "pointer");
        closeBtn.addClickListener(e -> modal.close());

        modalHeader.add(typeBadge, closeBtn);

        // Modal Body
        VerticalLayout modalBody = new VerticalLayout();
        modalBody.setPadding(false);
        modalBody.setSpacing(false);
        modalBody.getStyle().set("padding", "0 24px");
        modalBody.getStyle().set("overflow-y", "auto");
        modalBody.getStyle().set("flex", "1");

        // Title
        H2 modalTitle = new H2(item.getTitle() != null ? item.getTitle() : "Untitled");
        modalTitle.getStyle().set("color", "#ffffff");
        modalTitle.getStyle().set("margin", "16px 0");
        modalTitle.getStyle().set("font-weight", "700");

        // Metadata
        HorizontalLayout metadata = new HorizontalLayout();
        metadata.setSpacing(true);
        metadata.setAlignItems(FlexComponent.Alignment.CENTER);
        metadata.getStyle().set("margin-bottom", "20px");

        if (item.getCreatedAt() != null) {
            Span dateSpan = new Span("📅 " + item.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
            dateSpan.getStyle().set("color", "#CBD5E1");
            dateSpan.getStyle().set("font-size", "14px");
            metadata.add(dateSpan);
        }

        if (item.getTags() != null && !item.getTags().trim().isEmpty()) {
            Span tagsSpan = new Span("🏷️ " + item.getTags());
            tagsSpan.getStyle().set("color", "#A78BFA");
            tagsSpan.getStyle().set("font-size", "14px");
            metadata.add(tagsSpan);
        }

        // Source URL (if available)
        if (item.getSourceUrl() != null && !item.getSourceUrl().trim().isEmpty()) {
            Anchor urlLink = new Anchor(item.getSourceUrl(), "🔗 Open Original Source");
            urlLink.setTarget("_blank");
            urlLink.getStyle().set("color", "#60A5FA");
            urlLink.getStyle().set("text-decoration", "none");
            urlLink.getStyle().set("font-size", "14px");
            urlLink.getStyle().set("margin-top", "8px");
            metadata.add(urlLink);
        }

        // AI Context Section
        if (item.getAiContext() != null && !item.getAiContext().trim().isEmpty()) {
            Div aiSection = new Div();
            aiSection.getStyle().set("background", "rgba(167, 139, 250, 0.1)");
            aiSection.getStyle().set("border", "1px solid rgba(167, 139, 250, 0.2)");
            aiSection.getStyle().set("border-radius", "12px");
            aiSection.getStyle().set("padding", "16px");
            aiSection.getStyle().set("margin", "20px 0");

            H4 aiTitle = new H4("🤖 AI Summary");
            aiTitle.getStyle().set("color", "#A78BFA");
            aiTitle.getStyle().set("margin", "0 0 12px 0");

            Paragraph aiContent = new Paragraph(item.getAiContext());
            aiContent.getStyle().set("color", "#E2E8F0");
            aiContent.getStyle().set("margin", "0");
            aiContent.getStyle().set("line-height", "1.6");

            // Speak button for AI summary
            Button speakAiBtn = new Button("Listen to AI Summary");
            speakAiBtn.setIcon(VaadinIcon.VOLUME_UP.create());
            speakAiBtn.getStyle().set("background", "rgba(167, 139, 250, 0.2)");
            speakAiBtn.getStyle().set("border", "1px solid rgba(167, 139, 250, 0.3)");
            speakAiBtn.getStyle().set("color", "#A78BFA");
            speakAiBtn.getStyle().set("border-radius", "20px");
            speakAiBtn.getStyle().set("margin-top", "12px");
            speakAiBtn.getStyle().set("font-size", "12px");
            speakAiBtn.addClickListener(e -> speakText(item.getAiContext()));

            aiSection.add(aiTitle, aiContent, speakAiBtn);
            modalBody.add(aiSection);
        }

        // Content Section
        if (item.getContent() != null && !item.getContent().trim().isEmpty()) {
            Div contentSection = new Div();
            contentSection.getStyle().set("background", "rgba(255, 255, 255, 0.05)");
            contentSection.getStyle().set("border", "1px solid rgba(255, 255, 255, 0.1)");
            contentSection.getStyle().set("border-radius", "12px");
            contentSection.getStyle().set("padding", "16px");
            contentSection.getStyle().set("margin", "20px 0");

            H4 contentTitle = new H4("📄 Full Content");
            contentTitle.getStyle().set("color", "#ffffff");
            contentTitle.getStyle().set("margin", "0 0 12px 0");

            // Content area with scroll
            Div contentArea = new Div();
            contentArea.getStyle().set("max-height", "200px");
            contentArea.getStyle().set("overflow-y", "auto");
            contentArea.getStyle().set("background", "rgba(0, 0, 0, 0.2)");
            contentArea.getStyle().set("border-radius", "8px");
            contentArea.getStyle().set("padding", "12px");

            Paragraph fullContent = new Paragraph(item.getContent());
            fullContent.getStyle().set("color", "#E2E8F0");
            fullContent.getStyle().set("margin", "0");
            fullContent.getStyle().set("line-height", "1.6");
            fullContent.getStyle().set("white-space", "pre-wrap");
            fullContent.getStyle().set("font-family", "'SF Mono', 'Monaco', 'Cascadia Code', monospace");
            fullContent.getStyle().set("font-size", "13px");

            contentArea.add(fullContent);
            contentSection.add(contentTitle, contentArea);
            modalBody.add(contentSection);
        }

        modalBody.add(modalTitle, metadata);

        // Modal Footer with actions
        HorizontalLayout modalFooter = new HorizontalLayout();
        modalFooter.setWidthFull();
        modalFooter.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        modalFooter.getStyle().set("padding", "16px 24px 24px 24px");
        modalFooter.getStyle().set("border-top", "1px solid rgba(255,255,255,0.1)");

        Button deleteBtn = new Button("Delete", VaadinIcon.TRASH.create());
        deleteBtn.getStyle().set("background", "rgba(239, 68, 68, 0.2)");
        deleteBtn.getStyle().set("border", "1px solid rgba(239, 68, 68, 0.3)");
        deleteBtn.getStyle().set("color", "#EF4444");
        deleteBtn.getStyle().set("border-radius", "8px");
        deleteBtn.addClickListener(e -> {
            try {
                vaultItemService.delete(item.getId());
                modal.close();
                showNeonNotification("Memory deleted", true);
                if ("vault".equals(currentViewState)) {
                    loadAllItems();
                } else {
                    loadRecentItems();
                }
            } catch (Exception ex) {
                showNeonNotification("Error: " + ex.getMessage(), false);
            }
        });

        modalFooter.add(deleteBtn);

        modalContent.add(modalHeader, modalBody, modalFooter);
        modal.add(modalContent);

        // Add keyboard support (ESC to close) and FORCE dark theme
        modal.addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                modal.getElement().executeJs("""
                    const modal = this;
                    
                    // AGGRESSIVE dark theme enforcement
                    setTimeout(() => {
                        const overlay = document.querySelector('vaadin-dialog-overlay');
                        if (overlay) {
                            // Force overlay background
                            overlay.style.setProperty('background', 'rgba(5, 5, 10, 0.8)', 'important');
                            overlay.style.setProperty('background-color', 'rgba(5, 5, 10, 0.8)', 'important');
                            overlay.style.setProperty('backdrop-filter', 'blur(8px)', 'important');
                            
                            // Access shadow DOM
                            const shadowRoot = overlay.shadowRoot;
                            if (shadowRoot) {
                                // Force content part to be dark
                                const content = shadowRoot.querySelector('[part="content"]');
                                if (content) {
                                    content.style.setProperty('background', 'rgba(20, 20, 30, 0.8)', 'important');
                                    content.style.setProperty('background-color', 'rgba(20, 20, 30, 0.8)', 'important');
                                    content.style.setProperty('backdrop-filter', 'blur(20px)', 'important');
                                    content.style.setProperty('border', '1px solid rgba(255, 255, 255, 0.08)', 'important');
                                    content.style.setProperty('border-radius', '24px', 'important');
                                    content.style.setProperty('box-shadow', '0 20px 40px -10px rgba(0,0,0,0.5), 0 0 20px rgba(99, 102, 241, 0.15)', 'important');
                                    content.style.setProperty('color', '#ffffff', 'important');
                                    content.style.setProperty('padding', '0', 'important');
                                }
                                
                                // Force overlay part to be dark
                                const overlayPart = shadowRoot.querySelector('[part="overlay"]');
                                if (overlayPart) {
                                    overlayPart.style.setProperty('background', 'rgba(5, 5, 10, 0.8)', 'important');
                                    overlayPart.style.setProperty('background-color', 'rgba(5, 5, 10, 0.8)', 'important');
                                    overlayPart.style.setProperty('backdrop-filter', 'blur(8px)', 'important');
                                }
                                
                                // Force ALL elements inside to be transparent/white text
                                const allElements = content.querySelectorAll('*');
                                allElements.forEach(el => {
                                    if (!el.classList.contains('modern-button') && 
                                        !el.classList.contains('modal-speak-btn') && 
                                        !el.classList.contains('modal-delete-btn') &&
                                        !el.classList.contains('modal-close-btn')) {
                                        if (el.tagName === 'VAADIN-VERTICAL-LAYOUT' || 
                                            el.tagName === 'VAADIN-HORIZONTAL-LAYOUT' ||
                                            el.tagName === 'DIV') {
                                            el.style.setProperty('background', 'transparent', 'important');
                                            el.style.setProperty('background-color', 'transparent', 'important');
                                        }
                                        el.style.setProperty('color', '#ffffff', 'important');
                                    }
                                });
                            }
                        }
                    }, 50);
                    
                    // Keyboard support
                    const handleKeydown = (event) => {
                        if (event.key === 'Escape') {
                            modal.close();
                            document.removeEventListener('keydown', handleKeydown);
                        }
                    };
                    document.addEventListener('keydown', handleKeydown);
                    """);
            }
        });

        modal.open();
    }

    /**
     * Use ElevenLabs to speak the AI summary text
     */
    private void speakText(String text) {
        if (text == null || text.trim().isEmpty() || "Awaiting AI analysis...".equals(text)) {
            showNeonNotification("No AI summary available to read", false);
            return;
        }

        // Add JavaScript to call the speech API and play audio
        getElement().executeJs("""
            async function speakText(text) {
                try {
                    // Show loading notification
                    const notification = document.createElement('div');
                    notification.style.cssText = `
                        position: fixed;
                        top: 20px;
                        right: 20px;
                        background: linear-gradient(135deg, #60A5FA 0%, #A78BFA 50%, #F472B6 100%);
                        color: white;
                        padding: 15px 20px;
                        border-radius: 12px;
                        z-index: 10000;
                        font-family: 'Plus Jakarta Sans', sans-serif;
                        font-weight: 600;
                        box-shadow: 0 10px 25px rgba(0,0,0,0.3);
                    `;
                    notification.textContent = '🗣️ Generating speech...';
                    document.body.appendChild(notification);

                    const response = await fetch('/api/speech/synthesize', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify({ text: text })
                    });

                    const result = await response.json();
                    
                    // Remove loading notification
                    document.body.removeChild(notification);

                    if (result.success) {
                        // Create and play audio
                        const audio = new Audio(result.audio);
                        
                        // Show playing notification
                        const playNotification = document.createElement('div');
                        playNotification.style.cssText = notification.style.cssText;
                        playNotification.textContent = '🔊 Playing AI summary...';
                        document.body.appendChild(playNotification);
                        
                        audio.onended = () => {
                            document.body.removeChild(playNotification);
                        };
                        
                        audio.onerror = () => {
                            document.body.removeChild(playNotification);
                            const errorNotification = document.createElement('div');
                            errorNotification.style.cssText = notification.style.cssText.replace('60A5FA 0%, #A78BFA 50%, #F472B6', 'EF4444 0%, #DC2626 50%, #B91C1C');
                            errorNotification.textContent = '❌ Audio playback failed';
                            document.body.appendChild(errorNotification);
                            setTimeout(() => document.body.removeChild(errorNotification), 3000);
                        };
                        
                        await audio.play();
                    } else {
                        throw new Error(result.error || 'Speech synthesis failed');
                    }
                } catch (error) {
                    console.error('Speech error:', error);
                    
                    // Show error notification
                    const errorNotification = document.createElement('div');
                    errorNotification.style.cssText = `
                        position: fixed;
                        top: 20px;
                        right: 20px;
                        background: linear-gradient(135deg, #EF4444 0%, #DC2626 50%, #B91C1C 100%);
                        color: white;
                        padding: 15px 20px;
                        border-radius: 12px;
                        z-index: 10000;
                        font-family: 'Plus Jakarta Sans', sans-serif;
                        font-weight: 600;
                        box-shadow: 0 10px 25px rgba(0,0,0,0.3);
                    `;
                    errorNotification.textContent = '❌ Speech generation failed: ' + error.message;
                    document.body.appendChild(errorNotification);
                    setTimeout(() => document.body.removeChild(errorNotification), 5000);
                }
            }
            
            speakText($0);
            """, text);
    }

    /**
     * Get type icon for different item types
     */
    private String getTypeIcon(String itemType) {
        if (itemType == null) return "📄";
        return switch (itemType.toUpperCase()) {
            case "URL" -> "📄";
            case "IMAGE" -> "🖼️";
            case "TEXT" -> "📝";
            default -> "📄";
        };
    }

    /**
     * Get type color for different item types
     */
    private String getTypeColor(String itemType) {
        if (itemType == null) return "rgba(96, 165, 250, 0.8)";
        return switch (itemType.toUpperCase()) {
            case "URL" -> "rgba(96, 165, 250, 0.8)"; // Blue
            case "IMAGE" -> "rgba(34, 197, 94, 0.8)"; // Green
            case "TEXT" -> "rgba(251, 146, 60, 0.8)"; // Orange
            default -> "rgba(96, 165, 250, 0.8)";
        };
    }

    private void showNeonNotification(String text, boolean success) {
        Notification notification = new Notification(text, 3000, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(success ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
        notification.open();
    }
}