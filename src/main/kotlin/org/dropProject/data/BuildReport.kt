/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.dropProject.data

import org.dropProject.dao.Assignment
import org.dropProject.dao.AssignmentTestMethod
import org.dropProject.dao.Language
import org.dropProject.dao.Engine
import org.dropProject.services.JUnitMethodResult
import org.dropProject.services.JUnitMethodResultType
import org.dropProject.services.JUnitResults
import org.dropProject.services.JacocoResults
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * Enum representing the types of tests that DP supports:
 * - Student tests - unit tests written by the students to test their own work;
 * - Teacher - unit tests written by the teachers to test the student's work; The detailed results of these tests are
 * always shown to the students.
 * - Hidden Teacher Tests - unit tests written by the teachers; The results of these tests can be partially visible to
 * the students or not (configurable when creating the assignment).
 */
enum class TestType {
    STUDENT, TEACHER, HIDDEN
}

/**
 * Represents the output that is generated for a certain [Submission]'s code.
 *
 * @property outputLines is a List of String, where each String is a line of the Maven's build process's output
 * @property projectFolder is a String
 * @property assignment identifies the [Assignment] that Submission targetted.
 * @property junitResults is a List of [JunitResults] with the result of evaluating the Submission using JUnit tests
 * @property jacocoResults is a List of [JacocoResults] with the result of evaluating the Submission's code coverage
 * @property assignmentTestMethods is a List of [AssignmentTestMethod]. Each object describes on of the executed Unit Tests
 */
