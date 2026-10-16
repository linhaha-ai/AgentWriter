package com.agentwriter.service;

import com.agentwriter.dto.AdminStyleRequest;
import com.agentwriter.entity.WriterStyle;
import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.repository.WriterStyleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 写作风格管理服务
 */
@Service
public class WriterStyleService {

    private final WriterStyleRepository repository;

    public WriterStyleService(WriterStyleRepository repository) {
        this.repository = repository;
    }

    public List<WriterStyle> findAll() {
        return repository.findAll();
    }

    public List<WriterStyle> findEnabled() {
        return repository.findByEnabledTrue();
    }

    public WriterStyle findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    public WriterStyle create(AdminStyleRequest request) {
        var style = new WriterStyle();
        style.setName(request.name());
        style.setDescription(request.description());
        style.setTonePrompt(request.tonePrompt());
        style.setFeatures(request.features());
        style.setEnabled(request.enabled() != null ? request.enabled() : true);
        return repository.save(style);
    }

    public WriterStyle update(Long id, AdminStyleRequest request) {
        var style = findById(id);
        style.setName(request.name());
        style.setDescription(request.description());
        style.setTonePrompt(request.tonePrompt());
        style.setFeatures(request.features());
        if (request.enabled() != null) {
            style.setEnabled(request.enabled());
        }
        return repository.save(style);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ArticleNotFoundException(id);
        }
        repository.deleteById(id);
    }
}