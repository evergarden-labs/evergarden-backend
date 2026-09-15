package com.evergarden.evergardenbackend.archive.controller;

import com.evergarden.evergardenbackend.archive.dto.ArchiveCreateRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.archive.dto.ArchiveUpdateRequest;
import com.evergarden.evergardenbackend.archive.service.ArchiveService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-01·02·03·04·05·09. 명세: {@code evergardenapi.yaml}의 {@code /archives}. */
@RestController
@RequestMapping("/archives")
@RequiredArgsConstructor
@Validated
public class ArchiveController {

    private final ArchiveService archiveService;

    @PostMapping
    public ApiResponse<ArchiveDetail> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody ArchiveCreateRequest request) {
        return ApiResponse.of(archiveService.create(me.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<ArchiveSummary>> list(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        CursorPage<ArchiveSummary> page = archiveService.list(me.userId(), cursor, size);
        return ApiResponse.of(page.items(), page.meta());
    }

    @GetMapping("/search")
    public ApiResponse<List<ArchiveSummary>> search(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        CursorPage<ArchiveSummary> page =
                archiveService.search(me.userId(), keyword, from, to, cursor, size);
        return ApiResponse.of(page.items(), page.meta());
    }

    @GetMapping("/{archiveId}")
    public ApiResponse<ArchiveDetail> get(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId) {
        return ApiResponse.of(archiveService.get(me.userId(), archiveId));
    }

    @PatchMapping("/{archiveId}")
    public ApiResponse<ArchiveDetail> update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @Valid @RequestBody ArchiveUpdateRequest request) {
        return ApiResponse.of(archiveService.update(me.userId(), archiveId, request));
    }

    @DeleteMapping("/{archiveId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId) {
        archiveService.delete(me.userId(), archiveId);
        return ApiResponse.empty();
    }
}
