package com.antsight.backend.domain.bookmark;

import com.antsight.backend.common.exception.ApiException;
import com.antsight.backend.common.exception.ErrorCode;
import com.antsight.backend.common.response.ApiResponse;
import com.antsight.backend.domain.bookmark.dto.BookmarkRequest;
import com.antsight.backend.domain.bookmark.dto.BookmarkResponse;
import com.antsight.backend.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping
    public ApiResponse<List<BookmarkResponse>> getBookmarks(@AuthenticationPrincipal User user) {
        requireAuthenticated(user);
        return ApiResponse.ok(bookmarkService.getBookmarks(user.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> addBookmark(@AuthenticationPrincipal User user,
                                         @Valid @RequestBody BookmarkRequest request) {
        requireAuthenticated(user);
        bookmarkService.addBookmark(user.getId(), request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{ticker}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(@AuthenticationPrincipal User user,
                               @PathVariable String ticker) {
        requireAuthenticated(user);
        bookmarkService.removeBookmark(user.getId(), ticker);
    }

    private void requireAuthenticated(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
    }
}
