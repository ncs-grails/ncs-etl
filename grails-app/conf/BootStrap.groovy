class BootStrap {

    def init = { servletContext ->

		def env = System.getenv()
		println "\nBrowse to https://${env['USERNAME']}.healthstudies.umn.edu:8443/ncs-etl/\n"

    }
    def destroy = {
    }
}
