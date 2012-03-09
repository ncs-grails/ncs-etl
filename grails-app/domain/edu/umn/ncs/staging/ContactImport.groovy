package edu.umn.ncs.staging

class ContactImport {

	/** Used to link to external person IDs */
	String sourcePersonKey
	Integer sourcePersonId
	String sourceDwellingUnitKey
	Integer sourceDwellingUnitId
	String sourceKeyId
	String sourceName
	Date sourceDate = new Date()

	String title
	String firstName
	String middleName
	String lastName
	String suffix
	String gender
	String birthDate

	String address
	String addressUnit
	String address2
	String city
	String state
	String zipCode
	String zip4
	String county
	String internationalPostalCode
	String countryName

	String primaryPhone
	String secondaryPhone
	String homePhone
	String workPhone
	String cellPhone

	String emailAddress

	String appointmentDateTime
	Integer appointmentTypeId

	Date instrumentDate
	Integer instrumentId
	Integer batchDirectionId
	Integer instrumentTypeId
	Integer resultId

	Integer eventOfInterestStudyId
	Integer eventOfInterestSourceId
	Date eventOfInterestContactDate
	Integer eventOfInterestTypeId
	Integer eventOfInterestCode
	Date eventOfInterestDate
	String eventOfInterestDescription

	String studyName
	String subjectId
	Integer enrollmentTypeId
	Date enrollmentDate

	static transients = [ 'address1', 'zipcode' ]

	String getZipcode() { zipCode }

	String getAddress1() {
		if (addressUnit) {
			return "${address} Unit ${addressUnit}"
		} else {
			return address
		}
	}

	String toString() { "${sourceName}[${sourceKeyId}]" }

	static constraints = {
		title(nullable:true)
		firstName(nullable:true)
		middleName(nullable:true)
		lastName(nullable:true)
		suffix(nullable:true)
		gender(nullable:true)
		birthDate(nullable:true)
		sourceKeyId(nullable:true)
		sourcePersonKey(nullable:true)
		sourceDwellingUnitKey(nullable:true)
		sourceName(nullable:true)
		sourceDate(nullable:true)
		address(nullable:true)
		addressUnit(nullable:true)
		address2(nullable:true)
		city(nullable:true)
		state(nullable:true)
		zipCode(nullable:true)
		zip4(nullable:true)
		county(nullable:true)
		internationalPostalCode(nullable:true)
		countryName(nullable:true)
		primaryPhone(nullable:true)
		secondaryPhone(nullable:true)
		homePhone(nullable:true)
		workPhone(nullable:true)
		cellPhone(nullable:true)
		emailAddress(nullable:true)
		appointmentDateTime(nullable:true)
		appointmentTypeId(nullable:true)
		instrumentDate(nullable:true)
		instrumentId(nullable:true)
		batchDirectionId(nullable:true)
		instrumentTypeId(nullable:true)
		resultId(nullable:true)
		eventOfInterestStudyId(nullable:true)
		eventOfInterestSourceId(nullable:true)
		eventOfInterestContactDate(nullable:true)
		eventOfInterestTypeId(nullable:true)
		eventOfInterestCode(nullable:true)
		eventOfInterestDate(nullable:true)
		eventOfInterestDescription(nullable:true)
		studyName(nullable:true)
		subjectId(nullable:true)
		enrollmentTypeId(nullable:true)
		enrollmentDate(nullable:true)
	}
}
