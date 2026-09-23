package com.crispyland.agent.llm;

import com.crispyland.agent.memory.Message;
import java.util.List;

/**
 * Provider-neutral request the agent hands to an {@link LlmClient}.
 * No JSON, no HTTP — just what to ask and how.
 *
 * @param tools  what the model may call, or empty. Empty is not a special case that gets handled
 *               later: with nothing here the serialized request has no {@code tools} key at all,
 *               so a turn made with the tool switch off is byte-for-byte the turn this app was
 *               making before tools existed
 * @param rounds tool exchanges already completed <em>within this turn</em>, oldest first. A turn
 *               that needs a tool sends the same request again with one more round appended, which
 *               is why this is on the request rather than being a second method on the client: the
 *               follow-up is not a different kind of call, it is the same call knowing more
 */
public record ChatRequest(
        String model,
        List<Message> messages,
        Double temperature,
        Integer maxCompletionTokens,
        String reasoningEffort,
        List<String> stopSequences,
        String responseSchema,
        List<ToolSpec> tools,
        List<ToolRound> rounds) {

    public ChatRequest {
        messages = List.copyOf(messages);
        stopSequences = (stopSequences == null) ? List.of() : List.copyOf(stopSequences);
        tools = (tools == null) ? List.of() : List.copyOf(tools);
        rounds = (rounds == null) ? List.of() : List.copyOf(rounds);
    }

    /** A request with no tools, which is every request this application made before today. */
    public ChatRequest(String model, List<Message> messages, Double temperature,
                       Integer maxCompletionTokens, String reasoningEffort,
                       List<String> stopSequences, String responseSchema) {
        this(model, messages, temperature, maxCompletionTokens, reasoningEffort, stopSequences,
                responseSchema, List.of(), List.of());
    }

    public ChatRequest withTools(List<ToolSpec> offered) {
        return new ChatRequest(model, messages, temperature, maxCompletionTokens, reasoningEffort,
                stopSequences, responseSchema, offered, rounds);
    }

    public ChatRequest withRounds(List<ToolRound> completed) {
        return new ChatRequest(model, messages, temperature, maxCompletionTokens, reasoningEffort,
                stopSequences, responseSchema, tools, completed);
    }
}
