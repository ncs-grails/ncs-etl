package edu.umn.ncs
import groovyx.gpars.GParsPool
import groovy.sql.Sql
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import edu.umn.ncs.staging.ContactImport
import edu.umn.ncs.staging.ContactImportLink
import edu.umn.ncs.staging.ContactImportZp4
import com.semaphorecorp.zp4.Zp4Address

class ImportContactService {

    static transactional = true
	
	def username = "ajz"	

	def us = Country.findByAbbreviation("us")

	private def makePhoneNumber(phoneNumber) {
		def phoneInstanceId = null
		
		PhoneNumber.withTransaction{
			def phone = phoneNumber.replaceAll('[a-zA-Z-.]', '')
			if (phone[0] == '1' && phone.length() == 11) {
				phone = phone - '1'
			}

			def phoneInstance = PhoneNumber.findByPhoneNumber(phone)
			if (! phoneInstance) {
				phoneInstance = new PhoneNumber(phoneNumber: phone)
				phoneInstance.userCreated = username
				phoneInstance.appCreated = 'ncs_etl'
				
				if ( ! phoneInstance.save(flush:true) ) {
					println "failed to save phone number: ${phone}"
				}
			}
			phoneInstanceId = phoneInstance?.id
		}
		return phoneInstanceId
	}
	
	private def makeEmailAddress(contactImportInstance) {
		def emailAddress = contactImportInstance.emailAddress
		def emailInstanceId = null
		
		EmailAddress.withTransaction{

			def emailInstance = EmailAddress.findByEmailAddress(emailAddress)
			if (! emailInstance) {
				emailInstance = new EmailAddress(emailAddress: emailAddress)
				emailInstance.userCreated = username
				emailInstance.appCreated = 'ncs_etl'
				
				if ( ! emailInstance.save(flush:true) ) {
					println "failed to save email : ${emailAddress}"
				}
			}
			emailInstanceId = emailInstance?.id
		}
		return emailInstanceId
	}
	
	private def makeStreetAddress(addressInstance) {
		def streetAddressId = null
		
		def address = addressInstance.address1
		if (addressInstance.class == "class edu.umn.ncs.staging.ContactImport") {
			if (addressInstance?.addressUnit) {
				address += " # ${addressInstance.addressUnit}"
			}
		}
		def zipCode = null
		try {
			zipCode = Integer.parseInt(addressInstance?.zipcode)
		} catch (Exception ex) {
			println "Invalid Zipcode: ${addressInstance?.zipcode}"
		}

		StreetAddress.withTransaction{
			
			def streetAddressInstanceList = StreetAddress.createCriteria().list{
				and {
					eq("address", address)
					eq("city", addressInstance.city)
					eq("state", addressInstance.state)
					eq("zipCode", zipCode)
					country{
						idEq(us.id)
					}
				}
				cache(false)
			}
			
			streetAddressInstanceList.each { streetAddressInstance ->
				println "Found Address: ${streetAddressInstance.address}"
				streetAddressId = streetAddressInstance.id
			}
			
			// Try again for oddly named apartments
			if (! streetAddressId ) {
				address = address.replace(' Unit ', ' # ')
				streetAddressInstanceList = StreetAddress.createCriteria().list{
					and {
						ilike("address", address.replace('#', '%'))
						eq("city", addressInstance.city)
						eq("state", addressInstance.state)
						eq("zipCode", zipCode)
						country{
							idEq(us.id)
						}
					}
					cache(false)
				}
				streetAddressInstanceList.each { streetAddressInstance ->
					println "Found Address: ${streetAddressInstance.address}"
					streetAddressId = streetAddressInstance.id
				}
			}
			
			
			if (! streetAddressId) {
				println "Could not find StreetAddress for ${addressInstance.class}: ${addressInstance.id}"
				println "\t${address}"
				println "\t${addressInstance.city}, ${addressInstance.state} ${zipCode}"
			}
			
		}
		
		return streetAddressId
	}
	
