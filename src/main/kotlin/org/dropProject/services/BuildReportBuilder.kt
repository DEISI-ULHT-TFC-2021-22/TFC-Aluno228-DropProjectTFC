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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.dropProject.dao.*
import org.dropProject.dao.JUnitReport
import org.dropProject.dao.JacocoReport
import org.dropProject.dao.Submission
import org.dropProject.data.*
import org.dropProject.data.BuildReport
import org.dropProject.repository.AssignmentTestMethodRepository
import org.dropProject.repository.JUnitReportRepository
import org.dropProject.repository.JacocoReportRepository
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.util.logging.Logger

/**
 * This class contains functions that perform the creation of [BuildReport]s for both [Assignment]s and [Submission]s.
 */
@Service
abstract class BuildReportBuilder {

    @Autowired
    lateinit var junitResultsParserMaven: JunitResultsParserMaven

    @Autowired
    lateinit var junitResultsParserGradle: JunitResultsParserGradle

    @Autowired
    lateinit var jacocoResultsParser: JacocoResultsParser

    @Autowired
    lateinit var jUnitReportRepository: JUnitReportRepository

    @Autowired
    lateinit var jacocoReportRepository: JacocoReportRepository

    @Autowired
    lateinit var assignmentTestMethodRepository: AssignmentTestMethodRepository

    /**
     * Builds a BuildReport
     *
     * @param outputLines is a List of String with the output of a build process
     * @param mavenizedProjectFolder is a String
     * @param assignment is an [Assignment]
     * @param submission is a [Submission]
     *
     * @return a [BuildReport]
     */
    abstract fun build(outputLines: List<String>,
              mavenizedProjectFolder: String,   
              assignment: Assignment,
              submission: Submission? = null) : BuildReport 
}
