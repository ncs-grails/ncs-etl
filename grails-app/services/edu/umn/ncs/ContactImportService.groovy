package edu.umn.ncs
import groovyx.gpars.GParsPool
import groovy.sql.Sql
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import edu.umn.ncs.staging.ContactImport
import edu.umn.ncs.staging.ContactImportLink
import edu.umn.ncs.staging.ContactImportZp4

class ContactImportService {
	def debug = true
	
    static transactional = true

	static appCreated = 'ncs-etl'
	
	def dataSource
	def springSecurityService
	private def username = null
	def us = Country.findByAbbreviation("us")

	private def makePhoneNumber(phoneNumber) {
		def phoneInstanceId = null
		
		PhoneNumber.withTransaction{
			def phone = phoneNumber.replaceAll('[a-zA-Z-.]', '').trim()
			if (phone[0] == '1' && phone.length() == 11) {
				phone = phone - '1'
			}

			def phoneInstance = PhoneNumber.findByPhoneNumber(phone)
			if (! phoneInstance) {
				phoneInstance = new PhoneNumber(phoneNumber: phone)
				phoneInstance.userCreated = username
				phoneInstance.appCreated = appCreated
				
				if ( ! phoneInstance.save(flush:true) ) {
					println "failed to save phone number: '${phone}'"
					phoneInstance.errors.each{
						println "\t${it}"
					}
				}
			}
			phoneInstanceId = phoneInstance?.id
		}
		return phoneInstanceId
	}
	
	private def makeEmailAddress(contactImportInstance) {
		def emailAddress = contactImportInstance.emailAddress.toLowerCase().trim()
		def emailInstanceId = null
		
		EmailAddress.withTransaction{

			def emailInstance = EmailAddress.findByEmailAddress(emailAddress)
			if (! emailInstance) {
				emailInstance = new EmailAddress(emailAddress: emailAddress)
				emailInstance.userCreated = username
				emailInstance.appCreated = appCreated
				
				if ( ! emailInstance.validate() ) {
					println "INVALID Email Address: ${emailAddress}"
				} else if ( ! emailInstance.save(flush:true) ) {
					println "failed to save email : '${emailAddress}'"
					emailInstance.errors.each{
						println "\t${it}"
					}
				}
			}
			emailInstanceId = emailInstance?.id
		}
		return emailInstanceId
	}
	
	private def makeStreetAddress(addressInstance, standardized) {
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
			log.warn "Invalid Zipcode: ${addressInstance?.zipcode}"
		}
		try {
			zip4 = Integer.parseInt(addressInstance?.zip4)
		} catch (Exception ex) {
			log.warn "Invalid Zip4: ${addressInstance?.zip4}"
		}