abstract class BuildReport(val outputLines: List<String>,
                       val projectFolder: String,
                       val assignment: Assignment,
                       val junitResults: List<JUnitResults>,
                       val jacocoResults: List<JacocoResults>,
                       val assignmentTestMethods: List<AssignmentTestMethod>) {

    //Edit output to show to report
    fun getOutput() : String {
        return outputLines.joinToString(separator = "\n")
    }

    //Create junitSummary for report
    fun junitSummary(testType: TestType = TestType.TEACHER) : String? {
        val junitSummary = junitSummaryAsObject(testType)
        if (junitSummary != null) {
            return "Tests run: ${junitSummary.numTests}, Failures: ${junitSummary.numFailures}, " +
                    "Errors: ${junitSummary.numErrors}, Time elapsed: ${junitSummary.ellapsed} sec"
        } else {
            return null
        }
    }

     /**
     * Creates a summary of the testing results, considering a certain [TestType].
     *
     * @param testType is a [TestType], indicating which tests should be considered (e.g TEACHER tests)
     *
     * @return a [JUnitSummary]
     */
    fun junitSummaryAsObject(testType: TestType = TestType.TEACHER) : JUnitSummary? {
        if (junitResults
                .filter{testType == TestType.TEACHER && it.isTeacherPublic(assignment) ||
                        testType == TestType.STUDENT && it.isStudent(assignment) ||
                        testType == TestType.HIDDEN && it.isTeacherHidden()}
                .isEmpty()) {
            return null
        }

        var totalTests = 0
        var totalErrors = 0
        var totalFailures = 0
        var totalSkipped = 0
        var totalElapsed = 0.0f
        var totalMandatoryOK = 0  // mandatory tests that passed

        for (junitResult in junitResults) {
            if (testType == TestType.TEACHER && junitResult.isTeacherPublic(assignment) ||
                    testType == TestType.STUDENT && junitResult.isStudent(assignment) ||
                    testType == TestType.HIDDEN && junitResult.isTeacherHidden()) {
                totalTests += junitResult.numTests
                totalErrors += junitResult.numErrors
                totalFailures += junitResult.numFailures
                totalSkipped += junitResult.numSkipped
                totalElapsed += junitResult.timeEllapsed

                assignment.mandatoryTestsSuffix?.let {
                    mandatoryTestsSuffix ->
                        totalMandatoryOK += junitResult.junitMethodResults
                            .filter {
                                it.fullMethodName.endsWith(mandatoryTestsSuffix) &&
                                        it.type == JUnitMethodResultType.SUCCESS
                            }
                            .count()
                }
            }
        }

        return JUnitSummary(totalTests, totalFailures, totalErrors, totalSkipped, totalElapsed, totalMandatoryOK)
    }

     /**
     * Calculates the total elapsed time during the execution of the Unit Tests. Considers both the public and the
     * private (hidden) tests.
     *
     * @return a BigDecimal representing the elapsed time
     */
    fun elapsedTimeJUnit() : BigDecimal? {
        var total : BigDecimal? = null
        val junitSummaryTeacher = junitSummaryAsObject(TestType.TEACHER)
        if (junitSummaryTeacher != null) {
            total = junitSummaryTeacher.ellapsed.toBigDecimal()
        }

        val junitSummaryHidden = junitSummaryAsObject(TestType.HIDDEN)
        if (junitSummaryHidden != null && total != null) {
            total += junitSummaryHidden.ellapsed.toBigDecimal()
        }

        return total
    }

     /**
     * Determines if the evaluation resulted in any JUnit errors or failures.
     */
    fun hasJUnitErrors(testType: TestType = TestType.TEACHER) : Boolean? {
        val junitSummary = junitSummaryAsObject(testType)
        if (junitSummary != null) {
            return junitSummary.numErrors > 0 || junitSummary.numFailures > 0
        } else {
            return null
        }
    }
    
    /**
     * Determines if the evaluation resulted in any JUnit errors or failures.
     */
    fun jUnitErrors(testType: TestType = TestType.TEACHER) : String? {
        var result = ""
        for (junitResult in junitResults) {
            if (testType == TestType.TEACHER && junitResult.isTeacherPublic(assignment) ||
                    testType == TestType.STUDENT && junitResult.isStudent(assignment) ||
                    testType == TestType.HIDDEN && junitResult.isTeacherHidden()) {
                result += junitResult.junitMethodResults
                        .filter { it.type != JUnitMethodResultType.SUCCESS && it.type != JUnitMethodResultType.IGNORED }
                        .map { it.filterStacktrace(assignment.packageName.orEmpty()); it }
                        .joinToString(separator = "\n")
            }
        }

        if (result.isEmpty()) {
            return null
        } else {
            return result
        }

        //        if (hasJUnitErrors() == true) {
        //            val testReport = File("${projectFolder}/target/surefire-reports")
        //                    .walkTopDown()
        //                    .filter { it -> it.name.endsWith(".txt") }
        //                    .map { it -> String(Files.readAllBytes(it.toPath()))  }
        //                    .joinToString(separator = "\n")
        //            return testReport
        //        }
        //        return null
    }

    /**
     * Determines if the student's (own) Test class contains at least the minimum number of JUnit tests that are expected
     * by the [Assignment].
     *
     * @return a String with an informative error message or null.
     */
    fun notEnoughStudentTestsMessage() : String? {

        if (!assignment.acceptsStudentTests) {
            throw IllegalArgumentException("This method shouldn't have been called!")
        }

        val junitSummary = junitSummaryAsObject(TestType.STUDENT)

        if (junitSummary == null) {
            return "The submission doesn't include unit tests. " +
                    "The assignment requires a minimum of ${assignment.minStudentTests} tests."
        }

        if (junitSummary.numTests < assignment.minStudentTests!!) {
            return "The submission only includes ${junitSummary.numTests} unit tests. " +
                    "The assignment requires a minimum of ${assignment.minStudentTests} tests."
        }

        return null
    }

    //Get results of tests from assignment test methods repository
    fun testResults() : List<JUnitMethodResult>? {
        if (assignmentTestMethods.isEmpty()) {
            return null  // assignment is not properly configured
        }

        var globalMethodResults = mutableListOf<JUnitMethodResult>()
        for (junitResult in junitResults) {
            if (junitResult.isTeacherPublic(assignment) || junitResult.isTeacherHidden()) {
                globalMethodResults.addAll(junitResult.junitMethodResults)
            }
        }

        var result = mutableListOf<JUnitMethodResult>()
        for (assignmentTest in assignmentTestMethods) {
            var found = false
            for (submissionTest in globalMethodResults) {
                if (submissionTest.methodName.equals(assignmentTest.testMethod) &&
                        submissionTest.getClassName().equals(assignmentTest.testClass)) {
                    result.add(submissionTest)
                    found = true
                    break
                }
            }

            // make sure there are no holes in the tests "matrix"
            if (!found) {
                result.add(JUnitMethodResult.empty())
            }
        }

        return result
    }

    //Check for full execution failed of report
    abstract fun executionFailed() : Boolean

    /**
     * Collects from Output the errors related with the Compilation process.
     * Will stop the compilation of everything
     *
     * @return a List of String where each String is a Compilation problem / warning.
     */
    abstract fun compilationErrors() : List<String>

    //Check if checkstyle validation is active to specific language
    abstract fun checkstyleValidationActive() : Boolean 

    /**
     * Collects from Output the errors related with the CheckStyle plugin / rules.
     * Doesnt appear in Assignment creation
     *
     * @return a List of String where each String is a CheckStyle problem / warning.
     */
    abstract fun checkstyleErrors() : List<String>
}