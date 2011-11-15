package edu.umn.ncs.staging

class ContactImportLink {

	Long personId
	Long primaryPhoneId
	Long secondaryPhoneId
	Long homePhoneId
	Long workPhoneId
	Long cellPhoneId
	Long emailAddressId
	Long addressId
	Long dwellingUnitId
	Long householdId
	Long appointmentId
	Long trackedItemId
	String norcSuId

	String toString() { "ContactImportLink for ${contactImport}" }

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
		householdId(nullable:true)
		appointmentId(nullable:true)
		trackedItemId(nullable:true)
		norcSuId(nullable:true)
	}
}
