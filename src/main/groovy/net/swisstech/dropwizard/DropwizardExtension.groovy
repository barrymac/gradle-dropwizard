package net.swisstech.dropwizard

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

class DropwizardExtension {

    /** the main class of your dropwizard application */
    String mainClass

    String dropwizardConfigFile

    String taskName

    String wireMockRoot

    String wireMockPort

    boolean startWireMock

    def urlsToBeMocked

    /**
     * list of jvmArgs to be added, same semantics as JavaExec.jvmArgs(), put your args including '-D' and everything there
     */
    List<String> jvmArgs = []

    /** validate the configuration */
    DropwizardExtension validate(Project project) {
        if (mainClass) {
            throw new InvalidUserDataException("The mainClass must not be null/empty")
        }
        if (dropwizardConfigFile) {
            throw new InvalidUserDataException("The dropwizardConfigFile must not be null/empty")
        }
        if (startWireMock && (wireMockPort || wireMockRoot)) {
            throw new InvalidUserDataException("The wiremock port and root must be set if wiremock is being used")
        }
        File cfg = project.file(dropwizardConfigFile)

        if (!cfg.exists()) {
            throw new InvalidUserDataException("The dropwizardConfigFile does not exist (expected at ${cfg.absolutePath}")
        }
        if (!cfg.isFile()) {
            throw new InvalidUserDataException("The dropwizardConfigFile is not a file (expected at ${cfg.absolutePath}")
        }
        return this
    }
}
