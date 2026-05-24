package com.antsight.backend.domain.bookmark.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BookmarkRequest {

    @NotBlank(message = "티커를 입력해주세요.")
    private String ticker;
}
