package com.crispyland.mcp;

import java.util.List;

/**
 * What the MCP panel is showing, as of one page render.
 * <p>
 * A snapshot rather than a live handle, because the page is rendered once per request and a view
 * that could re-dial the server halfway down the template would report two different states in
 * one screen. Everything here is already decided by the time Thymeleaf sees it.
 *
 * @param enabled whether the switch is on. Kept separate from {@code connected} below: off and
 *                unreachable are both "no tools", and collapsing them would make a stopped server
 *                look like a setting somebody chose
 */
public record McpSnapshot(boolean enabled, List<Server> servers) {

    public static final McpSnapshot OFF = new McpSnapshot(false, List.of());

    public McpSnapshot {
        servers = List.copyOf(servers);
    }

    public int toolCount() {
        return servers.stream().mapToInt(server -> server.tools().size()).sum();
    }

    /**
     * True only when every server that is switched on answered. One red light is a red light.
     * <p>
     * Servers that are switched off are excluded rather than counted as failures: nobody dialled
     * them, so there is nothing to report about them, and a lamp that went red because somebody
     * turned a server off would be describing a choice as a fault. All of them off is not green
     * either — there is no connection in hand to be green about.
     */
    public boolean allConnected() {
        return servers.stream().anyMatch(Server::enabled)
                && servers.stream().filter(Server::enabled).allMatch(Server::connected);
    }

    /** How many of the configured servers are switched on. */
    public long enabledCount() {
        return servers.stream().filter(Server::enabled).count();
    }

    /**
     * One configured MCP server and what came back from it.
     *
     * @param label     the connection's name from configuration — known before any handshake, and
     *                  so the only thing there is to call a server that never answered
     * @param name      what the server calls itself, or empty until it has said
     * @param enabled   whether this server's own switch is on. Three states are needed here and
     *                  not two: off, on-and-answering, and on-but-unreachable are different facts,
     *                  and the one thing this panel must never do is let a deliberate choice look
     *                  like a fault
     * @param connected whether it answered this render. Always false when {@code enabled} is
     *                  false, because nothing was asked of it
     * @param error     why the handshake or listing failed, or empty. Non-empty exactly when this
     *                  server is enabled, the master switch is on, and {@code connected} is false
     */
    public record Server(String label, String name, String version, String url,
                         boolean enabled, boolean connected, String error, List<Tool> tools) {

        public Server {
            tools = List.copyOf(tools);
        }

        /** What to show as the heading: the server's own name once it has offered one. */
        public String title() {
            return name.isBlank() ? label : name;
        }

        /**
         * The server's own name, but only when it is telling us something the connection key did
         * not. Frankfurter calls itself exactly what its key here calls it, and a line that reads
         * "frankfurter frankfurter" makes the panel look like it is stuttering rather than
         * reporting two different facts that happen to agree.
         */
        public String alsoCalled() {
            return name.equalsIgnoreCase(label) ? "" : name;
        }

        /**
         * Whether this server is somebody else's machine.
         * <p>
         * Read off the URL rather than off the transport, because that is the distinction worth
         * showing: the panel is making the claim that the same client aggregates a process
         * started from this repository and a public server it has no relationship with, and
         * "localhost or not" is that claim stated in the one place the user can verify it.
         */
        public boolean remote() {
            return !(url.contains("://localhost") || url.contains("://127.0.0.1"));
        }
    }

    /**
     * One tool as the server described it. This is the whole point of the exercise — every field
     * here was discovered at runtime over the protocol, not compiled in.
     */
    public record Tool(String name, String title, String description, List<Param> params) {

        public Tool {
            params = List.copyOf(params);
        }
    }

    /** One entry from a tool's input schema. */
    public record Param(String name, String type, boolean required, String description) {
    }
}
