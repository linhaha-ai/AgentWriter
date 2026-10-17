package com.agentwriter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 资源图库图片素材。
 *
 * <p>{@code url} 存可直接访问的地址：本地生成/上传的图片为 {@code /media/files/gallery/...}
 * 相对路径（经后端静态资源映射访问），手动保存的站外链接为完整 URL。
 * {@code tags} 以逗号分隔存储，查询时按“逗号包裹匹配”精确筛选。</p>
 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

    /** 来源：GENERATED=AI 生成、UPLOADED=本地上传、URL=手动保存链接 */
    public static final String SOURCE_GENERATED = "GENERATED";
    public static final String SOURCE_UPLOADED = "UPLOADED";
    public static final String SOURCE_URL = "URL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 素材名称 */
    @Column(length = 200)
    private String name;

    /** 图片访问地址（相对路径或完整 URL） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    /** 生成提示词（仅 AI 生成的素材有值） */
    @Column(columnDefinition = "TEXT")
    private String prompt;

    /** 来源：GENERATED / UPLOADED / URL */
    @Column(nullable = false, length = 20)
    private String source = SOURCE_URL;

    /** 所属分类（删除分类后置空） */
    @Column(name = "category_id")
    private Long categoryId;

    /** 标签，逗号分隔（如 "海报,科技,蓝色"） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String tags = "";

    /** 图片宽度（像素，可选） */
    @Column
    private Integer width;

    /** 图片高度（像素，可选） */
    @Column(name = "height")
    private Integer height;

    /** 文件字节数（可选） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 图片 MIME 类型（可选） */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 创建时间（数据库默认 now()） */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间（应用侧维护） */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
