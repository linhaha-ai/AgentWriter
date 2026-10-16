package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.AdminStyleRequest;
import com.agentwriter.entity.WriterStyle;
import com.agentwriter.service.WriterStyleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台写作风格管理
 */
@RestController
@RequestMapping("/api/admin/styles")
public class AdminStyleController {

    private final WriterStyleService styleService;

    public AdminStyleController(WriterStyleService styleService) {
        this.styleService = styleService;
    }

    @GetMapping
    public ApiResponse<List<WriterStyle>> findAll() {
        return ApiResponse.ok(styleService.findAll());
    }

    @GetMapping("/enabled")
    public ApiResponse<List<WriterStyle>> findEnabled() {
        return ApiResponse.ok(styleService.findEnabled());
    }

    @GetMapping("/{id}")
    public ApiResponse<WriterStyle> findById(@PathVariable Long id) {
        return ApiResponse.ok(styleService.findById(id));
    }

    @PostMapping
    public ApiResponse<WriterStyle> create(@Valid @RequestBody AdminStyleRequest request) {
        return ApiResponse.ok(styleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WriterStyle> update(@PathVariable Long id,
                                           @Valid @RequestBody AdminStyleRequest request) {
        return ApiResponse.ok(styleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        styleService.delete(id);
        return ApiResponse.ok(null);
    }
}