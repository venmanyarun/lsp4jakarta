/*******************************************************************************
 * Copyright (c) 2022, 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Yijia Jing
 *******************************************************************************/

package org.eclipse.lsp4jakarta.jdt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class ASTUtils {

    private static final Logger LOGGER = Logger.getLogger(ASTUtils.class.getName());

    /**
     * Converts a given compilation unit to an ASTNode.
     *
     * @param unit
     * @return ASTNode parsed from the compilation unit
     */
    public static ASTNode getASTNode(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(unit);
        parser.setResolveBindings(true);
        return parser.createAST(null);
    }

    /**
     * Given a compilation unit returns a list of all method invocations.
     *
     * @param unit
     * @return list of method invocations
     */
    public static List<MethodInvocation> getMethodInvocations(ICompilationUnit unit) {
        ASTNode node = getASTNode(unit);
        MethodInvocationVisitor visitor = new ASTUtils().new MethodInvocationVisitor();
        node.accept(visitor);
        return visitor.getMethodInvocations();
    }

    /**
     * This visitor visits an ASTNode and records all the method invocations during its visit.
     */
    public class MethodInvocationVisitor extends ASTVisitor {
        private final List<MethodInvocation> invocations = new ArrayList<>();

        @Override
        public boolean visit(final MethodInvocation m) {
            invocations.add(m);
            return super.visit(m);
        }

        public List<MethodInvocation> getMethodInvocations() {
            return Collections.unmodifiableList(invocations);
        }
    }

    /**
     * This visitor visits an ASTNode and records all the method declarations during its visit.
     */
    private class MethodDeclarationVisitor extends ASTVisitor {
        private final List<MethodDeclaration> declarations = new ArrayList<>();

        @Override
        public boolean visit(final MethodDeclaration m) {
            declarations.add(m);
            return super.visit(m);
        }

        public List<MethodDeclaration> getMethodDeclarations() {
            return Collections.unmodifiableList(declarations);
        }
    }

    /**
     * Given a compilation unit returns a list of all method declarations.
     *
     * @param unit
     * @return list of method declarations
     */
    public static List<MethodDeclaration> getMethodDeclarations(ICompilationUnit unit) {
        ASTNode node = getASTNode(unit);
        MethodDeclarationVisitor visitor = new ASTUtils().new MethodDeclarationVisitor();
        node.accept(visitor);
        return visitor.getMethodDeclarations();
    }

    /**
     * Checks whether the given MethodDeclaration contains a call to the specified method
     * on the specified parent type. Does that by getting Method invocations specific to the method.
     *
     * @param methodDecl
     * @param targetMethod
     * @param parentFQN
     * @return boolean
     */
    public static boolean containsMethodInvocation(MethodDeclaration methodDecl, String targetMethod, String parentFQN) {
        if (methodDecl == null || methodDecl.getBody() == null) {
            return false;
        }
        AtomicBoolean found = new AtomicBoolean(false);
        methodDecl.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                if (found.get())
                    return false; // stop descending if found
                if (targetMethod.equals(node.getName().getIdentifier())) {
                    IMethodBinding binding = node.resolveMethodBinding();
                    if (binding != null) {
                        ITypeBinding declaringClass = binding.getDeclaringClass();
                        if (declaringClass != null &&
                            parentFQN.equals(declaringClass.getQualifiedName())) {
                            found.set(true);
                            return false; // stop visiting children of this node
                        }
                    }
                }
                return true; // keep traversing nodes until found
            }
        });
        return found.get();
    }

    /**
     * Visitor that searches for a specific AST node by name and element type.
     * Supports finding fields, methods, and parameters with optimized traversal.
     */
    private static class NodeFinderVisitor extends ASTVisitor {
        private final String targetName;
        private final int elementType;
        private ASTNode foundNode;
        private boolean shouldStop;

        public NodeFinderVisitor(String targetName, int elementType) {
            this.targetName = targetName;
            this.elementType = elementType;
            this.foundNode = null;
            this.shouldStop = false;
        }

        @Override
        public boolean visit(FieldDeclaration fieldNode) {
            if (shouldStop || elementType != IJavaElement.FIELD) {
                return false;
            }

            // Check each variable fragment in the field declaration
            for (Object fragment : fieldNode.fragments()) {
                if (fragment instanceof VariableDeclarationFragment) {
                    VariableDeclarationFragment varFragment = (VariableDeclarationFragment) fragment;
                    if (matchesName(targetName, varFragment.getName().getIdentifier())) {
                        foundNode = fieldNode;
                        shouldStop = true;
                        return false; // Stop traversal
                    }
                }
            }
            return true; // Continue traversal
        }

        @Override
        public boolean visit(MethodDeclaration methodNode) {
            if (shouldStop || elementType != IJavaElement.METHOD) {
                return false;
            }

            if (matchesName(targetName, methodNode.getName().getIdentifier())) {
                foundNode = methodNode;
                shouldStop = true;
                return false; // Stop traversal
            }
            return true; // Continue traversal
        }

        @Override
        public boolean visit(SingleVariableDeclaration paramNode) {
            if (shouldStop || elementType != IJavaElement.LOCAL_VARIABLE) {
                return false;
            }

            if (matchesName(targetName, paramNode.getName().getIdentifier())) {
                foundNode = paramNode;
                shouldStop = true;
                return false; // Stop traversal
            }
            return true; // Continue traversal
        }

        public ASTNode getFoundNode() {
            return foundNode;
        }
    }

    /**
     * Helper method to compare target name with candidate name.
     *
     * @param targetName the name being searched for
     * @param candidateName the name to compare against
     * @return true if names match, false otherwise
     */
    private static boolean matchesName(String targetName, String candidateName) {
        return targetName != null && targetName.equals(candidateName);
    }

    /**
     * Finds the AST node corresponding to a Java element by traversing the AST.
     *
     * <p>This method searches for the AST node that represents the given Java element
     * (field, method, or parameter) within the compilation unit's AST. It uses a visitor
     * pattern to traverse the AST and match nodes by element name.</p>
     *
     * <p><b>Supported Element Types:</b></p>
     * <ul>
     * <li>Fields - Returns the {@link FieldDeclaration} node</li>
     * <li>Methods - Returns the {@link MethodDeclaration} node</li>
     * <li>Parameters - Returns the {@link SingleVariableDeclaration} node</li>
     * </ul>
     *
     * @param astRoot the compilation unit AST root to search within
     * @param element the Java element to find (must have a valid element name)
     * @return the AST node corresponding to the element, or {@code null} if not found or on error
     *
     * @see FieldDeclaration
     * @see MethodDeclaration
     * @see SingleVariableDeclaration
     */
    public static ASTNode findASTNode(CompilationUnit astRoot, IJavaElement element) {
        if (astRoot == null || element == null) {
            return null;
        }

        try {
            final String targetElementName = element.getElementName();
            if (targetElementName == null || targetElementName.isEmpty()) {
                return null;
            }

            // Get element type to optimize traversal
            final int elementType = element.getElementType();

            // Only search for supported element types
            if (elementType != IJavaElement.FIELD &&
                elementType != IJavaElement.METHOD &&
                elementType != IJavaElement.LOCAL_VARIABLE) {
                return null;
            }

            // Use custom visitor to find the node
            NodeFinderVisitor visitor = new NodeFinderVisitor(targetElementName, elementType);
            astRoot.accept(visitor);

            return visitor.getFoundNode();

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error finding AST node for element: " + element.getElementName(), e);
            return null;
        }
    }

    /**
     * Extracts the {@link Type} from an AST node representing a field, method, or parameter.
     *
     * <p>This method provides a unified way to extract type information from different
     * kinds of AST nodes. It handles the differences in how types are accessed across
     * field declarations, method declarations, and parameter declarations.</p>
     *
     * <p><b>Supported Node Types:</b></p>
     * <ul>
     * <li>{@link FieldDeclaration} - Returns the field's type</li>
     * <li>{@link MethodDeclaration} - Returns the method's return type</li>
     * <li>{@link SingleVariableDeclaration} - Returns the parameter's type</li>
     * </ul>
     *
     * @param node the AST node to extract the type from
     * @return the {@link Type} from the node, or {@code null} if the node type is unsupported or null
     *
     * @see FieldDeclaration#getType()
     * @see MethodDeclaration#getReturnType2()
     * @see SingleVariableDeclaration#getType()
     */
    public static Type getTypeFromNode(ASTNode node) {
        if (node == null) {
            return null;
        }

        // Extract type based on node type
        if (node instanceof FieldDeclaration) {
            FieldDeclaration fieldDecl = (FieldDeclaration) node;
            return fieldDecl.getType();

        } else if (node instanceof MethodDeclaration) {
            MethodDeclaration methodDecl = (MethodDeclaration) node;
            return methodDecl.getReturnType2();

        } else if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration paramDecl = (SingleVariableDeclaration) node;
            return paramDecl.getType();
        }

        // Unsupported node type
        LOGGER.log(Level.WARNING, "Unsupported node type in getTypeFromNode: " +
                                  (node != null ? node.getClass().getSimpleName() : "null"));
        return null;
    }
}