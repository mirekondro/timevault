// TimeVault Chrome Extension - Popup JavaScript

const TIMEVAULT_API_URL = 'http://localhost:8080/api/vault';

class TimeVaultExtension {
    constructor() {
        this.currentTab = null;
        this.pageData = null;
        this.isLoading = false;

        this.initializeElements();
        this.loadCurrentPage();
        this.setupEventListeners();
    }

    initializeElements() {
        // Get DOM elements
        this.statusIndicator = document.getElementById('statusIndicator');
        this.pageTitle = document.getElementById('pageTitle');
        this.pageUrl = document.getElementById('pageUrl');
        this.pageDescription = document.getElementById('pageDescription');
        this.includeContent = document.getElementById('includeContent');
        this.includeImages = document.getElementById('includeImages');
        this.saveBtn = document.getElementById('saveBtn');
        this.viewVaultBtn = document.getElementById('viewVaultBtn');
        this.progressContainer = document.getElementById('progressContainer');
        this.progressFill = document.getElementById('progressFill');
        this.progressText = document.getElementById('progressText');
        this.successMessage = document.getElementById('successMessage');
        this.errorMessage = document.getElementById('errorMessage');
        this.errorSubtitle = document.getElementById('errorSubtitle');
    }

    async loadCurrentPage() {
        try {
            // Get current active tab
            const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
            this.currentTab = tab;

            if (!tab) {
                throw new Error('No active tab found');
            }

            // Check if we can access this tab
            if (tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://') || tab.url.startsWith('edge://') || tab.url.startsWith('moz-extension://')) {
                throw new Error('Cannot access browser internal pages');
            }

            // Extract page data using improved method
            this.pageData = {
                title: tab.title || 'Untitled Page',
                url: tab.url,
                content: '', // Will be filled by content extraction
                description: '',
                timestamp: new Date().toISOString()
            };

            // Try to extract content from the page
            try {
                const results = await chrome.scripting.executeScript({
                    target: { tabId: tab.id },
                    func: this.extractPageContent
                });

                if (results && results[0] && results[0].result) {
                    // Merge extracted data with basic page data
                    this.pageData = { ...this.pageData, ...results[0].result };
                }
            } catch (extractError) {
                console.log('Content extraction failed, using basic page data:', extractError);
                // Continue with basic page data
            }

            this.updatePagePreview();
            this.checkTimeVaultConnection();

        } catch (error) {
            console.error('Error loading page:', error);
            this.showError('Failed to load page', error.message);
        }
    }

    // This function runs in the context of the webpage
    extractPageContent() {
        // Get page title
        const title = document.title || 'Untitled Page';

        // Get page URL
        const url = window.location.href;

        // Extract main content with better strategies
        let content = '';
        let description = '';

        // Strategy 1: Try semantic HTML5 elements first
        const semanticSelectors = [
            'article',
            'main',
            '[role="main"]',
            '.post-content',
            '.article-content',
            '.entry-content',
            '.content-body',
            '.story-body'
        ];

        let mainElement = null;
        for (const selector of semanticSelectors) {
            mainElement = document.querySelector(selector);
            if (mainElement && mainElement.innerText.length > 200) break;
        }

        // Strategy 2: Look for content-heavy divs
        if (!mainElement || mainElement.innerText.length < 200) {
            const candidates = document.querySelectorAll('div, section');
            let maxLength = 0;

            for (const candidate of candidates) {
                const text = candidate.innerText || '';
                // Skip if it's likely navigation, sidebar, etc.
                if (text.length > maxLength && text.length > 300 &&
                    !candidate.className.match(/(nav|menu|sidebar|footer|header|ads?)/i)) {
                    maxLength = text.length;
                    mainElement = candidate;
                }
            }
        }

        // Strategy 3: Fallback to body but try to clean it
        if (!mainElement) {
            mainElement = document.body;
        }

        if (mainElement) {
            // Clone to avoid modifying original
            const cloned = mainElement.cloneNode(true);

            // Remove unwanted elements more aggressively
            const unwantedSelectors = [
                'script', 'style', 'noscript',
                'nav', 'header', 'footer',
                '.nav', '.navigation', '.menu',
                '.sidebar', '.widget', '.social',
                '.ads', '.ad', '.advertisement',
                '.comments', '.comment', '.replies',
                '.breadcrumb', '.breadcrumbs',
                '.tags', '.share', '.related'
            ];

            unwantedSelectors.forEach(selector => {
                const elements = cloned.querySelectorAll(selector);
                elements.forEach(el => el.remove());
            });

            // Get clean text content
            content = cloned.innerText || cloned.textContent || '';

            // Clean up whitespace and formatting
            content = content
                .replace(/\s+/g, ' ')
                .replace(/\n\s*\n/g, '\n')
                .trim();

            // For popup preview, take first meaningful chunk (not just first 200 chars)
            if (content.length > 0) {
                // Try to get first few sentences for better preview
                const sentences = content.match(/[^\.!?]+[\.!?]+/g);
                if (sentences && sentences.length > 0) {
                    description = sentences.slice(0, 3).join(' ');
                    if (description.length > 300) {
                        description = description.substring(0, 300) + '...';
                    }
                } else {
                    description = content.substring(0, 300) + '...';
                }
            }

            // Limit full content for API (keep more for better AI analysis)
            if (content.length > 8000) {
                content = content.substring(0, 8000) + '...';
            }
        }

        // Get meta description as fallback
        const metaDescription = document.querySelector('meta[name="description"]');
        if (!description && metaDescription) {
            description = metaDescription.content;
        }

        // Detect content type for better AI prompting
        const contentType = this.detectContentType(title, content, url);

        return {
            title,
            url,
            content,
            description,
            contentType,
            timestamp: new Date().toISOString()
        };
    }

