security {

	// see DefaultSecurityConfig.groovy for all settable/overridable properties
	active = true

	useRequestMapDomainClass = false
	useControllerAnnotations = true

	forceHttps = true
	channelConfig.secure = ['/**']

	// Used for UMN Cookie Auth
	useUmnCookie = true
	umn {
		authServer = 'www.umn.edu'
		validationServer = 'x500.umn.edu'
		moduleName = 'WEBCOOKIE'
		requiredvalidationLevel = 30
		insufficientValidationLevelMessage = 4
		validCookieTimeInSeconds = 60 * 60
	}

	useUmnadRoles = true
	umnad {
		ldapUri = 'ldap://localhost'
		ldapUserDn = 'enhs-hs-svc-grails@ad.umn.edu'
		ldapUserPw = 'aeb8moshiCaiquei'
		ldapPeopleBaseDn = 'OU=People,DC=ad,DC=umn,DC=edu'
		ldapGroupsBaseDn = 'ou=Groups,ou=ENHS,ou=SPH,ou=Medical,ou=TC,ou=Units,dc=ad,dc=umn,dc=edu'
		ldapMemberAttribute = 'member'
		rolePrefix = 'EnHS-'
		roleSuffix = ''
	}
}
