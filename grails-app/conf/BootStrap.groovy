class BootStrap {

    def init = { servletContext ->

        // add 'capitalize()' function to Strings
        String.metaClass.capitalize = {->
            return delegate.tokenize().collect{ word ->
                word.substring(0,1).toUpperCase() + word.substring(1, word.size())
            }.join(' ')
        }
	
		def env = System.getenv()
		println "\nBrowse to https://${env['USERNAME']}.healthstudies.umn.edu:8443/ncs-etl/\n"

    }
    def destroy = {
    }
}
