grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
//grails.project.war.file = "target/${appName}-${appVersion}.war"
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()
        grailsRepo "http://svn.cccs.umn.edu/ncs-grails-plugins"

        // uncomment the below to enable remote dependency resolution
        // from public Maven repositories
        //mavenLocal()
        //mavenCentral()
        mavenRepo "http://artifact.ncs.umn.edu/plugins-release"
        //mavenRepo "http://artifact.ncs.umn.edu/plugins-snapshot"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.

		// Exclude dependencies to resolve conflicts with pdf and renderer plugin
		// compile("org.xhtmlrenderer:core-renderer:R8") {
		// 	excludes 'xml-apis', 'xmlParserAPIs'
		// }

        runtime 'mysql:mysql-connector-java:5.1.18'
		build 'org.codehaus.gpars:gpars:0.11'
    }
    plugins {
		compile ":hibernate:$grailsVersion"
		compile ":tomcat:$grailsVersion"

		compile ":spring-security-core:1.2.7.3"
		compile ":spring-security-ldap:1.0.6"
		compile ":spring-security-shibboleth-native-sp:1.0.3"

		compile ":address-lookup-zpfour:0.1.2"
		compile ":audit-logging:0.5.4"
		compile ":jquery:1.7.1"
		compile ":ncs-norc-link:0.4"
		compile ":ncs-people:0.9"
		compile ":ncs-recruitment:1.0"
		compile ":ncs-tracking:3.2.5"
		compile ":ncs-web-template:0.2"
		compile ":quartz:0.4.2"

		provided ":spring-security-mock:1.0.1"

		test ":code-coverage:1.2.5"
		test ":codenarc:0.16.1"
	}
}

codenarc.reports = {
	JenkinsXmlReport('xml') {
		outputFile = 'target/test-reports/CodeNarcReport.xml'
		title = 'CodeNarc Report for NCS ETL Process'
	}
	JenkinsHtmlReport('html') {
		outputFile = 'CodeNarcReport.html'
		title = 'CodeNarc Report for NCS ETL Process'
	}
}
codenarc.propertiesFile = 'grails-app/conf/codenarc.properties'
