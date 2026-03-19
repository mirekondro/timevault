# 🧠 TimeVault Chrome Extension

Save any webpage to your TimeVault with one click! This Chrome extension integrates seamlessly with your TimeVault application, providing AI-powered content analysis and smart tagging.

## ✨ Features

- **One-Click Save**: Save any webpage instantly to your TimeVault
- **Smart Content Extraction**: Automatically extracts main content from pages
- **AI Analysis**: Uses Gemini AI to generate titles and descriptions
- **Visual Preview**: See page preview before saving
- **Quick Access**: Right-click context menu for links and pages
- **Vault Search**: Search your vault directly from the extension
- **Connection Status**: Visual indicator shows TimeVault connection status

## 🚀 Installation

### 1. Load Extension in Chrome

1. **Open Chrome Extensions page**:
   - Go to `chrome://extensions/` in your browser
   - Or click the three dots menu → More tools → Extensions

2. **Enable Developer Mode**:
   - Toggle "Developer mode" in the top-right corner

3. **Load the Extension**:
   - Click "Load unpacked"
   - Navigate to the `timevault/chrome-extension` folder
   - Select the folder and click "Select Folder"

4. **Pin the Extension**:
   - Click the puzzle piece icon in Chrome toolbar
   - Find "TimeVault - Save to Digital Memory"
   - Click the pin icon to keep it visible

### 2. Verify TimeVault Connection

1. **Start TimeVault Application**:
   ```bash
   cd /path/to/timevault
   ./mvnw spring-boot:run
   ```

2. **Check Connection**:
   - Click the TimeVault extension icon
   - Green dot = Connected ✅
   - Red dot = Disconnected ❌

## 🎯 Usage

### Method 1: Extension Popup
1. Navigate to any webpage
2. Click the TimeVault extension icon
3. Review the page preview
4. Choose save options:
   - ✅ Include page content for AI analysis
   - ✅ Extract main images
5. Click "Save to Vault"
6. Watch AI analysis progress
7. Success! Page saved with smart title and description

### Method 2: Right-Click Menu
1. Right-click on any page or link
2. Select "Save to TimeVault"
3. Automatic quick save with notification

### Method 3: Keyboard Shortcuts (Future)
- `Ctrl+Shift+S` (or `Cmd+Shift+S` on Mac): Quick save current page
- `Ctrl+Shift+V` (or `Cmd+Shift+V` on Mac): Open TimeVault

## 🔧 How It Works

### Content Extraction
The extension intelligently extracts content using multiple strategies:

1. **Semantic HTML**: Looks for `<main>`, `<article>`, `[role="main"]`
2. **Common Selectors**: Searches for `.content`, `.main-content`, `#content`
3. **Content Analysis**: Finds elements with most meaningful text
4. **Cleanup**: Removes navigation, ads, scripts, and unwanted elements

### AI Processing
1. **Content Sent**: Page content sent to TimeVault API
2. **Gemini Analysis**: AI generates proper title and description
3. **Smart Tagging**: Automatic categorization and tagging
4. **Database Save**: Stored with searchable metadata

### API Integration
Extension communicates with TimeVault via REST API:
- `GET /api/vault/health` - Check connection
- `POST /api/vault/save-url` - Save webpage
- `GET /api/vault/recent` - Get recent items
- `GET /api/vault/search` - Search vault

## 🎨 Visual Design

- **Beautiful Popup**: Gradient design matching TimeVault theme
- **Progress Indicators**: Real-time save progress with AI analysis steps
- **Status Feedback**: Clear success/error messages
- **Page Preview**: Shows title, URL, and content preview
- **Save Options**: Checkboxes for content inclusion preferences

## 🛠️ Technical Details

### Permissions Required
- `activeTab`: Access current webpage content
- `storage`: Save extension preferences
- `scripting`: Extract page content

### Files Structure
```
chrome-extension/
├── manifest.json      # Extension configuration
├── popup.html         # Main popup interface
├── popup.css          # Beautiful styling
├── popup.js           # Popup functionality
├── content.js         # Page content extraction
├── background.js      # Background service worker
└── icons/            # Extension icons
    ├── icon-16.png
    ├── icon-32.png
    ├── icon-48.png
    └── icon-128.png
```

### Security
- Only connects to localhost:8080 (your TimeVault)
- No external data transmission
- Content processed locally and via your AI API
- No tracking or analytics

## 🔍 Troubleshooting

### Extension Not Working
1. **Check TimeVault is Running**:
   - Verify `http://localhost:8080` is accessible
   - Green/red status indicator shows connection

2. **Check Extension Permissions**:
   - Go to `chrome://extensions/`
   - Ensure TimeVault extension is enabled
   - Check that "Developer mode" is on

3. **Console Errors**:
   - Right-click extension icon → Inspect popup
   - Check console for error messages

### Cannot Save Certain Pages
- Browser internal pages (`chrome://`, `edge://`) cannot be accessed
- Some sites block content extraction
- Check if page has anti-bot protections

### API Connection Issues
1. **CORS Errors**: TimeVault API includes CORS headers for Chrome extension
2. **Network Issues**: Check firewall/antivirus blocking localhost
3. **Port Conflicts**: Ensure nothing else using port 8080

## 🎉 Success!

You now have a powerful Chrome extension that:
- ✅ Saves any webpage to TimeVault instantly
- ✅ Uses AI to generate smart titles and descriptions  
- ✅ Provides beautiful, intuitive interface
- ✅ Integrates seamlessly with your vault
- ✅ Makes web browsing more productive

**Start saving your digital discoveries with one click! 🚀**

## 🔄 Future Enhancements

- Keyboard shortcuts
- Batch save multiple tabs
- Custom save templates
- Offline sync when reconnected
- Share saved items
- Advanced filtering options
