package com.example.web.api.client;

import com.example.shared.api.ApiAuthRequest;
import com.example.shared.api.ApiMessageResponse;
import com.example.shared.api.ApiSessionResponse;
import com.example.shared.model.UserSession;
import com.example.shared.service.ApiSessionService;
import com.example.shared.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Desktop-ready auth/session API.
 */
@RestController
@RequestMapping("/api/client/auth")
@CrossOrigin(origins = "*")
public class ClientAuthController extends ClientApiSupport {

    private final AuthService authService;

    @Autowired
    public ClientAuthController(AuthService authService, ApiSessionService apiSessionService) {
        super(apiSessionService);
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody ApiAuthRequest request) {
        try {
            UserSession session = authService.register(valueOrEmpty(request == null ? null : request.email()),
                    valueOrEmpty(request == null ? null : request.password()));
            return ResponseEntity.status(HttpStatus.CREATED).body(apiSessionService().createSession(session));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiMessageResponse(exception.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody ApiAuthRequest request) {
        try {
            UserSession session = authService.authenticate(valueOrEmpty(request == null ? null : request.email()),
                    valueOrEmpty(request == null ? null : request.password()));
            return ResponseEntity.ok(apiSessionService().createSession(session));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiMessageResponse(exception.getMessage()));
        }
    }

    @GetMapping("/me")
    public ApiSessionResponse currentSession(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        String token = requireBearerToken(authorizationHeader);
        requireSession(token);
        return apiSessionService().getSession(token)
                .orElseThrow();
    }

    @PostMapping("/logout")
    public ApiMessageResponse logout(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        String token = requireBearerToken(authorizationHeader);
        apiSessionService().invalidate(token);
        return new ApiMessageResponse("Signed out successfully.");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
