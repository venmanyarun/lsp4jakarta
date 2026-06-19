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
import org.eclipse.lsp4j.Diagnostic;
import com.redhat.lsp4j.mcp.annotations.Inject;
import com.redhat.lsp4j.mcp.annotations.RequireDidOpen;
import com.redhat.lsp4j.mcp.annotations.Tool;
import com.redhat.lsp4j.mcp.annotations.ToolArg;
import com.redhat.lsp4j.mcp.cache.McpCache;
import com.redhat.lsp4j.mcp.tools.McpTool;
import com.redhat.lsp4j.mcp.tools.TextDocumentIdentifier;

/**
 * MCP tool to validate Jakarta EE annotations in a file.
 */
public class JakartaValidateAnnotationsTool implements McpTool {

    @Inject
    private McpCache cache;

    @Tool(
          name = "jakarta_validate_annotations",
          description = "Validate Jakarta EE annotations in a Java file and return diagnostics")
    @RequireDidOpen(uriParam = "textDocument.uri", languageId = "java")
    public List<Diagnostic> validateAnnotations(
                                                @ToolArg(name = "textDocument", description = "Java file to validate") TextDocumentIdentifier textDocument) {

        // Get diagnostics from cache (populated by publishDiagnostics)
        return cache.getDiagnostics(textDocument.getUri());
    }
}

// Made with Bob
