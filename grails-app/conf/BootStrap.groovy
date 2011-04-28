import edu.umn.ncs.staging.ContactImport
import edu.umn.ncs.staging.ContactImportZp4

class BootStrap {

    def init = { servletContext ->

        // add 'capitalize()' function to Strings
        String.metaClass.capitalize = {->
            return delegate.tokenize().collect{ word ->
                word.substring(0,1).toUpperCase() + word.substring(1, word.size())
            }.join(' ')
        }
		
		// add cleanUp to StreetAddress
		// this depends on the address-lookup-zpfour function
		ContactImport.metaClass.standardize = {->
			ContactImportZp4 contactImportZp4Instance = null
			if (delegate.address1) {
				Integer z = 0
				// initialize the return variable
				contactImportZp4Instance = new ContactImportZp4()
				if (delegate.zipCode) {
					z = Integer.parseInt(delegate.zipCode)
	        	}
				def a1 = new com.semaphorecorp.zp4.StreetAddress(address: delegate.address1,
						city: delegate.city,
						state: delegate.state,
						zipCode: z )
				def za = a1.lookup()
				if (za.certified == 'C') {
					def a2 = za.toStreetAddress()
					
					contactImportZp4Instance.address1 = a2.address
					contactImportZp4Instance.city = a2.city
					contactImportZp4Instance.state = a2.state
					contactImportZp4Instance.zipcode = "${a2.zipCode}"
					contactImportZp4Instance.zip4 = "${a2.zip4}"
					contactImportZp4Instance.updated = true
				} else {
					contactImportZp4Instance.updated = false
				}
			}
			return contactImportZp4Instance
		}
		
		environments {
			development {
				// this makes development a tad bit easier.
				def env = System.getenv()
				println "\nBrowse to https://${env['USERNAME']}.healthstudies.umn.edu:8443/ncs-etl/\n"
			}
		}

    }
    def destroy = {
    }
}
