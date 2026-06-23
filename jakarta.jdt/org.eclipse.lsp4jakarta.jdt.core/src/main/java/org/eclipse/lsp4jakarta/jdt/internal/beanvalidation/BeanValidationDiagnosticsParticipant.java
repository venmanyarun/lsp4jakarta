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
*     IBM Corporation, Reza Akhavan - initial API and implementation
*******************************************************************************/

package org.eclipse.lsp4jakarta.jdt.internal.beanvalidation;

import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.ASSERT_FALSE;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.ASSERT_TRUE;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.CHAR_SEQUENCE_FQ;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.DECIMAL_MAX;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.DECIMAL_MIN;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.DIGITS;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.EMAIL;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.FUTURE;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.FUTURE_OR_PRESENT;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.MAX;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.MIN;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NEGATIVE;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NEGATIVE_OR_ZERO;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NOT_BLANK;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NOT_EMPTY;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NUMERIC_AND_CHAR_WRAPPER_TYPES;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NUMERIC_AND_DECIMAL_WRAPPER_TYPES;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NUMERIC_WRAPPER_TYPES;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.WRAPPER_TYPES_FQ;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.PAST;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.PAST_OR_PRESENT;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.PATTERN;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.POSITIVE;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.POSITIVE_OR_ZERO;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.NON_CASCADABLE_TYPES;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.PRIMITIVE_TYPES;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.SET_OF_ANNOTATIONS;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.SET_OF_DATE_TYPES;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.SIZE;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.STRING_FQ;
import static org.eclipse.lsp4jakarta.jdt.internal.beanvalidation.Constants.VALID;

import org.eclipse.lsp4jakarta.jdt.core.ASTUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4jakarta.jdt.core.java.diagnostics.IJavaDiagnosticsParticipant;
import org.eclipse.lsp4jakarta.jdt.core.java.diagnostics.JavaDiagnosticsContext;
import org.eclipse.lsp4jakarta.jdt.core.utils.IJDTUtils;
import org.eclipse.lsp4jakarta.jdt.core.utils.PositionUtils;
import org.eclipse.lsp4jakarta.jdt.core.utils.TypeHierarchyUtils;
import org.eclipse.lsp4jakarta.jdt.internal.DiagnosticUtils;
import org.eclipse.lsp4jakarta.jdt.internal.Messages;
import org.eclipse.lsp4jakarta.jdt.internal.core.java.ManagedBean;
import org.eclipse.lsp4jakarta.jdt.internal.core.ls.JDTUtilsLSImpl;

/**
 * Bean validation diagnostics participant that manages the use of validation
 * element constraints.
 */
public class BeanValidationDiagnosticsParticipant implements IJavaDiagnosticsParticipant {

