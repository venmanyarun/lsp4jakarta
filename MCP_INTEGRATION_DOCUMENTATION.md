# Jakarta EE Language Server  - MCP Integration Documentation

**Version**: 1.0  
**Date**: 2026-06-19  
**Status**: ✅ Complete

---

## Table of Contents

1. [High-Level Summary](#high-level-summary)
2. [Repository Setup](#repository-setup)
3. [Changes Made](#changes-made)
4. [Testing Guide](#testing-guide)
5. [MCP Server Configuration](#mcp-server-configuration)
6. [Screenshots](#screenshots)
7. [Troubleshooting](#troubleshooting)

---

## High-Level Summary

### Overview

This integration enables the **Model Context Protocol (MCP)** server within the **Jakarta EE Language Server (LSP4Jakarta)**, allowing AI assistants (like Bob/Roo-Cline) to interact with Jakarta EE language features through standardized MCP tools. The Liberty Tools VSCode extension is used as the host environment for testing the MCP-enabled language server.

### Key Changes

#### 1. LSP4Jakarta Language Server (Backend)

**Modified Files:**
- `jakarta.ls/pom.xml` - Added MCP dependencies (lsp4j-mcp, mcp-sdk, undertow)
- `JakartaLanguageServerLauncher.java` - Integrated MCP server initialization with wrappers
- `JakartaLanguageServer.java` - Implemented unified API interface

**Created Files:**
- `JakartaLanguageServerAPI.java` - Unified API interface for MCP wrapper
- `JakartaValidateAnnotationsTool.java` - MCP tool for annotation validation
- `JakartaGetQuickFixesTool.java` - MCP tool for quick fixes
- `META-INF/services/com.redhat.lsp4j.mcp.tools.McpTool` - SPI registration
- `src/assembly/distribution.xml` - Custom assembly descriptor for SPI merging

#### 2. Liberty Tools VSCode Extension (Frontend)

**Modified Files:**
- `src/util/javaServerStarter.ts` - Added `-Djakarta.mcp.port=9339` for debug mode

### Architecture

```
Jakarta Language Server (JVM)
├── LSP Server (wrapped by McpLanguageServerWrapper)
├── LSP Client (wrapped by McpLanguageClientWrapper)
├── McpCache (shared state)
└── LspMcpServer (HTTP/SSE on port 9339)
    └── 4 MCP Tools (auto-discovered via SPI)

                ↕ HTTP/SSE

Bob IDE (AI Assistant)
└── Connects to http://localhost:9339/mcp/sse
```

### Available MCP Tools

1. `lsp_get_diagnostics` - Get diagnostics for files
2. `lsp_get_code_actions` - Get code actions
3. `jakarta_validate_annotations` - Validate Jakarta EE annotations
4. `jakarta_get_quick_fixes` - Get Jakarta-specific quick fixes

---

## Repository Setup

### Prerequisites

1. **Java Development Kit (JDK)** - Version 17 or higher
2. **Maven** - Version 3.6 or higher
3. **Node.js and npm** - Version 16 or higher
4. **Git** - For cloning repositories
5. **Bob IDE** - With extension support enabled

### Directory Structure

All three repositories must be in the same parent directory:

```
parent-directory/
├── quarkus-ls/           # Contains lsp4j-mcp library
├── lsp4jakarta/          # Jakarta EE Language Server
└── liberty-tools-vscode/ # VS Code Extension
```

### Clone Repositories

```bash
# Create parent directory
mkdir mcp-integration-workspace
cd mcp-integration-workspace

# Clone quarkus-ls (for lsp4j-mcp library)
git clone https://github.com/redhat-developer/quarkus-ls.git
cd quarkus-ls
# Checkout specific commit if needed
git switch --detach 3a1063c3b6e9166524175b9f160d73a12ab47ea6
cd ..

# Clone lsp4jakarta
git clone https://github.com/eclipse/lsp4jakarta.git
cd lsp4jakarta
# TODO: Replace with your commit hash
git checkout <YOUR_COMMIT_HASH_HERE>
cd ..

# Clone liberty-tools-vscode
git clone https://github.com/OpenLiberty/liberty-tools-vscode.git
cd liberty-tools-vscode
cd ..
```

---

## Changes to be Made

### 1. Quarkus-LS Repository

**Purpose:** Provides the `lsp4j-mcp` library for MCP integration.

**Location:** `quarkus-ls/lsp4j-mcp/`

#### Required Change: Fix URI Parsing

**File:** `lsp4j-mcp/src/main/java/com/redhat/lsp4j/mcp/server/McpToolRegistry.java`

**Issue:** The original code doesn't properly handle file:// URIs on Windows systems.

**Fix:** Update the URI parsing logic in the `DidOpenInterceptorHandler.execute()` method (around line 331):

```java
// Convert file:// URI to file system path
// Handle both Unix (file:///path) and Windows (file:///C:/path) URIs
String path = uri;
if (path.startsWith("file://")) {
    path = path.substring("file://".length());
    // On Unix, file:///path becomes /path (preserve leading /)
    // On Windows, file:///C:/path becomes /C:/path, then we remove leading /
    if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
        // Windows path: /C:/path -> C:/path
        path = path.substring(1);
    }
}
content = Files.readString(Paths.get(path));
```

**What this fixes:**
- Properly converts `file:///path/to/file` (Unix) to `/path/to/file`
- Properly converts `file:///C:/path/to/file` (Windows) to `C:/path/to/file`
- Ensures file reading works on both Unix and Windows systems

**Build and Install:**

```bash
cd quarkus-ls/lsp4j-mcp
mvn clean install
```

This installs `lsp4j-mcp-0.1.0-SNAPSHOT.jar` to `~/.m2/repository/`

### 2. LSP4Jakarta Repository

**Purpose:** Jakarta EE Language Server with MCP integration.

**Location:** `lsp4jakarta/`

**Status:** ✅ All MCP changes are already present in the branch you checked out.

#### Summary of Changes Already Present

The following MCP integration changes have already been implemented in the lsp4jakarta branch:

1. **Dependencies Added** (`jakarta.ls/pom.xml`):
   - lsp4j-mcp library (0.1.0-SNAPSHOT)
   - MCP Java SDK (2.0.0-M2)
   - Undertow for HTTP/SSE server (2.3.17.Final)
   - Custom assembly plugin configuration

2. **New Files Created**:
   - `JakartaLanguageServerAPI.java` - Unified API interface for MCP wrapper
   - `JakartaValidateAnnotationsTool.java` - MCP tool for annotation validation
   - `JakartaGetQuickFixesTool.java` - MCP tool for quick fixes
   - `META-INF/services/com.redhat.lsp4j.mcp.tools.McpTool` - SPI registration
   - `src/assembly/distribution.xml` - Custom assembly descriptor for SPI merging

3. **Modified Files**:
   - `JakartaLanguageServer.java` - Now implements JakartaLanguageServerAPI
   - `JakartaLanguageServerLauncher.java` - Added MCP server initialization with wrappers

4. **MCP Features**:
   - Embedded HTTP/SSE server on port 9339
   - 4 MCP tools registered via SPI
   - Shared state between LSP and MCP via McpCache
   - Auto-discovery of tools at runtime

**No additional changes needed** - Just build the project as described in the testing guide.

### 3. Liberty Tools VSCode Repository

**Purpose:** VS Code extension that hosts the Jakarta Language Server.

**Location:** `liberty-tools-vscode/`

#### File: `src/util/javaServerStarter.ts`

Add MCP port configuration in `prepareParams()` function:

```typescript
function prepareParams(jarName: string): string[] {
  const params: string[] = [];

  if (DEBUG) {
    if (jarName === LIBERTY_LS_JAR) {
      params.push(`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${LIBERTY_LS_DEBUG_PORT},quiet=y`);
    }
    else if (jarName === JAKARTA_LS_JAR) {
      params.push(`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${JAKARTA_LS_DEBUG_PORT},quiet=y`);
      // Enable MCP server for Jakarta LS in debug mode
      params.push('-Djakarta.mcp.port=9339');
    }
  }
  // ... rest of function
}
```

**Impact:** MCP server starts automatically in debug mode (F5).

---

## Testing Guide

### Step 1: Build lsp4j-mcp Library

```bash
cd quarkus-ls/lsp4j-mcp
mvn clean install
```

### Step 2: Build and Copy Jakarta LS JAR

```bash
cd liberty-tools-vscode

# Install dependencies (if not already done)
npm install

# Build Jakarta LS and copy JAR automatically
npm run buildJakarta
```

**What this does:**
- Builds lsp4jakarta using `./buildAll.sh`
- Copies the JAR to `jars/` directory automatically
- Output: `jars/org.eclipse.lsp4jakarta.ls-0.2.6-SNAPSHOT-jar-with-dependencies.jar` (~16MB)

**Alternative (Manual):**
If you prefer to build manually:
```bash
cd lsp4jakarta
./buildAll.sh
cd ../liberty-tools-vscode
cp ../lsp4jakarta/jakarta.ls/target/org.eclipse.lsp4jakarta.ls-0.2.6-SNAPSHOT-jar-with-dependencies.jar jars/
```

### Step 3: Build Extension

```bash
# Build the extension (if not already done)
npm run compile
```

### Step 4: Create VSIX Package

```bash
# Install vsce if needed
npm install -g @vscode/vsce

# Create VSIX
vsce package
```

**Output:** `liberty-dev-vscode-ext-26.0.4.vsix`

### Step 5: Install VSIX in Bob IDE

#### Method 1: Command Line

```bash
# In Bob IDE terminal
code --install-extension liberty-dev-vscode-ext-26.0.4.vsix
```

#### Method 2: Bob IDE UI

1. Open Bob IDE
2. Go to Extensions view
3. Click `...` menu → "Install from VSIX..."
4. Select `liberty-dev-vscode-ext-26.0.4.vsix`
5. Reload Bob IDE

### Step 6: Test in Debug Mode

#### A. Start Extension in Debug Mode

1. Open `liberty-tools-vscode` project in Bob IDE
2. Press `F5` to start debugging
3. A new **Extension Development Host** window opens

#### B. Open Sample Jakarta EE Project

In the Extension Development Host window:

1. Open the sample project:
   - **Recommended**: `lsp4jakarta/jakarta.jdt/org.eclipse.lsp4jakarta.jdt.test/projects/jakarta-sample`
   - Or any Java project with Jakarta EE code

2. Wait for the extension to activate (Liberty Tools icon appears in activity bar)

3. Open a Java file with Jakarta EE annotations (e.g., `SingletonSessionBean.java`)

#### C. Verify MCP Server Started

Check Output panel in Extension Development Host:
- Go to **View → Output**
- Select **"Language Server for Jakarta EE"** from dropdown

**Expected logs:**
```
[INFO] Starting Jakarta Language Server...
[INFO] MCP server enabled on port 9339
[INFO] MCP SSE endpoint: http://localhost:9339/mcp/sse
[INFO] Registering MCP tools...
[INFO] Discovered 4 MCP tools:
[INFO]   ✓ lsp_get_diagnostics
[INFO]   ✓ lsp_get_code_actions
[INFO]   ✓ jakarta_validate_annotations
[INFO]   ✓ jakarta_get_quick_fixes
[INFO] MCP server ready
[INFO] Jakarta Language Server initialized
```

**Note:** The Extension Development Host is a separate VS Code window where your extension runs for testing. The MCP server runs in this window, not in your main Bob IDE window.

### Step 7: Verify MCP Endpoint

```bash
curl -N http://localhost:9339/mcp/sse
```

Should return SSE connection.

### Step 8: Test with Sample File

Open the existing test file with intentional errors:

**File:** `lsp4jakarta/jakarta.jdt/org.eclipse.lsp4jakarta.jdt.test/projects/jakarta-sample/src/main/java/io/openliberty/sample/jakarta/cdi/SingletonSessionBean.java`

This file contains multiple test cases for Jakarta EE CDI scope validation:

```java
package io.openliberty.sample.jakarta.cdi;

import jakarta.ejb.Singleton;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;

// Test case 1: Singleton with invalid scope (RequestScoped) - should report error
@Singleton
@RequestScoped
public class SingletonSessionBean {
}

// Test case 2: Singleton with invalid scope (SessionScoped) - should report error
@Singleton
@SessionScoped
class SingletonWithSessionScope {
}
// ... more test cases
```

**Expected Diagnostics:**
- Line 11: `@RequestScoped` - Error: "@Singleton EJBs cannot use @RequestScoped scope"
- Line 17: `@SessionScoped` - Error: "@Singleton EJBs cannot use @SessionScoped scope"
- Line 40: `@RequestScoped` - Error: "@Singleton EJBs cannot use @RequestScoped scope"
- Line 47: `@SessionScoped` - Error: "@Singleton EJBs cannot use @SessionScoped scope"

Open this file in the Extension Development Host and verify red squiggly lines appear under the invalid scope annotations.

---

## MCP Server Configuration

### Bob IDE Configuration

Create MCP settings file:

**File:** `~/.bob/settings/mcp_settings.json`

```json
{
  "mcpServers": {
    "jakarta-ls": {
      "url": "http://localhost:9339/mcp/sse",
      "name": "Jakarta EE Language Server",
      "description": "Provides Jakarta EE validation and quick fixes",
      "enabled": true
    }
  }
}
```

**After creating the file:**
1. Restart Bob IDE to load configuration
2. Verify "jakarta-ls" appears in Bob's MCP servers list
3. Check that 4 tools are available

### Verifying Connection

In Bob IDE chat:

```
You: What MCP servers are available?

Bob: I can see the following MCP servers:
- jakarta-ls (Connected)
  Tools: lsp_get_diagnostics, lsp_get_code_actions, 
         jakarta_validate_annotations, jakarta_get_quick_fixes
```

---

## Screenshots

### 1. MCP Server Configuration in Bob Settings

**Location:** Bob IDE → Settings → MCP Servers

**What to capture:**
- MCP Servers section showing "jakarta-ls"
- Connection status (green "Connected" indicator)
- List of 4 available tools
- Server URL: `http://localhost:9339/mcp/sse`

**Screenshot placeholder:**
```
┌─────────────────────────────────────────────────┐
│ Bob Settings - MCP Servers                      │
├─────────────────────────────────────────────────┤
│ Connected Servers:                              │
│                                                 │
│ ● jakarta-ls                    [Connected]     │
│   http://localhost:9339/mcp/sse                 │
│                                                 │
│   Tools (4):                                    │
│   ✓ lsp_get_diagnostics                         │
│   ✓ lsp_get_code_actions                        │
│   ✓ jakarta_validate_annotations                │
│   ✓ jakarta_get_quick_fixes                     │
└─────────────────────────────────────────────────┘
```

### 2. MCP Server Startup Logs

**Location:** Extension Development Host → Output → "Language Server for Jakarta EE"

**What to capture:**
- MCP server startup messages
- Port configuration (9339)
- Tool discovery (4 tools)
- "MCP server ready" message

**Screenshot placeholder:**
```
[INFO] Starting Jakarta Language Server...
[INFO] MCP server enabled on port 9339
[INFO] MCP SSE endpoint: http://localhost:9339/mcp/sse
[INFO] Discovered 4 MCP tools:
[INFO]   ✓ lsp_get_diagnostics
[INFO]   ✓ lsp_get_code_actions
[INFO]   ✓ jakarta_validate_annotations
[INFO]   ✓ jakarta_get_quick_fixes
[INFO] MCP server ready
```

### 3. MCP Tool Execution

**Location:** Extension Development Host → Output → "Language Server for Jakarta EE"

**What to capture:**
- Tool invocation log
- Parameters passed
- File operations (didOpen)
- Results returned
- Execution time

**Screenshot placeholder:**
```
[INFO] MCP Tool invoked: jakarta_validate_annotations
[INFO] Parameters: {"textDocument":{"uri":"file:///.../SingletonSessionBean.java"}}
[INFO] Opening file: SingletonSessionBean.java
[INFO] Found 4 diagnostic(s)
[INFO] Returning diagnostics to MCP client
[INFO] Tool execution completed in 45ms
```

### 4. Bob Using MCP Tools

**Location:** Bob IDE → Chat Panel

**What to capture:**
- User query
- Bob's response showing tool usage
- Diagnostic results
- Quick fix suggestions

**Screenshot placeholder:**
```
You: Check SingletonSessionBean.java for Jakarta EE issues

Bob: I'll validate the Jakarta EE annotations.

🔧 Using tool: jakarta_validate_annotations
📄 File: SingletonSessionBean.java

Found 4 issues:
❌ Line 11: @RequestScoped - Error: "@Singleton EJBs cannot use @RequestScoped scope"
   Severity: ERROR
   Code: jakarta-cdi-invalid-scope

❌ Line 17: @SessionScoped - Error: "@Singleton EJBs cannot use @SessionScoped scope"
   Severity: ERROR
   Code: jakarta-cdi-invalid-scope

🔧 Using tool: jakarta_get_quick_fixes
Available fixes:
1. Remove @RequestScoped annotation
2. Remove @SessionScoped annotation
3. Change @Singleton to @Stateless

💡 Recommendation: Remove conflicting CDI scope annotations from @Singleton EJBs.
```

### 5. Before and After File Changes

**What to capture:**
- Side-by-side comparison
- Original file with error
- Fixed file after Bob's suggestions
- Highlight the changes (added @Id, imports, etc.)

**Screenshot placeholder:**
```
┌──────────────────────┬──────────────────────┐
│ BEFORE (Error)       │ AFTER (Fixed)        │
├──────────────────────┼──────────────────────┤
│ @Entity              │ @Entity              │
│ public class Test {  │ public class Test {  │
│   ❌ private String   │   ✅ @Id              │
│      name;           │   @GeneratedValue    │
│                      │   private Long id;   │
│                      │                      │
│                      │   private String     │
│                      │   name;              │
└──────────────────────┴──────────────────────┘
```

---

## Troubleshooting

### Issue 1: MCP Server Not Starting

**Symptoms:**
- No MCP logs in language server output
- Bob can't connect to jakarta-ls
- Port 9339 not responding

**Solutions:**

1. **Verify debug mode:**
   - MCP only starts in debug mode (F5)
   - Check you're not using Ctrl+F5 (run without debugging)

2. **Check JAR file:**
   ```bash
   ls -lh jars/org.eclipse.lsp4jakarta.ls-0.2.6-SNAPSHOT-jar-with-dependencies.jar
   # Should be ~16MB
   ```

3. **Check port availability:**
   ```bash
   lsof -i :9339
   # If in use, kill the process
   lsof -ti:9339 | xargs kill -9
   ```

### Issue 2: Bob Can't See MCP Tools

**Symptoms:**
- Bob shows jakarta-ls as connected
- But no tools listed
- Tool invocations fail

**Solutions:**

1. **Verify SPI file in JAR:**
   ```bash
   jar tf jars/org.eclipse.lsp4jakarta.ls-*.jar | grep META-INF/services
   unzip -p jars/org.eclipse.lsp4jakarta.ls-*.jar \
     META-INF/services/com.redhat.lsp4j.mcp.tools.McpTool
   # Should list 4 tool classes
   ```

2. **Rebuild with proper assembly:**
   ```bash
   cd lsp4jakarta
   ./buildAll.sh
   cp jakarta.ls/target/*.jar ../liberty-tools-vscode/jars/
   ```

3. **Restart everything:**
   ```bash
   pkill -f "lsp4jakarta"
   # Reload Bob IDE window
   ```

### Issue 3: Tool Invocations Fail

**Symptoms:**
- Bob tries to use tool
- Returns error or timeout
- Language server logs show exceptions

**Solutions:**

1. **Check file is opened:**
   - MCP tools require files to be opened first
   - Bob should use `lsp_didOpen` before other tools

2. **Verify file URI format:**
   - Must be absolute path: `file:///absolute/path/to/file.java`

3. **Check diagnostics cache:**
   - Open file in editor first to populate cache

### Issue 4: Extension Not Loading

**Symptoms:**
- Liberty Tools extension doesn't activate
- No language server output
- Jakarta features not working

**Solutions:**

1. **Check dependencies:**
   - Verify these extensions are installed:
     - redhat.vscode-microprofile
     - redhat.vscode-xml
     - vscjava.vscode-java-debug

2. **Check Java installation:**
   ```bash
   java -version
   # Should show version 17 or higher
   ```

3. **Reinstall extension:**
   ```bash
   code --uninstall-extension Open-Liberty.liberty-dev-vscode-ext
   code --install-extension liberty-dev-vscode-ext-26.0.4.vsix
   ```

### Debug Checklist

- [ ] Java 17+ installed
- [ ] Maven 3.6+ installed
- [ ] Node.js 16+ installed
- [ ] All three repos cloned in same parent directory
- [ ] lsp4j-mcp built and installed (`mvn clean install`)
- [ ] lsp4jakarta built (`./buildAll.sh`)
- [ ] JAR copied to liberty-tools-vscode/jars/
- [ ] JAR size is ~16MB
- [ ] Extension built (`npm install && npm run compile`)
- [ ] VSIX created (`vsce package`)
- [ ] VSIX installed in Bob IDE
- [ ] Running in debug mode (F5)
- [ ] Java project opened
- [ ] MCP startup logs visible
- [ ] Port 9339 accessible
- [ ] Bob MCP config file created
- [ ] Bob IDE restarted
- [ ] Bob shows jakarta-ls connected
- [ ] Bob shows 4 tools available

---

## Summary

This integration successfully enables MCP support in Liberty Tools VSCode extension, allowing AI assistants like Bob to interact with the Jakarta EE Language Server through standardized tools.

**Key Achievements:**
- ✅ Embedded MCP server in Jakarta LS
- ✅ 4 MCP tools available
- ✅ Seamless integration with Bob IDE
- ✅ Zero-configuration in debug mode
- ✅ Comprehensive testing guide

**Next Steps:**
1. Enable MCP in production builds (via extension settings)
2. Add more Jakarta-specific MCP tools
3. Create user-facing documentation
4. Add telemetry for MCP usage

---

**Document Version:** 1.0  
**Last Updated:** 2026-06-19  
**Maintained By:** Liberty Tools Team