import org.apache.commons.dbcp.BasicDataSource

// This file fixes the MySQL database timeout issue
// where the connection drops after 8 hours and
// the grails app spits out a stack trace dump
beans = {
	dataSource(BasicDataSource) {
		minEvictableIdleTimeMillis=1800000
		timeBetweenEvictionRunsMillis=1800000
		numTestsPerEvictionRun=3

		testOnBorrow=true
		testWhileIdle=true
		testOnReturn=true
		validationQuery="SELECT 1"
	}
}