    // Helper function to detect what type of content this is
    detectContentType(title, content, url) {
        const titleLower = title.toLowerCase();
        const contentLower = content.toLowerCase();
        const urlLower = url.toLowerCase();

        // News sites and articles
        if (urlLower.includes('news') || urlLower.includes('article') ||
            titleLower.includes('news') || contentLower.includes('breaking') ||
            urlLower.match(/(bbc|cnn|reuters|ap|npr|guardian|times|post)/)) {
            return 'news';
        }

        // Technical documentation
        if (urlLower.includes('docs') || urlLower.includes('documentation') ||
            titleLower.includes('documentation') || titleLower.includes('api') ||
            contentLower.includes('function') || contentLower.includes('parameter')) {
            return 'documentation';
        }

        // Tutorials and guides
        if (titleLower.includes('how to') || titleLower.includes('tutorial') ||
            titleLower.includes('guide') || contentLower.includes('step') ||
            contentLower.includes('tutorial')) {
            return 'tutorial';
        }

        // Academic/research content
        if (titleLower.includes('research') || titleLower.includes('study') ||
            contentLower.includes('abstract') || contentLower.includes('methodology') ||
            urlLower.includes('journal') || urlLower.includes('academic')) {
            return 'research';
        }

        // Blog posts
        if (urlLower.includes('blog') || urlLower.includes('medium.com') ||
            contentLower.includes('published') || contentLower.includes('author')) {
            return 'blog';
        }

        // Product pages
        if (titleLower.includes('buy') || titleLower.includes('price') ||
            contentLower.includes('add to cart') || contentLower.includes('purchase')) {
            return 'product';
        }

        return 'general';
    }

    updatePagePreview() {
        if (!this.pageData) return;

        this.pageTitle.textContent = this.pageData.title;
        this.pageUrl.textContent = this.pageData.url;

        // Show actual content preview instead of generic description
        let preview = '';

        if (this.pageData.content && this.pageData.content.length > 50) {
            // Take first meaningful sentences from the content
            const sentences = this.pageData.content.match(/[^\.!?]+[\.!?]+/g);
            if (sentences && sentences.length > 0) {
                // Show first 2-3 sentences as preview
                preview = sentences.slice(0, 2).join(' ');
                if (preview.length > 200) {
                    preview = preview.substring(0, 200) + '...';
                }
            } else {
                // Fallback to first part of content
                preview = this.pageData.content.substring(0, 200) + '...';
            }
        } else if (this.pageData.description) {
            preview = this.pageData.description;
        } else {
            preview = 'No content preview available - will analyze page structure';
        }

        // Add content type indicator
        if (this.pageData.contentType) {
            const typeMap = {
                'news': '📰 News',
                'tutorial': '📚 Tutorial',
                'documentation': '📖 Documentation',
                'research': '🔬 Research',
                'blog': '✍️ Blog Post',
                'product': '🛍️ Product'
            };
            const typeLabel = typeMap[this.pageData.contentType] || '📄 Article';
            preview = `${typeLabel}: ${preview}`;
        }

        this.pageDescription.textContent = preview;
    }

    async checkTimeVaultConnection() {
        try {
            // Try to connect to TimeVault
            const response = await fetch('http://localhost:8080', {
                method: 'GET',
                mode: 'no-cors' // Use no-cors to avoid CORS issues during initial check
            });

            // If we get here without error, assume TimeVault is running
            this.statusIndicator.className = 'status-indicator';
            this.statusIndicator.title = 'TimeVault Connected';

        } catch (error) {
            // Mark as disconnected but don't prevent usage
            this.statusIndicator.className = 'status-indicator error';
            this.statusIndicator.title = 'TimeVault may be disconnected - Make sure TimeVault is running on localhost:8080';
            console.warn('TimeVault connection check failed:', error);
        }
    }

