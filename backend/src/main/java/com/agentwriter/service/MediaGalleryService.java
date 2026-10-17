package com.agentwriter.service;

import com.agentwriter.config.ImageProperties;
import com.agentwriter.dto.MediaAssetView;
import com.agentwriter.dto.MediaCategoryView;
import com.agentwriter.dto.TagCount;
import com.agentwriter.entity.MediaAsset;
import com.agentwriter.entity.MediaCategory;
import com.agentwriter.exception.ImageGenerationUnavailableException;
import com.agentwriter.repository.MediaAssetRepository;
import com.agentwriter.repository.MediaCategoryRepository;
import com.agentwriter.service.OpenAiCompatibleImageClient.GeneratedImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 资源图库业务：素材 CRUD、分类管理、标签聚合，以及 AI 生成/上传/链接三种入库来源。
 *
 * <p>本地图片（生成/上传）统一写入 {@code {storage-directory}/gallery/} 目录，
 * 素材的 {@code url} 保存为 {@code /media/files/gallery/...} 相对路径，
 * 由 {@link com.agentwriter.config.WebConfig} 静态映射对外提供访问。</p>
 */
@Service
public class MediaGalleryService {

    /** 本地文件对外访问的 URL 前缀（与 WebConfig 的静态映射对应） */
    public static final String MEDIA_URL_PREFIX = "/media/files/";
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int MAX_PAGE_SIZE = 100;

    private final MediaAssetRepository assetRepository;
    private final MediaCategoryRepository categoryRepository;
    private final OpenAiCompatibleImageClient imageClient;
    private final ImageProperties imageProperties;

    public MediaGalleryService(
            MediaAssetRepository assetRepository,
            MediaCategoryRepository categoryRepository,
            OpenAiCompatibleImageClient imageClient,
            ImageProperties imageProperties
    ) {
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.imageClient = imageClient;
        this.imageProperties = imageProperties;
    }

    // ==================== 素材查询 ====================

    public Page<MediaAssetView> search(Long categoryId, String tag, String keyword, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MediaAsset> result = assetRepository.search(categoryId, blankToNull(tag), blankToNull(keyword), pageable);
        return result.map(this::toView);
    }

    public MediaAssetView get(Long id) {
        return toView(requireAsset(id));
    }

    // ==================== 分类管理 ====================

