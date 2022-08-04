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

import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.model.impl.DefaultJavaMethod
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.dropProject.Constants
import org.dropProject.dao.Assignment
import org.dropProject.dao.Language
import org.dropProject.dao.TestVisibility
import org.dropProject.extensions.toEscapedHtml
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileReader

/**
 * This class performs validation of the assignments created by teachers, in order to make sure that they have the
 * correct formats and include the expected plugins.
 *
 * @property report is a List of [Info], containing warnings about the problems that were identified during the validation
 * @property testMethods is a List of String, containing the names of the JUnit test methods that were found in the assignment's
 * test classes. Each String will contain the name of a test method, prefixed by the name of the class where it was declared.
 */
@Service
@Scope("prototype")
abstract class AssignmentValidator {

    enum class InfoType { INFO, WARNING, ERROR }

    /**
     * Represents an assignment validation message that is used to report problems with the [Assignment] being validated.
     */
    data class Info(val type: InfoType, val message: String, val description: String = "")

    val report = mutableListOf<Info>()
    val testMethods = mutableListOf<String>()

    /**
     * Picks between which [Assignment] validator we will be using.
     * That depends on which compiler is being used
     *
     * @param assignmentFolder is a File, representing the file system folder where the assignment's code is stored
     * @param assignment is the Assignment to validate
     */
    abstract fun validate(assignmentFolder: File, assignment: Assignment)

    /**
     * Validates an [Assignment]'s test files to determine if the test classes are respecting the expected
     * formats (for example, ensure that the respective filename starts with the correct prefix).
     *
     * @param assignmentFolder is a File, representing the file system folder where the assignment's code is stored
     * @param assignment is the Assignment to validate
     */
    abstract fun validateProperTestClasses(assignmentFolder: File, assignment: Assignment)

    //Get all code files within folder (for java and kotlin)
    protected fun searchAllSourceFilesWithinFolder(folder: File, text: String): Boolean {
        return folder.walkTopDown()
                .filter { it -> it.name.endsWith(".java") || it.name.endsWith(".kt") }
                .any {
                    it.readText().contains(text)
                }
    }
}
