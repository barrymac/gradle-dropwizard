README
=====

Dropwizard plugin to run a task concurrently with dropwizard and wiremock(optional)

## Example Usage:

add following in your build.gradle and modify settings accordingly:

apply plugin: 'net.swisstech.dropwizard'
dropwizard() {
    dropwizardConfigFile = 'YOUR_APPLICATION_CONFIG.yaml'
    mainClass = mainClassName
    taskName = "<taskName>"
    wireMockPort = <WIREMOCK_PORT>
    wireMockRoot= "src/test/resources/wiremock"
    urlsToBeMocked = ['https://externalService.com']
    startWireMock = true/false
}