		StreetAddress.withTransaction{
			
			def streetAddressInstanceList = []

			if ( ! streetAddressId && ! zip4) {
				streetAddressInstanceList = StreetAddress.createCriteria().list{
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
					log.trace "Found Address: ${streetAddressInstance.address}, ID: ${streetAddressInstance.id}"
					streetAddressId = streetAddressInstance.id
				}
			}

			if ( ! streetAddressId && zip4) {
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
					log.trace "Found Address: ${streetAddressInstance.address}, ID: ${streetAddressInstance.id}"
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
					log.trace "Found Address: ${streetAddressInstance.address}"
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
				log.info "Could not find StreetAddress for ${className}: ${addressInstance.id}"
				log.info "\t${address}"
				log.info "\t${addressInstance.city}, ${addressInstance.state} ${zipCode}-${zip4}"
				
				
				if (className == "class edu.umn.ncs.staging.ContactImportZp4") {
					log.info "Creating new address from ZP4..."
					def streetAddressInstance = new StreetAddress(address:address,
						address2:addressInstance.address2, city:addressInstance.city, 
						state: addressInstance.state, zipCode: zipCode, zip4: zip4,
						country: us, standardized: standardized, userCreated: 'norc',
						appCreated: appCreated)

					if ( ! streetAddressInstance.save(flush:true) ) {
						log.warn "Failed to save new Address!"
						throw FailedToSaveStreetAddressException
					} else {
						streetAddressId = streetAddressInstance.id
					}
				} else {
					log.warn "Not creating a new StreetAddress because it wasn't cleaned by ZP4"
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
					dwellingUnitInstance.appCreated = appCreated
					
					if ( ! dwellingUnitInstance.save(flush:true) ) {
						println "failed to save dwelling unit : ${streetAddress}"
						dwellingUnitInstance.errors.each{
							println "${it}"
						}
						throw FailedToSaveDwellingUnitException
					}
				}
				dwellingUnitId = dwellingUnitInstance?.id
			}
		}
		return dwellingUnitId
	}

	def updateBirthdate(contactImportInstance, personInstance) {

		def dateFormat = "yyyy-MM-dd"
		Date dob = null
		def birthDateDayKnown = false
		def birthDateMonthKnown = false
		def birthDateYearKnown = false
		def newScore = 0

		if (personInstance && contactImportInstance.birthDate && contactImportInstance.birthDate != '--') {
			def birthDate = contactImportInstance.birthDate

			def components = birthDate.split('-')
			def componentCount = components.size()
			if (componentCount <= 3 && componentCount > 0) {
				
				
				String year = ''
				String month = ''
				String day = ''

				// Determine if a year is present
				if (birthDate ==~ /(\d{4})-(\d{1,2})?-(\d{1,2})?/) {
					//println "found year in birthDate::${birthDate}"
					try {
						year = components[0]
					} catch (Exception ex) {
						println "Could not get year from birthDate::${birthDate}"
					}
				}
				// Determine if a month is present
                                if (birthDate ==~ /(\d{4})?-(\d{1,2})-(\d{1,2})?/) {
                                        //println "found month in birthDate::${birthDate}"
                                        if (year == '') {
						try {
							month = components[0]
						} catch (Exception ex) {
							println "Could not get month from birthDate::${birthDate}"
						}
					} else {
						try {
                                                        month = components[1]
                                                } catch (Exception ex) {
                                                        println "Has Year...Could not get month from birthDate::${birthDate}"
                                                }
					}
                                }
				// Determine if a day is present
                                if (birthDate ==~ /(\d{4})?-(\d{1,2})?-(\d{1,2})/) {
                                        //println "found day in birthDate::${birthDate}"
                                        if (year == '' && month == '') {
						try {
                                                        day = components[0]
                                                } catch (Exception ex) {
                                                        println "Could not get day from birthDate::${birthDate}"
                                                }
                                        } else {
						if (year == '' || month == '') {
                                                	try {
                                                        	day = components[1]
                                                	} catch (Exception ex) {
                                                        	println "Has Year OR Month...Could not get day from birthDate::${birthDate}"
                                                	}
						} else {
							try {
                                                                day = components[2]
                                                        } catch (Exception ex) {
                                                                println "Has Year AND Month...Could not get day from birthDate::${birthDate}"
                                                        }
						}
                                        }
                                }
				/*
				if (year != '' && (month == '' || day == '' )) {
					println "found year ${year} in partial birthDate::${birthDate}"
				}
				if (month != '' && (year == '' || day == '' )) {
                                        println "found month ${month} in partial birthDate::${birthDate}"
                                }
				if (day != '' && (year == '' || month == '' )) {
                                        println "found day ${day} in partial birthDate::${birthDate}"
                                }
				*/
				Integer intDay = 15
				Integer intMonth = 6
				Integer intYear = 0

				if (year.length() > 0) {
					intYear = Integer.parseInt(year)
					birthDateYearKnown = true
					newScore += 100
				}
				if (month.length() > 0) {
					intMonth = Integer.parseInt(month)
					birthDateMonthKnown = true
					newScore += 10
				}
				if (day.length() > 0) {
					intDay = Integer.parseInt(day)
					birthDateDayKnown = true
					newScore += 1
				}
				def stringDate = "${intYear}-${intMonth}-${intDay}"
				try {
					dob = Date.parse(dateFormat, stringDate)
				} catch (Exception ex) {
					log.warn "unable to parse birthdate: ${stringDate}"
				}
			} else {
				log.warn "invalid date format: ${birthDate}, only ${componentCount} parts."
			}
		}

		// If the dob was found and parsed ... and it's different than the person's DOB...
		if (dob && personInstance?.birthDate != dob) {
			def oldScore = 0

			if (personInstance.birthDateDayKnown) { oldScore += 100 }
			if (personInstance.birthDateMonthKnown) { oldScore += 10 }
			if (personInstance.birthDateYearKnown) { oldScore += 1 }

			// If the new one is more exact than the old one...
			if  ( newScore >= oldScore ) {

				Person.withTransaction{
					// re-load person with write access
					personInstance = Person.get(personInstance.id)
					println "Updating DOB for ${personInstance} from '${personInstance.birthDate}' to '${dob}'..."
					personInstance.birthDate = dob
					if ( ! personInstance.save(flush:true) ) {
						println "ERROR Saving Birthdate: ${dob} for person: ${personInstance}"
						personInstance.errors.each{
							println "\t${it}"
						}
					}
				}
			}
		}
	}

	def makePerson(contactImportInstance, contactImportLinkInstance) {
		
		def personInstance = null
		def personInstanceId = null
		
		def dateFormat = "yyyy-MM-dd"
		
		def male = Gender.read(1)
		def female = Gender.read(2)
		
		Person.withTransaction{
		
			def norcSuId = null
			
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
			
			log.trace "Looking for Person: ${lastName}, ${firstName}"
		
			// Search for a person by Person ID
			if (contactImportLinkInstance.personId) {				
				// Save the person instance to the local version
				personInstance = Person.read(contactImportLinkInstance.personId.toLong()) 				
			}
			// search for a person by SUID
			if (!personInstance && contactImportLinkInstance.norcSuId) {
				// Look them up by NORC ID
				norcSuId = contactImportLinkInstance.norcSuId
				def norcPersonLink = PersonLink.findByNorcSuId(norcSuId)
				if (norcPersonLink) {
					if ( ! contactImportLinkInstance.personId ) {
						contactImportLinkInstance.personId = norcPersonLink.person.id
					}
					// Save the person instance to the local version
					personInstance = Person.read(norcPersonLink.person.id)
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
			
			// TODO: disabled person creation... for now
			if (! personInstance){
				
				personInstance = new Person(title: title,
					firstName: firstName,
					middleName: middleName,
					lastName: lastName,
					suffix: suffix,
					gender: gender,
					birthDate: dob, 
					appCreated: appCreated,
					isRecruitable: true)
				
				println "making person: ${personInstance.fullName}; ${personInstance.gender}"
				if (personInstance.save(flush:true)) {
					// If there's a NORC SU_ID, and it doesn't end in 00...
					if ( norcSuId && ! (norcSuId =~ /00$/) ) {
						def personLink = new PersonLink(norcSuId: norcSuId, 
							person: personInstance).save(flush:true)
					}
				} else {
					personInstance.errors.each{ println "${it}" }
					throw FailedToSavePersonException
				}
			} else {
				println "found person!"
			}
	
			if (personInstance && ( norcSuId =~ /0[1-9]$/ )) {
				println "NORC SU_ID: ${norcSuId}"
				def personLinkInstance = PersonLink.findByNorcSuId(norcSuId)
				if (! personLinkInstance) {

					personLinkInstance = PersonLink.findByPerson(personInstance)
					if (personLinkInstance) {
						println "WARNING!: Person: ${personInstance.id} already has NORC SU_ID: ${personLinkInstance.norcSuId}, but attempting to assign them new NORC SU_ID: ${norcSuId}!"
					} else {
						println "Creating: new PersonLink"
						personLinkInstance = new PersonLink(norcSuId: norcSuId, person:personInstance)
						if ( ! personLinkInstance.save(flush:true) ) {
							personLinkInstance.errors.each{ println "${it}" }
							throw FailedToSavePersonLinkInstanceException
						}
					}
				} else if ( ! personLinkInstance.norcSuId) {
					println "Updating: PersonLink"
					personLinkInstance.norcSuId = norcSuId
					if ( ! personLinkInstance.save(flush:true) ) {
						personLinkInstance.errors.each{ println "${it}" }
						throw FailedToSavePersonLinkInstanceException
					}
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
						appCreated: appCreated, source: source,
						infoDate: sourceDate)
					if ( ! personPhoneInstance.save(flush:true) ) {
						println "Failed to Created Person <-> Phone Link for ${personInstance.fullName}"
						personPhoneInstance.errors.each{ println "\t${it}" }
						throw FailedToSavePersonPhoneException
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
						appCreated: appCreated, source: source,
						infoDate: sourceDate)
					if ( ! personEmailInstance.save(flush:true) ) {
						println "Failed to Create Person <-> Email Link for ${personInstance.fullName}"
						personEmailInstance.errors.each{ println "\t${it}" }
						throw FailedToSavePersonEmailException
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
						appCreated: appCreated, source: source,
						infoDate: sourceDate)
					if ( ! personAddressInstance.save(flush:true) ) {
						println "Failed to Create Person <-> Address Link for ${personInstance.fullName}"
						personAddressInstance.errors.each{ println "\t${it}" }
						throw FailedToSavePersonAddressException
					}
				}
			}
		} else { println "makePersonAddress: Something is Missing!" }
	}
	
	
	def makeTrackedItem(personInstance, dwellingUnitInstance, instrumentInstance, 
		instrumentFormatInstance, batchDirectionInstance, instrumentDate) {
		
		// Initial
		def isInitialInstance = IsInitial.read(1)
		
		Date lowDate = instrumentDate - 1
		Date highDate = instrumentDate + 1
		TrackedItem trackedItemInstance = null
		Batch batchInstance = null
		
		
		// log.trace "Looking for Tracked Item with Batch Date: ${instrumentDate}, and Instrument Type: ${instrumentInstance.name}"
		
		TrackedItem.withTransaction {
			
			def trackedItemInstanceList = []
			
			if (personInstance) {
				// If there's a person, look it up by the person
				trackedItemInstanceList = TrackedItem.createCriteria().list{
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
						person {
							eq("id", personInstance?.id)
						}
					}
					maxResults(1)
				}
			} else if (dwellingUnitInstance) {
				trackedItemInstanceList = TrackedItem.createCriteria().list{
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
						dwellingUnit {
							eq("id", dwellingUnitInstance?.id)
						}
					}
					maxResults(1)
				}
			}
			
			// any matches?  first match gets returned
			if (debug) { println "Searching for existing tracked item in list::trackedItemInstanceList::${trackedItemInstanceList}..."}
			trackedItemInstanceList.each {
				if ( ! trackedItemInstance ) {
					trackedItemInstance = it
					batchInstance = it.batch
				}
				log.trace "Found Tracked Item: ${trackedItemInstance.id}"
			}
	
			if ( ! batchInstance ) {
				def batchInstanceList = Batch.createCriteria().list{
					and {
						between("instrumentDate", lowDate, highDate)
						instruments {
							instrument {
								idEq(instrumentInstance.id)
							}
						}
						direction {
							idEq(batchDirectionInstance.id)
						}
						format {
							idEq(instrumentFormatInstance.id)
						}
					}
					maxResults(1)
				}
				// any batches of this instrument type?
				batchInstanceList.each {
					if (! batchInstance ) { batchInstance = it }
					// log.trace "Found Batch: ${batchInstance.id}"
				}
			}
	
			if (! batchInstance ) {
				println "Creating a new Batch..."
				// create a batch
				batchInstance = new Batch(dateCreated:instrumentDate, instrumentDate:instrumentDate,
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
			
			if ( ! trackedItemInstance && batchInstance?.id ) {
				// create a tracked item
				if (debug) {
					println "batch.id::${batchInstance?.id}::batch::${batchInstance}::instrumentDate::${batchInstance?.instrumentDate}"
				}
				trackedItemInstance = new TrackedItem(person:personInstance, dwellingUnit:dwellingUnitInstance, batch: batchInstance)
				if (debug) {
					println "Attempting to save tracked item::trackedItemInstance::${trackedItemInstance} for person::${personInstance}"
				}
				if ( ! trackedItemInstance.save(flush:true) ) {
					println "Failed to create tracked item."
				}
				if (debug) {
					println "Tracked item saved! =)"
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
						householdInstance.appCreated = appCreated
					} else {
						householdInstance.userUpdated = appCreated
						householdInstance.lastUpdated = now
					}
					
					householdInstance.addToPeople(personInstance)
					
					if ( householdInstance.save(flush:true) ) {
						householdId = householdInstance.id
					} else {
						println "Failed to save household."
						householdInstance.errors.each{ println it }
						throw FailedToSaveHouseholdException
					}
				}
			}
		}
		
		return householdId
	}

    def processContact() {
		
		def now = new Date()
		username = springSecurityService?.principal?.getUsername()
		
		def sourceNorc = Source.findByName("Name")
		
		if ( ! sourceNorc )  {
			sourceNorc = new Source(name:'NORC', 
				appCreated:appCreated, selectable:false)
			if ( ! sourceNorc.save(flush:true)) {
				println 'WARNING, Unable to create NORC Source record'
			}
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
						// Person
						if (contactImportInstance.sourcePersonId &&
                                                                ! contactImportLinkInstance.personId) {
							if (debug) {
								println "Person ID exists in contact import. Assigning to contact import link::${contactImportInstance.sourcePersonId}"
							}
							contactImportLinkInstance.personId = contactImportInstance.sourcePersonId
                                                }
	
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
						// Home Phone Number...
						if (contactImportInstance.homePhone && 
								! contactImportLinkInstance.homePhoneId) {
							contactImportLinkInstance.homePhoneId = makePhoneNumber(contactImportInstance.homePhone)
						}
						// Work Phone Number...
						if (contactImportInstance.workPhone && 
								! contactImportLinkInstance.workPhoneId) {
							contactImportLinkInstance.workPhoneId = makePhoneNumber(contactImportInstance.workPhone)
						}
						// Cell Phone Number...
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
								contactImportLinkInstance.addressId = makeStreetAddress(contactImportZp4, true)
							} else {
								// This is a "dirty" address
								contactImportLinkInstance.addressId = makeStreetAddress(contactImportInstance, false)
							}
						}
									
						if (contactImportInstance.sourceName =~ /^norc_.*/) {
							// NORC Dwelling Unit SU_ID...
							if (contactImportInstance.sourceDwellingUnitKey && ! contactImportLinkInstance.norcSuId) {
								def norcSuId = "00${contactImportInstance.sourceDwellingUnitKey}"
								contactImportLinkInstance.norcDwellingSuId = norcSuId

								def norcDwellingLink = DwellingUnitLink.findByNorcSuId(norcSuId)
								if (norcDwellingLink) {
									contactImportLinkInstance.norcDwellingSuId = norcDwellingLink.norcSuId
									if ( ! contactImportLinkInstance.dwellingUnitId ) {
										contactImportLinkInstance.dwellingUnitId = norcDwellingLink.dwellingUnit.id
									}
								} else {
									println "Unable to find NORC SU_ID for DwellingUnit: ${norcSuId}!"
								}
							}

							// NORC Person SU_ID...
							if (contactImportInstance.sourcePersonKey && ! contactImportLinkInstance.norcSuId) {

								def norcSuId = contactImportInstance.sourcePersonKey

								contactImportLinkInstance.norcSuId = norcSuId

								def norcPersonLink = PersonLink.findByNorcSuId(norcSuId)
								if (norcPersonLink) {
									if ( ! contactImportLinkInstance.personId ) {
										contactImportLinkInstance.personId = norcPersonLink.person.id
									} else if (contactImportLinkInstance.personId != norcPersonLink.person.id ) {
										println "Person: ${contactImportLinkInstance.personId} should be: ${norcPersonLink.person.id}!"
									}
								} else {
									println "Unable to find NORC SU_ID for Person: ${norcSuId}!"
								}
							}
						} else {
							println "NON NORC Batch Source: ${contactImportInstance.sourceName}"
						}
						
						// Looking for person base onf SU_ID
						if (contactImportLinkInstance.norcSuId && ! contactImportLinkInstance.personId) {
							def norcSuId = contactImportLinkInstance.norcSuId
							log.trace "Looking up Person by NORC SUID (8 digit, ${norcSuId}, ${contactImportInstance.lastName}, ${contactImportInstance.firstName})"
							def norcPersonLink = PersonLink.findByNorcSuId(norcSuId)
							if (norcPersonLink) {
								contactImportLinkInstance.personId = norcPersonLink.person.id
							}
						}

						if (contactImportLinkInstance.norcDwellingSuId && ! contactImportLinkInstance.dwellingUnitId) {
							def norcSuId = contactImportLinkInstance.norcDwellingSuId
							log.trace "Looking up Dwelling Unit by NORC SUID (10 digit)"
							def norcDwellingLink = DwellingUnitLink.findByNorcSuId(norcSuId)
							if (norcDwellingLink) {
								contactImportLinkInstance.dwellingUnitId = norcDwellingLink.dwellingUnit.id
							}
						}

						def dwellingUnitInstance = DwellingUnit.read(contactImportLinkInstance.dwellingUnitId)
						
						// Dwelling Unit...
						if (contactImportLinkInstance.addressId && ! dwellingUnitInstance) {
							// Make the dwelling unit
							contactImportLinkInstance.dwellingUnitId = makeDwellingUnit(contactImportLinkInstance.addressId)
							// load teh dwelling unit instance
							dwellingUnitInstance = DwellingUnit.read(contactImportLinkInstance.dwellingUnitId)
						}

						// Person...
						if ( (contactImportInstance.firstName || contactImportInstance.lastName) && ! contactImportLinkInstance.personId) {
							contactImportLinkInstance.personId = makePerson(contactImportInstance, contactImportLinkInstance)
						}

						// Birthdate Updates
						if ( contactImportLinkInstance.personId && contactImportInstance.birthDate ) {
							updateBirthdate(contactImportInstance, Person.read(contactImportLinkInstance.personId) )
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
						if (!personInstance) {						
							log.trace "No UMN person fould with id ${contactImportLinkInstance.personId}"
						}
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
						
						// Check for existing tracked item id
						if ( personInstance && contactImportInstance.sourceTrackedItemId ) {
							// Try to find tracked item
							def existingTrackedItemInstance = TrackedItem.get(contactImportInstance.sourceTrackedItemId)
							if (existingTrackedItemInstance) {								
								if (debug) {
                                                                	println "Linking tracked item::${contactImportInstance.sourceTrackedItemId} to person::${personInstance}"
                                                        	}

								// Assign tracked item to person
								existingTrackedItemInstance.person = personInstance
								if (existingTrackedItemInstance.save(flush:true)) {
									contactImportLinkInstance.trackedItemId = existingTrackedItemInstance.id
								}
							}
						}

						// Instruments
						if ( ( personInstance || dwellingUnitInstance) && contactImportInstance.instrumentId && ! contactImportLinkInstance.trackedItemId) {
							def instrumentInstance = Instrument.read(contactImportInstance.instrumentId)
							def instrumentDate = contactImportInstance.instrumentDate ?: contactImportInstance.sourceDate ?: now
							
							// Phone (default as this is what NORC usually does)
							def instrumentFormatInstance = InstrumentFormat.read(contactImportInstance.instrumentTypeId ?: 3)
							// Incoming (default)
							def batchDirectionInstance = BatchDirection.read(contactImportInstance.batchDirectionId ?: 2)
							
							if (debug) {
								println "Creating tracked item | person::${personInstance} | dwellingUnit::${dwellingUnitInstance} | instrument::${instrumentInstance} | instrumentFormat::${instrumentFormatInstance} | batchDirection::${batchDirectionInstance} | instrumentDate::${instrumentDate}"
							}
							contactImportLinkInstance.trackedItemId = makeTrackedItem(personInstance, 
								dwellingUnitInstance, instrumentInstance, instrumentFormatInstance, 
								batchDirectionInstance, instrumentDate)
							
						}

						// Result
						if ( contactImportLinkInstance.trackedItemId && contactImportInstance.resultId ) {

							TrackedItem.withTransaction{
								def trackedItemInstance = TrackedItem.get(contactImportLinkInstance.trackedItemId)
								def resultInstance = Result.read(contactImportInstance.resultId)

								if ( ! resultInstance) {
									println "Invalid Result ID: ${contactImportInstance.resultId}"
								} else if (trackedItemInstance && ! trackedItemInstance?.result) {
									def itemResultInstance = new ItemResult(trackedItem: trackedItemInstance,
									result: resultInstance, userCreated: username, 
									receivedDate: contactImportInstance.instrumentDate,
									appCreated: appCreated)

									trackedItemInstance.result = itemResultInstance

									if ( ! trackedItemInstance.save(flush:true) ) {
										trackedItemInstance.errors.each{ println it }
										throw FailedToSaveItemResultException
									} else {
										println "saved result ${resultInstance}"
									}
								}
							}
						}
						
						// Household
						if (personInstance && dwellingUnitInstance) {
							def householdInstance = Household.read(contactImportLinkInstance.householdId)
							if ( ! householdInstance ) {
								contactImportLinkInstance.householdId = makeHousehold(personInstance, dwellingUnitInstance)
							}
						}

						// TODO: Appointments???
					}
					
					if ( ! contactImportLinkInstance.save(flush:true)) {
						println "Could not create ContactImportLink:"
						contactImportLinkInstance.errors.each{
							println "\t${it}"
						}
					}
				}
			}
		}

		// Person NORC_SUID Link
		def query = """INSERT INTO person_link
				(version, norc_su_id, person_id)
			SELECT 0, ci.source_person_key, p.id
			FROM contact_import ci INNER JOIN
				contact_import_link cil ON ci.id = cil.contact_import_id INNER JOIN
				person p ON p.id = cil.person_id LEFT OUTER JOIN
				person_link pl ON p.id = pl.person_id
			WHERE (cil.person_id IS NOT NULL)
				AND (ci.source_person_key NOT LIKE '%00')
				AND (ci.source_person_key NOT LIKE '9%')
				AND (pl.id IS NULL)
			GROUP BY ci.source_person_key, p.id;"""
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
									contactImportZp4Instance.errors.each{ println it }
									throw FailedToSaveContactImportZp4InstanceException
								}
							}
						}
					}
				}
			}
		}
		println "done standardizing contact import addresses."
	}

	def cleanUpDirtyAddresses = {
		// Get list of dirty street addresses
		def dirtyStreetAddresses = StreetAddress.findAllByStandardized(false)

		log.debug "Found ${dirtyStreetAddresses.size()} dirty street addresses."
		dirtyStreetAddresses.each{ sa ->
				log.debug "Need to process: ${sa}."
		}
		def cleanCount = 0

		// Processs them in parallel to speed it up...
		//GParsPool.withPool {
		//	dirtyStreetAddresses.eachParallel{ sa ->
		//		StreetAddress.withTransaction{
		dirtyStreetAddresses.each{ sa ->
					// clean address
					def streetAddressInstance = StreetAddress.get(sa.id)
					log.debug "Standardizing ${streetAddressInstance}..."
					def cleanedAddressResult = streetAddressInstance.standardize()
					log.debug "Processing ${streetAddressInstance}..."

					// Check for a certified result
					if (cleanedAddressResult.certified == 'C') {
						// convert the result to a semaphore street address
						def cleanedAddressInstance = cleanedAddressResult.toStreetAddress()

						// Save the street address values to our streetAddressInstance
						streetAddressInstance.address = cleanedAddressInstance.address 
						streetAddressInstance.city = cleanedAddressInstance.city 
						streetAddressInstance.state = cleanedAddressInstance.state 
						streetAddressInstance.zipCode = cleanedAddressInstance.zipCode 
						streetAddressInstance.zip4 = cleanedAddressInstance.zip4 
						streetAddressInstance.standardized = true
						
						// Save the changes
						log.debug "Saving ${streetAddressInstance}..."
						if (! streetAddressInstance.save(flush:true)) {
							log.error "Error saving streetAddressInstance after standardizing it!"
							log.error streetAddressInstance.errors
						} else {
							cleanCount++
							log.info "Cleaned Street Address ID: ${streetAddressInstance.id}"
						}
					} else {
						log.debug "Unable to clean ${streetAddressInstance}"
					}
		//		}
		//	}
		}

		log.info "Cleaned ${cleanCount} street addresses."
	}

	def cleanUpPreferredOrder = {
		// Clean up addresses
		def sql = """
		select count(pa), pa.person, pa.preferredOrder
		from PersonAddress pa
		group by pa.person, pa.preferredOrder
		having count(pa) > 1
		"""
		def multiContactResults = Person.executeQuery(sql)

		multiContactResults.each{ result  ->
			def personInstance = Person.get(result[1].id)
			def recordCount = personInstance.streetAddresses.size()
			def sortOrder = recordCount - 1

			println "Person: ${personInstance} has ${result[0]} addresses with the same order."

			personInstance.streetAddresses.sort{ it.dateCreated }.each{ personAddressInstance ->
				if (debug) {
					println	"\tSetting preferred order for address ${personAddressInstance.streetAddress.id}:${personAddressInstance.streetAddress.address} to ${sortOrder}"
				}
				// Update preferred order of all addresses for this person
				personAddressInstance.preferredOrder = sortOrder
				if ( !  personAddressInstance.save() ) {
					println "ERROR Trying to update Sort Order for ${personInstance}:"
					println "\t${sortOrder}\t${personAddressInstance.dateCreated}\t${personAddressInstance.streetAddress.address}"
				}
				sortOrder--
			}
		}

		// Clean up phone numbers
		sql = """
		select count(pp), pp.person, pp.preferredOrder
		from PersonPhone pp
		group by pp.person, pp.preferredOrder
		having count(pp) > 1
		"""
		multiContactResults = Person.executeQuery(sql)

		multiContactResults.each{ result  ->
			def personInstance = Person.get(result[1].id)
			def recordCount = personInstance.phoneNumbers.size()
			def sortOrder = recordCount - 1

			println "Person: ${personInstance} has ${result[0]} phones with the same order."

			personInstance.phoneNumbers.sort{ it.dateCreated }.each{ personPhoneInstance ->
				personPhoneInstance.preferredOrder = sortOrder
				if ( !  personPhoneInstance.save() ) {
					println "ERROR Trying to update Sort Order for ${personInstance}:"
					println "\t${sortOrder}\t${personPhoneInstance.dateCreated}\t${personPhoneInstance.phoneNumber}"
				}
				sortOrder--	
			}
		}

		println "Done cleaning up sort order problems."
	}
}
