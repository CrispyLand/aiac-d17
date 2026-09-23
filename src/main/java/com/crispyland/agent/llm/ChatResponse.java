package com.crispyland.agent.llm;

import com.crispyland.agent.usage.TokenUsage;
import java.util.List;

/**
 * Provider-neutral parsed reply. The agent never sees raw JSON.
 *
 * @param toolCalls what the model wants run before it will answer, or empty on the ordinary turn
 *                  where it simply answered. More than one is normal and means they are meant to
 *                  run together, not in sequence
 */
public record ChatResponse(String content, String model, String finishReason, TokenUsage usage,
                           List<ToolCall> toolCalls) {

    public ChatResponse {
        usage = (usage == null) ? TokenUsage.NONE : usage;
        toolCalls = (toolCalls == null) ? List.of() : List.copyOf(toolCalls);
    }

    /** A reply that asked for nothing to be run. */
    public ChatResponse(String content, String model, String finishReason, TokenUsage usage) {
        this(content, model, finishReason, usage, List.of());
    }

    public boolean wantsTools() {
        return !toolCalls.isEmpty();
    }
}
