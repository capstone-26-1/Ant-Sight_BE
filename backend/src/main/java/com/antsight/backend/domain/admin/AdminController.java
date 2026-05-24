package com.antsight.backend.domain.admin;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.common.response.ApiResponse;
import com.antsight.backend.domain.user.User;
import com.antsight.backend.domain.user.UserRepository;
import com.antsight.backend.domain.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<List<UserSummary>> listUsers() {
        List<UserSummary> users = userRepository.findAll()
                .stream()
                .map(UserSummary::from)
                .toList();
        return ApiResponse.ok(users);
    }

    @PatchMapping("/{id}/block")
    @Transactional
    public ApiResponse<Void> blockUser(@PathVariable Long id,
                                       @AuthenticationPrincipal User admin) {
        User target = findUser(id);
        if (target.getId().equals(admin.getId())) {
            throw new ApiException(ErrorCode.CANNOT_BLOCK_SELF);
        }
        target.block();
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/unblock")
    @Transactional
    public ApiResponse<Void> unblockUser(@PathVariable Long id,
                                         @AuthenticationPrincipal User admin) {
        User target = findUser(id);
        target.unblock(admin.getId());
        return ApiResponse.ok(null);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
