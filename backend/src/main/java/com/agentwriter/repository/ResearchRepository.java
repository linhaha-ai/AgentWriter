package com.agentwriter.repository;

import com.agentwriter.entity.Research;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResearchRepository extends JpaRepository<Research, Long> {

    List<Research> findByTopicId(Long topicId);
}
