package com.agentwriter.service;

import com.agentwriter.dto.AdminTemplateRequest;
import com.agentwriter.entity.ArticleTemplate;
import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.repository.ArticleTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文章模板管理服务
 */
@Service
public class ArticleTemplateService {

    private final ArticleTemplateRepository repository;

    public ArticleTemplateService(ArticleTemplateRepository repository) {
        this.repository = repository;
    }

    public List<ArticleTemplate> findAll() {
        return repository.findAll();
    }

    public List<ArticleTemplate> findEnabled() {
        return repository.findByEnabledTrue();
    }

    public ArticleTemplate findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    public ArticleTemplate create(AdminTemplateRequest request) {
        var template = new ArticleTemplate();
        template.setName(request.name());
        template.setDescription(request.description());
        template.setTitleTemplate(request.titleTemplate());
        template.setOutlineTemplate(request.outlineTemplate());
        template.setCoverRule(request.coverRule());
        template.setEnabled(request.enabled() != null ? request.enabled() : true);
        return repository.save(template);
    }

    public ArticleTemplate update(Long id, AdminTemplateRequest request) {
        var template = findById(id);
        template.setName(request.name());
        template.setDescription(request.description());
        template.setTitleTemplate(request.titleTemplate());
        template.setOutlineTemplate(request.outlineTemplate());
        template.setCoverRule(request.coverRule());
        if (request.enabled() != null) {
            template.setEnabled(request.enabled());
        }
        return repository.save(template);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ArticleNotFoundException(id);
        }
        repository.deleteById(id);
    }
}