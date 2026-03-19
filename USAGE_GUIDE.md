# TimeVault - Complete Setup & Usage Guide

## ✅ Database Setup Complete
- **Server**: EASV-DB4:1433
- **Database**: timevault_debug
- **User**: CS2025b_e_8
- **Credentials**: Stored in `src/main/resources/db.properties`

## ✅ Services Implemented

### 1. **VaultItemService** (`shared/service/VaultItemService.java`)
Main business logic service with:
- `saveUrl(url, title, pageContent)` - Saves URL with AI context
- `saveText(title, content)` - Saves text with AI context
- `saveImage(title, imageData, mimeType, originalFilename)` - Saves image with Gemini Vision analysis
- `findRecent()` - Get 10 most recent items
- `search(keyword)` - Search across all items
- `delete(id)` - Delete an item
- `regenerateContext(id)` - Regenerate AI context for existing item
- `isAiConfigured()` - Check if Gemini API is set up

### 2. **GeminiService** (`shared/service/GeminiService.java`)
AI integration with Google Gemini 2.0 Flash:
- `generateTextContext(content)` - Creates 3-sentence summary for text
- `generateUrlContext(url, pageContent)` - Creates 3-sentence summary for URLs
- `analyzeImage(imageData, mimeType, filename)` - **Gemini Vision** for image analysis
- `generateEmbedding(text)` - Text embeddings for future semantic search
- Falls back to local context generation if API key not set

### 3. **FileStorageService** (`shared/service/FileStorageService.java`)
File handling:
- `store(MultipartFile)` - Store uploaded files
- `store(byte[], filename)` - Store byte arrays
- `load(relativePath)` - Load file as bytes
- `delete(relativePath)` - Delete stored file
- `exists(relativePath)` - Check if file exists
- Files stored in: `~/.timevault/uploads/yyyy/MM/`

## 🚀 How to Run

### Step 1: Set Gemini API Key (Optional but recommended)
```bash
export GEMINI_API_KEY="your-gemini-api-key"
```

### Step 2: Run the Application
```bash
cd /Users/miroslavondrousek/GitHub/timevault
./mvnw spring-boot:run
```

### Step 3: Open in Browser
```
http://localhost:8080
```

## 📝 How to Add Items to Database

### Add URL
1. Click "URL" tab
2. Paste URL: `https://example.com/article`
3. Click "Save to Vault"
4. ✅ Content saved with AI-generated 3-sentence context
5. Auto-tagged by: URL, date, platform (GitHub/Medium/Twitter/etc)

### Add Text Note
1. Click "Text" tab
2. Type or paste text
3. Click "Save to Vault"
4. ✅ Content saved with AI-generated summary
5. Auto-tagged by: TEXT, date

### Add Image
1. Click "Image" tab
2. Upload image (JPG, PNG, GIF, WebP)
3. Drag & drop or click to browse
4. ✅ Image analyzed by **Gemini Vision API**
5. Generates 3-sentence description of image
6. Auto-tagged by: IMAGE, date, file type
7. Stored in: `~/.timevault/uploads/2026/03/`

## 🔍 Search & Browse

### Recent Items
- Dashboard shows 10 most recent saved items
- Displays title, AI context, type, tags, and date
- Click delete button to remove item

### Search
- Type in search box at top
- Searches across titles and content
- Real-time results as you type
- Click refresh to clear search

## 🗄️ Database Schema

```sql
vault_items (
  id BIGINT PRIMARY KEY (auto-increment),
  title NVARCHAR(500) NOT NULL,
  content NVARCHAR(MAX),          -- URL, file path, or text
  ai_context NVARCHAR(MAX),       -- 3-sentence AI summary
  item_type NVARCHAR(50),         -- URL, IMAGE, or TEXT
  tags NVARCHAR(500),             -- Auto-generated: type, date, platform
  source_url NVARCHAR(1000),      -- Original URL (for URL type only)
  created_at DATETIME2,
  updated_at DATETIME2
)
```

## 🤖 AI Features

### Without Gemini API Key
- Falls back to simple text-based summaries
- Still auto-tags by type, date, platform
- Image storage still works

### With Gemini API Key (Recommended)
- **Text URLs**: Uses Gemini to summarize webpage content (3 sentences)
- **Text Notes**: Uses Gemini to create meaningful summaries (3 sentences)
- **Images**: Uses **Gemini Vision 2.0** to analyze and describe images (3 sentences)
  - Detects objects, text, scenes
  - Describes composition and content
  - Explains potential usefulness
- **Embeddings**: Text embeddings for future semantic search

## 📂 File Structure

```
src/main/java/com/example/
├── Application.java
├── shared/
│   ├── model/VaultItem.java
│   ├── repository/VaultItemRepository.java
│   └── service/
│       ├── VaultItemService.java       ← Main business logic
│       ├── GeminiService.java          ← AI integration
│       └── FileStorageService.java     ← File storage
├── web/views/MainView.java             ← Web UI (Vaadin)
└── desktop/DesktopReadme.java          ← Desktop (JavaFX - Friend's part)
```

## 🐛 Troubleshooting

### Database Connection Error
- Check `src/main/resources/db.properties`
- Verify SQL Server is running
- Test connection in DataGrip

### Gemini API Errors
- Check if `GEMINI_API_KEY` environment variable is set
- Verify API key is valid at: https://ai.google.dev/
- Check internet connection
- App will still work with fallback summaries

### Image Upload Issues
- Maximum file size: 10MB
- Supported formats: JPG, PNG, GIF, WebP
- Check disk space in `~/.timevault/uploads/`

## 📊 Statistics

- Total items saved: Shows on each item card
- Filter by type: URLs, Images, Text
- Date range: All timestamped for future date-based filtering

---

**Ready to start using TimeVault!** 🎉

