// TimeVault Chrome Extension - Content Script
// This script runs on every webpage and can interact with the page content

(function() {
    'use strict';

    // Only initialize once
    if (window.timeVaultInitialized) return;
    window.timeVaultInitialized = true;

    // TimeVault content script functionality
    class TimeVaultContentScript {
        constructor() {
            this.init();
        }

        init() {
            // Listen for messages from popup
            chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
                this.handleMessage(message, sender, sendResponse);
                return true; // Keep message channel open for async response
            });

            // Add subtle visual indicator when page is TimeVault-ready
            this.addTimeVaultIndicator();
        }

        handleMessage(message, sender, sendResponse) {
            switch (message.action) {
                case 'extractPageContent':
                    this.extractPageContent().then(data => {
                        sendResponse({ success: true, data });
                    }).catch(error => {
                        sendResponse({ success: false, error: error.message });
                    });
                    break;

                case 'highlightContent':
                    this.highlightMainContent();
                    sendResponse({ success: true });
                    break;

                case 'captureScreenshot':
                    this.captureVisibleContent().then(data => {
                        sendResponse({ success: true, data });
                    }).catch(error => {
                        sendResponse({ success: false, error: error.message });
                    });
                    break;

                default:
                    sendResponse({ success: false, error: 'Unknown action' });
            }
        }

        async extractPageContent() {
            try {
                const data = {
                    // Basic page info
                    title: document.title || 'Untitled Page',
                    url: window.location.href,
                    favicon: this.getFavicon(),

                    // Content extraction
                    content: this.extractMainContent(),
                    description: this.getMetaDescription(),
                    keywords: this.getMetaKeywords(),

                    // Page structure
                    headings: this.extractHeadings(),
                    images: this.extractImages(),
                    links: this.extractImportantLinks(),

                    // Technical details
                    language: document.documentElement.lang || 'en',
                    charset: document.characterSet || 'UTF-8',

                    // Timestamps
                    timestamp: new Date().toISOString(),
                    lastModified: document.lastModified ? new Date(document.lastModified).toISOString() : null
                };

                return data;
            } catch (error) {
                console.error('TimeVault: Error extracting page content:', error);
                throw error;
            }
        }

        extractMainContent() {
            // Try multiple strategies to extract the main content
            let mainContent = '';

            // Strategy 1: Look for semantic HTML5 elements
            const semanticSelectors = [
                'main',
                'article',
                '[role="main"]',
                '.main-content',
                '.content',
                '#main',
                '#content'
            ];

            let contentElement = null;
            for (const selector of semanticSelectors) {
                contentElement = document.querySelector(selector);
                if (contentElement && contentElement.innerText.length > 100) {
                    break;
                }
            }

            // Strategy 2: Find the element with most text content
            if (!contentElement) {
                const candidates = document.querySelectorAll('div, section, article');
                let maxLength = 0;

                candidates.forEach(el => {
                    const text = el.innerText || '';
                    if (text.length > maxLength && text.length > 200) {
                        maxLength = text.length;
                        contentElement = el;
                    }
                });
            }

            // Strategy 3: Fallback to body
            if (!contentElement) {
                contentElement = document.body;
            }

            if (contentElement) {
                // Clone to avoid modifying original
                const clone = contentElement.cloneNode(true);

                // Remove unwanted elements
                const unwantedSelectors = [
                    'script', 'style', 'noscript',
                    'nav', 'header', 'footer',
                    '.ad', '.ads', '.advertisement',
                    '.sidebar', '.widget', '.social-share',
                    '.comments', '.comment-section',
                    '.navigation', '.breadcrumb'
                ];

                unwantedSelectors.forEach(selector => {
                    const elements = clone.querySelectorAll(selector);
                    elements.forEach(el => el.remove());
                });

                // Extract text and clean it up
                mainContent = clone.innerText || clone.textContent || '';
                mainContent = mainContent
                    .replace(/\\s+/g, ' ')
                    .replace(/\\n\\s*\\n/g, '\\n')
                    .trim();

                // Limit size to prevent huge payloads
                if (mainContent.length > 8000) {
                    mainContent = mainContent.substring(0, 8000) + '...';
                }
            }

            return mainContent;
        }

        getMetaDescription() {
            const metaDesc = document.querySelector('meta[name="description"]');
            return metaDesc ? metaDesc.getAttribute('content') || '' : '';
        }

        getMetaKeywords() {
            const metaKeywords = document.querySelector('meta[name="keywords"]');
            return metaKeywords ? metaKeywords.getAttribute('content') || '' : '';
        }

        getFavicon() {
            const links = document.querySelectorAll('link[rel*="icon"]');
            for (const link of links) {
                const href = link.getAttribute('href');
                if (href) {
                    return new URL(href, window.location.href).href;
                }
            }
            return `${window.location.origin}/favicon.ico`;
        }

        extractHeadings() {
            const headings = [];
            const headingElements = document.querySelectorAll('h1, h2, h3, h4, h5, h6');

            headingElements.forEach((heading, index) => {
                if (index < 20) { // Limit to first 20 headings
                    headings.push({
                        level: parseInt(heading.tagName[1]),
                        text: heading.innerText.trim(),
                        id: heading.id || null
                    });
                }
            });

            return headings;
        }

        extractImages() {
            const images = [];
            const imgElements = document.querySelectorAll('img[src]');

            imgElements.forEach((img, index) => {
                if (index < 10 && img.naturalWidth > 100 && img.naturalHeight > 100) {
                    try {
                        images.push({
                            src: new URL(img.src, window.location.href).href,
                            alt: img.alt || '',
                            title: img.title || '',
                            width: img.naturalWidth,
                            height: img.naturalHeight,
                            loading: img.loading || 'eager'
                        });
                    } catch (e) {
                        // Skip invalid URLs
                    }
                }
            });

            return images;
        }

        extractImportantLinks() {
            const links = [];
            const linkElements = document.querySelectorAll('a[href]');

            linkElements.forEach((link, index) => {
                if (index < 50) { // Limit to first 50 links
                    const href = link.getAttribute('href');
                    const text = link.innerText.trim();

                    if (href && text && text.length < 100) {
                        try {
                            links.push({
                                href: new URL(href, window.location.href).href,
                                text: text,
                                title: link.title || ''
                            });
                        } catch (e) {
                            // Skip invalid URLs
                        }
                    }
                }
            });

            return links;
        }

        highlightMainContent() {
            // Visual feedback - temporarily highlight the main content area
            const mainContent = document.querySelector('main, article, .content, #content') || document.body;

            if (mainContent) {
                const originalOutline = mainContent.style.outline;
                mainContent.style.outline = '3px solid #667eea';
                mainContent.style.outlineOffset = '2px';

                setTimeout(() => {
                    mainContent.style.outline = originalOutline;
                }, 2000);
            }
        }

        async captureVisibleContent() {
            // This would require additional permissions for capturing screenshots
            // For now, just return viewport dimensions
            return {
                viewport: {
                    width: window.innerWidth,
                    height: window.innerHeight,
                    scrollX: window.pageXOffset,
                    scrollY: window.pageYOffset
                },
                document: {
                    width: document.documentElement.scrollWidth,
                    height: document.documentElement.scrollHeight
                }
            };
        }

        addTimeVaultIndicator() {
            // Add a subtle indicator that this page can be saved to TimeVault
            if (document.querySelector('.timevault-indicator')) return;

            const indicator = document.createElement('div');
            indicator.className = 'timevault-indicator';
            indicator.style.cssText = `
                position: fixed;
                bottom: 20px;
                right: 20px;
                width: 12px;
                height: 12px;
                background: #667eea;
                border-radius: 50%;
                box-shadow: 0 0 10px rgba(102, 126, 234, 0.5);
                z-index: 10000;
                opacity: 0.7;
                transition: all 0.3s ease;
                pointer-events: none;
            `;

            document.body.appendChild(indicator);

            // Fade out after 3 seconds
            setTimeout(() => {
                indicator.style.opacity = '0';
                setTimeout(() => {
                    if (indicator.parentNode) {
                        indicator.parentNode.removeChild(indicator);
                    }
                }, 300);
            }, 3000);
        }
    }

    // Initialize content script
    new TimeVaultContentScript();

})();
