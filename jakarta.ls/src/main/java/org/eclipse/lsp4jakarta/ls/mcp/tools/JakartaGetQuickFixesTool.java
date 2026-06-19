/*******************************************************************************
* Copyright (c) 2026 IBM Corporation and others.
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

package org.eclipse.lsp4jakarta.ls.mcp.tools;

import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageServer;
import com.redhat.lsp4j.mcp.annotations.Inject;
import com.redhat.lsp4j.mcp.annotations.RequireDidOpen;
import com.redhat.lsp4j.mcp.annotations.Tool;
import com.redhat.lsp4j.mcp.annotations.ToolArg;
import com.redhat.lsp4j.mcp.tools.McpTool;
import com.redhat.lsp4j.mcp.tools.TextDocumentIdentifier;

/**
 * MCP tool to get quick fixes for Jakarta EE diagnostics.
 */
public class JakartaGetQuickFixesTool implements McpTool {

    @Inject
    private LanguageServer languageServer;

    @Tool(
          name = "jakarta_get_quick_fixes",
          description = "Get available quick fixes for Jakarta EE diagnostics at a position")
    @RequireDidOpen(uriParam = "textDocument.uri", languageId = "java")
    public List<CodeAction> getQuickFixes(
                                          @ToolArg(name = "textDocument", description = "Java file") TextDocumentIdentifier textDocument,
                                          @ToolArg(name = "range", description = "Range in the file") Range range,
                                          @ToolArg(name = "context", description = "Code action context") CodeActionContext context) {

        CodeActionParams params = new CodeActionParams();
        // Convert MCP TextDocumentIdentifier to LSP4J TextDocumentIdentifier
        org.eclipse.lsp4j.TextDocumentIdentifier lspTextDocument = new org.eclipse.lsp4j.TextDocumentIdentifier();
        lspTextDocument.setUri(textDocument.getUri());
        params.setTextDocument(lspTextDocument);
        params.setRange(range);
        params.setContext(context);

        try {
            return languageServer.getTextDocumentService().codeAction(params).get().stream().filter(either -> either.isRight()).map(either -> either.getRight()).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get code actions", e);
        }
    }
}
