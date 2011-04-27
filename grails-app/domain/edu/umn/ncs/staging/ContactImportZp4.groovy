package edu.umn.ncs.staging

class ContactImportZp4 {
	
	String address1
	String address2
	String addressLeftOvers
	String city
	String cityPreferred
	String state
	String zipcode
	String zip4
	String errMsg
	String errDetail
	Date updateDate
	Boolean updated
	
	static belongsTo = [contactImport : ContactImport ]
	
    static constraints = {
		contactImportId(nullable:true)
		address1(maxSize: 40, nullable:true)
		address2(maxSize: 40, nullable:true)
		addressLeftOvers(nullable:true)
		city(maxSize: 30, nullable:true)
		cityPreferred(maxSize: 30, nullable:true)
		state(maxSize: 2, nullable:true)
		zipcode(maxSize: 5, nullable:true)
		zip4(maxSize: 4, nullable:true)
		errMsg(nullable:true)
		errDetail(nullable:true)
		updateDate(nullable:true)
		updated()
    }
}