    /** Logger object to record events for this class. */
    private static final Logger LOGGER = Logger.getLogger(BeanValidationDiagnosticsParticipant.class.getName());

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Diagnostic> collectDiagnostics(JavaDiagnosticsContext context, IProgressMonitor monitor) throws CoreException {
        IJDTUtils utils = JDTUtilsLSImpl.getInstance();
        String uri = context.getUri();
        ICompilationUnit unit = utils.resolveCompilationUnit(uri);
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (unit == null) {
            return diagnostics;
        }

        IType[] alltypes;
        IField[] allFields;
        IAnnotation[] annotations;
        IMethod[] allMethods;

        alltypes = unit.getAllTypes();
        for (IType type : alltypes) {
            allFields = type.getFields();
            for (IField field : allFields) {
                annotations = field.getAnnotations();
                // Check for conflicting constraints on fields
                checkConflictingConstraints(context, uri, field, annotations, diagnostics);

                // NEW: Process TYPE_USE annotations on field type with lazy processing
                String fieldTypeSignature = field.getTypeSignature();

                // Always process direct field annotations first
                for (IAnnotation annotation : annotations) {
                    String matchedAnnotation = DiagnosticUtils.getMatchedJavaElementName(type,
                                                                                         annotation.getElementName(),
                                                                                         SET_OF_ANNOTATIONS.toArray(new String[0]));
                    if (matchedAnnotation != null) {
                        validAnnotation(context, uri, field, annotation, matchedAnnotation, diagnostics);
                    }
                }

                // Additionally, process TYPE_USE annotations if type has generics/arrays
                if (mightHaveTypeUseAnnotations(fieldTypeSignature)) {
                    processTypeUseAnnotations(context, uri, field, fieldTypeSignature, diagnostics);
                }
            }
            allMethods = type.getMethods();
            for (IMethod method : allMethods) {
                annotations = method.getAnnotations();
                // Check for conflicting constraints on methods
                checkConflictingConstraints(context, uri, method, annotations, diagnostics);

                // NEW: Process TYPE_USE annotations on return type with lazy processing
                String returnTypeSignature = method.getReturnType();

                // Always process direct method annotations first
                for (IAnnotation annotation : annotations) {
                    String matchedAnnotation = DiagnosticUtils.getMatchedJavaElementName(type,
                                                                                         annotation.getElementName(),
                                                                                         SET_OF_ANNOTATIONS.toArray(new String[0]));
                    if (matchedAnnotation != null) {
                        validAnnotation(context, uri, method, annotation, matchedAnnotation, diagnostics);
                    }
                }

                // Additionally, process TYPE_USE annotations if return type has generics/arrays
                if (mightHaveTypeUseAnnotations(returnTypeSignature)) {
                    processTypeUseAnnotations(context, uri, method, returnTypeSignature, diagnostics);
                }

                // parameter level annotations
                for (ILocalVariable param : method.getParameters()) {
                    IAnnotation[] paramAnnotations = param.getAnnotations();
                    // Check for conflicting constraints on parameters
                    checkConflictingConstraints(context, uri, param, paramAnnotations, diagnostics);

                    // NEW: Process TYPE_USE annotations on parameters with lazy processing
                    String paramTypeSignature = param.getTypeSignature();

                    for (IAnnotation annotation : paramAnnotations) {
                        String matchedAnnotation = DiagnosticUtils.getMatchedJavaElementName(type,
                                                                                             annotation.getElementName(),
                                                                                             SET_OF_ANNOTATIONS.toArray(new String[0]));
                        if (matchedAnnotation != null) {
                            validAnnotation(context, uri, param, annotation, matchedAnnotation, diagnostics);
                        }
                    }

                    if (mightHaveTypeUseAnnotations(paramTypeSignature)) {
                        processTypeUseAnnotations(context, uri, param, paramTypeSignature, diagnostics);
                    }
                }
            }
        }

        return diagnostics;
    }

