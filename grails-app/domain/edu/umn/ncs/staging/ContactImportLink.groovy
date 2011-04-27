package edu.umn.ncs.staging

class ContactImportLink {
	
	Integer personId
	Integer primaryPhoneId
	Integer secondaryPhoneId
	Integer homePhoneId
	Integer workPhoneId
	Integer cellPhoneId
	Integer emailAddressId
	Integer addressId
	Integer dwellingUnitId
	Integer appointmentId
	Integer trackedItemId
	String norcSuId
	
	static belongsTo = [contactImport : ContactImport ]

	    static constraints = {
		personId(nullable:true)
		addressId(nullable:true)
		primaryPhoneId(nullable:true)
		secondaryPhoneId(nullable:true)
		homePhoneId(nullable:true)
		workPhoneId(nullable:true)
		cellPhoneId(nullable:true)
		emailAddressId(nullable:true)
		dwellingUnitId(nullable:true)
		appointmentId(nullable:true)
		trackedItemId(nullable:true)
		norcSuId(nullable:true)
    }
}
