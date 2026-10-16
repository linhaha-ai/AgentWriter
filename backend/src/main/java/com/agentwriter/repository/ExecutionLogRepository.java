package com.agentwriter.repository;

import com.agentwriter.entity.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    List<ExecutionLog> findByArticleId(Long articleId);

    List<ExecutionLog> findByArticleIdOrderByCreatedAtAsc(Long articleId);

    List<ExecutionLog> findByStep(String step);

    List<ExecutionLog> findByStatus(String status);

    List<ExecutionLog> findTop20ByOrderByCreatedAtDesc();
}
