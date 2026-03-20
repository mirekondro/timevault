package com.example.web.api.client;

import com.example.shared.api.ApiMessageResponse;
import com.example.shared.api.ApiProfileEmailRequest;
import com.example.shared.api.ApiProfilePasswordRequest;
import com.example.shared.api.ApiSessionResponse;
import com.example.shared.model.UserSession;
import com.example.shared.service.ApiSessionService;
import com.example.shared.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile/account endpoints for the desktop-facing API.
 */
@RestController
@RequestMapping("/api/client/profile")
@CrossOrigin(origins = "*")
public class ClientProfileController extends ClientApiSupport {

    private final AuthService authService;

    @Autowired
    public ClientProfileController(AuthService authService, ApiSessionService apiSessionService) {
        super(apiSessionService);
        this.authService = authService;
    }

    @PatchMapping("/email")
    public ResponseEntity<?> updateEmail(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                         @RequestBody ApiProfileEmailRequest request) {
        String token = requireBearerToken(authorizationHeader);
        UserSession currentSession = requireSession(token);

        try {
            UserSession updatedSession = authService.updateEmail(
                    currentSession.id(),
                    valueOrEmpty(request == null ? null : request.newEmail()),
                    valueOrEmpty(request == null ? null : request.currentPassword()));
            ApiSessionResponse refreshedSession = apiSessionService().refreshSession(token, updatedSession);
            return ResponseEntity.ok(refreshedSession);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiMessageResponse(exception.getMessage()));
        }
    }

    @PatchMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestHeader(name = "Authorization", required = false) String authorizationHeader,
                                            @RequestBody ApiProfilePasswordRequest request) {
        String token = requireBearerToken(authorizationHeader);
        UserSession currentSession = requireSession(token);

        try {
            authService.updatePassword(
                    currentSession.id(),
                    valueOrEmpty(request == null ? null : request.currentPassword()),
                    valueOrEmpty(request == null ? null : request.newPassword()),
                    valueOrEmpty(request == null ? null : request.confirmPassword()));
            return ResponseEntity.ok(new ApiMessageResponse("Password updated successfully."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiMessageResponse(exception.getMessage()));
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
