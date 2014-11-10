package net.swisstech.gradle.dropwizard

import net.swisstech.swissarmyknife.lang.*

import org.slf4j.*

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.*
import org.gradle.api.plugins.*
import org.gradle.api.publish.maven.*

import org.gradle.util.ConfigureUtil

// TODO improve in process/port handline: check if the required ports are already used
// before starting and kill the other process in case it's a remnant of a previous run

/** adds integration tests to a dropwizard project */
class DropwizardInttestPlugin implements Plugin<Project> {

	static final Logger LOG = LoggerFactory.getLogger(DropwizardInttestPlugin.class)

	void apply(Project project) {

		project.extensions.create('dropwizard_inttest', DropwizardInttestExtension)

		// we require the dropwizard plugin to be present because we re-use
		// the dropwizardRun task for its commandline to run the server
		try {
			project.plugins.getPlugin('dropwizard')
		}
		catch (UnknownPluginException e) {
			throw new InvalidUserDataException("DropwizardInttest requires the 'dropwizard' plugin")
		}

		project.afterEvaluate {
			configureProject(project)
			def joinSep = '\n\t\t\t\t\t\t\t'
			println ">>> project.configurations.intTestCompile        = ${project.configurations.intTestCompile.join(joinSep)}"
			println ">>> project.configurations.intTestRuntime        = ${project.configurations.intTestRuntime.join(joinSep)}"
			println ">>> "
			println ">>> project.sourceSets.main.java.srcDirs         = ${project.sourceSets.main.java.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.main.resources.srcDirs    = ${project.sourceSets.main.resources.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.main.output               = ${project.sourceSets.main.output.join(joinSep)}"
			println ">>> "
			println ">>> project.sourceSets.test.java.srcDirs         = ${project.sourceSets.test.java.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.test.resources.srcDirs    = ${project.sourceSets.test.resources.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.test.output               = ${project.sourceSets.test.output.join(joinSep)}"
			println ">>> "
			println ">>> project.sourceSets.intTest.java.srcDirs      = ${project.sourceSets.intTest.java.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.intTest.resources.srcDirs = ${project.sourceSets.intTest.resources.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.intTest.output            = ${project.sourceSets.intTest.output.join(joinSep)}"
			println ">>> "
			println ">>> project.sourceSets.accTest.java.srcDirs      = ${project.sourceSets.accTest.java.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.accTest.resources.srcDirs = ${project.sourceSets.accTest.resources.srcDirs.join(joinSep)}"
			println ">>> project.sourceSets.accTest.output            = ${project.sourceSets.accTest.output.join(joinSep)}"
			println ">>> "
			println ">>> project.tasks.test.testSrcDirs               = ${project.tasks.test.testSrcDirs}"
			println ">>> project.tasks.test.testClassesDir            = ${project.tasks.test.testClassesDir}"
			println ">>> project.tasks.test.classpath                 = ${project.tasks.test.classpath.join(joinSep)}"
			println ">>> "
			println ">>> project.tasks.intTest.testSrcDirs            = ${project.tasks.intTest.testSrcDirs}"
			println ">>> project.tasks.intTest.testClassesDir         = ${project.tasks.intTest.testClassesDir}"
			println ">>> project.tasks.intTest.classpath              = ${project.tasks.intTest.classpath.join(joinSep)}"
			println ">>> "
			println ">>> project.tasks.accTest.testSrcDirs            = ${project.tasks.accTest.testSrcDirs}"
			println ">>> project.tasks.accTest.testClassesDir         = ${project.tasks.accTest.testClassesDir}"
			println ">>> project.tasks.accTest.classpath              = ${project.tasks.accTest.classpath.join(joinSep)}"
			println ">>> "
		}
	}

