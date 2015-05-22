README
=====

Dropwizard plugin to run a gradle task after dropwizard is started, optionally using wiremock to substitute external services, and shutdown dropwizard afterwards

## Example Usage:

add following in your build.gradle and modify settings accordingly:

at the top before everything

```
buildscript {
    dependencies {
        classpath 'net.swisstech:gradle-dropwizard:1.1.12'
        classpath 'org.yaml:snakeyaml:1.14'
        classpath 'net.swisstech:swissarmyknife:1.1.6'
        classpath 'com.github.tomakehurst:wiremock:1.53'
    }
}
```

```
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
```