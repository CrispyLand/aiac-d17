package com.crispyland.mcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A standalone MCP server, run separately from the agent and talked to over HTTP.
 * <p>
 * It holds no agent code and no model client. That separation is the exercise: the agent
 * discovers what this can do by asking it at runtime, not by having been compiled against it,
 * which is the difference between a tool protocol and an ordinary dependency.
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
