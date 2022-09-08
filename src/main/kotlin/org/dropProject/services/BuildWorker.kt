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
package org.dropProject.services

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.*
import org.dropProject.data.BuildReport
import org.dropProject.data.TestType
import org.dropProject.data.Result
import org.dropProject.repository.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*
import java.util.logging.Logger

/**
 * Determines if a project's POM file contains the configuration to calculate the code's test coverage report.
 * @param mavenizedProjectFolder
 * @return a Boolean
 */
fun hasCoverageReport(mavenizedProjectFolder: File): Boolean {
    val pomFile = File("${mavenizedProjectFolder}/pom.xml")
    return pomFile.readText().contains("jacoco-maven-plugin")
}

/**
 * This class contains functions that execute the build process for [Assignment]s and [Submission]s.
 */
@Service
class BuildWorker(
        val mavenInvoker: MavenInvoker,
        val gradleInvoker: GradleInvoker,
        val assignmentRepository: AssignmentRepository,
        val submissionRepository: SubmissionRepository,
        val gitSubmissionRepository: GitSubmissionRepository,
        val submissionReportRepository: SubmissionReportRepository,
        val buildReportRepository: BuildReportRepository,
        val jUnitReportRepository: JUnitReportRepository,
        val jacocoReportRepository: JacocoReportRepository,
        val buildReportBuilderMaven: BuildReportBuilderMaven,
        val buildReportBuilderGradle: BuildReportBuilderGradle,
        val buildReportBuilderAndroid: BuildReportBuilderAndroid) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Checks a [Submission], performing all relevant build and evaluation steps (for example, Compilation) and storing
     * each step's results in the database.
     * Only used for Submission
     *
     * @param projectFolder is a File
     * @param authorsStr is a String
     * @param submission is a [Submission]
     * @param principalName is a String
     * @param dontChangeStatusDate is a Boolean
     * @param rebuildByTeacher is a Boolean
     */
    @Async
    @Transactional
    fun checkSubmission(projectFolder: File, authorsStr: String, submission: Submission,
                          principalName: String?, dontChangeStatusDate: Boolean = false, rebuildByTeacher: Boolean = false) {
        val assignment = assignmentRepository.findById(submission.assignmentId).orElse(null)
        val realPrincipalName = if (rebuildByTeacher) submission.submitterUserId else principalName

        //NEW: Added condition to check if either Maven, Gradle or Android are used
        var result: Result = Result()
        if (assignment.engine == Engine.MAVEN) {
            if (assignment.maxMemoryMb != null) {
                LOG.info("[${authorsStr}] Started maven invocation (max: ${assignment.maxMemoryMb}Mb)")
            } else {
                LOG.info("[${authorsStr}] Started maven invocation")
            }

            //Run invoker of engine (clean, compile, test)
            result = mavenInvoker.run(projectFolder, realPrincipalName, assignment.maxMemoryMb)

            //Create build report for Maven
            buildMaven(result, assignment, submission, projectFolder, realPrincipalName, dontChangeStatusDate, rebuildByTeacher)
        } else if (assignment.engine == Engine.GRADLE) { //Assignment engine is Gradle
            if (assignment.maxMemoryMb != null) {
                LOG.info("[${authorsStr}] Started gradle invocation (max: ${assignment.maxMemoryMb}Mb)")
            } else {
                LOG.info("[${authorsStr}] Started gradle invocation")
            }

            //Run invoker of engine (clean, compile, test)
            result = gradleInvoker.run(projectFolder, realPrincipalName, assignment)

            //Create build report for Gradle
            buildGradle(result, assignment, submission, projectFolder, realPrincipalName, dontChangeStatusDate, rebuildByTeacher)
        } else if (assignment.engine == Engine.ANDROID) { //Assignment engine is Android
            if (assignment.maxMemoryMb != null) {
                LOG.info("[${authorsStr}] Started android invocation (max: ${assignment.maxMemoryMb}Mb)")
            } else {
                LOG.info("[${authorsStr}] Started android invocation")
            }

            //Run invoker of engine (clean, compile, test)
            result = gradleInvoker.run(projectFolder, realPrincipalName, assignment)

            //Create build report for Android
            buildAndroid(result, assignment, submission, projectFolder, realPrincipalName, dontChangeStatusDate, rebuildByTeacher)
        }

        //Part is functional for all engines
        submission.gitSubmissionId?.let {
            gitSubmissionId ->
                val gitSubmission = gitSubmissionRepository.getById(gitSubmissionId)
                gitSubmission.lastSubmissionId = submission.id
        }

        submissionRepository.save(submission)
    }

    //Create build report for Maven
    private fun buildMaven(result: Result, assignment: Assignment, submission: Submission, 
                        projectFolder: File, realPrincipalName: String?, dontChangeStatusDate: Boolean,  rebuildByTeacher: Boolean) {
        // check result for errors (expired, too much output)
        when {
            result.expiredByTimeout -> submission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT,
                                                                    dontUpdateStatusDate = dontChangeStatusDate)
            result.tooMuchOutput() -> submission.setStatus(SubmissionStatus.TOO_MUCH_OUTPUT,
                                                                    dontUpdateStatusDate = dontChangeStatusDate)
            else -> { // get build report
                val buildReport = buildReportBuilderMaven.build(result.outputLines, projectFolder.absolutePath,
                        assignment, submission)

                // clear previous indicators except PROJECT_STRUCTURE
                submissionReportRepository.deleteBySubmissionIdExceptProjectStructure(submission.id)

                if (!buildReport.executionFailed()) {

                    submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                            reportKey = Indicator.COMPILATION.code, reportValue = if (buildReport.compilationErrors().isEmpty()) "OK" else "NOK"))

                    if (buildReport.compilationErrors().isEmpty()) {

                        if (buildReport.checkstyleValidationActive()) {
                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.CHECKSTYLE.code, reportValue = if (buildReport.checkstyleErrors().isEmpty()) "OK" else "NOK"))
                        }

                        // PMD not yet implemented
                        // submissionReportRepository.deleteBySubmissionIdAndReportKey(submission.id, "Code Quality (PMD)")
                        // submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                        // reportKey = "Code Quality (PMD)", reportValue = if (buildReport.PMDerrors().isEmpty()) "OK" else "NOK"))

                        //Check if assignment accepts student tests
                        if (assignment.acceptsStudentTests) {
                            val junitSummary = buildReport.junitSummaryAsObject(TestType.STUDENT)
                            val indicator =
                                    if (buildReport.hasJUnitErrors(TestType.STUDENT) == true) {
                                        "NOK"
                                    } else if (junitSummary?.numTests == null || junitSummary.numTests < assignment.minStudentTests!!) {
                                        // student hasn't implemented enough tests
                                        "Not Enough Tests"
                                    } else {
                                        "OK"
                                    }

                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.STUDENT_UNIT_TESTS.code,
                                    reportValue = indicator,
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                        }

                        if (buildReport.hasJUnitErrors(TestType.TEACHER) != null) {
                            val junitSummary = buildReport.junitSummaryAsObject(TestType.TEACHER)
                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.TEACHER_UNIT_TESTS.code,
                                    reportValue = if (buildReport.hasJUnitErrors(TestType.TEACHER) == true) "NOK" else "OK",
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                        }

                        if (buildReport.hasJUnitErrors(TestType.HIDDEN) != null) {
                            val junitSummary = buildReport.junitSummaryAsObject(TestType.HIDDEN)
                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.HIDDEN_UNIT_TESTS.code,
                                    reportValue = if (buildReport.hasJUnitErrors(TestType.HIDDEN) == true) "NOK" else "OK",
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                        }
                    }
                }

                //TODO: Build report search for maven output? What about gradle?
                val buildReportDB = buildReportRepository.save(org.dropProject.dao.BuildReport(
                        buildReport = buildReport.getOutput()))
                submission.buildReportId = buildReportDB.id

                // store the junit reports in the DB
                File("${projectFolder}/target/surefire-reports")
                        .walkTopDown()
                        .filter { it -> it.name.endsWith(".xml") }
                        .forEach {
                            val report = JUnitReport(submissionId = submission.id, fileName = it.name,
                                    xmlReport = it.readText(Charset.forName("UTF-8")))
                            jUnitReportRepository.save(report)
                        }

                //NEW: Added verification for engine Maven to make sure no errors happen in test coverage
                if (assignment.engine == Engine.MAVEN && assignment.calculateStudentTestsCoverage && hasCoverageReport(projectFolder)) {

                    // this may seem stupid but I have to rename TestTeacher files to something that will make junit ignore them,
                    // then invoke maven again, so that the coverage report is based
                    // on the sole execution of the student unit tests (otherwise, it will include coverage from
                    // the teacher tests) and finally rename TestTeacher files back
                    File("${projectFolder}/src/test")
                            .walkTopDown()
                            .filter { it -> it.name.startsWith("TestTeacher") }
                            .forEach {
                                Files.move(it.toPath(), it.toPath().resolveSibling("${it.name}.ignore"))
                            }


                    LOG.info("Started maven invocation again (for coverage)")

                    val mavenResultCoverage = mavenInvoker.run(projectFolder, realPrincipalName, assignment.maxMemoryMb)
                    if (!mavenResultCoverage.expiredByTimeout) {
                        LOG.info("Finished maven invocation (for coverage)")

                        // check again the result of the tests
                        val buildReportCoverage = buildReportBuilderMaven.build(result.outputLines, projectFolder.absolutePath,
                                assignment, submission)
                        if (buildReportCoverage.hasJUnitErrors(TestType.STUDENT) == true) {
                            LOG.warn("Submission ${submission.id} failed executing student tests when isolated from teacher tests")
                        } else {
                            if (File("${projectFolder}/target/site/jacoco").exists()) {
                                // store the jacoco reports in the DB
                                File("${projectFolder}/target/site/jacoco")
                                        .listFiles()
                                        .filter { it -> it.name.endsWith(".csv") }
                                        .forEach {
                                            val report = JacocoReport(submissionId = submission.id, fileName = it.name,
                                                    csvReport = it.readText(Charset.forName("UTF-8")))
                                            jacocoReportRepository.save(report)
                                        }
                            } else {
                                LOG.warn("Submission ${submission.id} failed measuring coverage because the folder " +
                                        "[${projectFolder}/target/site/jacoco] doesn't exist")
                            }
                        }

                    }

                    File("${projectFolder}/src/test")
                            .walkTopDown()
                            .filter { it -> it.name.endsWith(".ignore") }
                            .forEach {
                                Files.move(it.toPath(), it.toPath().resolveSibling(it.name.replace(".ignore","")))
                            }

                }

                if (!rebuildByTeacher) {
                    submission.setStatus(SubmissionStatus.VALIDATED, dontUpdateStatusDate = dontChangeStatusDate)
                } else {
                    submission.setStatus(SubmissionStatus.VALIDATED_REBUILT, dontUpdateStatusDate = dontChangeStatusDate)
                }
            }
        }
    }

    //Create build report for Gradle
    private fun buildGradle(result: Result, assignment: Assignment, submission: Submission, 
                        projectFolder: File, realPrincipalName: String?, dontChangeStatusDate: Boolean,  rebuildByTeacher: Boolean) {
        LOG.info("Build report started for Gradle.")
       
        // check result for errors (expired, too much output)
        when {
            result.expiredByTimeout -> submission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT,
                                                                    dontUpdateStatusDate = dontChangeStatusDate)
            result.tooMuchOutput() -> submission.setStatus(SubmissionStatus.TOO_MUCH_OUTPUT,
                                                                    dontUpdateStatusDate = dontChangeStatusDate)
            else -> { 
                // get build report
                val buildReport = buildReportBuilderGradle.build(result.outputLines, projectFolder.absolutePath,
                        assignment, submission)

                // clear previous indicators except PROJECT_STRUCTURE
                submissionReportRepository.deleteBySubmissionIdExceptProjectStructure(submission.id)

                //Check if we got any fatal errors
                if (!buildReport.executionFailed()) {
                    //if not then submission can be added
                    submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                            reportKey = Indicator.COMPILATION.code, reportValue = if (buildReport.compilationErrors().isEmpty()) "OK" else "NOK"))

                    //Check if there are any project compilation errors
                    if (buildReport.compilationErrors().isEmpty()) {
                        if (buildReport.checkstyleValidationActive()) {
                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.CHECKSTYLE.code, reportValue = if (buildReport.checkstyleErrors().isEmpty()) "OK" else "NOK"))
                        }

                        //Check student tests
                        if (assignment.acceptsStudentTests) {
                            LOG.info("Checked for student tests!")

                            val junitSummary = buildReport.junitSummaryAsObject(TestType.STUDENT)
                            val indicator =
                                    if (buildReport.hasJUnitErrors(TestType.STUDENT) == true) {
                                        "NOK"
                                    } else if (junitSummary?.numTests == null || junitSummary.numTests < assignment.minStudentTests!!) {
                                        // student hasn't implemented enough tests
                                        "Not Enough Tests"
                                    } else {
                                        "OK"
                                    }

                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.STUDENT_UNIT_TESTS.code,
                                    reportValue = indicator,
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))   
                        }

                        //Check if teacher tests have errors
                        if (buildReport.hasJUnitErrors(TestType.TEACHER) != null) {
                            val junitSummary = buildReport.junitSummaryAsObject(TestType.TEACHER)

                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.TEACHER_UNIT_TESTS.code,
                                    reportValue = if (buildReport.hasJUnitErrors(TestType.TEACHER) == true) "NOK" else "OK",
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                        }

                        //Check if hidden tests have errors
                        if (buildReport.hasJUnitErrors(TestType.HIDDEN) != null) {
                            val junitSummary = buildReport.junitSummaryAsObject(TestType.HIDDEN)

                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.HIDDEN_UNIT_TESTS.code,
                                    reportValue = if (buildReport.hasJUnitErrors(TestType.HIDDEN) == true) "NOK" else "OK",
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                        }
                    }
                }

                //Get report output
                val buildReportDB = buildReportRepository.save(org.dropProject.dao.BuildReport(buildReport = buildReport.getOutput()))
                submission.buildReportId = buildReportDB.id

                //Store the report in the DB
                File("${projectFolder}/target/surefire-reports")
                        .walkTopDown()
                        .filter { it -> it.name.endsWith(".xml") }
                        .forEach {
                            val report = JUnitReport(submissionId = submission.id, fileName = it.name,
                                    xmlReport = it.readText(Charset.forName("UTF-8")))
                            jUnitReportRepository.save(report)
                        }

                //TO DO: For now Gradle has no coverage report, have to add (take example from Maven)

                //Set status of submission
                if (!rebuildByTeacher) {
                    submission.setStatus(SubmissionStatus.VALIDATED, dontUpdateStatusDate = dontChangeStatusDate)
                } else {
                    submission.setStatus(SubmissionStatus.VALIDATED_REBUILT, dontUpdateStatusDate = dontChangeStatusDate)
                }
            }
        }
    }

    //Create build report for Android (currently almost the same as buildGradle)
    private fun buildAndroid(result: Result, assignment: Assignment, submission: Submission, 
                        projectFolder: File, realPrincipalName: String?, dontChangeStatusDate: Boolean,  rebuildByTeacher: Boolean) {
        LOG.info("Build report started for Android.")
       
        // check result for errors (expired, too much output)
        when {
            result.expiredByTimeout -> submission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT,
                                                                    dontUpdateStatusDate = dontChangeStatusDate)
            result.tooMuchOutput() -> submission.setStatus(SubmissionStatus.TOO_MUCH_OUTPUT,
                                                                    dontUpdateStatusDate = dontChangeStatusDate)
            else -> { 
                // get build report
                val buildReport = buildReportBuilderAndroid.build(result.outputLines, projectFolder.absolutePath,
                        assignment, submission)

                // clear previous indicators except PROJECT_STRUCTURE
                submissionReportRepository.deleteBySubmissionIdExceptProjectStructure(submission.id)

                //Check if we got any fatal errors
                if (!buildReport.executionFailed()) {
                    //if not then submission can be added
                    submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                            reportKey = Indicator.COMPILATION.code, reportValue = if (buildReport.compilationErrors().isEmpty()) "OK" else "NOK"))

                    //Check if there are any project compilation errors
                    if (buildReport.compilationErrors().isEmpty()) {
                        if (buildReport.checkstyleValidationActive()) {
                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.CHECKSTYLE.code, reportValue = if (buildReport.checkstyleErrors().isEmpty()) "OK" else "NOK"))
                        }

                        //Check student tests
                        if (assignment.acceptsStudentTests) {
                            LOG.info("Checked for student tests!")

                            val junitSummary = buildReport.junitSummaryAsObject(TestType.STUDENT)
                            val indicator =
                                    if (buildReport.hasJUnitErrors(TestType.STUDENT) == true) {
                                        "NOK"
                                    } else if (junitSummary?.numTests == null || junitSummary.numTests < assignment.minStudentTests!!) {
                                        // student hasn't implemented enough tests
                                        "Not Enough Tests"
                                    } else {
                                        "OK"
                                    }

                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.STUDENT_UNIT_TESTS.code,
                                    reportValue = indicator,
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))   
                        }

                        //Check if teacher tests have errors
                        if (buildReport.hasJUnitErrors(TestType.TEACHER) != null) {
                            val junitSummary = buildReport.junitSummaryAsObject(TestType.TEACHER)

                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.TEACHER_UNIT_TESTS.code,
                                    reportValue = if (buildReport.hasJUnitErrors(TestType.TEACHER) == true) "NOK" else "OK",
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                        }

                        //Check if hidden tests have errors
                        if (buildReport.hasJUnitErrors(TestType.HIDDEN) != null) {
                            val junitSummary = buildReport.junitSummaryAsObject(TestType.HIDDEN)

                            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.HIDDEN_UNIT_TESTS.code,
                                    reportValue = if (buildReport.hasJUnitErrors(TestType.HIDDEN) == true) "NOK" else "OK",
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                        }
                    }
                }

                //Get report output
                val buildReportDB = buildReportRepository.save(org.dropProject.dao.BuildReport(buildReport = buildReport.getOutput()))
                submission.buildReportId = buildReportDB.id

                //Store the report in the DB 
                File("${projectFolder}/target/surefire-reports")
                        .walkTopDown()
                        .filter { it -> it.name.endsWith(".xml") }
                        .forEach {
                            val report = JUnitReport(submissionId = submission.id, fileName = it.name,
                                    xmlReport = it.readText(Charset.forName("UTF-8")))
                            jUnitReportRepository.save(report)
                        }

                //TO DO: For now Android has no coverage report, have to add (take example from Maven)

                //Set status of submission
                if (!rebuildByTeacher) {
                    submission.setStatus(SubmissionStatus.VALIDATED, dontUpdateStatusDate = dontChangeStatusDate)
                } else {
                    submission.setStatus(SubmissionStatus.VALIDATED_REBUILT, dontUpdateStatusDate = dontChangeStatusDate)
                }
            }
        }
    }

    /**
     * NEW: Added Gradle check for LOG comparable to Maven
     * Checks an [Assignment], performing all the relevant steps and generates the respective [BuildReport].
     * Used only in Assignment (when it is submitted)
     * 
     * @param assignmentFolder is a File
     * @param assignment is an [Assignment]
     * @param principalName is a String
     *
     * @return a [BuildReport] or null
     */
    fun checkAssignment(assignmentFolder: File, assignment: Assignment, principalName: String?) : BuildReport? {
        LOG.info("Engine used for assignment ${assignment.id} is ${assignment.engine}.")
        LOG.info("Programming language used for assignment ${assignment.id} is ${assignment.language}.")
        
        if (assignment.engine == Engine.MAVEN) {
            val mavenResult = mavenInvoker.run(assignmentFolder, principalName, assignment.maxMemoryMb)
            if (!mavenResult.expiredByTimeout) {
                LOG.info("Maven invoker OK for ${assignment.id}")
                return buildReportBuilderMaven.build(mavenResult.outputLines, assignmentFolder.absolutePath, assignment)
            } else {
                LOG.info("Maven invoker aborted by timeout for ${assignment.id}")
            }
        } else if (assignment.engine == Engine.GRADLE) { //Assignment is Gradle
            val gradleResult = gradleInvoker.run(assignmentFolder, principalName, assignment)
            if (!gradleResult.expiredByTimeout) {
                LOG.info("Gradle invoker OK for ${assignment.id}")

                return buildReportBuilderGradle.build(gradleResult.outputLines, assignmentFolder.absolutePath, assignment)
            } else {
                LOG.info("Gradle invoker aborted by timeout for ${assignment.id}")
            }
        } else if (assignment.engine == Engine.ANDROID) { //Assignment is Android
            val androidResult = gradleInvoker.run(assignmentFolder, principalName, assignment) //(still built using gradle)
            if (!androidResult.expiredByTimeout) {
                LOG.info("Android invoker OK for ${assignment.id}")

                return buildReportBuilderAndroid.build(androidResult.outputLines, assignmentFolder.absolutePath, assignment)
            } else {
                LOG.info("Android invoker aborted by timeout for ${assignment.id}")
            }
        }

        return null
    }
}