    private void validAnnotation(JavaDiagnosticsContext context, String uri, IJavaElement element,
                                 IAnnotation annotation,
                                 String matchedAnnotation,
                                 List<Diagnostic> diagnostics) throws CoreException {

        String type = null;
        IType declaringType = null;
        boolean isMethod = false;
        boolean isField = false;
        if (element instanceof IMethod) {
            type = ((IMethod) element).getReturnType();
            declaringType = ((IMember) element).getDeclaringType();
            isMethod = true;
        } else if (element instanceof IField) {
            type = ((IField) element).getTypeSignature();
            declaringType = ((IMember) element).getDeclaringType();
            isField = true;
        } else if (isParameterType(element)) {
            type = ((ILocalVariable) element).getTypeSignature();
            declaringType = ((IMethod) ((ILocalVariable) element).getDeclaringMember()).getDeclaringType();
        }

        if (declaringType != null) {
            String annotationName = annotation.getElementName();

            // For array types, extract the component type for validation
            // When @Email is used on String[], we validate against String, not String[]
            if (isArrayType(type)) {
                type = Signature.getElementType(type);
            }

            //The below block throws diagnostics if invalid element type is used with constraint annotations
            switch (matchedAnnotation) {
                case ASSERT_FALSE, ASSERT_TRUE -> {
                    String message = getDiagnosticMessage(isMethod, isField, annotationName, "AnnotationBoolean");

                    if (!type.equals(Signature.SIG_BOOLEAN) && !getDataTypeName(type).equals("Boolean")) {
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                                 matchedAnnotation, ErrorCode.InvalidAnnotationOnNonBooleanMethodOrField,
                                                                 DiagnosticSeverity.Error));
                    }
                }
                case DECIMAL_MAX, DECIMAL_MIN, DIGITS -> {
                    String dataTypeFQName = DiagnosticUtils.getMatchedJavaElementName(declaringType,
                                                                                      getDataTypeName(type),
                                                                                      NUMERIC_AND_CHAR_WRAPPER_TYPES);

                    if (dataTypeFQName == null && !type.equals(Signature.SIG_BYTE)
                        && !type.equals(Signature.SIG_SHORT) && !type.equals(Signature.SIG_INT)
                        && !type.equals(Signature.SIG_LONG)) {
                        String message = getDiagnosticMessage(isMethod, isField, annotationName,
                                                              "AnnotationBigDecimal");
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                                 matchedAnnotation,
                                                                 ErrorCode.InvalidAnnotationOnNonBigDecimalCharByteShortIntLongMethodOrField,
                                                                 DiagnosticSeverity.Error));
                    }
                }
                case EMAIL, NOT_BLANK, PATTERN -> checkStringOnly(context, uri, element, diagnostics, annotationName, isMethod, type, matchedAnnotation, declaringType, isField);
                case FUTURE, FUTURE_OR_PRESENT, PAST, PAST_OR_PRESENT -> {
                    String dataType = getDataTypeName(type);
                    String dataTypeFQName = DiagnosticUtils.getMatchedJavaElementName(declaringType, dataType,
                                                                                      SET_OF_DATE_TYPES.toArray(new String[0]));
                    if (dataTypeFQName == null) {
                        String message = getDiagnosticMessage(isMethod, isField, annotationName, "AnnotationDate");
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                                 matchedAnnotation, ErrorCode.InvalidAnnotationOnNonDateTimeMethodOrField,
                                                                 DiagnosticSeverity.Error));
                    }
                }
                case MIN, MAX -> {
                    String dataTypeFQName = DiagnosticUtils.getMatchedJavaElementName(declaringType,
                                                                                      getDataTypeName(type),
                                                                                      NUMERIC_WRAPPER_TYPES);
                    if (dataTypeFQName == null && !type.equals(Signature.SIG_BYTE)
                        && !type.equals(Signature.SIG_SHORT) && !type.equals(Signature.SIG_INT)
                        && !type.equals(Signature.SIG_LONG)) {
                        String message = getDiagnosticMessage(isMethod, isField, annotationName, "AnnotationMinMax");
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                                 matchedAnnotation, ErrorCode.InvalidAnnotationOnNonMinMaxMethodOrField,
                                                                 DiagnosticSeverity.Error));
                    }
                }
                case NEGATIVE, NEGATIVE_OR_ZERO, POSITIVE, POSITIVE_OR_ZERO -> {
                    String dataTypeFQName = DiagnosticUtils.getMatchedJavaElementName(declaringType,
                                                                                      getDataTypeName(type),
                                                                                      NUMERIC_AND_DECIMAL_WRAPPER_TYPES);
                    if (dataTypeFQName == null && !type.equals(Signature.SIG_BYTE)
                        && !type.equals(Signature.SIG_SHORT) && !type.equals(Signature.SIG_INT)
                        && !type.equals(Signature.SIG_LONG) && !type.equals(Signature.SIG_FLOAT)
                        && !type.equals(Signature.SIG_DOUBLE)) {
                        String message = getDiagnosticMessage(isMethod, isField, annotationName, "AnnotationPositive");
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                                 matchedAnnotation, ErrorCode.InvalidAnnotationOnNonPositiveMethodOrField,
                                                                 DiagnosticSeverity.Error));
                    }
                }
                // These ones contains check on all collection types which requires resolving
                // the String of the type somehow
                // This will also require us to check if the field type was a custom collection
                // subtype which means we
                // have to resolve it and get the super interfaces and check to see if
                // Collection, Map or Array was implemented
                // for that custom type (which could as well be a user made subtype)
                case NOT_EMPTY, SIZE -> {
                    if (!(isSizeOrNonEmptyAllowed(declaringType, type))) {
                        String message = getDiagnosticMessage(isMethod, isField, annotationName,
                                                              "SizeOrNonEmptyAnnotations");
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                                 matchedAnnotation, ErrorCode.InvalidAnnotationOnNonSizeMethodOrField,
                                                                 DiagnosticSeverity.Error));
                    }
                }
                case VALID -> {
                    if (!isCascadableType(declaringType, type)) {
                        String message = Messages.getMessage("InvalidValidAnnotation");
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                                 matchedAnnotation, ErrorCode.InvalidValidAnnotationOnNonCascadableType,
                                                                 DiagnosticSeverity.Error));
                    }
                }
                default -> LOGGER.log(Level.SEVERE, "Unexpected value for annotation");
            }
            //Throws invalid static element diagnostics if the element is static and has constraint annotations
            if (!isParameterType(element) && Flags.isStatic(((IMember) element).getFlags())) {
                String message = isMethod ? Messages.getMessage("ConstraintAnnotationsMethod") : Messages.getMessage("ConstraintAnnotationsField");
                Range range = PositionUtils.toNameRange(element, context.getUtils());
                diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE, matchedAnnotation,
                                                         ErrorCode.InvalidConstrainAnnotationOnStaticMethodOrField, DiagnosticSeverity.Error));
            }
        }
    }

    /**
     * getDiagnosticMessage
     *
     * @param isMethod
     * @param isField
     * @param annotationName
     * @param messageKey
     * @return
     */
    private String getDiagnosticMessage(boolean isMethod, boolean isField, String annotationName, String messageKey) {
        String message = isMethod ? Messages.getMessage(messageKey + "Methods",
                                                        "@" + annotationName) : isField ? Messages.getMessage(messageKey + "Fields",
                                                                                                              "@" + annotationName) : Messages.getMessage(messageKey + "Params",
                                                                                                                                                          "@" + annotationName);
        return message;
    }

    private boolean isParameterType(IJavaElement element) {
        return element instanceof ILocalVariable;
    }

    /**
     * isSizeOrNonEmptyAllowed
     * This method checks whether the supported types for the Size and NotEmpty annotations are CharSequence, Collection, Map, or array.
     * https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0#builtinconstraints-size
     * https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0#builtinconstraints-notempty
     *
     * @param parentType
     * @param childTypeString
     * @return
     * @throws CoreException
     */
    boolean isSizeOrNonEmptyAllowed(IType parentType, String childTypeString) throws CoreException {
        if (isArrayType(childTypeString)) {
            return true;
        } else if (PRIMITIVE_TYPES.contains(childTypeString)) {
            return false;
        } else {
            IType fieldType = ManagedBean.getChildITypeByName(parentType, getDataTypeName(childTypeString));
            return fieldType != null
                   && (doesITypeHaveSuperType(fieldType, Constants.CHAR_SEQUENCE_FQ)
                       || doesITypeHaveSuperType(fieldType, Constants.COLLECTION_FQ)
                       || doesITypeHaveSuperType(fieldType, Constants.MAP_FQ));
        }
    }

    /**
     * isCascadableType
     * This method checks whether a type is cascadable for @Valid annotation.
     * Non-cascadable types include: primitives, primitive arrays, boxed types, String, and other simple types.
     * Cascadable types include: complex objects, object arrays, collections, and maps.
     *
     * @param parentType the declaring type
     * @param childTypeString the type signature to check
     * @return true if the type is cascadable, false otherwise
     * @throws CoreException
     */
    boolean isCascadableType(IType parentType, String childTypeString) throws CoreException {
        // Check arrays: primitive arrays are NOT cascadable, object arrays are cascadable
        if (isArrayType(childTypeString)) {
            // Get the element type signature (remove the array bracket '[')
            String elementTypeSignature = Signature.getElementType(childTypeString);
            // If the element type is primitive, the array is not cascadable
            if (PRIMITIVE_TYPES.contains(elementTypeSignature)) {
                return false;
            }
            // Object arrays are cascadable
            return true;
        }

        // Primitive types are not cascadable
        if (PRIMITIVE_TYPES.contains(childTypeString)) {
            return false;
        }

        String dataTypeName = getDataTypeName(childTypeString);

        // Boxed primitive types are not cascadable - use fully qualified name check
        String wrapperTypeFQName = DiagnosticUtils.getMatchedJavaElementName(parentType, dataTypeName,
                                                                             WRAPPER_TYPES_FQ.toArray(new String[0]));
        if (wrapperTypeFQName != null) {
            return false;
        }

        // Check against known non-cascadable types
        String dataTypeFQName = DiagnosticUtils.getMatchedJavaElementName(parentType, dataTypeName, NON_CASCADABLE_TYPES);

        if (dataTypeFQName != null) {
            return false;
        }

        // Enum types are not cascadable
        // Try to resolve the type - ManagedBean.getChildITypeByName handles most cases via resolveType()
        IType fieldType = ManagedBean.getChildITypeByName(parentType, dataTypeName);

        // Fallback: For types in the same compilation unit that resolveType() might miss,
        // check sibling types directly (e.g., inner classes, same-file classes)
        if (fieldType == null && parentType != null) {
            String simpleName = dataTypeName;
            int lastSlash = dataTypeName.lastIndexOf('/');
            if (lastSlash >= 0) {
                simpleName = dataTypeName.substring(lastSlash + 1);
            }

            IType[] types = parentType.getCompilationUnit().getAllTypes();
            for (IType type : types) {
                if (type.getElementName().equals(simpleName)) {
                    fieldType = type;
                    break;
                }
            }
        }

        if (fieldType != null && fieldType.isEnum()) {
            return false;
        }

        // Collections and Maps are cascadable
        if (fieldType != null && (doesITypeHaveSuperType(fieldType, Constants.COLLECTION_FQ) ||
                                  doesITypeHaveSuperType(fieldType, Constants.MAP_FQ))) {
            return true;
        }

        // All other complex types (custom classes, etc.) are cascadable
        return true;
    }

    private void checkStringOnly(JavaDiagnosticsContext context, String uri, IJavaElement element,
                                 List<Diagnostic> diagnostics,
                                 String annotationName, boolean isMethod, String type, String matchedAnnotation, IType declaringType, boolean isField) throws JavaModelException {
        String dataTypeFQName = DiagnosticUtils.getMatchedJavaElementName(declaringType, getDataTypeName(type),
                                                                          new String[] { STRING_FQ, CHAR_SEQUENCE_FQ });
        if (dataTypeFQName == null) {
            String message = getDiagnosticMessage(isMethod, isField, annotationName, "AnnotationString");
            Range range = PositionUtils.toNameRange(element, context.getUtils());
            diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                     matchedAnnotation, ErrorCode.InvalidAnnotationOnNonStringMethodOrField,
                                                     DiagnosticSeverity.Error));
        }
    }

    private static String getDataTypeName(String type) {
        int length = type.length();
        if (length > 0 && type.charAt(0) == 'Q' && type.charAt(length - 1) == ';') {
            return type.substring(1, length - 1);
        }
        return type;
    }

    /**
     * doesITypeHaveSuperType
     * Check if specified superType is present or not in the type hierarchy
     *
     * @param fieldType
     * @param superType
     * @return
     * @throws CoreException
     */
    private boolean doesITypeHaveSuperType(IType fieldType, String superType) throws CoreException {
        return TypeHierarchyUtils.doesITypeHaveSuperType(fieldType, superType) == 1;
    }

    /**
     * Return true if it is Array type, and false otherwise
     *
     * @param childTypeString
     * @return
     */
    public static boolean isArrayType(String childTypeString) {
        return null != childTypeString && childTypeString.startsWith("[");
    }

    /**
     * Check for conflicting constraint annotations (e.g., @Min > @Max, @DecimalMin > @DecimalMax, @Size min > max).
     */
    private void checkConflictingConstraints(JavaDiagnosticsContext context, String uri, IJavaElement element,
                                             IAnnotation[] annotations, List<Diagnostic> diagnostics) throws JavaModelException {
        IType declaringType = element instanceof IMember ? ((IMember) element).getDeclaringType() : element instanceof ILocalVariable ? ((IMethod) ((ILocalVariable) element).getDeclaringMember()).getDeclaringType() : null;
        if (declaringType == null)
            return;

        IAnnotation minAnnotation = null, maxAnnotation = null, decMinAnnotation = null, decMaxAnnotation = null, sizeAnnotation = null;

        for (IAnnotation annotation : annotations) {
            String matched = DiagnosticUtils.getMatchedJavaElementName(declaringType, annotation.getElementName(),
                                                                       new String[] { MIN, MAX, DECIMAL_MIN, DECIMAL_MAX, SIZE });
            if (matched != null) {
                switch (matched) {
                    case MIN -> minAnnotation = annotation;
                    case MAX -> maxAnnotation = annotation;
                    case DECIMAL_MIN -> decMinAnnotation = annotation;
                    case DECIMAL_MAX -> decMaxAnnotation = annotation;
                    case SIZE -> sizeAnnotation = annotation;
                }
            }
        }

        // Check @Min/@Max conflict
        if (minAnnotation != null && maxAnnotation != null) {
            Number min = DiagnosticUtils.getAnnotationMemberValue(minAnnotation, "value", Number.class);
            Number max = DiagnosticUtils.getAnnotationMemberValue(maxAnnotation, "value", Number.class);

            if (min != null && max != null && min.longValue() > max.longValue()) {
                Range range = PositionUtils.toNameRange(element, context.getUtils());
                diagnostics.add(context.createDiagnostic(uri,
                                                         Messages.getMessage("ConflictingConstraintAnnotationsMinMax", min.toString(), max.toString()),
                                                         range, Constants.DIAGNOSTIC_SOURCE, null, ErrorCode.ConflictingConstraintAnnotations, DiagnosticSeverity.Warning));
            }
        }

        // Check @DecimalMin/@DecimalMax conflict
        if (decMinAnnotation != null && decMaxAnnotation != null) {
            String min = DiagnosticUtils.getAnnotationMemberValue(decMinAnnotation, "value", String.class);
            String max = DiagnosticUtils.getAnnotationMemberValue(decMaxAnnotation, "value", String.class);
            if (min != null && max != null) {
                try {
                    if (Double.parseDouble(min) > Double.parseDouble(max)) {
                        Range range = PositionUtils.toNameRange(element, context.getUtils());
                        diagnostics.add(context.createDiagnostic(uri,
                                                                 Messages.getMessage("ConflictingConstraintAnnotationsDecimalMinMax", min, max),
                                                                 range, Constants.DIAGNOSTIC_SOURCE, null, ErrorCode.ConflictingConstraintAnnotations, DiagnosticSeverity.Warning));
                    }
                } catch (NumberFormatException e) {
                    LOGGER.log(Level.INFO, "Ignore invalid number format");
                }
            }
        }

        // Check @Size min/max conflict
        if (sizeAnnotation != null) {
            Number min = DiagnosticUtils.getAnnotationMemberValue(sizeAnnotation, "min", Number.class);
            Number max = DiagnosticUtils.getAnnotationMemberValue(sizeAnnotation, "max", Number.class);
            if (min != null && max != null && min.intValue() > max.intValue()) {
                Range range = PositionUtils.toNameRange(element, context.getUtils());
                diagnostics.add(context.createDiagnostic(uri,
                                                         Messages.getMessage("ConflictingConstraintAnnotationsSize", min.toString(), max.toString()),
                                                         range, Constants.DIAGNOSTIC_SOURCE, null, ErrorCode.ConflictingConstraintAnnotations, DiagnosticSeverity.Warning));
            }
        }
    }

    /**
     * Quick check if a type signature might contain TYPE_USE annotations.
     * This avoids expensive AST parsing for 90%+ of fields.
     *
     * @param typeSignature the type signature to check
     * @return true if the type might have TYPE_USE annotations
     */
    private boolean mightHaveTypeUseAnnotations(String typeSignature) {
        if (typeSignature == null) {
            return false;
        }

        // 1. Parameterized types (generics) might have TYPE_USE
        if (Signature.getTypeArguments(typeSignature).length > 0) {
            return true;
        }

        // 2. Array types might have TYPE_USE on component
        if (Signature.getArrayCount(typeSignature) > 0) {
            return true;
        }

        // 3. Simple types (String, int, etc.) cannot have TYPE_USE in their signature
        return false;
    }

    /**
     * Process TYPE_USE annotations on generic type arguments and array components.
     * Only called after lazy processing check confirms potential TYPE_USE annotations exist.
     *
     * @param context the diagnostics context
     * @param uri the file URI
     * @param element the Java element (field, method, or parameter)
     * @param typeSignature the type signature
     * @param diagnostics the list to add diagnostics to
     * @throws CoreException if an error occurs
     */
    private void processTypeUseAnnotations(JavaDiagnosticsContext context, String uri,
                                           IJavaElement element, String typeSignature,
                                           List<Diagnostic> diagnostics) throws CoreException {
        // At this point, we know the type might have TYPE_USE annotations
        // Now do the expensive AST parsing

        ICompilationUnit cu = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
        if (cu == null) {
            return;
        }

        // Use ASTUtils to get AST with bindings
        ASTNode astNode = ASTUtils.getASTNode(cu);
        if (!(astNode instanceof CompilationUnit)) {
            return;
        }
        CompilationUnit astRoot = (CompilationUnit) astNode;

        // Find the node for this element using ASTUtils
        ASTNode node = ASTUtils.findASTNode(astRoot, element);
        if (node == null) {
            return;
        }

        // Get the type from the node using ASTUtils
        Type type = ASTUtils.getTypeFromNode(node);
        if (type == null) {
            return;
        }

        // 1. Check if type is parameterized (has generics)
        if (type instanceof ParameterizedType) {
            processGenericTypeArguments(context, uri, element, cu, type, (ParameterizedType) type, diagnostics);
        }

        // 2. Check if type is array
        if (type instanceof ArrayType) {
            processArrayComponentType(context, uri, element, cu, type, (ArrayType) type, diagnostics);
        }
    }

    /**
     * Process TYPE_USE annotations on generic type arguments.
     */
    private void processGenericTypeArguments(JavaDiagnosticsContext context, String uri,
                                             IJavaElement element, ICompilationUnit cu,
                                             Type rootType,
                                             ParameterizedType paramType,
                                             List<Diagnostic> diagnostics) throws CoreException {
        @SuppressWarnings("unchecked")
        List<Type> typeArgs = paramType.typeArguments();

        for (Type typeArg : typeArgs) {
            // Get annotations on this type argument
            // Note: Only AnnotatableType has annotations() method
            if (typeArg instanceof org.eclipse.jdt.core.dom.AnnotatableType) {
                org.eclipse.jdt.core.dom.AnnotatableType annotatableType = (org.eclipse.jdt.core.dom.AnnotatableType) typeArg;
                @SuppressWarnings("unchecked")
                List<Annotation> annotations = annotatableType.annotations();

                for (Annotation annotation : annotations) {
                    ITypeBinding typeBinding = typeArg.resolveBinding();

                    if (typeBinding != null) {
                        validateTypeUseAnnotation(context, uri, element, cu, rootType, annotation, typeBinding, diagnostics);
                    }
                }
            }

            // Recursively process nested generics
            if (typeArg instanceof ParameterizedType) {
                processGenericTypeArguments(context, uri, element, cu, rootType, (ParameterizedType) typeArg, diagnostics);
            }
        }
    }

    /**
     * Process TYPE_USE annotations on array component types.
     */
    private void processArrayComponentType(JavaDiagnosticsContext context, String uri,
                                           IJavaElement element, ICompilationUnit cu,
                                           Type rootType,
                                           ArrayType arrayType,
                                           List<Diagnostic> diagnostics) throws CoreException {
        Type componentType = arrayType.getElementType();

        // Get annotations on component type
        // Note: Only AnnotatableType has annotations() method
        if (componentType instanceof org.eclipse.jdt.core.dom.AnnotatableType) {
            org.eclipse.jdt.core.dom.AnnotatableType annotatableType = (org.eclipse.jdt.core.dom.AnnotatableType) componentType;
            @SuppressWarnings("unchecked")
            List<Annotation> annotations = annotatableType.annotations();

            for (Annotation annotation : annotations) {
                ITypeBinding typeBinding = componentType.resolveBinding();

                if (typeBinding != null) {
                    validateTypeUseAnnotation(context, uri, element, cu, rootType, annotation, typeBinding, diagnostics);
                }
            }
        }
    }

    /**
     * Validate a TYPE_USE annotation against the constrained type.
     */
    private void validateTypeUseAnnotation(JavaDiagnosticsContext context, String uri,
                                           IJavaElement element, ICompilationUnit cu,
                                           Type rootType,
                                           Annotation annotation, ITypeBinding constrainedType,
                                           List<Diagnostic> diagnostics) throws CoreException {
        String annotationName = annotation.getTypeName().getFullyQualifiedName();

        // Get the declaring type for matching
        IType declaringType = getDeclaringType(element);
        if (declaringType == null) {
            return;
        }

        // Match against known constraint annotations
        String matchedAnnotation = DiagnosticUtils.getMatchedJavaElementName(
                                                                             declaringType, annotationName, SET_OF_ANNOTATIONS.toArray(new String[0]));

        if (matchedAnnotation == null) {
            return;
        }

        // For TYPE_USE annotations, highlight the entire type expression (e.g., List<@Min(1) String>)
        // This provides better context than just highlighting the annotation or element name
        Range range;
        if (rootType != null && rootType.getStartPosition() >= 0 && rootType.getLength() > 0) {
            range = JDTUtils.toRange(cu, rootType.getStartPosition(), rootType.getLength());
        } else {
            // Fallback to element's name range if rootType position is invalid
            range = PositionUtils.toNameRange(element, context.getUtils());
        }

        // Validate the annotation against the constrained type
        validateConstraintAnnotation(context, uri, element, matchedAnnotation,
                                     constrainedType, declaringType, range, diagnostics);
    }

    /**
     * Extracts the declaring type from a Java element.
     *
     * @param element the Java element (field, method, or parameter)
     * @return the declaring type, or null if not found
     */
    private IType getDeclaringType(IJavaElement element) {
        if (element instanceof IMember) {
            return ((IMember) element).getDeclaringType();
        } else if (element instanceof ILocalVariable) {
            ILocalVariable localVar = (ILocalVariable) element;
            IJavaElement declaringMember = localVar.getDeclaringMember();
            if (declaringMember instanceof IMethod) {
                return ((IMethod) declaringMember).getDeclaringType();
            }
        }
        return null;
    }

    /**
     * Validates a constraint annotation against the constrained type.
     *
     * @param context the diagnostics context
     * @param uri the file URI
     * @param element the Java element being validated
     * @param annotationName the matched annotation name
     * @param constrainedType the type being constrained
     * @param declaringType the declaring type for type matching
     * @param diagnostics the list to add diagnostics to
     */
    private void validateConstraintAnnotation(JavaDiagnosticsContext context, String uri,
                                              IJavaElement element, String annotationName,
                                              ITypeBinding constrainedType, IType declaringType,
                                              Range range, List<Diagnostic> diagnostics) throws JavaModelException {
        String typeName = constrainedType.getQualifiedName();
        boolean isPrimitive = constrainedType.isPrimitive();

        switch (annotationName) {
            case ASSERT_TRUE, ASSERT_FALSE -> validateBooleanConstraint(context, uri, annotationName, typeName, range, diagnostics);

            case EMAIL, NOT_BLANK, PATTERN -> validateStringConstraint(context, uri, annotationName, typeName, range, diagnostics);

            case DECIMAL_MAX, DECIMAL_MIN, DIGITS -> validateNumericConstraint(context, uri, annotationName, typeName,
                                                                               declaringType, isPrimitive, NUMERIC_AND_CHAR_WRAPPER_TYPES,
                                                                               "AnnotationBigDecimalMethods",
                                                                               ErrorCode.InvalidAnnotationOnNonBigDecimalCharByteShortIntLongMethodOrField,
                                                                               range, diagnostics);

            case MIN, MAX -> validateNumericConstraint(context, uri, annotationName, typeName,
                                                       declaringType, isPrimitive, NUMERIC_WRAPPER_TYPES,
                                                       "AnnotationMinMaxMethods",
                                                       ErrorCode.InvalidAnnotationOnNonMinMaxMethodOrField,
                                                       range, diagnostics);

            case NEGATIVE, NEGATIVE_OR_ZERO, POSITIVE, POSITIVE_OR_ZERO -> validateNumericConstraint(context, uri, annotationName, typeName,
                                                                                                     declaringType, isPrimitive, NUMERIC_AND_DECIMAL_WRAPPER_TYPES,
                                                                                                     "AnnotationPositiveMethods",
                                                                                                     ErrorCode.InvalidAnnotationOnNonPositiveMethodOrField,
                                                                                                     range, diagnostics);

            case FUTURE, FUTURE_OR_PRESENT, PAST, PAST_OR_PRESENT -> validateDateTimeConstraint(context, uri, annotationName, typeName,
                                                                                                declaringType, range, diagnostics);

            default -> {
                // SIZE and NOT_EMPTY require collection/map/array checks - handled separately
            }
        }
    }

    /**
     * Validates boolean constraint annotations (@AssertTrue, @AssertFalse).
     */
    private void validateBooleanConstraint(JavaDiagnosticsContext context, String uri,
                                           String annotationName, String typeName,
                                           Range range, List<Diagnostic> diagnostics) throws JavaModelException {
        if (!typeName.equals("boolean") && !typeName.equals("java.lang.Boolean")) {
            addDiagnostic(context, uri, annotationName,
                          "AnnotationBooleanMethods",
                          ErrorCode.InvalidAnnotationOnNonBooleanMethodOrField,
                          range, diagnostics);
        }
    }

    /**
     * Validates string constraint annotations (@Email, @NotBlank, @Pattern).
     */
    private void validateStringConstraint(JavaDiagnosticsContext context, String uri,
                                          String annotationName, String typeName,
                                          Range range, List<Diagnostic> diagnostics) throws JavaModelException {
        if (!typeName.equals("java.lang.String") && !typeName.equals("java.lang.CharSequence")) {
            addDiagnostic(context, uri, annotationName,
                          "AnnotationStringMethods",
                          ErrorCode.InvalidAnnotationOnNonStringMethodOrField,
                          range, diagnostics);
        }
    }

    /**
     * Validates numeric constraint annotations.
     */
    private void validateNumericConstraint(JavaDiagnosticsContext context, String uri,
                                           String annotationName, String typeName,
                                           IType declaringType, boolean isPrimitive,
                                           String[] allowedTypes, String messageKey,
                                           ErrorCode errorCode, Range range,
                                           List<Diagnostic> diagnostics) throws JavaModelException {
        String matchedType = DiagnosticUtils.getMatchedJavaElementName(declaringType, typeName, allowedTypes);
        if (matchedType == null && !isPrimitive) {
            addDiagnostic(context, uri, annotationName, messageKey, errorCode, range, diagnostics);
        }
    }

    /**
     * Validates date/time constraint annotations.
     */
    private void validateDateTimeConstraint(JavaDiagnosticsContext context, String uri,
                                            String annotationName, String typeName,
                                            IType declaringType, Range range,
                                            List<Diagnostic> diagnostics) throws JavaModelException {
        String matchedType = DiagnosticUtils.getMatchedJavaElementName(
                                                                       declaringType, typeName, SET_OF_DATE_TYPES.toArray(new String[0]));

        if (matchedType == null) {
            addDiagnostic(context, uri, annotationName,
                          "AnnotationDateMethods",
                          ErrorCode.InvalidAnnotationOnNonDateTimeMethodOrField,
                          range, diagnostics);
        }
    }

    /**
     * Helper method to add a diagnostic with consistent formatting.
     */
    private void addDiagnostic(JavaDiagnosticsContext context, String uri,
                               String annotationName, String messageKey, ErrorCode errorCode,
                               Range range, List<Diagnostic> diagnostics) throws JavaModelException {
        String message = Messages.getMessage(messageKey, "@" + annotationName);
        diagnostics.add(context.createDiagnostic(uri, message, range, Constants.DIAGNOSTIC_SOURCE,
                                                 annotationName, errorCode, DiagnosticSeverity.Error));
    }
}