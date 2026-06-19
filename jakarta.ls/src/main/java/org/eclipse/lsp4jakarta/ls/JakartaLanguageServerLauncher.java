/*******************************************************************************
* Copyright (c) 2020, 2026 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.lsp4jakarta.ls;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.launch.LSPLauncher.Builder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4jakarta.ls.api.JakartaLanguageClientAPI;
import org.eclipse.lsp4jakarta.ls.api.JakartaLanguageServerAPI;
import org.eclipse.lsp4jakarta.ls.commons.ParentProcessWatcher;

// MCP imports
import com.redhat.lsp4j.mcp.cache.McpCache;
import com.redhat.lsp4j.mcp.server.LspMcpServer;
import com.redhat.lsp4j.mcp.server.McpLanguageClientWrapper;
import com.redhat.lsp4j.mcp.server.McpLanguageServerWrapper;

public class JakartaLanguageServerLauncher {
    public static void main(String[] args) {
        JakartaLanguageServer server = new JakartaLanguageServer();

        // Create MCP cache shared between wrappers
        McpCache mcpCache = new McpCache();

        // Wrap the Language Server to intercept didOpen/didClose
        // Use JakartaLanguageServerAPI to preserve all custom interfaces
        JakartaLanguageServerAPI wrappedServer = McpLanguageServerWrapper.wrap(
                                                                               JakartaLanguageServerAPI.class, server, mcpCache);

        Function<MessageConsumer, MessageConsumer> wrapper;
        wrapper = it -> it;
        if ("true".equals(System.getProperty("runAsync"))) {
            wrapper = it -> msg -> CompletableFuture.runAsync(() -> it.consume(msg));
        }
        if (!"false".equals(System.getProperty("watchParentProcess"))) {
            wrapper = new ParentProcessWatcher(wrappedServer, wrapper);
        }

        Launcher<LanguageClient> launcher = createServerLauncher(wrappedServer, System.in, System.out,
                                                                 Executors.newCachedThreadPool());

        // Wrap the client to intercept publishDiagnostics
        JakartaLanguageClientAPI client = (JakartaLanguageClientAPI) launcher.getRemoteProxy();
        JakartaLanguageClientAPI wrappedClient = McpLanguageClientWrapper.wrap(
                                                                               JakartaLanguageClientAPI.class, client, mcpCache);

        server.setLanguageClient(wrappedClient);

        // Get MCP port from system property (default: 9339)
        int mcpPort = Integer.parseInt(System.getProperty("jakarta.mcp.port", "9339"));

        // Start MCP server with annotation-based tools
        LspMcpServer mcpServer = LspMcpServer.builder().serverInfo("jakarta-ls",
                                                                   "0.2.6").port(mcpPort).registerDependency(org.eclipse.lsp4j.services.LanguageServer.class,
                                                                                                             wrappedServer).registerDependency(com.redhat.lsp4j.mcp.cache.McpCache.class,
                                                                                                                                               mcpCache).build(); // Auto-discovers tools via SPI

        // Start MCP server in background thread
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait for LS to be fully initialized
                mcpServer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "MCP-Server-Starter").start();

        launcher.startListening();
    }

    /**
     * Create a new Launcher for a language server and an input and output stream.
     * Threads are started with the given executor service. The wrapper function is
     * applied to the incoming and outgoing message streams so additional message
     * handling such as validation and tracing can be included.
     *
     * @param server - the server that receives method calls from the
     *            remote client
     * @param in - input stream to listen for incoming messages
     * @param out - output stream to send outgoing messages
     * @param executorService - the executor service used to start threads
     * @param wrapper - a function for plugging in additional message
     *            consumers
     */
    public static Launcher<LanguageClient> createServerLauncher(LanguageServer server, InputStream in, OutputStream out,
                                                                ExecutorService executorService) {
        return new Builder<LanguageClient>().setLocalService(server).setRemoteInterface(JakartaLanguageClientAPI.class).setInput(in).setOutput(out).setExecutorService(executorService).create();
    }

}