	private def makeDwellingUnit(streetAddressId) {
		def dwellingUnitId = null
		
		def streetAddress = StreetAddress.read(streetAddressId)
		if (streetAddressId) {
			DwellingUnit.withTransaction{
	
				def dwellingUnitInstance = DwellingUnit.findByAddress(streetAddress)
				if (! dwellingUnitInstance) {
					dwellingUnitInstance = new EmailAddress(address: streetAddress)
					dwellingUnitInstance.userCreated = username
					dwellingUnitInstance.appCreated = 'ncs_etl'
					
					if ( ! dwellingUnitInstance.save(flush:true) ) {
						println "failed to save dwelling unit : ${streetAddress}"
					}
				}
				dwellingUnitId = dwellingUnitInstance?.id
			}
		}
		return dwellingUnitId
	}

	def makePerson(contactImportInstance, contactImportLinkInstance) {
		
		def personInstance = null
		def personInstanceId = null
		
		Person.withTransaction{
		
			def norcSuId = null
			
			def male = Gender.read(1)
			def female = Gender.read(2)
			
			def gender = null
			if (contactImportInstance.gender =~ /^[Mm1]/) {
				gender = male
			} else if (contactImportInstance.gender =~  /^[Ff2]/) {
				gender = female
			}
			
			def dob = contactImportInstance.birthDate
			
			def title = contactImportInstance.title?.toLowerCase()?.capitalize()
			def firstName = contactImportInstance.firstName?.toLowerCase()?.capitalize()
			def middleName = contactImportInstance.middleName?.toLowerCase()?.capitalize()
			def lastName = contactImportInstance.lastName?.toLowerCase()?.capitalize()
			def suffix = contactImportInstance.suffix?.toLowerCase()?.capitalize()
			
			println "Looking for Person ${lastName}, ${firstName}"
			
			// search for a person by SUID
			if (contactImportLinkInstance.norcSuId) {
				// Look them up by NORC ID
				norcSuId = contactImportLinkInstance.norcSuId
				def norcPersonLink = PersonLink.findByNorcSuId(norcSuId)
				if (norcPersonLink) {
					contactImportLinkInstance.norcSuId = norcPersonLink.norcSuId
					if ( ! contactImportLinkInstance.personId ) {
						contactImportLinkInstance.personId = norcPersonLink.person.id
					}
				}
				
				// Household Match
				def personInstanceList = PersonLink.createCriteria().list {
					and {
						person {
							and {
								eq("firstName", firstName)
								eq("middleName", middleName)
								eq("lastName", lastName)
							}
						}
						ilike("norcSuId", "${norcSuId[0..7]}%")
					}
				}
				personInstanceList.each{
					if ( ! personInstance) { personInstance = it.person }
				}
			}
			
			// name, dob, gender match (this can not stay..)
			if (! personInstance){
				def personInstanceList = Person.createCriteria().list {
					and {
						eq("firstName", firstName)
						eq("lastName", lastName)
						eq("gender", gender)
					}
				}
				personInstanceList.each{
					if ( ! personInstance) { personInstance = it }
				}
			}
			
			// name, phone number match
			if (! personInstance ){
				def phoneIdList = []
				
				if (contactImportLinkInstance.primaryPhoneId) { phoneIdList.add(contactImportLinkInstance.primaryPhoneId) }
				if (contactImportLinkInstance.secondaryPhoneId) { phoneIdList.add(contactImportLinkInstance.secondaryPhoneId) }
				if (contactImportLinkInstance.homePhoneId) { phoneIdList.add(contactImportLinkInstance.homePhoneId) }
				if (contactImportLinkInstance.workPhoneId) { phoneIdList.add(contactImportLinkInstance.workPhoneId) }
				if (contactImportLinkInstance.cellPhoneId) { phoneIdList.add(contactImportLinkInstance.cellPhoneId) }
			
				if (phoneIdList) {
					def personInstanceList = Person.createCriteria().list {
						and {
							eq("firstName", firstName)
							eq("lastName", lastName)
						}
						phoneNumbers {
							phoneNumber {
								'in'("id", phoneIdList)
							}
						}
					}
					personInstanceList.each{
						if ( ! personInstance) { personInstance = it }
					}
				}
			}
			
			// name, address match
			if (! personInstance && contactImportLinkInstance.addressId){
				def personInstanceList = Person.createCriteria().list {
					and {
						eq("firstName", firstName)
						eq("lastName", lastName)
					}
					streetAddresses {
						streetAddress {
							idEq(contactImportLinkInstance.addressId)
						}
					}
				}
				personInstanceList.each{
					if ( ! personInstance) { personInstance = it }
				}
			}
			
			if (! personInstance ){
				
				personInstance = new Person(title: title,
					firstName: firstName,
					middleName: middleName,
					lastName: lastName,
					suffix: suffix,
					gender: gender,
					birthDate: dob, 
					appCreated: 'ncs-etl',
					isRecruitable: true)
				
				println "making person: ${personInstance.fullName}; ${personInstance.gender}"
				if (personInstance.save(flush:true)) {
					// If there's a NORC SU_ID, and it doesn't end in 00...
					if ( norcSuId && ! (norcSuId =~ /00$/) ) {
						def personLink = new PersonLink(norcSuId: norcSuId, person: personInstance).save(flush:true)
					}
				} else {
					personInstance.errors.each{
						println "${it}"
					}
				}
				
			} else {
				println "found person!"
			}
		}
		
		if (personInstance?.id) { personInstanceId = personInstance.id }
		return personInstanceId
	}
	
