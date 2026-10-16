package com.agentwriter.repository;

import com.agentwriter.entity.AgentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentLogRepository extends JpaRepository<AgentLog, Long> {

    List<AgentLog> findByLogId(Long logId);

    List<AgentLog> findByAgentName(String agentName);
}
