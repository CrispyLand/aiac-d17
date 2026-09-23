package com.crispyland.mcpserver;

import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * Two tools whose bodies are stubs and whose <em>signatures</em> are not.
 * <p>
 * Nothing here calls a real API, and that is deliberate: what a client discovers over MCP is the
 * name, the description and the input schema, none of which get any more honest for having a live
 * feed behind them. Returning fixed data keeps the thing being demonstrated — that a separate
 * process can be asked what it can do — free of API keys, rate limits and outages that have
 * nothing to do with it.
 * <p>
 * Exchange rates used to be a third stub here and are now the remote Frankfurter server's job.
 * That is the better division: this server exists to show that <em>a</em> server can be
 * interrogated, and a second, real, public one shows that the client aggregates rather than
 * special-cases. Duplicating a currency tool locally would only have invited the question of
 * which of the two the agent was really talking to.
 * <p>
 * The descriptions are written for a reader who cannot see this file, because that is the only
 * form in which they reach the client. Every parameter carries its own {@link McpToolParam}
 * description for the same reason: the generated JSON schema is the entire contract.
 * <p>
 * An ordinary {@code @Service}. The {@code ServerAnnotatedMethodBeanPostProcessor} that ships
 * with the MCP server starter finds {@link McpTool} methods on any bean, so there is no registry
 * to keep in step with this class and no second place to add a tool.
 */
@Service
public class StubTools {

    @McpTool(name = "getWeather",
            description = "Get the current weather for a city, as a short human-readable summary "
                    + "including temperature, conditions and wind.")
    public String getWeather(
            @McpToolParam(description = "City name, optionally with a country, e.g. 'Almaty' or 'Paris, FR'",
                    required = true) String city) {

        return "%s: 18°C, partly cloudy, wind 3 m/s (sample weather)".formatted(city.strip());
    }

    @McpTool(name = "getTopNews",
            description = "Get the top news headlines of the day, most important first.")
    public List<String> getTopNews(
            @McpToolParam(description = "How many headlines to return, from 1 to 5",
                    required = false) Integer count) {

        List<String> headlines = List.of(
                "Local Java agent learns to ask other processes what they can do",
                "Model Context Protocol adoption climbs across tooling vendors",
                "Spring AI 2.0 lands with Boot 4 support",
                "Researchers publish new results on long-context retrieval",
                "Markets steady as central banks hold rates");

        // Clamped rather than validated into an error. A tool that throws on a value the schema
        // called optional is a tool the caller cannot use without reading its source.
        int wanted = (count == null) ? headlines.size() : Math.clamp(count, 1, headlines.size());
        return headlines.subList(0, wanted);
    }
}
