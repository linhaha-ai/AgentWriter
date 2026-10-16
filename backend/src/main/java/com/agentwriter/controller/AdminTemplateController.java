package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.AdminTemplateRequest;
import com.agentwriter.entity.ArticleTemplate;
import com.agentwriter.service.ArticleTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台文章模板管理
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplateController {

    private final ArticleTemplateService templateService;

    public AdminTemplateController(ArticleTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<ArticleTemplate>> findAll() {
        return ApiResponse.ok(templateService.findAll());
    }

    @GetMapping("/enabled")
    public ApiResponse<List<ArticleTemplate>> findEnabled() {
        return ApiResponse.ok(templateService.findEnabled());
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleTemplate> findById(@PathVariable Long id) {
        return ApiResponse.ok(templateService.findById(id));
    }

    @PostMapping
    public ApiResponse<ArticleTemplate> create(@Valid @RequestBody AdminTemplateRequest request) {
        return ApiResponse.ok(templateService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ArticleTemplate> update(@PathVariable Long id,
                                               @Valid @RequestBody AdminTemplateRequest request) {
        return ApiResponse.ok(templateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ApiResponse.ok(null);
    }
}