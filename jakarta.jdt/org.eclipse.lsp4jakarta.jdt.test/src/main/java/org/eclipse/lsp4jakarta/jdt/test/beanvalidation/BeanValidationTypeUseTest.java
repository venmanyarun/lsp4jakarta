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
package org.eclipse.lsp4jakarta.jdt.test.beanvalidation;

import static org.eclipse.lsp4jakarta.jdt.test.core.JakartaForJavaAssert.assertJavaDiagnostics;
import static org.eclipse.lsp4jakarta.jdt.test.core.JakartaForJavaAssert.d;

import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4jakarta.commons.JakartaJavaDiagnosticsParams;
import org.eclipse.lsp4jakarta.jdt.core.utils.IJDTUtils;
import org.eclipse.lsp4jakarta.jdt.internal.core.ls.JDTUtilsLSImpl;
import org.eclipse.lsp4jakarta.jdt.test.core.BaseJakartaTest;
import org.junit.Test;

/**
 * JUnit test class for TYPE_USE validation of Bean Validation constraints.
 * Tests validation of constraints on generic type arguments and array component types.
 */
public class BeanValidationTypeUseTest extends BaseJakartaTest {
    protected static IJDTUtils IJDT_UTILS = JDTUtilsLSImpl.getInstance();

    @Test
    public void testStringConstraintsOnGenerics() throws Exception {
        JakartaJavaDiagnosticsParams diagnosticsParams = getDiagnosticsParams("TypeUseStringConstraints.java");

        // Line 26: List<@Email Integer> - @Email on Integer (should be String/CharSequence)
        Diagnostic emailOnIntegerDiagnostic = d(25, 12, 32,
                                                "The @jakarta.validation.constraints.Email annotation can only be used on String and CharSequence type methods.",
                                                DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                "InvalidAnnotationOnNonStringMethodOrField",
                                                "jakarta.validation.constraints.Email");

        // Line 29: List<@NotBlank Integer> - @NotBlank on Integer (should be String/CharSequence)
        Diagnostic notBlankOnIntegerDiagnostic = d(28, 12, 35,
                                                   "The @jakarta.validation.constraints.NotBlank annotation can only be used on String and CharSequence type methods.",
                                                   DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                   "InvalidAnnotationOnNonStringMethodOrField",
                                                   "jakarta.validation.constraints.NotBlank");

        // Line 32: List<@Pattern Boolean> - @Pattern on Boolean (should be String/CharSequence)
        Diagnostic patternOnBooleanDiagnostic = d(31, 12, 49,
                                                  "The @jakarta.validation.constraints.Pattern annotation can only be used on String and CharSequence type methods.",
                                                  DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                  "InvalidAnnotationOnNonStringMethodOrField",
                                                  "jakarta.validation.constraints.Pattern");

        assertJavaDiagnostics(diagnosticsParams, IJDT_UTILS,
                              emailOnIntegerDiagnostic, notBlankOnIntegerDiagnostic, patternOnBooleanDiagnostic);
    }

    @Test
    public void testBooleanConstraintsOnGenerics() throws Exception {
        JakartaJavaDiagnosticsParams diagnosticsParams = getDiagnosticsParams("TypeUseBooleanConstraints.java");

        // Line 23: List<@AssertTrue String> - @AssertTrue on String (should be boolean/Boolean)
        Diagnostic assertTrueOnStringDiagnostic = d(22, 12, 36,
                                                    "The @jakarta.validation.constraints.AssertTrue annotation can only be used on boolean and Boolean type methods.",
                                                    DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                    "InvalidAnnotationOnNonBooleanMethodOrField",
                                                    "jakarta.validation.constraints.AssertTrue");

        // Line 26: List<@AssertFalse Integer> - @AssertFalse on Integer (should be boolean/Boolean)
        Diagnostic assertFalseOnIntegerDiagnostic = d(25, 12, 38,
                                                      "The @jakarta.validation.constraints.AssertFalse annotation can only be used on boolean and Boolean type methods.",
                                                      DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                      "InvalidAnnotationOnNonBooleanMethodOrField",
                                                      "jakarta.validation.constraints.AssertFalse");

        assertJavaDiagnostics(diagnosticsParams, IJDT_UTILS,
                              assertTrueOnStringDiagnostic, assertFalseOnIntegerDiagnostic);
    }

