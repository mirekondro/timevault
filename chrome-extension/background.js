// TimeVault Chrome Extension - Background Service Worker
// This runs in the background and handles extension events

// Extension installation and setup
chrome.runtime.onInstalled.addListener((details) => {
    console.log('TimeVault extension installed:', details.reason);

    if (details.reason === 'install') {
        // First time installation
        chrome.tabs.create({
            url: 'http://localhost:8080'
        });

        // Set up default settings
        chrome.storage.sync.set({
            timeVaultUrl: 'http://localhost:8080',
            autoSave: false,
            includeImages: true,
            notificationEnabled: true,
            quickSave: true
        });
    }
});

// Handle extension icon clicks
chrome.action.onClicked.addListener(async (tab) => {
    console.log('TimeVault extension icon clicked for:', tab.url);

    // Check if we can access this tab
    if (tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://') || tab.url.startsWith('edge://')) {
        // Can't access browser internal pages
        chrome.notifications.create({
            type: 'basic',
            iconUrl: 'icons/icon-48.png',
            title: 'TimeVault',
            message: 'Cannot save browser internal pages'
        });
        return;
    }

    // The popup will open automatically due to the manifest configuration
});

// Handle keyboard shortcuts
chrome.commands.onCommand.addListener(async (command, tab) => {
    console.log('TimeVault command triggered:', command);

    switch (command) {
        case 'quick-save':
            await quickSaveCurrentPage(tab);
            break;
        case 'open-vault':
            chrome.tabs.create({ url: 'http://localhost:8080' });
            break;
    }
});

// Quick save functionality
async function quickSaveCurrentPage(tab) {
    try {
        // Check if tab is accessible
        if (!tab || tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://')) {
            throw new Error('Cannot access this page');
        }

        // Extract content from the page
        const results = await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            func: () => {
                return {
                    title: document.title,
                    url: window.location.href,
                    content: document.body.innerText.substring(0, 5000)
                };
            }
        });

        if (!results || !results[0] || !results[0].result) {
            throw new Error('Failed to extract page content');
        }

        const pageData = results[0].result;

        // Send to TimeVault API
        const response = await fetch('http://localhost:8080/api/vault/save-url', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                url: pageData.url,
                title: pageData.title,
                content: pageData.content,
                source: 'chrome-extension-quick-save'
            })
        });

        if (response.ok) {
            // Show success notification
            chrome.notifications.create({
                type: 'basic',
                iconUrl: 'icons/icon-48.png',
                title: 'TimeVault - Saved!',
                message: `"${pageData.title}" saved to your vault`
            });
        } else {
            throw new Error(`API error: ${response.status}`);
        }

    } catch (error) {
        console.error('Quick save error:', error);

        chrome.notifications.create({
            type: 'basic',
            iconUrl: 'icons/icon-48.png',
            title: 'TimeVault - Save Failed',
            message: error.message
        });
    }
}

// Context menu integration (right-click menu)
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
    if (info.menuItemId === 'save-to-timevault') {
        if (info.linkUrl) {
            // User right-clicked on a link
            await saveLinkToVault(info.linkUrl, tab);
        } else {
            // User right-clicked on the page
            await quickSaveCurrentPage(tab);
        }
    }
});

// Save a specific link to TimeVault
async function saveLinkToVault(linkUrl, sourceTab) {
    try {
        const response = await fetch('http://localhost:8080/api/vault/save-url', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                url: linkUrl,
                title: `Link from ${sourceTab.title}`,
                content: '',
                source: 'chrome-extension-context-menu'
            })
        });

        if (response.ok) {
            chrome.notifications.create({
                type: 'basic',
                iconUrl: 'icons/icon-48.png',
                title: 'TimeVault - Link Saved!',
                message: `Link saved to your vault`
            });
        } else {
            throw new Error(`API error: ${response.status}`);
        }

    } catch (error) {
        chrome.notifications.create({
            type: 'basic',
            iconUrl: 'icons/icon-48.png',
            title: 'TimeVault - Save Failed',
            message: error.message
        });
    }
}

// Set up context menu when extension starts
chrome.runtime.onStartup.addListener(() => {
    setupContextMenu();
});

chrome.runtime.onInstalled.addListener(() => {
    setupContextMenu();
});

function setupContextMenu() {
    chrome.contextMenus.create({
        id: 'save-to-timevault',
        title: 'Save to TimeVault',
        contexts: ['page', 'link'],
        documentUrlPatterns: ['http://*/*', 'https://*/*']
    });
}

// Handle messages from content scripts and popup
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    switch (message.action) {
        case 'checkTimeVaultConnection':
            checkTimeVaultConnection().then(result => {
                sendResponse(result);
            });
            return true; // Keep message channel open

        case 'savePageData':
            savePageToTimeVault(message.data).then(result => {
                sendResponse(result);
            }).catch(error => {
                sendResponse({ success: false, error: error.message });
            });
            return true;

        case 'openVault':
            chrome.tabs.create({ url: message.url || 'http://localhost:8080' });
            sendResponse({ success: true });
            break;
    }
});

// Check if TimeVault is running
async function checkTimeVaultConnection() {
    try {
        const response = await fetch('http://localhost:8080/api/vault/health', {
            method: 'GET',
            mode: 'cors'
        });

        return {
            success: true,
            status: response.ok ? 'connected' : 'error',
            statusCode: response.status
        };
    } catch (error) {
        return {
            success: false,
            status: 'disconnected',
            error: error.message
        };
    }
}

// Save page data to TimeVault
async function savePageToTimeVault(pageData) {
    try {
        const response = await fetch('http://localhost:8080/api/vault/save-url', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(pageData)
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status}: ${errorText}`);
        }

        const result = await response.json();
        return { success: true, data: result };

    } catch (error) {
        throw new Error(`Failed to save to TimeVault: ${error.message}`);
    }
}

// Periodic connection check
setInterval(async () => {
    const result = await checkTimeVaultConnection();
    chrome.storage.local.set({
        timeVaultStatus: result.status,
        lastChecked: Date.now()
    });
}, 60000); // Check every minute
