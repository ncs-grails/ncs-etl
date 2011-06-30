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
	
	def dataSource
	def authenticateService
	private def username = authenticateService?.principal()?.getUsername()
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
		def zip4 = null
		try {
			zipCode = Integer.parseInt(addressInstance?.zipcode)
		} catch (Exception ex) {
			println "Invalid Zipcode: ${addressInstance?.zipcode}"
		}
		try {
			zip4 = Integer.parseInt(addressInstance?.zip4)
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
				println "Found Address: ${streetAddressInstance.address}, ID: ${streetAddressInstance.id}"
				streetAddressId = streetAddressInstance.id
			}

			if (zip4) {
				streetAddressInstanceList = StreetAddress.createCriteria().list{
					and {
						eq("address", address)
						eq("zipCode", zipCode)
						eq("zip4", zip4)
						country{
							idEq(us.id)
						}
					}
					cache(false)
				}
				
				streetAddressInstanceList.each { streetAddressInstance ->
					println "Found Address: ${streetAddressInstance.address}, ID: ${streetAddressInstance.id}"
					streetAddressId = streetAddressInstance.id
				}
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
			
			if (streetAddressId && addressInstance.city && addressInstance.class.toString() == "edu.umn.ncs.staging.ContactImportZp4") {
				def streetAddressInstance = StreetAddress.get(streetAddressId)
				streetAddressInstance.city = addressInstance.city
				streetAddressInstance.save(flush:true)
			}
			
			
			if (! streetAddressId) {
				
				def className = addressInstance.class.toString()

								// We make one?
				println "Could not find StreetAddress for ${className}: ${addressInstance.id}"
				println "\t${address}"
				println "\t${addressInstance.city}, ${addressInstance.state} ${zipCode}-${zip4}"
				
				
				if (className == "class edu.umn.ncs.staging.ContactImportZp4") {
					println "Creating new address..."
					def streetAddressInstance = new StreetAddress(address:address,
						address2:addressInstance.address2, city:addressInstance.city, 
						state: addressInstance.state, zipcode: zipCode, zip4: zip4,
						country: us, standardized: true, userCreated: 'norc',
						appCreated: 'ncs-etl')

					if ( ! streetAddressInstance.save(flush:true) ) {
						println "Failed to save new Address!"
					} else {
						streetAddressId = streetAddressInstance.id
					}
				}
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
					dwellingUnitInstance = new DwellingUnit(address: streetAddress)
					dwellingUnitInstance.userCreated = username
					dwellingUnitInstance.appCreated = 'ncs_etl'
					
					if ( ! dwellingUnitInstance.save(flush:true) ) {
						println "failed to save dwelling unit : ${streetAddress}"
						dwellingUnitInstance.errors.each{
							println "${it}"
						}
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
		
		def dateFormat = "yyyy-MM-dd"
		
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
			
			Date dob = null
			if (contactImportInstance.birthDate && contactImportInstance.birthDate != '--') {
				try {
					dob = Date.parse(dateFormat, contactImportInstance.birthDate)
				} catch (Exception ex) {
					println "unable to parse birthdate: ${contactImportInstance.birthDate}"
				}
			}
			
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
	
			if (personInstance && ( norcSuId =~ /0[1-9]$/ )) {
				println "NORC SU_ID: ${norcSuId}"
				def personLinkInstance = PersonLink.findByNorcSuId(norcSuId)
				if (! personLinkInstance) {
					personLinkInstance = new PersonLink(norcSuId: norcSuId, person:personInstance)
					//personLinkInstance.save(flush:true)
				} else if ( ! personLinkInstance.norcSuId) {
					personLinkInstance.norcSuId = norcSuId
					//personLinkInstance.save(flush:true)
				}
			}
		}
		
		if (personInstance?.id) {
			personInstanceId = personInstance.id
		}
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
	
	
	def makeTrackedItem(personInstance, dwellingUnitInstance, instrumentInstance, instrumentDate) {
		
		// TODO: pull this from the contact_import table

		// Phone (default)
		def instrumentFormatInstance = InstrumentFormat.read(3)
		// Incoming
		def batchDirectionInstance = BatchDirection.read(2)
		// Initial
		def isInitialInstance = IsInitial.read(1)
		
		Date lowDate = instrumentDate - 1
		Date highDate = instrumentDate + 1
		TrackedItem trackedItemInstance = null
		Batch batchInstance = null
		
		TrackedItem.withTransaction {
			
			def trackedItemInstanceList = TrackedItem.createCriteria().list{
				and {
					batch {
						and {
							between("instrumentDate", lowDate, highDate)
							instruments {
								and {
									instrument {
										idEq(instrumentInstance.id)
									}
									isInitial {
										idEq(isInitialInstance.id)
									}
								}
							}
							direction {
								idEq(batchDirectionInstance.id)
							}
							format {
								idEq(instrumentFormatInstance.id)
							}
						}
					}
					or {
						dwellingUnit {
							eq("id", dwellingUnitInstance?.id)
						}
						person {
							eq("id", personInstance?.id)
						}
					}
				}
				maxResults(1)
			}
			// any matches?  first match gets returned
			trackedItemInstanceList.each {
				if ( ! trackedItemInstance ) {
					trackedItemInstance = it
					batchInstance = it.batch
				}
				// println "Found Tracked Item: ${trackedItemInstance.id}"
			}
	
			def batchInstanceList = Batch.createCriteria().list{
				and {
					between("instrumentDate", lowDate, highDate)
					instruments {
						instrument {
							idEq(instrumentInstance.id)
						}
					}
				}
				maxResults(1)
			}
			// any batches of this instrument type?
			batchInstanceList.each {
				if (! batchInstance ) { batchInstance = it }
				// println "Found Batch: ${batchInstance.id}"
			}
	
			if (! batchInstance ) {
				// create a batch
				batchInstance = new Batch(instrumentDate:instrumentDate,
					trackingDocumentSent: false, format: instrumentFormatInstance,
					direction: batchDirectionInstance, batchRunByWhat:'ncs-etl',
					batchRunBy: 'norc')
				// add batch instrument
				batchInstance.addToInstruments(instrument: instrumentInstance,
					isInitial: isInitialInstance)

				if ( ! batchInstance.save(flush:true) ) {
					batchInstance.errors.each{
						println "${it}"
					}
				}
			}
			
			if ( ! trackedItemInstance ) {
				// create a batch
				trackedItemInstance = new TrackedItem(person:personInstance, dwellingUnit:dwellingUnitInstance, batch: batchInstance)
				if ( ! trackedItemInstance.save(flush:true) ) {
					println "Failed to create tracked item."
				}
			}
			
			return trackedItemInstance?.id
		}
	}
	
	
	def makeHousehold(personInstance, dwellingUnitInstance) {
		def householdId = null
		def now = new Date()
		
		TrackedItem.withTransaction {
		
			if (personInstance?.id && dwellingUnitInstance?.id) {
				// find by person and dwellingUnit
				def c = Household.createCriteria()
				def householdInstanceList = c.list{
					and {
						people {
							eq('id', personInstance.id)
						}
						dwelling {
							eq('id', dwellingUnitInstance.id)
						}
					}
				}
				
				householdInstanceList.each{ hh ->
					householdId = hh.id
				}
				
				// if not, then new Household
				if ( ! householdId ) {
					
					def householdInstance = Household.findByDwelling(dwellingUnitInstance)
					if ( ! householdInstance ) {
						householdInstance = new Household(dwelling:dwellingUnitInstance)
						householdInstance.appCreated = 'ncs-etl'
					} else {
						householdInstance.userUpdated = 'ncs-etl'
						householdInstance.lastUpdated = now
					}
					
					householdInstance.addToPeople(personInstance)
					
					if ( householdInstance.save(flush:true) ) {
						householdId = householdInstance.id
					} else {
						println "Failed to save household."
					}
				}
			}
		}
		
		return householdId
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
							
							if (norcSuId && norcSuId.length() == 10) {
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

						def dwellingUnitInstance = DwellingUnit.read(contactImportLinkInstance.dwellingUnitId)
						
						// Dwelling Unit...
						if (contactImportLinkInstance.addressId && ! dwellingUnitInstance) {
							contactImportLinkInstance.dwellingUnitId = makeDwellingUnit(contactImportLinkInstance.addressId)
							dwellingUnitInstance = DwellingUnit.read(contactImportLinkInstance.dwellingUnitId)
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

						// Instruments
						if ( ( personInstance || dwellingUnitInstance) && contactImportInstance.instrumentId && ! contactImportLinkInstance.trackedItemId) {
							def instrumentInstance = Instrument.read(contactImportInstance.instrumentId)
							def instrumentDate = contactImportInstance.instrumentDate ?: contactImportInstance.sourceDate ?: now
							
							contactImportLinkInstance.trackedItemId = makeTrackedItem(personInstance,
								dwellingUnitInstance, instrumentInstance, instrumentDate)
						}
						
						// Household
						if (personInstance && dwellingUnitInstance) {
							def householdInstance = Household.read(contactImportLinkInstance.householdId)
							if ( ! householdInstance ) {
								contactImportLinkInstance.householdId = makeHousehold(personInstance, dwellingUnitInstance)
							}
						}

						// TODO: Appointments
					}
					
					if ( ! contactImportLinkInstance.save(flush:true)) {
						println "Could not create ContactImportLink."
					}
				}
			}
		}

				// Person NORC_SUID Link
		def query = """INSERT INTO person_link
				(version, norc_su_id, person_id)
			SELECT 0, ci.source_key_id, cil.person_id
			FROM contact_import ci INNER JOIN
				contact_import_link cil ON ci.id = cil.contact_import_id LEFT OUTER JOIN
				person_link pl ON cil.person_id = pl.person_id
			WHERE (cil.person_id IS NOT NULL)
				AND (ci.source_key_id NOT LIKE '%00')
				AND (pl.id IS NULL);"""
		def sql = new Sql(dataSource) 
		sql.execute(query)

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
