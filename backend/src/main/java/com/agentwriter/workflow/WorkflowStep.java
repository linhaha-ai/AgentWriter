package com.agentwriter.workflow;

/**
 * 工作流步骤接口
 */
public interface WorkflowStep {

    /**
     * 步骤名称，对应 execution_log.step
     */
    String name();

    /**
     * 执行步骤逻辑
     */
    void execute(WorkflowContext context) throws Exception;
}
