package com.tibyaan.runtime.spring;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Spike SEC-0 and phase SEC-1, rule S1 (Volume 7 section 15): the caller the HOST authenticated, carried from the
 * servlet request that delivered a JSON-RPC message to the tool call that message produces. The runtime reads
 * the request's user principal, which the host's own security filter chain set, and nothing else: no header,
 * no token, no validation. It travels in the MCP SDK's per-request transport context and is read back from the
 * exchange the SDK hands the tool callback, so it can never bleed between sessions or concurrent calls.
 */
final class CallerContext {

    /** The transport-context key under which the caller travels. */
    static final String CALLER_KEY = "tibyan.caller";

    private CallerContext() {
    }

    /** The extractor the runtime's transport provider installs: the request's principal, or nothing. */
    static McpTransportContext capture(ServerRequest request) {
        return request.principal()
                .<McpTransportContext>map(principal -> McpTransportContext.create(Map.of(CALLER_KEY, principal)))
                .orElse(McpTransportContext.EMPTY);
    }

    /** The caller of the tool call this context belongs to, if the host authenticated one. */
    static Optional<Principal> callerOf(ToolContext toolContext) {
        if (toolContext == null) {
            return Optional.empty();
        }
        Optional<McpSyncServerExchange> exchange = McpToolUtils.getMcpExchange(toolContext);
        if (exchange.isEmpty() || exchange.get().transportContext() == null) {
            return Optional.empty();
        }
        Object caller = exchange.get().transportContext().get(CALLER_KEY);
        return caller instanceof Principal principal ? Optional.of(principal) : Optional.empty();
    }
}
