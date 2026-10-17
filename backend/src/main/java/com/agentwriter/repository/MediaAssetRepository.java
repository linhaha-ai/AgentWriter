package com.agentwriter.repository;

import com.agentwriter.entity.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图库图片素材数据访问。
 */
@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /**
     * 按分类/标签/关键字分页查询素材。
     *
     * <p>标签用「逗号包裹匹配」避免子串误命中（如 tag=a 不会匹配到 ab）。
     * 关键字模糊匹配素材名称与生成提示词。</p>
     *
     * @param categoryId 分类 ID（null 表示不过滤）
     * @param tag        标签（null 或空白表示不过滤）
     * @param keyword    名称/提示词关键字（null 或空白表示不过滤）
     */
    @Query("""
            select m from MediaAsset m
            where (:categoryId is null or m.categoryId = :categoryId)
              and (:tag is null or :tag = '' or concat(',', m.tags, ',') like concat('%,', :tag, ',%'))
              and (:keyword is null or :keyword = '' or lower(coalesce(m.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.prompt, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<MediaAsset> search(@Param("categoryId") Long categoryId,
                            @Param("tag") String tag,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    /** 统计某分类下的素材数（分类计数用） */
    long countByCategoryId(Long categoryId);

    /** 素材分类被删除时把引用置空（controller 删除分类前调用） */
    @Modifying
    @Query("update MediaAsset m set m.categoryId = null where m.categoryId = :categoryId")
    int clearCategory(@Param("categoryId") Long categoryId);

    /** 聚合全部标签（用于图库标签筛选列表） */
    @Query("select m.tags from MediaAsset m where m.tags is not null and m.tags <> ''")
    List<String> findAllTagStrings();
}
