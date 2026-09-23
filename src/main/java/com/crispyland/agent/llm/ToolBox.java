package com.crispyland.agent.llm;

import java.util.List;

/**
 * Whatever the agent is allowed to do besides talk.
 * <p>
 * An interface in this package, rather than the agent reaching into the MCP classes, so that the
 * dependency runs one way: MCP knows what an agent wants, the agent does not know MCP exists. The
 * point is worth the extra file — a pipeline that imported {@code McpSyncClient} would be an agent
 * that could only ever have MCP tools, which is precisely the coupling the protocol exists to
 * remove.
 */
public interface ToolBox {

    /** A toolbox that is always empty. What the agent uses when nothing has been wired in. */
    ToolBox NONE = new ToolBox() {
        @Override
        public List<ToolSpec> available() {
            return List.of();
        }

        @Override
        public ToolResult call(ToolCall call) {
            return ToolResult.failed(call, "No tools are available.");
        }
    };

    /**
     * What to offer the model on this turn, or empty to offer nothing.
     * <p>
     * Asked once per turn rather than cached, because the answer can change between one message
     * and the next — a switch gets thrown, a server goes down — and a model told about a tool that
     * is no longer there will call it and get an error instead of an answer.
     */
    List<ToolSpec> available();

    /**
     * Runs one call and returns what to tell the model.
     * <p>
     * Must not throw. Everything that can go wrong here — an unknown name, a bad argument, a dead
     * server — is information the model can act on, and is far more useful returned as a failed
     * {@link ToolResult} than thrown as an exception that ends a turn the user was waiting on.
     */
    ToolResult call(ToolCall call);
}
