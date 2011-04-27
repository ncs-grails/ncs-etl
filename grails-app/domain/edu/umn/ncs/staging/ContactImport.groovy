package edu.umn.ncs.staging

class ContactImport {
	
	String title
	String firstName
	String middleName
	String lastName
	String suffix
	String gender
	String birthDate
	String sourceKeyId
	String sourceName
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
	String appointmentDatetime
	String emailAddress
	Date instrumentDate
	Integer instrumentId
	
	static transients = [ 'address1' ]
	
	String getAddress1() {
		if (addressUnit) {
			return "${address} Unit ${addressUnit}"
		} else {
			return address
		}
	}
	
    static constraints = {
		title(nullable:true)
		firstName(nullable:true)
		middleName(nullable:true)
		lastName(nullable:true)
		suffix(nullable:true)
		gender(nullable:true)
		birthDate(nullable:true)
		sourceKeyId(nullable:true)
		sourceName(nullable:true)
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
		appointmentDatetime(nullable:true)
		emailAddress(nullable:true)
		instrumentDate(nullable:true)
		instrumentId(nullable:true)
    }
}
