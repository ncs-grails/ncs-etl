import org.grails.plugins.springsecurity.service.AuthenticateService
import edu.umn.auth.UmnCookieAuthenticationProvider

/**
 * Logout Controller (Example).
 */
class LogoutController {

    /**
     * Dependency injection for the authentication service.
     */
    def authenticateService

    /**
     * Dependency injection for UmnCookieAuthenticationProvider.
     */
    def umnCookieAuthenticationProvider

    /**
     * Index action. Redirects to the Spring security logout uri.
     */
    def index = {

        def config = authenticateService.securityConfig.security
        
        if (config.useUmnCookie) {
			def rurl = request.requestURL.toString()
			if (!rurl.contains('.umn.edu')) {
				render 'UMN Cookie Auth does not work without a *.umn.edu address.'
	            return
			} else {
				if (authenticateService.isLoggedIn()) {
					request.getSession()?.invalidate()
					redirect url:umnCookieAuthenticationProvider.logoutUrl(rurl)
				} else {
					redirect uri:'/'
				}
			}
        } else {
            // TODO  put any pre-logout code here
            redirect uri:'/j_spring_security_logout'
        }
    }
}
