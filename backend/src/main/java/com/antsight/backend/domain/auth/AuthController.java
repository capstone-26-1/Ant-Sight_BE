package com.antsight.backend.domain.auth;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.common.response.ApiResponse;
import com.antsight.backend.domain.auth.dto.AuthResponse;
import com.antsight.backend.domain.auth.dto.LoginRequest;
import com.antsight.backend.domain.auth.dto.SignupRequest;
import com.antsight.backend.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        return ApiResponse.ok(AuthResponse.of(null, user));
    }
}
