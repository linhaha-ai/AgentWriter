package com.agentwriter.repository;

import com.agentwriter.agent.Publisher;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.service.ArticlePublicationService;
import com.agentwriter.service.ArticleService;
import com.agentwriter.exception.ArticleTransitionConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig
@ContextConfiguration(classes = ArticleRepositoryPostgresRaceTest.PostgresConfig.class)
@EnabledIfSystemProperty(named = "task8.postgres", matches = "true")
class ArticleRepositoryPostgresRaceTest {

    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleService articleService;

    @Test
    void realPostgresConditionalUpdateAllowsOneProviderCallAndOneConflict() throws Exception {
        Article article = new Article();
        article.setTitle("PostgreSQL race");
        article.setStatus("generated");
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        Long articleId = articleRepository.saveAndFlush(article).getId();

        AtomicInteger providerCalls = new AtomicInteger();
        Publisher publisher = target -> {
            providerCalls.incrementAndGet();
            return new PublishResult("postgres-test", null, LocalDateTime.now(), target.getId(), null);
        };
        ArticlePublicationService publicationService = new ArticlePublicationService(
                articleService, publisher, mock(ExecutionLogRepository.class), new ObjectMapper());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        outcomes.add(publicationService.publish(articleId));
                    } catch (ArticleTransitionConflictException exception) {
                        outcomes.add(exception);
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }

        assertThat(providerCalls).hasValue(1);
        assertThat(outcomes.stream().filter(ArticleTransitionConflictException.class::isInstance)).hasSize(1);
        assertThat(articleRepository.findById(articleId).orElseThrow().getStatus()).isEqualTo("publishing");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ArticleRepository.class)
    static class PostgresConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(System.getProperty("task8.jdbc.url"));
            dataSource.setUsername(System.getProperty("task8.jdbc.username"));
            dataSource.setPassword(System.getProperty("task8.jdbc.password"));
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.agentwriter.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        ArticleService articleService(ArticleRepository articleRepository) {
            return new ArticleService(articleRepository);
        }
    }
}