	// makePersonPhone
	def makePersonPhone(personInstance, phoneInstance, phoneType, source, sourceDate) {
		if ( personInstance && phoneInstance ) {
			PersonPhone.withTransaction{
				
				def personPhoneInstance = PersonPhone.findByPersonAndPhoneNumber(personInstance, phoneInstance)
				
				if ( ! personPhoneInstance ) {
					personPhoneInstance = new PersonPhone(person:personInstance,
						phoneNumber:phoneInstance, phoneType: phoneType,
						appCreated: 'ncs-etl', source: source,
						infoDate: sourceDate)
					if ( ! personPhoneInstance.save(flush:true) ) {
						println "Failed to Created Person <-> Phone Link for ${personInstance.fullName}"
						personPhoneInstance.errors.each{ println "\t${it}" }
					}
				}
			}
		} else { println "makePersonPhone: Something is Missing!" }
	}
	
	// makePersonEmail
	def makePersonEmail(personInstance, emailInstance, source, sourceDate) {
		if ( personInstance && emailInstance ) {
			PersonEmail.withTransaction{
				
				def personEmailInstance = PersonEmail.findByPersonAndEmailAddress(personInstance, emailInstance)
				
				if ( ! personEmailInstance ) {
					def personalEmail = EmailType.read(1)
					
					personEmailInstance = new PersonEmail(person:personInstance,
						emailAddress:emailInstance, emailType: personalEmail,
						appCreated: 'ncs-etl', source: source,
						infoDate: sourceDate)
					if ( ! personEmailInstance.save(flush:true) ) {
						println "Failed to Create Person <-> Email Link for ${personInstance.fullName}"
						personEmailInstance.errors.each{ println "\t${it}" }
					}
				}
			}
		} else { println "makePersonEmail: Something is Missing!" }
	}

	// makePersonAddress
	def makePersonAddress(personInstance, addressInstance, source, sourceDate) {
		if ( personInstance && addressInstance ) {
			PersonAddress.withTransaction{
				
				def personAddressInstance = PersonAddress.findByPersonAndStreetAddress(personInstance, addressInstance)
				
				if ( ! personAddressInstance ) {
					def homeAddress = AddressType.read(1)
					
					personAddressInstance = new PersonAddress(person:personInstance,
						streetAddress:addressInstance, addressType: homeAddress,
						appCreated: 'ncs-etl', source: source,
						infoDate: sourceDate)
					if ( ! personAddressInstance.save(flush:true) ) {
						println "Failed to Create Person <-> Address Link for ${personInstance.fullName}"
						personAddressInstance.errors.each{ println "\t${it}" }
					}
				}
			}
		} else { println "makePersonAddress: Something is Missing!" }
	}

