package com.agentwriter.workflow;

import com.agentwriter.dto.GenerationEvent;
import com.agentwriter.dto.ProgressResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 维护文章生成 SSE 订阅，并向同一文章的所有页面广播增量事件。
 */
@Component
public class GenerationEventHub {

    private final Map<Long, Set<SseEmitter>> emittersByArticle = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long articleId, ProgressResponse snapshot) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByArticle.computeIfAbsent(articleId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(articleId, emitter));
        emitter.onTimeout(() -> remove(articleId, emitter));
        emitter.onError(ignored -> remove(articleId, emitter));

        send(articleId, emitter, SseEmitter.event().name("snapshot").data(snapshot));
        if (isTerminal(snapshot.status())) {
            completeArticle(articleId);
        }
        return emitter;
    }

    public void publishProgress(GenerationEvent event) {
        publish(event.articleId(), "progress", event);
    }

    public void publishLog(GenerationEvent event) {
        publish(event.articleId(), "log", event);
    }

    public void publishTerminal(String eventName, GenerationEvent event) {
        publish(event.articleId(), eventName, event);
        completeArticle(event.articleId());
    }

    @Scheduled(fixedDelay = 15_000)
    void sendHeartbeat() {
        emittersByArticle.forEach((articleId, emitters) ->
                emitters.forEach(emitter -> send(
                        articleId,
                        emitter,
                        SseEmitter.event().comment("keepalive")
                ))
        );
    }

    int subscriberCount(Long articleId) {
        Set<SseEmitter> emitters = emittersByArticle.get(articleId);
        return emitters == null ? 0 : emitters.size();
    }

    private void publish(Long articleId, String eventName, GenerationEvent event) {
        Set<SseEmitter> emitters = emittersByArticle.get(articleId);
        if (emitters == null) {
            return;
        }
        emitters.forEach(emitter -> send(articleId, emitter, SseEmitter.event().name(eventName).data(event)));
    }

    private void send(Long articleId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception ignored) {
            remove(articleId, emitter);
            emitter.complete();
        }
    }

    private void completeArticle(Long articleId) {
        Set<SseEmitter> emitters = emittersByArticle.remove(articleId);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
        }
    }

    private void remove(Long articleId, SseEmitter emitter) {
        emittersByArticle.computeIfPresent(articleId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private static boolean isTerminal(String status) {
        return "generated".equals(status) || "failed".equals(status);
    }
}
