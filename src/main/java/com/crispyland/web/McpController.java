package com.crispyland.web;

import com.crispyland.mcp.McpCatalogue;
import com.crispyland.mcp.McpSnapshot;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The MCP switch, and the {@code mcp} attribute every page render needs.
 * <p>
 * A {@link ControllerAdvice} rather than two more lines in {@code ChatController}, so that
 * today's feature can be added — or deleted — without editing the class that runs the agent.
 * The panel is a bystander on that page; it should not be able to break a chat turn by being
 * wired into the method that serves one.
 */
@Controller
@ControllerAdvice
public class McpController {

    private final McpCatalogue mcp;

    public McpController(McpCatalogue mcp) {
        this.mcp = mcp;
    }

    /**
     * Runs before every rendered view, which includes the POST that answers a chat turn — that
     * one returns the page directly rather than redirecting, so a GET-only version of this would
     * blank the panel on exactly the request the user looks at most.
     * <p>
     * With the switch off this contacts nothing and costs nothing.
     */
    @ModelAttribute("mcp")
    public McpSnapshot snapshot() {
        return mcp.snapshot();
    }

    @PostMapping("/mcp/toggle")
    public String toggle() {
        mcp.toggle();
        return "redirect:/";
    }

    /**
     * One server's switch. Separate endpoint rather than an optional parameter on the one above,
     * because a missing or misspelled {@code label} would otherwise silently flip the master
     * switch — the largest possible effect from the smallest possible typo.
     */
    @PostMapping("/mcp/server/toggle")
    public String toggleServer(@RequestParam String label) {
        mcp.toggle(label);
        return "redirect:/";
    }
}