    def processContact() {
		
		def now = new Date()

		def sourceNorc = Source.findByName("Name")
		
		if ( ! sourceNorc )  {
			sourceNorc = new Source(name:'NORC', 
				appCreated:'ncs-etl', selectable:false).save(flush:true)
		}

		GParsPool.withPool {
			
			// Create Links if Needed
			
			ContactImport.list().eachParallel{ contactImportInstance ->
				
				ContactImportLink.withTransaction{
					def contactImportLinkInstance = ContactImportLink.findByContactImport(contactImportInstance)
					if ( ! contactImportLinkInstance ) {
						contactImportLinkInstance = new ContactImportLink( contactImport: contactImportInstance)
					}
					
					if (contactImportLinkInstance) {
						
						// Primary Phone Number...
						if (contactImportInstance.primaryPhone && 
								! contactImportLinkInstance.primaryPhoneId) {
							contactImportLinkInstance.primaryPhoneId = makePhoneNumber(contactImportInstance.primaryPhone)
						}
						// Secondary Phone Number...
						if (contactImportInstance.secondaryPhone && 
								! contactImportLinkInstance.secondaryPhoneId) {
							contactImportLinkInstance.secondaryPhoneId = makePhoneNumber(contactImportInstance.secondaryPhone)
						}
						// Primary Phone Number...
						if (contactImportInstance.homePhone && 
								! contactImportLinkInstance.homePhoneId) {
							contactImportLinkInstance.homePhoneId = makePhoneNumber(contactImportInstance.homePhone)
						}
						// Primary Phone Number...
						if (contactImportInstance.workPhone && 
								! contactImportLinkInstance.workPhoneId) {
							contactImportLinkInstance.workPhoneId = makePhoneNumber(contactImportInstance.workPhone)
						}
						// Primary Phone Number...
						if (contactImportInstance.cellPhone && 
								! contactImportLinkInstance.cellPhoneId) {
							contactImportLinkInstance.cellPhoneId = makePhoneNumber(contactImportInstance.cellPhone)
						}
						
						// Email Address...
						if (contactImportInstance.emailAddress &&
									! contactImportLinkInstance.emailAddressId) {
								contactImportLinkInstance.emailAddressId = makeEmailAddress(contactImportInstance)
						}
									
						// Street Address...
						if (contactImportInstance.address &&
									! contactImportLinkInstance.addressId) {
									
							def contactImportZp4 = ContactImportZp4.findByContactImport(contactImportInstance)
							if (contactImportZp4?.updated) {
								// We have a standardized address to work with
								contactImportLinkInstance.addressId = makeStreetAddress(contactImportZp4)
							} else {
								// This is a "dirty" address
								contactImportLinkInstance.addressId = makeStreetAddress(contactImportInstance)
							}
						}
						
						// NORC SU_ID...
						if (contactImportInstance.sourceKeyId && ! contactImportLinkInstance.norcSuId) {
							def norcSuId = null
							if (contactImportInstance.sourceName == "hh_batch") {
								norcSuId = "00${contactImportInstance.sourceKeyId}"
							}
							def norcDwellingLink = null
							
							if (norcSuId.length() == 10) {
								if (norcSuId =~ /00$/) {
									norcDwellingLink = DwellingUnitLink.findByNorcSuId(norcSuId)
								} else {
									norcDwellingLink = DwellingUnitLink.findByNorcSuId("${norcSuId[0..7]}00")
								}
								// Lookup dwelling unit info...
								norcDwellingLink = DwellingUnitLink.findByNorcSuId(norcSuId)
								if (norcDwellingLink) {
									contactImportLinkInstance.norcSuId = norcDwellingLink.norcSuId
									if ( ! contactImportLinkInstance.dwellingUnitId ) {
										contactImportLinkInstance.dwellingUnitId = norcDwellingLink.dwellingUnit.id
									}
								} else {
									println "Unable to find NORC SU_ID for DwellingUnit!"
								}
							}
						}

						// Dwelling Unit...
						if (contactImportLinkInstance.addressId && ! contactImportLinkInstance.dwellingUnitId) {
							contactImportLinkInstance.dwellingUnitId = makeDwellingUnit(contactImportLinkInstance.addressId)
						}

						// Person...
						if ( (contactImportInstance.firstName || contactImportInstance.lastName) && ! contactImportLinkInstance.personId) {
							
							contactImportLinkInstance.personId = makePerson(contactImportInstance, contactImportLinkInstance)
						}
						
						// Person Contact - Phones

						/*
						 * primaryPhone
						 * secondaryPhone
						 * homePhone
						 * workPhone
						 * cellPhone			
						 */
						
						/* Load the phone types */
						def primaryPhoneType = PhoneType.read(2)
						def secondaryPhoneType = PhoneType.read(3)
						def homePhoneType = PhoneType.read(4)
						def workPhoneType = PhoneType.read(5)
						def cellPhoneType = PhoneType.read(1)
						
						def personInstance = Person.read(contactImportLinkInstance.personId)
						
						// primaryPhone
						if (personInstance && contactImportLinkInstance.primaryPhoneId) {
							makePersonPhone(personInstance, 
								PhoneNumber.read(contactImportLinkInstance.primaryPhoneId), 
								primaryPhoneType, sourceNorc, contactImportInstance.sourceDate ?: now)
						}
						// secondaryPhone
						if (personInstance && contactImportLinkInstance.secondaryPhoneId) {
							makePersonPhone(personInstance, 
								PhoneNumber.read(contactImportLinkInstance.secondaryPhoneId), 
								secondaryPhoneType, sourceNorc, contactImportInstance.sourceDate ?: now)
						}
						// homePhone
						if (personInstance && contactImportLinkInstance.homePhoneId) {
							makePersonPhone(personInstance, 
								PhoneNumber.read(contactImportLinkInstance.homePhoneId), 
								homePhoneType, sourceNorc, contactImportInstance.sourceDate ?: now)
						}
						// workPhone
						if (personInstance && contactImportLinkInstance.workPhoneId) {
							makePersonPhone(personInstance, 
								PhoneNumber.read(contactImportLinkInstance.workPhoneId), 
								workPhoneType, sourceNorc, contactImportInstance.sourceDate ?: now)
						}
						// cellPhone
						if (personInstance && contactImportLinkInstance.cellPhoneId) {
							makePersonPhone(personInstance,
								PhoneNumber.read(contactImportLinkInstance.cellPhoneId),
								cellPhoneType, sourceNorc, contactImportInstance.sourceDate ?: now)
						}
						
						// Person Contact - Email
						if (personInstance && contactImportLinkInstance.emailAddressId) {
							makePersonEmail(personInstance,
								EmailAddress.read(contactImportLinkInstance.emailAddressId),
								sourceNorc, contactImportInstance.sourceDate ?: now)
						}

						// Person Contact - Address
						if (personInstance && contactImportLinkInstance.addressId) {
							makePersonAddress(personInstance,
								StreetAddress.read(contactImportLinkInstance.addressId),
								sourceNorc, contactImportInstance.sourceDate ?: now)
						}

						// TODO: Appointments
					}
					
					if ( ! contactImportLinkInstance.save(flush:true)) {
						println "Could not create ContactImportLink."
					}
				}
			}
		}
		// Pull Records, Create Phones,
		println "Finished Importing."
    }
	
	def zp4StandardizeImportData() {
		GParsPool.withPool {
			
			// Create Links if Needed
			
			ContactImport.list().eachParallel{ contactImportInstance ->
				// standardize all addresses that are not
				ContactImportZp4.withTransaction{
					def contactImportZp4Instance = ContactImportZp4.findByContactImport(contactImportInstance)
					if ( ! contactImportZp4Instance ) {
						
						// call the standardize metaClass (configured in the BootStrap)
						contactImportZp4Instance = contactImportInstance.standardize()
						
						// if it was updated, save it
						if (contactImportZp4Instance) {
							contactImportZp4Instance.contactImport = contactImportInstance
							if (contactImportZp4Instance.updated) {
								if ( ! contactImportZp4Instance.save(flush:true) ) {
									println "failed to save ${contactImportZp4Instance} for ${contactImportInstance}."
								}
							}
						}
					}
				}
			}
		}
		println "done standardizing contact import addresses."
	}
}