    setupEventListeners() {
        // Save button
        this.saveBtn.addEventListener('click', () => this.saveToVault());

        // View vault button
        this.viewVaultBtn.addEventListener('click', () => {
            chrome.tabs.create({ url: 'http://localhost:8080' });
            window.close();
        });

        // Add alternative save method button (fallback)
        this.saveBtn.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            this.fallbackSave();
        });
    }

    async fallbackSave() {
        // Alternative saving method: open TimeVault with URL pre-filled
        try {
            const url = encodeURIComponent(this.pageData.url);
            const title = encodeURIComponent(this.pageData.title);

            // Open TimeVault with URL parameters (if supported)
            const vaultUrl = `http://localhost:8080?url=${url}&title=${title}&source=extension`;

            chrome.tabs.create({ url: vaultUrl });
            window.close();
        } catch (error) {
            this.showError('Fallback Failed', 'Could not open TimeVault');
        }
    }

    async saveToVault() {
        if (this.isLoading || !this.pageData) return;

        this.isLoading = true;
        this.showProgress('Analyzing content...', 10);

        try {
            // Prepare enhanced data for saving with content type
            const saveData = {
                url: this.pageData.url,
                title: this.pageData.title,
                content: this.includeContent.checked ? (this.pageData.content || '') : '',
                description: this.pageData.description || '',
                contentType: this.pageData.contentType || 'general',
                source: 'chrome-extension'
            };

            // Show different progress messages based on content type
            const contentTypeMessages = {
                'news': 'Summarizing news content...',
                'tutorial': 'Extracting tutorial steps...',
                'documentation': 'Analyzing documentation...',
                'research': 'Processing research content...',
                'blog': 'Summarizing blog post...',
                'product': 'Analyzing product details...',
                'general': 'Processing content with AI...'
            };

            const progressMessage = contentTypeMessages[this.pageData.contentType] || 'Processing with AI...';
            this.showProgress(progressMessage, 30);

            // Send to TimeVault API
            let response;
            try {
                response = await fetch(`${TIMEVAULT_API_URL}/save-url`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    },
                    mode: 'cors',
                    body: JSON.stringify(saveData)
                });
            } catch (apiError) {
                console.log('API failed, trying fallback:', apiError);
                throw new Error('TimeVault API not available. Make sure TimeVault is running on localhost:8080');
            }

            this.showProgress('Generating intelligent summary...', 70);

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Save failed: ${response.status} - ${errorText}`);
            }

            const result = await response.json();

            this.showProgress('Content saved with AI analysis!', 100);

            // Show success with content type
            setTimeout(() => {
                this.showSuccess();
            }, 300);

        } catch (error) {
            console.error('Save error:', error);

            let errorMessage = error.message;
            if (error.message.includes('Failed to fetch') || error.message.includes('net::')) {
                errorMessage = 'Cannot connect to TimeVault. Make sure it\'s running on localhost:8080';
            } else if (error.message.includes('CORS')) {
                errorMessage = 'Connection blocked. Check TimeVault CORS settings.';
            }

            this.showError('Save Failed', errorMessage);
        } finally {
            this.isLoading = false;
        }
    }

    showProgress(text, percent) {
        this.hideAllMessages();
        this.progressContainer.style.display = 'block';
        this.progressText.textContent = text;
        this.progressFill.style.width = `${percent}%`;
        this.saveBtn.disabled = true;
    }

    showSuccess() {
        this.hideAllMessages();
        this.successMessage.style.display = 'block';
        this.saveBtn.disabled = false;

        // Auto-close after 2 seconds
        setTimeout(() => {
            window.close();
        }, 2000);
    }

    showError(title, subtitle) {
        this.hideAllMessages();
        this.errorMessage.style.display = 'block';
        this.errorMessage.querySelector('.error-text').textContent = title;
        this.errorSubtitle.textContent = subtitle;
        this.saveBtn.disabled = false;
    }

    hideAllMessages() {
        this.progressContainer.style.display = 'none';
        this.successMessage.style.display = 'none';
        this.errorMessage.style.display = 'none';
    }
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new TimeVaultExtension();
});

// Handle extension icon click
chrome.action.onClicked.addListener((tab) => {
    // This will trigger the popup to open
    console.log('TimeVault extension clicked for tab:', tab.url);
});
