package com.evergarden.evergardenbackend.archive.controller;

import com.evergarden.evergardenbackend.archive.dto.ArchiveCoverRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemResponse;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemUpdateRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemsAddRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveLayoutRequest;
import com.evergarden.evergardenbackend.archive.service.ArchiveItemService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-06·07·08. 명세: {@code evergardenapi.yaml}의 {@code /archives/{archiveId}/items} 계열. */
@RestController
@RequestMapping("/archives/{archiveId}")
@RequiredArgsConstructor
public class ArchiveItemController {

    private final ArchiveItemService archiveItemService;

    @PostMapping("/items")
    public ApiResponse<List<ArchiveItemResponse>> addItems(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @Valid @RequestBody ArchiveItemsAddRequest request) {
        return ApiResponse.of(archiveItemService.addItems(me.userId(), archiveId, request));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<ArchiveItemResponse> updateItem(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @PathVariable Long itemId,
            @Valid @RequestBody ArchiveItemUpdateRequest request) {
        return ApiResponse.of(archiveItemService.updateItem(me.userId(), archiveId, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> removeItem(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @PathVariable Long itemId) {
        archiveItemService.removeItem(me.userId(), archiveId, itemId);
        return ApiResponse.empty();
    }

    @PutMapping("/items/layout")
    public ApiResponse<ArchiveDetail> replaceLayout(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @Valid @RequestBody ArchiveLayoutRequest request) {
        return ApiResponse.of(archiveItemService.replaceLayout(me.userId(), archiveId, request));
    }

    @PutMapping("/cover")
    public ApiResponse<ArchiveDetail> setCover(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @Valid @RequestBody ArchiveCoverRequest request) {
        return ApiResponse.of(archiveItemService.setCover(me.userId(), archiveId, request));
    }
}
