package com.agentwriter.repository;

import com.agentwriter.entity.MediaCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 图库分类数据访问。
 */
@Repository
public interface MediaCategoryRepository extends JpaRepository<MediaCategory, Long> {

    /** 按名称查重（分类名唯一） */
    boolean existsByName(String name);

    /** 分类是否存在（排除指定 id，用于重命名时查重） */
    boolean existsByNameAndIdNot(String name, Long id);
}
