package com.schwab.agentic.agents;

import com.schwab.agentic.guardrails.ArtifactVersionService;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentTaskHandlerConfiguration {

    @Bean
    AgentTaskHandler requirementsTaskHandler(
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return handler(TaskKind.REQUIREMENTS, AgentRole.REQUIREMENTS, agents, artifacts, approvals, persistence);
    }

    @Bean
    AgentTaskHandler architectureTaskHandler(
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return handler(TaskKind.ARCHITECTURE, AgentRole.ARCHITECT, agents, artifacts, approvals, persistence);
    }

    @Bean
    AgentTaskHandler codeTaskHandler(
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return handler(TaskKind.CODE, AgentRole.CODER, agents, artifacts, approvals, persistence);
    }

    @Bean
    AgentTaskHandler testTaskHandler(
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return handler(TaskKind.TEST, AgentRole.TESTER, agents, artifacts, approvals, persistence);
    }

    @Bean
    AgentTaskHandler reviewTaskHandler(
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return handler(TaskKind.REVIEW, AgentRole.REVIEWER, agents, artifacts, approvals, persistence);
    }

    @Bean
    AgentTaskHandler securityTaskHandler(
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return handler(TaskKind.SECURITY, AgentRole.SECURITY, agents, artifacts, approvals, persistence);
    }

    @Bean
    AgentTaskHandler documentationTaskHandler(
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return handler(
                TaskKind.DOCUMENTATION, AgentRole.DOCUMENTATION, agents, artifacts, approvals, persistence);
    }

    private AgentTaskHandler handler(
            TaskKind kind,
            AgentRole role,
            AgentRegistry agents,
            ArtifactVersionService artifacts,
            ApprovalService approvals,
            OrchestrationPersistenceService persistence) {
        return new AgentTaskHandler(kind, agents.agentFor(role), artifacts, approvals, persistence);
    }
}
