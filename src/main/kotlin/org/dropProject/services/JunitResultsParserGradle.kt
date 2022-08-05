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

import org.apache.maven.plugin.surefire.log.api.PrintStreamLogger
import org.apache.maven.plugins.surefire.report.ReportTestCase
import org.apache.maven.plugins.surefire.report.TestSuiteXmlParser
import org.springframework.stereotype.Service
import java.io.InputStreamReader
import org.apache.maven.plugins.surefire.report.ReportTestSuite
import org.dropProject.Constants
import org.dropProject.dao.Assignment
import java.io.ByteArrayInputStream
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Utility for parsing JUnit test results a String.
 */
@Service
class JunitResultsParserGradle : JunitResultsParser() {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Parses from a String of Gradle compilation the test results of testing a single Test class.
     *
     * @param content is a String containing the contents of an XML file with a JUnit report.
     *
     * @return a [JUnitResults]
     */
    override fun parseXml(content: String) : JUnitResults {
        val parser = TestSuiteXmlParser(PrintStreamLogger(System.out))
        val byteArrayIs = ByteArrayInputStream(content.toByteArray())
        val parseResults: List<ReportTestSuite> = parser.parse(InputStreamReader(byteArrayIs, Charsets.UTF_8))

        assert(parseResults.size == 1)

        val parseResult = parseResults[0]
        val testCases : List<ReportTestCase> = parseResult.testCases

        val junitMethodResults = testCases.map { JUnitMethodResult(it.name, it.fullName,
                if (it.hasError()) JUnitMethodResultType.ERROR
                else if (it.hasFailure()) JUnitMethodResultType.FAILURE
                else if (it.hasSkipped()) JUnitMethodResultType.IGNORED else JUnitMethodResultType.SUCCESS,
                it.failureType, it.failureErrorLine,
                it.failureDetail) }.toList()

        return JUnitResults(parseResult.name, parseResult.fullClassName,
                parseResult.numberOfTests - parseResult.numberOfSkipped,
                parseResult.numberOfErrors, parseResult.numberOfFailures, parseResult.numberOfSkipped,
                parseResult.timeElapsed, junitMethodResults)
    }
}