    public List<MediaCategoryView> listCategories() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (MediaCategory category : categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder", "id"))) {
            counts.put(category.getId(), assetRepository.countByCategoryId(category.getId()));
        }
        return counts.entrySet().stream()
                .map(entry -> toCategoryView(categoryRepository.findById(entry.getKey()).orElseThrow(), entry.getValue()))
                .toList();
    }

    public MediaCategoryView createCategory(String name, String description, Integer sortOrder) {
        String cleanName = cleanName(name);
        if (categoryRepository.existsByName(cleanName)) {
            throw new IllegalArgumentException("分类名称已存在：" + cleanName);
        }
        MediaCategory category = new MediaCategory();
        category.setName(cleanName);
        category.setDescription(cleanText(description, 500));
        category.setSortOrder(sortOrder == null ? 0 : sortOrder);
        categoryRepository.save(category);
        return toCategoryView(category, 0L);
    }

    public MediaCategoryView updateCategory(Long id, String name, String description, Integer sortOrder) {
        MediaCategory category = requireCategory(id);
        String cleanName = cleanName(name);
        if (categoryRepository.existsByNameAndIdNot(cleanName, id)) {
            throw new IllegalArgumentException("分类名称已存在：" + cleanName);
        }
        category.setName(cleanName);
        category.setDescription(cleanText(description, 500));
        category.setSortOrder(sortOrder == null ? 0 : sortOrder);
        categoryRepository.save(category);
        return toCategoryView(category, assetRepository.countByCategoryId(id));
    }

    /** 删除分类：先把素材分类引用置空再删除 */
    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id);
        assetRepository.clearCategory(id);
        categoryRepository.deleteById(id);
    }

    // ==================== 标签 ====================

    public List<TagCount> listTags() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String tagString : assetRepository.findAllTagStrings()) {
            for (String tag : splitTags(tagString)) {
                counts.merge(tag, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new TagCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    // ==================== 素材入库 ====================

    /** 保存站外图片链接 */
    public MediaAssetView createFromUrl(String name, String url, Long categoryId, String tags) {
        if (url == null || url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("图片链接必须是 http(s) 绝对地址");
        }
        MediaAsset asset = new MediaAsset();
        asset.setName(cleanName(name));
        asset.setUrl(url.trim());
        asset.setSource(MediaAsset.SOURCE_URL);
        asset.setCategoryId(validCategoryOrNull(categoryId));
        asset.setTags(joinTags(tags));
        asset.setUpdatedAt(LocalDateTime.now());
        return toView(assetRepository.save(asset));
    }

    /** 本地上传图片 */
    public MediaAssetView upload(String name, MultipartFile file, Long categoryId, String tags) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片文件");
        }
        String contentType = file.getContentType();
        if (!isImageContentType(contentType)) {
            throw new IllegalArgumentException("仅支持图片文件（png/jpg/jpeg/gif/webp）");
        }
        String extension = extensionOf(contentType, file.getOriginalFilename());
        try {
            byte[] bytes = file.getBytes();
            String storageKey = storeFile(bytes, extension);
            MediaAsset asset = new MediaAsset();
            asset.setName(cleanName(name));
            asset.setUrl(MEDIA_URL_PREFIX + storageKey);
            asset.setSource(MediaAsset.SOURCE_UPLOADED);
            asset.setCategoryId(validCategoryOrNull(categoryId));
            asset.setTags(joinTags(tags));
            asset.setFileSize((long) bytes.length);
            asset.setContentType(contentType);
            asset.setUpdatedAt(LocalDateTime.now());
            return toView(assetRepository.save(asset));
        } catch (IOException ex) {
            throw new ImageGenerationUnavailableException("读取上传文件失败", ex);
        }
    }

    /** AI 生成图片并入图库 */
    public MediaAssetView generate(String prompt, String size, String name, Long categoryId, String tags) {
        GeneratedImage generated = imageClient.generate(prompt, size);
        String extension = extensionOf(generated.contentType(), null);
        String storageKey = storeFile(generated.bytes(), extension);
        MediaAsset asset = new MediaAsset();
        asset.setName(cleanName(name));
        asset.setUrl(MEDIA_URL_PREFIX + storageKey);
        asset.setPrompt(prompt.trim());
        asset.setSource(MediaAsset.SOURCE_GENERATED);
        asset.setCategoryId(validCategoryOrNull(categoryId));
        asset.setTags(joinTags(tags));
        asset.setFileSize((long) generated.bytes().length);
        asset.setContentType(generated.contentType());
        asset.setUpdatedAt(LocalDateTime.now());
        return toView(assetRepository.save(asset));
    }

    /** 更新素材元数据（名称/分类/标签） */
    public MediaAssetView update(Long id, String name, Long categoryId, String tags) {
        MediaAsset asset = requireAsset(id);
        asset.setName(cleanName(name));
        asset.setCategoryId(validCategoryOrNull(categoryId));
        asset.setTags(joinTags(tags));
        asset.setUpdatedAt(LocalDateTime.now());
        return toView(assetRepository.save(asset));
    }

    /** 删除素材（本地文件一并删除） */
    @Transactional
    public void delete(Long id) {
        MediaAsset asset = requireAsset(id);
        assetRepository.delete(asset);
        deleteLocalFile(asset.getUrl());
    }

    // ==================== 内部工具 ====================

    private MediaAsset requireAsset(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("图片素材不存在（ID=" + id + "）"));
    }

    private MediaCategory requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在（ID=" + id + "）"));
    }

    private Long validCategoryOrNull(Long categoryId) {
        return categoryId == null ? null : requireCategory(categoryId).getId();
    }

    private MediaAssetView toView(MediaAsset asset) {
        MediaCategory category = asset.getCategoryId() == null
                ? null
                : categoryRepository.findById(asset.getCategoryId()).orElse(null);
        return new MediaAssetView(
                asset.getId(),
                asset.getName(),
                asset.getUrl(),
                asset.getPrompt(),
                asset.getSource(),
                asset.getCategoryId(),
                category == null ? null : category.getName(),
                splitTags(asset.getTags()),
                asset.getWidth(),
                asset.getHeight(),
                asset.getFileSize(),
                asset.getContentType(),
                formatTime(asset.getCreatedAt()),
                formatTime(asset.getUpdatedAt())
        );
    }

    private MediaCategoryView toCategoryView(MediaCategory category, Long assetCount) {
        return new MediaCategoryView(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getSortOrder(),
                assetCount,
                formatTime(category.getCreatedAt())
        );
    }

    /** 写入本地存储，返回相对存储目录的 key（gallery/yyyyMM/uuid.ext） */
    private String storeFile(byte[] bytes, String extension) {
        Path galleryRoot = galleryRootDirectory();
        String month = LocalDateTime.now().format(MONTH_DIR);
        String key = "gallery/" + month + "/" + UUID.randomUUID() + extension;
        Path target = resolveSafe(galleryRoot, key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return key;
        } catch (IOException ex) {
            throw new ImageGenerationUnavailableException("保存图片文件失败", ex);
        }
    }

    /** 删除本地文件（仅当 url 是本站 /media/files/ 前缀的路径） */
    private void deleteLocalFile(String url) {
        if (url == null || !url.startsWith(MEDIA_URL_PREFIX)) {
            return;
        }
        String key = url.substring(MEDIA_URL_PREFIX.length());
        Path target = resolveSafe(galleryRootDirectory(), key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            // 文件删除失败不阻断记录删除，仅记录日志由上层处理
            throw new ImageGenerationUnavailableException("删除图片文件失败：" + target.getFileName(), ex);
        }
    }

    private Path galleryRootDirectory() {
        String directory = imageProperties.getStorageDirectory();
        if (directory == null || directory.isBlank()) {
            throw new ImageGenerationUnavailableException("未配置图片存储目录（agentwriter.image.storage-directory）");
        }
        return Path.of(directory).toAbsolutePath().normalize();
    }

    private static Path resolveSafe(Path root, String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("图片文件路径无效");
        }
        return target;
    }

    private static boolean isImageContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.equals("image/png") || type.equals("image/jpeg") || type.equals("image/jpg")
                || type.equals("image/gif") || type.equals("image/webp") || type.startsWith("image/");
    }

    private static String extensionOf(String contentType, String originalFilename) {
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase(Locale.ROOT);
            if (lower.matches(".*\\.(?i:png|jpe?g|gif|webp)$")) {
                return lower.substring(lower.lastIndexOf('.'));
            }
        }
        if (contentType != null) {
            return switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                case "image/jpeg", "image/jpg" -> ".jpg";
                default -> ".png";
            };
        }
        return ".png";
    }

    /** 标签规范化：逗号分隔 → 去空白 → 去重 → 逗号连接 */
    private static String joinTags(String tags) {
        return String.join(",", splitTags(tags));
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String raw : tags.split("[,，]")) {
            String tag = raw.trim();
            if (!tag.isEmpty() && !result.contains(tag)) {
                result.add(tag);
            }
        }
        return result;
    }

    private static String cleanName(String name) {
        return cleanText(name, 200);
    }

    private static String cleanText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
