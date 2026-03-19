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

        // Extract main content
        let content = '';

        // Try to get main content from common elements
        const contentSelectors = [
            'main',
            'article',
            '[role="main"]',
            '.content',
            '.main-content',
            '#content',
            '#main'
        ];

        let mainElement = null;
        for (const selector of contentSelectors) {
            mainElement = document.querySelector(selector);
            if (mainElement) break;
        }

        // Fallback to body if no main content found
        if (!mainElement) {
            mainElement = document.body;
        }

        if (mainElement) {
            // Clone the element to avoid modifying the original
            const cloned = mainElement.cloneNode(true);

            // Remove script and style tags
            const scriptsAndStyles = cloned.querySelectorAll('script, style, noscript');
            scriptsAndStyles.forEach(el => el.remove());

            // Remove navigation and footer elements
            const navElements = cloned.querySelectorAll('nav, header, footer, .nav, .navigation, .sidebar, .ads');
            navElements.forEach(el => el.remove());

            // Get text content
            content = cloned.innerText || cloned.textContent || '';

            // Clean up whitespace
            content = content.replace(/\\s+/g, ' ').trim();

            // Limit content size (first 5000 characters)
            if (content.length > 5000) {
                content = content.substring(0, 5000) + '...';
            }
        }

        // Extract meta description
        const metaDescription = document.querySelector('meta[name="description"]');
        const description = metaDescription ? metaDescription.content : '';

        // Extract images
        const images = [];
        const imgElements = document.querySelectorAll('img[src]');
        for (let i = 0; i < Math.min(imgElements.length, 5); i++) {
            const img = imgElements[i];
            if (img.src && img.width > 100 && img.height > 100) {
                images.push({
                    src: img.src,
                    alt: img.alt || '',
                    width: img.width,
                    height: img.height
                });
            }
        }

        return {
            title,
            url,
            content,
            description,
            images,
            timestamp: new Date().toISOString()
        };
    }

    updatePagePreview() {
        if (!this.pageData) return;

        this.pageTitle.textContent = this.pageData.title;
        this.pageUrl.textContent = this.pageData.url;

        // Show description or content preview
        const preview = this.pageData.description ||
                       this.pageData.content.substring(0, 200) + '...' ||
                       'No content preview available';

        this.pageDescription.textContent = preview;
    }

    async checkTimeVaultConnection() {
        try {
            const response = await fetch(`${TIMEVAULT_API_URL}/health`, {
                method: 'GET',
                mode: 'cors'
            });

            if (response.ok) {
                this.statusIndicator.className = 'status-indicator';
                this.statusIndicator.title = 'TimeVault Connected';
            } else {
                throw new Error('TimeVault not responding');
            }
        } catch (error) {
            this.statusIndicator.className = 'status-indicator error';
            this.statusIndicator.title = 'TimeVault Disconnected - Make sure TimeVault is running on localhost:8080';
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
    }

    async saveToVault() {
        if (this.isLoading || !this.pageData) return;

        this.isLoading = true;
        this.showProgress('Preparing content...', 10);

        try {
            // Prepare data for saving
            const saveData = {
                url: this.pageData.url,
                title: this.pageData.title,
                content: this.includeContent.checked ? (this.pageData.content || '') : '',
                description: this.pageData.description || '',
                source: 'chrome-extension'
            };

            this.showProgress('Connecting to TimeVault...', 30);

            // First try the API endpoint
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
                // Fallback: try to save via the web interface
                console.log('API failed, trying web interface fallback:', apiError);
                throw new Error('TimeVault API not available. Make sure TimeVault is running on localhost:8080');
            }

            this.showProgress('Processing with AI...', 70);

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Save failed: ${response.status} - ${errorText}`);
            }

            const result = await response.json();

            this.showProgress('Saved successfully!', 100);

            // Show success
            setTimeout(() => {
                this.showSuccess();
            }, 300);

        } catch (error) {
            console.error('Save error:', error);

            // Provide helpful error messages
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