    @Test
    public void testNumericConstraintsOnGenerics() throws Exception {
        JakartaJavaDiagnosticsParams diagnosticsParams = getDiagnosticsParams("TypeUseNumericConstraints.java");

        // Line 29: List<@Min(1) String> - @Min on String (should be numeric)
        Diagnostic minOnStringDiagnostic = d(28, 12, 32,
                                             "The @jakarta.validation.constraints.Min annotation can only be used on \n" +
                                                         "- BigDecimal \n" +
                                                         "- BigInteger\n" +
                                                         "- byte, short, int, long (and their respective wrappers) \n" +
                                                         " type methods.",
                                             DiagnosticSeverity.Error, "jakarta-bean-validation",
                                             "InvalidAnnotationOnNonMinMaxMethodOrField",
                                             "jakarta.validation.constraints.Min");

        // Line 32: List<@Max(100) Boolean> - @Max on Boolean (should be numeric)
        Diagnostic maxOnBooleanDiagnostic = d(31, 12, 35,
                                              "The @jakarta.validation.constraints.Max annotation can only be used on \n" +
                                                          "- BigDecimal \n" +
                                                          "- BigInteger\n" +
                                                          "- byte, short, int, long (and their respective wrappers) \n" +
                                                          " type methods.",
                                              DiagnosticSeverity.Error, "jakarta-bean-validation",
                                              "InvalidAnnotationOnNonMinMaxMethodOrField",
                                              "jakarta.validation.constraints.Max");

        // Line 35: List<@Positive String> - @Positive on String (should be numeric)
        Diagnostic positiveOnStringDiagnostic = d(34, 12, 34,
                                                  "The @jakarta.validation.constraints.Positive annotation can only be used on \n" +
                                                              "- BigDecimal \n" +
                                                              "- BigInteger\n" +
                                                              "- byte, short, int, long, float, double (and their respective wrappers) \n" +
                                                              " type methods.",
                                                  DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                  "InvalidAnnotationOnNonPositiveMethodOrField",
                                                  "jakarta.validation.constraints.Positive");

        // Line 38: List<@Negative Boolean> - @Negative on Boolean (should be numeric)
        Diagnostic negativeOnBooleanDiagnostic = d(37, 12, 35,
                                                   "The @jakarta.validation.constraints.Negative annotation can only be used on \n" +
                                                               "- BigDecimal \n" +
                                                               "- BigInteger\n" +
                                                               "- byte, short, int, long, float, double (and their respective wrappers) \n" +
                                                               " type methods.",
                                                   DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                   "InvalidAnnotationOnNonPositiveMethodOrField",
                                                   "jakarta.validation.constraints.Negative");

        assertJavaDiagnostics(diagnosticsParams, IJDT_UTILS,
                              minOnStringDiagnostic, maxOnBooleanDiagnostic,
                              positiveOnStringDiagnostic, negativeOnBooleanDiagnostic);
    }

    @Test
    public void testDecimalConstraints() throws Exception {
        JakartaJavaDiagnosticsParams diagnosticsParams = getDiagnosticsParams("TypeUseDecimalConstraints.java");

        // Line 30: Map<String, List<@Email Integer>> - @Email on Integer in nested generic
        Diagnostic emailOnNestedIntegerDiagnostic = d(29, 12, 45,
                                                      "The @jakarta.validation.constraints.Email annotation can only be used on String and CharSequence type methods.",
                                                      DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                      "InvalidAnnotationOnNonStringMethodOrField",
                                                      "jakarta.validation.constraints.Email");

        // Line 33: List<@DecimalMin Boolean> - @DecimalMin on Boolean
        Diagnostic decimalMinOnBooleanDiagnostic = d(32, 12, 44,
                                                     "The @jakarta.validation.constraints.DecimalMin annotation can only be used on: \n" +
                                                                 "- BigDecimal \n" +
                                                                 "- BigInteger \n" +
                                                                 "- CharSequence\n" +
                                                                 "- byte, short, int, long (and their respective wrappers) \n" +
                                                                 " type methods.",
                                                     DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                     "InvalidAnnotationOnNonBigDecimalCharByteShortIntLongMethodOrField",
                                                     "jakarta.validation.constraints.DecimalMin");

        // Line 36: List<@DecimalMax Boolean> - @DecimalMax on Boolean
        Diagnostic decimalMaxOnBooleanDiagnostic = d(35, 12, 46,
                                                     "The @jakarta.validation.constraints.DecimalMax annotation can only be used on: \n" +
                                                                 "- BigDecimal \n" +
                                                                 "- BigInteger \n" +
                                                                 "- CharSequence\n" +
                                                                 "- byte, short, int, long (and their respective wrappers) \n" +
                                                                 " type methods.",
                                                     DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                     "InvalidAnnotationOnNonBigDecimalCharByteShortIntLongMethodOrField",
                                                     "jakarta.validation.constraints.DecimalMax");

        // Line 39: List<@Digits Boolean> - @Digits on Boolean
        Diagnostic digitsOnBooleanDiagnostic = d(38, 12, 60,
                                                 "The @jakarta.validation.constraints.Digits annotation can only be used on: \n" +
                                                             "- BigDecimal \n" +
                                                             "- BigInteger \n" +
                                                             "- CharSequence\n" +
                                                             "- byte, short, int, long (and their respective wrappers) \n" +
                                                             " type methods.",
                                                 DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                 "InvalidAnnotationOnNonBigDecimalCharByteShortIntLongMethodOrField",
                                                 "jakarta.validation.constraints.Digits");

        assertJavaDiagnostics(diagnosticsParams, IJDT_UTILS,
                              emailOnNestedIntegerDiagnostic, decimalMinOnBooleanDiagnostic,
                              decimalMaxOnBooleanDiagnostic, digitsOnBooleanDiagnostic);
    }

