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

import org.dropProject.Constants
import org.dropProject.data.Result
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileReader
import org.dropProject.dao.*
import java.io.StringWriter
import java.util.*
import org.gradle.tooling.*

/**
 * NEW: Added to perform Gradle tasks
 * Utility to perform Gradle related tasks.
 */
@Service
class GradleInvoker {    
    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    var securityManagerEnabled = true

    @Value("\${dropProject.maven.repository}")
    val repository : String = ""

    fun disableSecurity() {
        securityManagerEnabled = false
    }

    var showOutput = false

    /**
     * Runs the project using Gradle. This function executes the compilation and testing of a submitted project.
     *
     * @param projectFolder is a File containing the project's files
     * @param principalName is a String
     * @param maxMemoryMb is an Int
     *
     * @return a Result
     */
    fun run(projectFolder: File, principalName: String?, assignment: Assignment) : Result {
        LOG.info("Project folder == ${projectFolder}")
 
        //Check if repository already exists
        if (!File(repository).exists()) {
            val success = File(repository).mkdirs()
            if (!success) {
                LOG.error("Couldn't create the repository folder: $repository")
            }
        }

        //Setup variables for output
        var exitLines = ArrayList<String>() //Output lines from invoker
        var exitCode = 0 //0 is good, everything else is bad (1)

        try {
            val connection = GradleConnector.newConnector().forProjectDirectory(projectFolder).connect()
            val build: BuildLauncher = connection.newBuild()

            //Select tasks to run
            build.forTasks("clean", "build", "test")

            //include some build arguments:
            /*
            //in case you want the build to use java different than default:
            build.setJavaHome(File("/path/to/java"))
            //configure the standard input:
            build.setStandardInput(ByteArrayInputStream("consume this!".toByteArray()))
            //if your build needs crazy amounts of memory:
            build.setJvmArguments("-Xmx2048m", "-XX:MaxPermSize=512m")
            */

            //if you want to listen to the progress events: 
            build.addProgressListener(ProgressListener {
                LOG.info("progress ${it.description}")
                exitLines.add(it.description)
            })

            //kick the build off
            LOG.info("Before gradle clean, build, test...")
            build.run()
            LOG.info("After gradle clean, build, test!")
        } catch (ex: Exception) {
            ex.printStackTrace()
            LOG.error(ex.localizedMessage)
            exitCode = 1
        }

        //Return result
        return Result(resultCode = exitCode, outputLines = exitLines)
    }
}