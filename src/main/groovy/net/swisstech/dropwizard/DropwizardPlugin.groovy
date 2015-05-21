package net.swisstech.dropwizard

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import net.swisstech.swissarmyknife.sys.linux.BackgroundProcess
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static net.swisstech.swissarmyknife.lang.Strings.isNotBlank

class DropwizardPlugin implements Plugin<Project> {

    static final Logger LOG = LoggerFactory.getLogger(DropwizardPlugin.class)

    void apply(Project project) {

        def osName = System.getProperty("os.name")

        if ((!"Linux".equals(osName)) && (!"Mac OS X".equals(osName))) {
            LOG.error("!!! WARNING: Your running on $osName so things might not work out as this plugin has been tested on linux and mac" )
        }

        project.extensions.create('dropwizard', DropwizardExtension)

        project.afterEvaluate {
            def configFile = project.dropwizard.dropwizardConfigFile

            if (project.dropwizard.startWireMock) {
                configFile = generateMockConfig(configFile, project.dropwizard.wireMockPort, project.dropwizard.urlsToBeMocked, project.buildDir.absolutePath)
            }

            project.ext.dwConfig = DropwizardConfigLoader.parse(project.file(configFile))

            configureProject(project)

            if (isNotBlank(project.dropwizard.taskName)) {
                configureTask(project, project.dropwizard.taskName)
            }
        }
    }

    /** base config of project, always applied */
    void configureProject(Project project) {

        project.configure(project) {

            // npn-boot is available from maven central
            repositories {
                mavenCentral()
                mavenLocal()
            }

            // we need a special classpath just for the boot classpath
            configurations {
                dropwizardRunBootClassPath
            }

            dependencies {
                // classpath for dropwizard's jvm's bootclasspath
                // get the correct npn lib version here:
                // http://www.eclipse.org/jetty/documentation/current/npn-chapter.html#npn-versions
                // TODO select correct version based on JVM version (although there seems to be some leeway)
                dropwizardRunBootClassPath "org.mortbay.jetty.npn:npn-boot:1.1.7.v20140316"
            }

            // we need to append to the bootclasspath manually because the impl
            // in JavaExec is buggy: http://gsfn.us/t/4mjt7

            // TODO the SERVER_VERSION is a thing specific to my personal use case and it
            // should be made configurable via the dropwizard extension

            task('dropwizardRun', type: JavaExec, dependsOn: "classes") {
                workingDir = projectDir
                classpath = sourceSets.main.runtimeClasspath
                main = dropwizard.mainClass
                jvmArgs "-Xbootclasspath/a:${configurations.dropwizardRunBootClassPath.asPath}"
                args "server", dropwizard.dropwizardConfigFile
                project.dropwizard.jvmArgs.each { jvmArgs it }
            }

        }
    }

    void configureTask(Project project, String taskName) {

        project.configure(project) {
            WireMockServer wireMockServer

            tasks."${taskName}".doFirst {

                if (project.dropwizard.startWireMock) {
                    LOG.info("Starting WIREMOCK server on port: $project.dropwizard.wireMockPort, with root dir: $project.dropwizard.wireMockRoot")
                    def wireMockConfiguration = WireMockConfiguration.wireMockConfig().port(project.dropwizard.wireMockPort as int).withRootDirectory("$project.dropwizard.wireMockRoot")
                    wireMockServer = new WireMockServer(wireMockConfiguration)
                    wireMockServer.start()
                }

                LOG.info("Starting dropwizard server before ${taskName}")

                // we re-use the commandline from the dropwizardRun task
                def commandLine = tasks['dropwizardRun'].commandLine
                long start = System.currentTimeMillis()

                project.ext."${taskName}Process" = BackgroundProcess.launch(commandLine, projectDir).waitForOpenPorts(dwConfig.ports, 10000)

                LOG.info("dropwizard up and running for ${taskName} after ${System.currentTimeMillis() - start} millis")
            }

            // kill the server
            tasks."${taskName}".doLast {
                LOG.info("stopping server after ${taskName}")
                def process = null
                try {
                    process = project."${taskName}Process"
                }
                catch (MissingPropertyException e) {
                }

                if (process) {
                    int rv = process.shutdown()
                    LOG.info("server stopped with exit value ${rv} after ${taskName}")
                } else {
                    LOG.warn("no process available to be shut down")
                }
                wireMockServer?.stop()
            }
        }
    }

    private String generateMockConfig(String dropwizardConfigFile, String stubPort,
                                      def urlsToBeMocked, String buildDir) {
        def config = new File(dropwizardConfigFile).text
        urlsToBeMocked.each { config = config.replaceAll(it, "http://localhost:$stubPort") }
        def configFile = "wiremock-$dropwizardConfigFile"
        printf configFile
        new File(configFile).write(config)
        return configFile
    }

}
