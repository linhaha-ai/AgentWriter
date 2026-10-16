package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.TopicCreateRequest;
import com.agentwriter.dto.TopicResponse;
import com.agentwriter.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @PostMapping
    public ApiResponse<TopicResponse> create(@Valid @RequestBody TopicCreateRequest request) {
        return ApiResponse.ok(topicService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TopicResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(topicService.findById(id));
    }

    @GetMapping
    public ApiResponse<List<TopicResponse>> findAll(@RequestParam(required = false) String status) {
        if (status != null) {
            return ApiResponse.ok(topicService.findByStatus(status));
        }
        return ApiResponse.ok(topicService.findAll());
    }

    @PostMapping("/{id}/select")
    public ApiResponse<TopicResponse> selectTopic(@PathVariable Long id) {
        return ApiResponse.ok(topicService.selectTopic(id));
    }

    /**
     * 手动触发话题采集：从 GitHub Trending / RSS 等来源抓取并保存到数据库。
     */
    @PostMapping("/collect")
    public ApiResponse<List<TopicResponse>> collect() {
        return ApiResponse.ok(topicService.collectTopics());
    }
}
