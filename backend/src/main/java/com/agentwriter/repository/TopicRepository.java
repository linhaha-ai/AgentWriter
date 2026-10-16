package com.agentwriter.repository;

import com.agentwriter.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByStatus(String status);

    List<Topic> findBySource(String source);

    List<Topic> findByStatusOrderByScoreDesc(String status);

    boolean existsByTitle(String title);
}
