package com.example.web.api.client;

import com.example.shared.api.ApiMessageResponse;
import com.example.shared.api.ApiStoredImageDto;
import com.example.shared.api.ApiVaultItemDto;
import com.example.shared.api.ApiVaultItemMutationRequest;
import com.example.shared.model.UserSession;
import com.example.shared.service.ApiSessionService;
import com.example.shared.service.DesktopVaultApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Desktop-ready vault API that mirrors the current local DAO operations.
 */
@RestController
@RequestMapping("/api/client/vault")
@CrossOrigin(origins = "*")
public class ClientVaultController extends ClientApiSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientVaultController.class);

    private final DesktopVaultApiService desktopVaultApiService;

    @Autowired
    public ClientVaultController(DesktopVaultApiService desktopVaultApiService, ApiSessionService apiSessionService) {
        super(apiSessionService);
        this.desktopVaultApiService = desktopVaultApiService;
    }

    @GetMapping("/items")
    public List<ApiVaultItemDto> getItems(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        return desktopVaultApiService.findAllItems(session.id());
    }

    @GetMapping("/items/{itemId}")
    public ApiVaultItemDto getItem(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                   @PathVariable long itemId) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        return desktopVaultApiService.findItem(session.id(), itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found."));
    }

    @PostMapping("/items")
    public ResponseEntity<?> createItem(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                        @RequestBody ApiVaultItemMutationRequest request) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        try {
            ApiVaultItemDto createdItem = desktopVaultApiService.createItem(session.id(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdItem);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiMessageResponse(exception.getMessage()));
        } catch (Exception exception) {
            return internalServerError("create", exception);
        }
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<?> updateItem(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                        @PathVariable long itemId,
                                        @RequestBody ApiVaultItemMutationRequest request) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        try {
            return desktopVaultApiService.updateItem(session.id(), itemId, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiMessageResponse("Item not found.")));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiMessageResponse(exception.getMessage()));
        } catch (Exception exception) {
            return internalServerError("update", exception);
        }
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<?> deleteItem(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                        @PathVariable long itemId) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        boolean deleted = desktopVaultApiService.deleteItem(session.id(), itemId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiMessageResponse("Item not found."));
        }
        return ResponseEntity.ok(new ApiMessageResponse("Item deleted successfully."));
    }

    @PostMapping("/items/{itemId}/restore")
    public ResponseEntity<?> restoreItem(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                         @PathVariable long itemId) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        boolean restored = desktopVaultApiService.restoreItem(session.id(), itemId);
        if (!restored) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiMessageResponse("Item not found."));
        }
        return ResponseEntity.ok(new ApiMessageResponse("Item restored successfully."));
    }

    @GetMapping("/items/{itemId}/images")
    public List<ApiStoredImageDto> getStoredImages(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                                   @PathVariable long itemId) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        List<ApiStoredImageDto> storedImages = desktopVaultApiService.findStoredImages(session.id(), itemId);
        if (storedImages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored images not found.");
        }
        return storedImages;
    }

    @GetMapping("/items/{itemId}/image")
    public ApiStoredImageDto getStoredImage(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                            @PathVariable long itemId) {
        UserSession session = requireAuthorizedSession(authorizationHeader);
        return desktopVaultApiService.findStoredImages(session.id(), itemId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored image not found."));
    }

    private UserSession requireAuthorizedSession(String authorizationHeader) {
        String token = requireBearerToken(authorizationHeader);
        return requireSession(token);
    }

    private ResponseEntity<ApiMessageResponse> internalServerError(String action, Exception exception) {
        LOGGER.error("Client vault API failed to {} item.", action, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiMessageResponse(resolveRootCauseMessage(exception)));
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = "The backend could not process the vault item request.";
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage().trim();
            }
            current = current.getCause();
        }
        return message;
    }
}