	void configureProject(Project project) {

		project.configure(project) {

			DropwizardInttestExtension dwe = dropwizard_inttest.validate()

			// parse development.yml for urls/ports
			ext.dwConfig = DropwizardConfigLoader.parse(file(dropwizard.dropwizardConfigFile))

			// the integration and acceptance test setups are identical, make them in a loop
			// TODO factor all the port stuff out into a util and refine it
			[ dwe.intTestTaskName, dwe.accTestTaskName ].each { String taskName ->

				// unnecessary as long as we're called in 'afterEvaluate'. at that
				// point the user's buildscript already needs to have the
				// configuration  or else he can't add dependencies to them.
				//configurations.maybeCreate("${taskName}Compile")

				// add a source set, we don't need to bother the user with that
				// since it's trivial and he doesn't need to do anything special
				sourceSets.create("${taskName}") {
					java.srcDir(     "src/${taskName}/java")
					resources.srcDir("src/${taskName}/resources")
				}

				// we only want the main classes available, don't need the test in intTest/accTest
				dependencies.add "${taskName}Compile", sourceSets.main.output

				// actual test task, not much to configure here
				task("${taskName}", type: Test.class, dependsOn: [ "classes", "${taskName}Classes" ]) {
					useTestNG()
					description    = 'Runs intTest against a locally running server. Generated by dropwizard_inttest plugin.'
					group          = 'verification'
					testSrcDirs    = sourceSets."${taskName}".java.srcDirs as List
					testClassesDir = sourceSets."${taskName}".output.classesDir
					classpath      = sourceSets."${taskName}".runtimeClasspath

					// add all urls parsed from the config to the environment
					dwConfig.urls.each {
						systemProperty(it.key, it.value)
					}
				}

				// start dropwizard before the tests are run, we check and wait
				// until the ports registered in the yml file are open so we know
				// dropwizard is up and running!
				tasks."${taskName}".doFirst {
					LOG.info("starting server before ${taskName}")

					// we re-use the commandline from the Dropwizard plugin's dropwizardRun
					def commandLine = tasks['dropwizardRun'].commandLine
					Process process = ProcessUtil.launch(commandLine, projectDir)

					Set<Integer> found   = [] as Set
					long         start   = System.currentTimeMillis()
					long         maxWait = start + 10000
					int          pid     = process.pid

					if (dwConfig.ports == null || dwConfig.ports.isEmpty()) {
						throw new InvalidUserDataException("No port definitions found in ${dropwizardConfigFile}")
					}

					LOG.info("waiting until all of these ports are open: ${dwConfig.ports}")

					// loop and sleep until all expected ports are open
					while (true) {
						for (String port : dwConfig.ports) {
							def foundPid = "lsof -t -i :${port}".execute().text.trim()
							if (foundPid != null && foundPid.length() > 0) {
								found << Integer.parseInt(port)
							}
							if (foundPid != null && !foundPid.isEmpty() && foundPid as int != pid) {
								ProcessUtil.killAndWait(process)
								throw new InvalidUserDataException("Ports are spread across multiple processes, bailing! Go check out that other process with pid: ${foundPid}!")
							}
						}

						LOG.info("Open ports right now: ${found}")
						if (found.containsAll(dwConfig.ports)) {
							break
						}

						try {
							int rv = process.exitValue()
							throw new InvalidUserDataException("Dropwizad process exited with exit code ${rv}")
						}
						catch (IllegalThreadStateException e) {
							// ignore, process is still running, all is good
						}

						if (System.currentTimeMillis() > maxWait) {
							ProcessUtil.killAndWait(process)
							throw new InvalidUserDataException("Timeout while waiting for dropwizard to start (was waiting for ports: ${dwConfig.ports})")
						}

						sleep(50)
					}

					def done = System.currentTimeMillis() - start
					LOG.info("dropwizard up and running for ${taskName} after ${done} millis")

					ext."${taskName}Process" = process
				}

				tasks."${taskName}".doLast {
					LOG.info("stopping server after ${taskName}")
					ProcessUtil.killAndWait(ext."${taskName}Process")
					LOG.info("server stopped after ${taskName}")
				}

				// add the compile classpath to eclipse
				if (plugins.hasPlugin('eclipse')) {
					eclipse.classpath.plusConfigurations += [ configurations["${taskName}Compile"] ]
				}


			} // end for each taskName

			// additionally, we need a shadow jar for the acceptance tests
			apply plugin: 'com.github.johnrengelman.shadow'
			task("${dwe.accTestTaskName}ShadowJar", type: Class.forName('com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar')) {
				// build should depend on us
				tasks['build'].dependsOn name
				// shadow extends jar, same config
				classifier = "acceptance"
				from sourceSets[dwe.accTestTaskName].runtimeClasspath
				manifest {
					mergeServiceFiles()
					attributes 'Main-Class': 'org.testng.TestNG'
				}
			}
		}
	}
}

class DropwizardInttestExtension {

	/**
	 * the name of your integration test task name. this also means there will
	 * be a configuration named <code>${intTestTaskName}Compile</code> and a
	 * <code>${intTestTaskName}Runtime</code>. Plus, your code and resources
	 * must be in <code>src/${intTestTaskName}/java</code> and
	 * <code>src/${intTestTaskName}/resources</code>. These source dirs will be
	 * in a <code>sourceSet</code> with the name, you guessed it:
	 * <code>${intTestTaskName}</code>
	 */
	def String intTestTaskName = null

	/**
	 * the name of your acceptance test task name. this also means there will
	 * be a configuration named <code>${accTestTaskName}Compile</code> and a
	 * <code>${accTestTaskName}Runtime</code>. Plus, your code and resources
	 * must be in <code>src/${accTestTaskName}/java</code> and
	 * <code>src/${accTestTaskName}/resources</code>. These source dirs will be
	 * in a <code>sourceSet</code> with the name, you guessed it:
	 * <code>${accTestTaskName}</code>
	 */
	def String accTestTaskName = null

	DropwizardInttestExtension validate() {
		if (Strings.isBlank(intTestTaskName)) {
			throw new InvalidUserDataException("The intTestTaskName must not be null/empty")
		}
		if (Strings.isBlank(accTestTaskName)) {
			throw new InvalidUserDataException("The accTestTaskName must not be null/empty")
		}
		return this
	}
}
