package edu.umn.ncs
import groovyx.gpars.GParsPool
import groovy.sql.Sql
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import edu.umn.ncs.staging.ContactImport
import edu.umn.ncs.staging.ContactImportLink
import edu.umn.ncs.staging.ContactImportZp4

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
		
		StreetAddress.withTransaction{
			def zipCode = null
			try {
				zipCode = Integer.parseInt(addressInstance?.zipcode)
			} catch (Exception ex) {
				println "Invalid Zipcode: ${addressInstance?.zipcode}"
			}
			
			def c = StreetAddress.createCriteria()
			def streetAddressInstanceList = c.list{
				and {
					eq("address", address)
					eq("address2", addressInstance.address2)
					eq("city", addressInstance.city)
					eq("state", addressInstance.state)
					eq("zipCode", zipCode)
					country{
						idEq(us.id)
					}
				}
			}
			
			streetAddressInstanceList.each { streetAddressInstance ->
				// println "Found Address: ${streetAddressInstance.address}"
				streetAddressId = streetAddressInstance.id
			}
			
			// Try again for oddly named apartments
			if (! streetAddressId ) {
				c = StreetAddress.createCriteria()
				streetAddressInstanceList = c.list{
					and {
						ilike("address", address.replace('#', '%'))
						eq("address2", addressInstance.address2)
						eq("city", addressInstance.city)
						eq("state", addressInstance.state)
						eq("zipCode", zipCode)
						country{
							idEq(us.id)
						}
					}
				}
				streetAddressInstanceList.each { streetAddressInstance ->
					// println "Found Address: ${streetAddressInstance.address}"
					streetAddressId = streetAddressInstance.id
				}
			}
			
			
			if (! streetAddressId) {
				println "Could not find StreetAddress for ${addressInstance.class}: ${addressInstance.id}"
					println "\t${addressInstance.address1}"
				if (addressInstance?.address2) {
					println "\t${addressInstance.address2}"
				}
				println "\t${addressInstance.city}, ${addressInstance.state} ${addressInstance.zipcode}"
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

	def makePerson() {
		return null
	}
	
    def processContact() {

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

						// Dwelling Unit...
						if (contactImportLinkInstance.addressId && ! contactImportLinkInstance.dwellingUnitId) {
							contactImportLinkInstance.dwellingUnitId = makeDwellingUnit(contactImportLinkInstance.addressId)
						}
						
						// NORC SU_ID...
						if (contactImportInstance.sourceKeyId && ! contactImportLinkInstance.norcSuId) {
							def norcSuId = null
							if (contactImportInstance.sourceName == "hh_batch") {
								norcSuId = contactImportInstance.sourceKeyId
							}
							
							if (norcSuId.length() == 8 && norcSuId =~ /00$/) {
								println "Household!"
							}

							contactImportLinkInstance.norcSuId = null
						}
						
						// Person...
						if ( (contactImportInstance.firstName || contactImportInstance.lastName) && ! contactImportLinkInstance.personId) {
							def firstName = contactImportInstance.firstName.toLowerCase().capitalize()
							def lastName = contactImportInstance.lastName.toLowerCase().capitalize()
							println "Looking for Person ${lastName}, ${firstName}"
						}
						// Person Contact - Phones
						// Person Contact - Email
						// Person Contact - Address
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
}
