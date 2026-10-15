package com.schwab.agentic.agents;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentRegistry {

    private final Map<AgentRole, Agent> agents;

    public AgentRegistry(List<Agent> agents) {
        EnumMap<AgentRole, Agent> registered = new EnumMap<>(AgentRole.class);
        for (Agent agent : agents) {
            if (agent.role() == null || registered.putIfAbsent(agent.role(), agent) != null) {
                throw new IllegalStateException("Agent roles must be non-null and unique: " + agent.role());
            }
        }
        this.agents = Map.copyOf(registered);
    }

    public Agent agentFor(AgentRole role) {
        Agent agent = agents.get(role);
        if (agent == null) {
            throw new IllegalStateException("No agent is registered for role " + role);
        }
        return agent;
    }
}
