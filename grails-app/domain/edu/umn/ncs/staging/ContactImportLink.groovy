package edu.umn.ncs.staging

/** Linking IDs to prep the import process */
class ContactImportLink {

	/** Person.id */
	Long personId
	/** primary Phone.id */
	Long primaryPhoneId
	/** secondary Phone.id */
	Long secondaryPhoneId
	/** home Phone.id */
	Long homePhoneId
	/** work Phone.id */
	Long workPhoneId
	/** cell Phone.id */
	Long cellPhoneId
	/** EmailAddress.id */
	Long emailAddressId
	/** StreetAddress.id */
	Long addressId
	/** DwellingUnit.id */
	Long dwellingUnitId
	/** Household.id */
	Long householdId
	/** Appointment.id */
	Long appointmentId
	/** Batch.id */
	Long batchId
	/** TrackedItem.id */
	Long trackedItemId
	/** EventOfInterest.id */
	Long eventOfInterestId
	/** Study.id */
	Long studyId
	/** Subject.id */
	Long subjectId

	/** NORC SU_ID */
	String norcSuId
	/** NORC SU_ID (for dwelling unit) */
	String norcDwellingSuId

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
		batchId(nullable:true)
		trackedItemId(nullable:true)
		eventOfInterestId(nullable:true)
		appointmentId(nullable:true)
		studyId(nullable:true)
		subjectId(nullable:true)
		norcSuId(nullable:true, size:8..8)
		norcDwellingSuId(nullable:true, size:10..10)
	}
}
