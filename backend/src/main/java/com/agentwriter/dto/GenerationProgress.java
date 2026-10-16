package com.agentwriter.dto;

/**
 * 生成进度对象 - 序列化为 JSON 存入 article.generation_progress 字段
 *
 * @param currentStep      当前执行步骤名称（如 research、outline、writer、review、seo）
 * @param totalSteps       总步骤数
 * @param currentStepIndex 当前步骤索引（从 0 开始）
 * @param stepStatus       当前步骤状态（running / success / failed）
 * @param progress         整体进度百分比（0-100）
 */
public record GenerationProgress(
        String currentStep,
        int totalSteps,
        int currentStepIndex,
        String stepStatus,
        int progress
) {
    /** 初始进度：生成即将开始 */
    public static GenerationProgress initial() {
        return new GenerationProgress("pending", 5, 0, "pending", 0);
    }

    /** 步骤进行中 */
    public static GenerationProgress running(String stepName, int stepIndex, int totalSteps) {
        int progress = Math.min((stepIndex * 100) / totalSteps, 95);
        return new GenerationProgress(stepName, totalSteps, stepIndex, "running", progress);
    }

    /** 步骤成功完成 */
    public static GenerationProgress completed(String stepName, int stepIndex, int totalSteps) {
        int progress = ((stepIndex + 1) * 100) / totalSteps;
        return new GenerationProgress(stepName, totalSteps, stepIndex, "success", progress);
    }

    /** 生成完成 */
    public static GenerationProgress done() {
        return new GenerationProgress("done", 5, 5, "success", 100);
    }

    /** 生成失败 */
    public static GenerationProgress failed(String stepName, int stepIndex) {
        return new GenerationProgress(stepName, 5, stepIndex, "failed",
                (stepIndex * 100) / 5);
    }
}