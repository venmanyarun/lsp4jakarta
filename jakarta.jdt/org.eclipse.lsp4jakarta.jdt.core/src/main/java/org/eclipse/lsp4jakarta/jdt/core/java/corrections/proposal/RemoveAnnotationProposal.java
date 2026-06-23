/*******************************************************************************
* Copyright (c) 2021, 2026 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
* which is available at https://www.apache.org/licenses/LICENSE-2.0.
*
* SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
*
* Contributors:
*     IBM Corporation, Jianing Xu - initial API and implementation
*******************************************************************************/
package org.eclipse.lsp4jakarta.jdt.core.java.corrections.proposal;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.lsp4j.CodeActionKind;

/**
 *
 * Code action proposal for deleting an existing annotation for
 * MethodDeclaration/Field.
 *
 * Author: Jianing Xu
 *
 */
public class RemoveAnnotationProposal extends ASTRewriteCorrectionProposal {
    private final CompilationUnit fInvocationNode;
    private final IBinding fBinding;

    private final String[] annotations;
    private final ASTNode declaringNode;

    /**
     * Constructor for DeleteAnnotationProposal
     *
     * @param label - annotation label
     * @param targetCU - the entire Java compilation unit
     * @param invocationNode
     * @param binding
     * @param relevance
     * @param declaringNode - declaringNode covered node of diagnostic
     * @param annotations
     *
     */
    public RemoveAnnotationProposal(String label, ICompilationUnit targetCU, CompilationUnit invocationNode,
                                    IBinding binding, int relevance, ASTNode declaringNode, String... annotations) {
        super(label, CodeActionKind.QuickFix, targetCU, null, relevance);
        this.fInvocationNode = invocationNode;
        this.fBinding = binding;
        this.declaringNode = declaringNode;
        this.annotations = annotations;
    }

    @Override
    protected ASTRewrite getRewrite() throws CoreException {
        ASTNode declNode = this.declaringNode;
        ASTNode boundNode = fInvocationNode.findDeclaringNode(fBinding);
        CompilationUnit newRoot = fInvocationNode;
        if (boundNode == null) {
            newRoot = ASTResolving.createQuickFixAST(getCompilationUnit(), null);
        }
        ImportRewrite imports = createImportRewrite(newRoot);
        if (declNode instanceof VariableDeclarationFragment) {
            declNode = declNode.getParent();
        }
        boolean isField = declNode instanceof FieldDeclaration;
        boolean isMethod = declNode instanceof MethodDeclaration;
        boolean isType = declNode instanceof TypeDeclaration;
        boolean isParam = declNode instanceof SingleVariableDeclaration;

        String[] annotations = getAnnotations();

        if (isField || isMethod || isType || isParam) {
            AST ast = declNode.getAST();
            ASTRewrite rewrite = ASTRewrite.create(ast);

            ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(declNode, imports);

            // First, try to remove annotations from modifiers (field/method/parameter-level annotations)
            @SuppressWarnings("unchecked")
            List<? extends ASTNode> children;
            if (isMethod) {
                children = (List<? extends ASTNode>) declNode.getStructuralProperty(MethodDeclaration.MODIFIERS2_PROPERTY);
            } else if (isType) {
                children = (List<? extends ASTNode>) declNode.getStructuralProperty(TypeDeclaration.MODIFIERS2_PROPERTY);
            } else if (isParam) {
                children = (List<? extends ASTNode>) declNode.getStructuralProperty(SingleVariableDeclaration.MODIFIERS2_PROPERTY);
            } else {
                children = (List<? extends ASTNode>) declNode.getStructuralProperty(FieldDeclaration.MODIFIERS2_PROPERTY);
            }

            // find and save existing annotation, then remove it from ast
            boolean foundInModifiers = false;
            for (ASTNode child : children) {
                if (child instanceof Annotation) {
                    Annotation annotation = (Annotation) child;
                    String matchingFqn = Arrays.stream(annotations).filter(fqn -> matchesAnnotation(fqn, annotation.getTypeName().toString())).findFirst().orElse(null);
                    if (matchingFqn != null) {
                        // Resolving fully qualified name from Annotation to fix issue #567
                        ITypeBinding binding = annotation.resolveTypeBinding();
                        if (binding != null && binding.getQualifiedName().equals(matchingFqn)) {
                            rewrite.remove(child, null);
                            foundInModifiers = true;
                        }
                    }
                }
            }

            // If not found in modifiers, search for TYPE_USE annotations within type structures
            if (!foundInModifiers) {
                // Get the type node to search for TYPE_USE annotations
                Type typeNode = null;
                if (isField) {
                    typeNode = ((FieldDeclaration) declNode).getType();
                } else if (isMethod) {
                    typeNode = ((MethodDeclaration) declNode).getReturnType2();
                } else if (isParam) {
                    typeNode = ((SingleVariableDeclaration) declNode).getType();
                }

                if (typeNode != null) {
                    removeTypeUseAnnotations(typeNode, annotations, rewrite);
                }
            }

            return rewrite;
        }

        return null;
    }

