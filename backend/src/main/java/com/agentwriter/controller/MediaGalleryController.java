package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.MediaAssetPage;
import com.agentwriter.dto.MediaAssetView;
import com.agentwriter.dto.MediaCategoryView;
import com.agentwriter.dto.TagCount;
import com.agentwriter.service.MediaGalleryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 资源图库接口。
 *
 * <p>素材来源三种：AI 生成（POST /generate）、本地上传（POST /upload）、
 * 保存站外链接（POST /url）。分类与标签均可独立管理。</p>
 */
@RestController
@RequestMapping("/api/gallery")
public class MediaGalleryController {

    private final MediaGalleryService galleryService;

    public MediaGalleryController(MediaGalleryService galleryService) {
        this.galleryService = galleryService;
    }

    // ==================== 素材 ====================

    /** 分页查询素材，支持分类 / 标签 / 关键字筛选 */
    @GetMapping
    public ApiResponse<MediaAssetPage> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        Page<MediaAssetView> result = galleryService.search(categoryId, tag, keyword, page, size);
        return ApiResponse.ok(new MediaAssetPage(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements()));
    }

    /** 素材详情 */
    @GetMapping("/{id}")
    public ApiResponse<MediaAssetView> get(@PathVariable Long id) {
        return ApiResponse.ok(galleryService.get(id));
    }

    /** 保存站外图片链接 */
    @PostMapping("/url")
    public ApiResponse<MediaAssetView> createFromUrl(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(galleryService.createFromUrl(
                asString(body.get("name")), asString(body.get("url")),
                asLong(body.get("categoryId")), asString(body.get("tags"))));
    }

    /** 本地上传图片 */
    @PostMapping("/upload")
    public ApiResponse<MediaAssetView> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tags
    ) {
        return ApiResponse.ok(galleryService.upload(name, file, categoryId, tags));
    }

    /** AI 生成图片 */
    @PostMapping("/generate")
    public ApiResponse<MediaAssetView> generate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(galleryService.generate(
                asString(body.get("prompt")), asString(body.get("size")),
                asString(body.get("name")), asLong(body.get("categoryId")), asString(body.get("tags"))));
    }

    /** 更新素材元数据（名称/分类/标签） */
    @PutMapping("/{id}")
    public ApiResponse<MediaAssetView> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(galleryService.update(
                id, asString(body.get("name")), asLong(body.get("categoryId")), asString(body.get("tags"))));
    }

    /** 删除素材（本地文件一并删除） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        galleryService.delete(id);
        return ApiResponse.ok();
    }

    // ==================== 分类 ====================

    /** 全部分类（含素材计数，按 sortOrder 升序） */
    @GetMapping("/categories")
    public ApiResponse<List<MediaCategoryView>> categories() {
        return ApiResponse.ok(galleryService.listCategories());
    }

    /** 新增分类 */
    @PostMapping("/categories")
    public ApiResponse<MediaCategoryView> createCategory(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(galleryService.createCategory(
                asString(body.get("name")), asString(body.get("description")), asInteger(body.get("sortOrder"))));
    }

    /** 更新分类 */
    @PutMapping("/categories/{id}")
    public ApiResponse<MediaCategoryView> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(galleryService.updateCategory(
                id, asString(body.get("name")), asString(body.get("description")), asInteger(body.get("sortOrder"))));
    }

    /** 删除分类（已有素材的分类引用置空） */
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        galleryService.deleteCategory(id);
        return ApiResponse.ok();
    }

    // ==================== 标签 ====================

    /** 全部标签及使用计数（按使用数降序） */
    @GetMapping("/tags")
    public ApiResponse<List<TagCount>> tags() {
        return ApiResponse.ok(galleryService.listTags());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long asLong(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer asInteger(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