    @Test
    public void testMethodConstraints() throws Exception {
        JakartaJavaDiagnosticsParams diagnosticsParams = getDiagnosticsParams("TypeUseMethodConstraints.java");

        // Line 27: public List<@Email Integer> getInvalidEmails() - return type (TYPE_USE in generic)
        Diagnostic emailOnGenericReturnTypeDiagnostic = d(26, 11, 31,
                                                          "The @jakarta.validation.constraints.Email annotation can only be used on String and CharSequence type methods.",
                                                          DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                          "InvalidAnnotationOnNonStringMethodOrField",
                                                          "jakarta.validation.constraints.Email");

        // Line 32: public @Email Integer[] getInvalidEmailArray() - return type array (method-level annotation on array type)
        Diagnostic emailOnArrayReturnTypeDiagnostic = d(31, 28, 48,
                                                        "The @Email annotation can only be used on String and CharSequence type methods.",
                                                        DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                        "InvalidAnnotationOnNonStringMethodOrField",
                                                        "jakarta.validation.constraints.Email");

        // Line 53: public void setInvalidEmailArray(@Email Integer[] numbers) - parameter array (parameter-level annotation)
        Diagnostic emailOnArrayParameterDiagnostic = d(52, 54, 61,
                                                       "The @Email annotation can only be used on String and CharSequence type parameters.",
                                                       DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                       "InvalidAnnotationOnNonStringMethodOrField",
                                                       "jakarta.validation.constraints.Email");

        assertJavaDiagnostics(diagnosticsParams, IJDT_UTILS,
                              emailOnGenericReturnTypeDiagnostic, emailOnArrayReturnTypeDiagnostic,
                              emailOnArrayParameterDiagnostic);
    }

    @Test
    public void testComplexNestedGenerics() throws Exception {
        JakartaJavaDiagnosticsParams diagnosticsParams = getDiagnosticsParams("TypeUseComplexConstraints.java");

        // Line 24: Map<String, List<@Email Integer>> - @Email on Integer
        Diagnostic emailOnMapValueTypeDiagnostic = d(23, 12, 45,
                                                     "The @jakarta.validation.constraints.Email annotation can only be used on String and CharSequence type methods.",
                                                     DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                     "InvalidAnnotationOnNonStringMethodOrField",
                                                     "jakarta.validation.constraints.Email");

        // Line 27: Map<@NotBlank Integer, List<String>> - @NotBlank on Integer
        Diagnostic notBlankOnMapKeyTypeDiagnostic = d(26, 12, 48,
                                                      "The @jakarta.validation.constraints.NotBlank annotation can only be used on String and CharSequence type methods.",
                                                      DiagnosticSeverity.Error, "jakarta-bean-validation",
                                                      "InvalidAnnotationOnNonStringMethodOrField",
                                                      "jakarta.validation.constraints.NotBlank");

        assertJavaDiagnostics(diagnosticsParams, IJDT_UTILS,
                              emailOnMapValueTypeDiagnostic, notBlankOnMapKeyTypeDiagnostic);
    }

    private JakartaJavaDiagnosticsParams getDiagnosticsParams(String fileName) throws Exception {
        IJavaProject javaProject = loadJavaProject("jakarta-sample", "");
        IFile javaFile = javaProject.getProject().getFile(
                                                          new Path("src/main/java/io/openliberty/sample/jakarta/beanvalidation/" + fileName));
        String uri = javaFile.getLocation().toFile().toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));
        return diagnosticsParams;
    }
}