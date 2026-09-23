package com.crispyland.agent.llm;

import java.util.List;

/**
 * One completed exchange of tool use: what the model asked for, and what it got back.
 * <p>
 * Kept as a list of rounds on the request rather than as extra {@code Message}s in the transcript,
 * and that separation is deliberate. These belong to the provider's wire format — they carry ids,
 * a {@code tool} role and a null content that the domain has no use for — and they are scaffolding
 * for one turn, not dialogue. The conversation records that the user asked and the assistant
 * answered; how many times the assistant stopped to look something up mid-answer is no more part
 * of the dialogue than the HTTP retries underneath it.
 */
public record ToolRound(List<ToolCall> calls, List<ToolResult> results) {

    public ToolRound {
        calls = List.copyOf(calls);
        results = List.copyOf(results);
    }
}
