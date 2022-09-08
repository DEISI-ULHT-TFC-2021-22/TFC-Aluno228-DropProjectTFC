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

//TODO: Have to finish this class (still not operation, checks outputlines of gradle, needs testing)

/**
 * Represents the output that is generated for a Gradle [Submission]'s code.
 *
 * @property outputLines is a List of String, where each String is a line of the Maven's build process's output
 * @property projectFolder is a String
 * @property assignment identifies the [Assignment] that Submission targetted.
 * @property junitResults is a List of [JunitResults] with the result of evaluating the Submission using JUnit tests
 * @property jacocoResults is a List of [JacocoResults] with the result of evaluating the Submission's code coverage
 * @property assignmentTestMethods is a List of [AssignmentTestMethod]. Each object describes on of the executed Unit Tests
 */
class BuildReportAndroid(outputLines: List<String>,
                       projectFolder: String,
                       assignment: Assignment,
                       junitResults: List<JUnitResults>,
                       jacocoResults: List<JacocoResults>,
                       assignmentTestMethods: List<AssignmentTestMethod>) : 
                       BuildReport(outputLines, projectFolder, assignment, junitResults, jacocoResults, assignmentTestMethods) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)
  
    //Check for full execution failed of report (Gradle)
    override fun executionFailed() : Boolean {        
        // if it has a failed goal other than compiler or surefire (junit), it is a fatal error
        //Havent seen any failures in gradle yet, so for now is fully true
        /*
        if (outputLines.
                        filter { it.startsWith("[ERROR] Failed to execute goal") }.isNotEmpty()) {
            return outputLines.filter {
                        it.startsWith("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin") ||
                        it.startsWith("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin") ||
                        it.startsWith("[ERROR] Failed to execute goal org.jetbrains.kotlin:kotlin-maven-plugin")
            }.isEmpty()
        }
        */

        return false;
    }

    /**
     * Collects from Gradle Output the errors related with the Compilation process.
     *
     * @return a List of String where each String is a Compilation problem / warning.
     */
    override fun compilationErrors() : List<String> {
        var errors = ArrayList<String>()
        //errors.add("Test of error")

        /* Android is still in Kotlin */
        val folder = if (assignment.language == Language.JAVA) "java" else "kotlin"

        /*
        // parse compilation errors
        run {
            val triggerStartOfCompilationOutput =
                    if (assignment.language == Language.JAVA)
                        "\\[ERROR\\] COMPILATION ERROR :.*".toRegex()
                    else
                        "\\[INFO\\] --- kotlin-maven-plugin:\\d+\\.\\d+\\.\\d+:compile.*".toRegex()

            var startIdx = -1; 
            var endIdx = -1;
            for ((idx, outputLine) in outputLines.withIndex()) {
                if (triggerStartOfCompilationOutput.matches(outputLine)) {
                    startIdx = idx + 1
                    LOG.trace("Found start of compilation output (line $idx)")
                } else if (startIdx > 0) {
                    if (outputLine.startsWith("[INFO] BUILD FAILURE") ||
                            outputLine.startsWith("[INFO] --- ")) {    // no compilation errors on Kotlin
                        endIdx = idx
                        LOG.trace("Found end of compilation output (line $idx)")
                        break
                    }
                }
            }

            if (startIdx > 0 && endIdx > startIdx) {
                errors.addAll(
                    outputLines
                                .subList(startIdx, endIdx)
                                .filter { it -> it.startsWith("[ERROR] ") || it.startsWith("  ") }
                                .map { it -> it.replace("[ERROR] ${projectFolder}/src/main/${folder}/", "") }
                                .map { it -> it.replace("[ERROR] ${projectFolder}/src/test/${folder}/", "[TEST] ") })
            }
        }

        // parse test compilation errors
        run {
            //if (language == Language.JAVA) "???" else
            val triggerStartOfTestCompilationOutput = "\\[ERROR\\] Failed to execute goal org\\.jetbrains\\.kotlin:kotlin-maven-plugin.*test-compile.*".toRegex()

            var startIdx = -1;
            var endIdx = -1;
            for ((idx, outputLine) in outputLines.withIndex()) {
                if (triggerStartOfTestCompilationOutput.matches(outputLine)) {
                    startIdx = idx + 1
                }
                if (outputLine.startsWith("[ERROR] -> [Help 1]")) {
                    endIdx = idx
                }
            }

            if (startIdx > 0) {
                errors.addAll(
                    outputLines
                                .subList(startIdx, endIdx)
                                .filter { it -> it.startsWith("[ERROR] ") || it.startsWith("  ") }
                                .map { it -> it.replace("[ERROR] ${projectFolder}/src/main/${folder}/", "") }
                                .map { it -> it.replace("[ERROR] ${projectFolder}/src/test/${folder}/", "[TEST] ") })
            }
        }

        // check if tests didn't run because of a crash or System.exit(). for the lack of better solution, I'll
        // consider this as a compilation error
        if (outputLines.any { it.contains("The forked VM terminated without properly saying goodbye.") }) {
           when (assignment.language) {
               Language.JAVA -> errors.add("Invalid call to System.exit(). Please remove this instruction")
               Language.KOTLIN ->  errors.add("Invalid call to System.exit() or exitProcess(). Please remove this instruction")
               Language.ANDROID ->  errors.add("Invalid call to System.exit() or exitProcess(). Please remove this instruction")
            }
        }
        */

        LOG.info("Finished checking for build errors -> ${errors}")
        return errors
    }

    //Check if checkstyle validation is active to specific language
    //TO DO: Create checkstyle validation, for now always true
    override fun checkstyleValidationActive() : Boolean {
        return true

        /*
        //TO DO: Have to add validation to when for Gradle
        when (assignment.language) {
            Language.JAVA -> {
                for (outputLine in outputLines) {
                    if (outputLine.startsWith("[INFO] Starting audit...")) {
                        return true
                    }
                }

                return false
            }
            Language.KOTLIN -> {
                for (outputLine in outputLines) {
                    if (outputLine.startsWith("[INFO] --- detekt-maven-plugin")) {
                        return true
                    }
                }

                return false
            }
            //NEW: Added the case for ANDROID language (same as the KOTLIN variant)
             Language.ANDROID -> {
                for (outputLine in outputLines) {
                    if (outputLine.startsWith("[INFO] --- detekt-maven-plugin")) {
                        return true
                    }
                }

                return false
            }
        }
        */
    }

    /**
     * Collects from Gradle Output the errors related with the CheckStyle plugin / rules.
     *
     * @return a List of String where each String is a CheckStyle problem / warning.
     */
    override fun checkstyleErrors() : List<String> {
        val folder = if (assignment.language == Language.JAVA) "java" else "kotlin"

        return outputLines
            .subList(1, 10)
            .filter { it -> it.startsWith("[INFO] ") }
            .map { it -> it.replace("[IFNO] ${projectFolder}/src/main/${folder}/", "") }
        /*
        when (assignment.language) {
            Language.JAVA -> {
                var startIdx = -1;
                var endIdx = -1;
                for ((idx, outputLine) in outputLines.withIndex()) {
                    if (outputLine.startsWith("[INFO] Starting audit...")) {
                        startIdx = idx + 1
                    }
                    if (outputLine.startsWith("Audit done.")) {
                        endIdx = idx
                    }
                }

                if (startIdx > 0) {
                    return outputLines
                            .subList(startIdx, endIdx)
                            .filter { it -> it.startsWith("[WARN] ") }
                            .map { it -> it.replace("[WARN] ${projectFolder}/src/main/${folder}/", "") }
                } else {
                    return emptyList()
                }
            }

            Language.KOTLIN -> {
                var startIdx = -1;
                var endIdx = -1;
                for ((idx, outputLine) in outputLines.withIndex()) {
                    if (outputLine.startsWith("[INFO] --- detekt-maven-plugin")) {
                        startIdx = idx + 1
                    }
                    // depending on the detekt-maven-plugin version, the output is different
                    if (startIdx > 0 &&
                            idx > startIdx + 1 &&
                            (outputLine.startsWith("detekt finished") || outputLine.startsWith("[INFO]"))) {
                        endIdx = idx
                        break
                    }
                }

                if (startIdx > 0) {
                    return outputLines
                            .subList(startIdx, endIdx)
                            .filter { it.startsWith("\t") && !it.startsWith("\t-") }
                            .map { it.replace("\t", "") }
                            .map { it.replace("${projectFolder}/src/main/${folder}/", "") }
                            .map { translateDetektError(it) }
                            .distinct()
                } else {
                    return emptyList()
                }
            }

            //NEW: Added verifier for ANDROID language build in function (same as KOTLIN)
            Language.ANDROID -> {
                var startIdx = -1;
                var endIdx = -1;
                for ((idx, outputLine) in outputLines.withIndex()) {
                    if (outputLine.startsWith("[INFO] --- detekt-maven-plugin")) {
                        startIdx = idx + 1
                    }
                    // depending on the detekt-maven-plugin version, the output is different
                    if (startIdx > 0 &&
                            idx > startIdx + 1 &&
                            (outputLine.startsWith("detekt finished") || outputLine.startsWith("[INFO]"))) {
                        endIdx = idx
                        break
                    }
                }

                if (startIdx > 0) {
                    return outputLines
                            .subList(startIdx, endIdx)
                            .filter { it.startsWith("\t") && !it.startsWith("\t-") }
                            .map { it.replace("\t", "") }
                            .map { it.replace("${projectFolder}/src/main/${folder}/", "") }
                            .map { translateDetektError(it) }
                            .distinct()
                } else {
                    return emptyList()
                }
            }
        }
        */
    }
}