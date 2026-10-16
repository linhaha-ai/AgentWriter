package com.agentwriter.repository;

import com.agentwriter.entity.WriterStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 写作风格 Repository
 */
@Repository
public interface WriterStyleRepository extends JpaRepository<WriterStyle, Long> {

    /** 查询所有已启用的风格 */
    List<WriterStyle> findByEnabledTrue();
}