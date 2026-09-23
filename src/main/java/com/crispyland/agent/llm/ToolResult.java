package com.crispyland.agent.llm;

/**
 * What running a tool produced, on its way back to the model.
 *
 * @param failed whether the tool refused or broke. The text still goes back either way, and that
 *               is the point: a model told "that currency code does not exist" can correct itself
 *               and ask again, whereas a turn aborted on the exception just loses. This flag
 *               exists so the failure can be logged and shown as a failure, not so it can be
 *               hidden from the model
 */
public record ToolResult(String callId, String name, String content, boolean failed) {

    public static ToolResult of(ToolCall call, String content) {
        return new ToolResult(call.id(), call.name(), content, false);
    }

    public static ToolResult failed(ToolCall call, String problem) {
        return new ToolResult(call.id(), call.name(), problem, true);
    }
}