    /**
     * Recursively removes TYPE_USE annotations from type structures (generics, arrays, etc.)
     *
     * @param typeNode The type node to search
     * @param annotations The annotations to remove
     * @param rewrite The AST rewrite object
     */
    private void removeTypeUseAnnotations(Type typeNode, String[] annotations, ASTRewrite rewrite) {
        if (typeNode == null) {
            return;
        }

        // Visit the type node and all its children to find annotations
        typeNode.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleType node) {
                removeAnnotationsFromType(node, annotations, rewrite);
                return true;
            }

            @Override
            public boolean visit(ParameterizedType node) {
                removeAnnotationsFromType(node, annotations, rewrite);
                // Continue visiting type arguments
                return true;
            }

            @Override
            public boolean visit(ArrayType node) {
                removeAnnotationsFromType(node, annotations, rewrite);
                return true;
            }
        });
    }

    /**
     * Removes annotations from a specific type node
     *
     * @param typeNode The type node
     * @param annotations The annotations to remove
     * @param rewrite The AST rewrite object
     */
    @SuppressWarnings("unchecked")
    private void removeAnnotationsFromType(Type typeNode, String[] annotations, ASTRewrite rewrite) {
        // Only SimpleType has annotations() method directly
        // The ASTVisitor will recursively visit all SimpleType nodes within complex types
        if (!(typeNode instanceof SimpleType)) {
            return;
        }

        List<Annotation> typeAnnotations = ((SimpleType) typeNode).annotations();
        if (typeAnnotations == null || typeAnnotations.isEmpty()) {
            return;
        }

        for (Annotation annotation : typeAnnotations) {
            String matchingFqn = Arrays.stream(annotations).filter(fqn -> matchesAnnotation(fqn, annotation.getTypeName().toString())).findFirst().orElse(null);
            if (matchingFqn != null) {
                ITypeBinding binding = annotation.resolveTypeBinding();
                if (binding != null && binding.getQualifiedName().equals(matchingFqn)) {
                    rewrite.remove(annotation, null);
                }
            }
        }
    }

    /**
     * Returns the Compilation Unit node
     *
     * @return the invocation node for the Compilation Unit
     */
    protected CompilationUnit getInvocationNode() {
        return this.fInvocationNode;
    }

    /**
     * Returns the Binding object associated with the new annotation change
     *
     * @return the binding object
     */
    protected IBinding getBinding() {
        return this.fBinding;
    }

    /**
     * Returns the annotations list
     *
     * @return the list of new annotations to add
     */
    protected String[] getAnnotations() {
        return this.annotations;
    }

    /**
     * Matches the Annotation
     *
     * @param fqn
     * @param typeName
     * @return
     */

    private static boolean matchesAnnotation(String fqn, String typeName) {
        return fqn.equals(typeName) || fqn.endsWith("." + typeName);
    }
}
