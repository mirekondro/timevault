package com.example.desktop;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║                    DESKTOP VERSION - FOR YOUR FRIEND                       ║
 * ╠═══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                            ║
 * ║  This package is for the DESKTOP version (JavaFX)                         ║
 * ║                                                                            ║
 * ║  Your friend should implement:                                            ║
 * ║  - DesktopApp.java      (JavaFX Application main class)                   ║
 * ║  - MainController.java  (JavaFX Controller)                               ║
 * ║  - main.fxml            (FXML layout)                                     ║
 * ║                                                                            ║
 * ║  Use the SHARED backend:                                                   ║
 * ║  - com.example.shared.model.VaultItem                                     ║
 * ║  - com.example.shared.repository.VaultItemRepository                      ║
 * ║  - com.example.shared.service.VaultItemService                            ║
 * ║                                                                            ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 *
 * USAGE EXAMPLE:
 *
 * // In JavaFX Controller:
 * @Autowired
 * private VaultItemService vaultItemService;
 *
 * // Save a URL
 * vaultItemService.saveUrl(url, title, content, aiContext);
 *
 * // Save text
 * vaultItemService.saveText(title, content, aiContext);
 *
 * // Get recent items
 * List<VaultItem> items = vaultItemService.findRecent();
 *
 * // Search
 * List<VaultItem> results = vaultItemService.search("keyword");
 *
 * // Delete
 * vaultItemService.delete(itemId);
 */
public class DesktopReadme {
    // This is just a documentation placeholder
    // Delete this file and create your JavaFX app here
}

