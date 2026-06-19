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

package org.eclipse.lsp4jakarta.ls.api;

import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4jakarta.ls.commons.ParentProcessWatcher.ProcessLanguageServer;

/**
 * Unified API interface for Jakarta Language Server that combines all custom interfaces.
 * This interface is used by the MCP wrapper to preserve all custom functionality.
 */
public interface JakartaLanguageServerAPI extends LanguageServer, ProcessLanguageServer, JakartaJavaProjectLabelsProvider, JakartaJavaFileInfoProvider {}
