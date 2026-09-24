package com.crispyland.agent.memory;

import java.util.List;

/**
 * What one message cost, captured when it was recorded.
 * <p>
 * The provider reports usage per turn, not per message, so the turn's numbers are split
 * the way they were actually earned: the user message carries the prompt tokens (the whole
 * context replayed to produce it), the assistant message carries the completion tokens plus
 * the turn total, latency and finish reason.
 *
 * @param toolsUsed which tools the answer was not written without, in the order they ran, a name
 *                  repeated if it ran twice. This is the one trace of tool use that outlives the
 *                  turn: the calls and their results are request scaffolding and are deliberately
 *                  not kept, but <em>that</em> an answer needed looking something up is a property
 *                  of the answer, and a reader deciding whether to trust a date or a balance wants
 *                  it next to the answer rather than in a log they do not have
 */
public record MessageStats(
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long latencyMillis,
        String model,
        String finishReason,
        List<String> toolsUsed) {

    public MessageStats {
        // Never null, so neither the view nor the store has to ask. An empty list is the honest
        // reading of both "this turn used no tools" and "this message predates the field".
        toolsUsed = (toolsUsed == null) ? List.of() : List.copyOf(toolsUsed);
    }

    public static MessageStats forPrompt(long promptTokens, String model) {
        return new MessageStats(promptTokens, 0, 0, 0, model, "", List.of());
    }

    public static MessageStats forCompletion(long completionTokens, long totalTokens,
                                             long latencyMillis, String model, String finishReason) {
        return new MessageStats(0, completionTokens, totalTokens, latencyMillis, model,
                finishReason, List.of());
    }

    /**
     * The same stats, recording what ran. Separate from {@link #forCompletion} because the tool
     * names are known at a different moment than the usage figures — and because most turns have
     * none, so the common path should not have to say so.
     */
    public MessageStats withToolsUsed(List<String> tools) {
        return new MessageStats(promptTokens, completionTokens, totalTokens, latencyMillis,
                model, finishReason, tools);
    }
}
