package edu.umn.ncs

/** This service has helper methods to load IDs into an instance
of the ContactImportLink class.  */
class ContactImportLinkService {

    static transactional = true

	def dataSouce
	def appCreated
	def username
	private def US = null

	public ContactImportLinkService() {
		def US = Country.findByAbbreviation("us")
	}

	// link all fields
	def linkAll(contactImportLinkInstance) {

		// Order is VERY important here!

		// link all the phone IDs and return the updated (if any) instance
		contactImportLinkInstance = linkAllPhones(contactImportLinkInstance)

		// These methods return an ID directly.
		contactImportLinkInstance.with{

			// The ?: are elvis operators that are used to try to update
			// the field only if it is null (or zero)

			// update the emailAddressId if needed
			emailAddressId = emailAddressId ?: linkEmailAddress(contactImportLinkInstance)
			// update the streetAddressId if needed
			streetAddressId = streetAddressId ?: linkStreetAddress(contactImportLinkInstance)

			// Update the person's su_id if needed
			norcSuId = norcSuId ?: linkNorcSuId(contactImportLinkInstance) 
			// Update the dwelling unit's su_id if needed
			norcDwellingSuId = norcDwellingSuId ?: linkNorcDwellingSuId(contactImportLinkInstance) 

			// find the dwelling unit id if possible.
			// Note: this is dependent upon having a valid address ID.
			// It will use the NORC SU_ID if available.
			dwellingUnitId = dwellingUnitId ?: linkDwellingUnit(contactImportLinkInstance)

			// find out the person id if possible.
			personId = personId ?: linkPerson(contactImportLinkInstance)

			// find the household id if possible.  This 
			// depends on having a valid dwelling unit and person ID
			householdId = householdId ?: linkHousehold(contactImportLinkInstance) 

			// Find out the batch associated with any instrument
			// information if available.
			batchId = batchId ?: linkBatch(contactImportLinkInstance)

			// find any tracked item IDs if available for 
			// a particular instrument type.  This depends
			// on having a valid person id or dwelling unit id
			trackedItemId = trackedItemId ?: linkTrackedItem(contactImportLinkInstance) 

			// find the appointment id if possible if
			// the person id is known
			appointmentId = appointmentId ?: linkAppointment(contactImportLinkInstance) 

			// find the EOI id if the person id is
			// known.
			eventOfInterestId = eventOfInterestId ?: linkEventOfInterest(contactImportLinkInstance)

			// find the Study id if the Study Name was passed
			studyId = studyId ?: linkStudy(contactImportLinkInstance)
		}

		return contactImportLinkInstance
	}


	def linkAllPhones(contactImportLinkInstance) {
		contactImportLinkInstance.with{
			primaryPhoneId = primaryPhoneId ?: linkPhone(contactImport.primaryPhone)
			secondaryPhoneId = secondaryPhoneId ?: linkPhone(contactImport.secondaryPhone)
			homePhoneId = homePhoneId ?: linkPhone(contactImport.homePhone)
			workPhoneId = workPhoneId ?: linkPhone(contactImport.workPhone)
			cellPhoneId = cellPhoneId ?: linkPhone(contactImport.cellPhone)
		}
		return contactImportLinkInstance
	}

	// BEGIN: PRIMARY LINK LOOKUP METHODS
	def linkPerson(contactImportLinkInstance) {
		def personId = null

		// Lookup the personId based on the su_id information
		personId = personId ?: helpLinkPersonByNorcSuId(contactImportLinkInstance)

		// Lookup the person based on the dwelling unit / name

		// Lookup the person based on the name / address / phone

		// Lookup the person based on the name / address

		// Lookup the person based on the name / phone

		// Lookup the person by name / birthdate
	}

	def linkPhone(phoneNumber) {
		// if there's a single "x", assume an extension
		def halves = phoneNumber.split('x')
		def extension = null

		if (halves.size() == 2) {
			phoneNumber = halves[0]
			extension = halves[1]
		}

		// clean all non-alphanumeric
		def phone = phoneNumber.replaceAll('[a-zA-Z-.]', '').trim()
		if (phone[0] == '1' && phone.length() == 11) {
			phone = phone - '1'
		}

		def phoneInstance = PhoneNumber.findWhere(countryCode:'1',
			phoneNumber: phone,
			extension: extension)

		phoneInstanceId = phoneInstance?.id

		return phoneInstanceId
	}

	def linkEmailAddress(contactImportLinkInstance) {
		def emailAddress = contactImportLinkInstance.contactImport.emailAddress?.toLowerCase()?.trim()
		def emailInstanceId = null
		
		if (emailAddress) {
			def emailInstance = EmailAddress.findByEmailAddress(emailAddress)
			emailInstanceId = emailInstance?.id
		}
		return emailInstanceId
	}

	def linkStreetAddress(contactImportLinkInstance) {
		def contactImportInstance = contactImportLinkInstance.contactImport
		def streetAddressId = null
		// TODO: right now, it won't create addresses that won't standardize.
		// NOTE: this may be the permanent intended behavior

		def addressInstance = ContactImportZp4.findByContactImport(contactImportInstance)
		if ( ! addressInstance?.updated) {
			// pull in the "un-clean" address if we must.
			addressInstance = contactImportLinkInstance
		}
		
		def address = addressInstance.address1.replaceAll('%',' ')

		if (addressInstance.class == "class edu.umn.ncs.staging.ContactImport") {
			if (addressInstance?.addressUnit) {
				address += " %${addressInstance.addressUnit}"
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

		def streetAddressInstanceList = []

		// If there's not zp4 or zipCode
		if ( ! streetAddressId && ! zipCode && ! zip4) {
			streetAddressInstanceList = StreetAddress.createCriteria().list{
				and {
					like("address", address)
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
			
		return streetAddressId
	}

	def linkDwellingUnit(contactImportLinkInstance) {
		def streetAddressId = contactImportLinkInstance.streetAddressId
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

	def linkHousehold(contactImportLinkInstance) {
		def householdId = null

		contactImportLinkInstance.with{
			// lookup household if personId exists
			if (personId && dwellingUnitId) {
				// find by person and dwellingUnit
				def c = Household.createCriteria()
				def householdInstance = c.get{
					and {
						people {
							eq('id', personId)
						}
						dwelling {
							eq('id', dwellingUnitId)
						}
					}
					maxResults(1)
				}
				householdId = householdInstance?.id
			}
		}
		return householdId
	}

	def linkAppointment(contactImportLinkInstance) {
		def contactImportInstance = contactImportLinkInstance.contactImport
		def appointmentId = null

		if (contactImportLinkInstance.personId
				&& contactImportInstance.appointmentDateTime 
				&& contactImportLinkInstance.appointmentTypeId) {

			def dayBefore = contactImportInstance.appointmentDateTime - 1
			def dayAfter = contactImportInstance.appointmentDateTime + 1

			// look for any appointments of the same type that are within
			// a day of the reported appointment
			def appointmentInstance = Appointment.createCriteria().get(){
				and{
					person{ idEq(contactImportLinkInstance.personId) }
					type{ idEq(contactImportInstance.appointmentTypeId) }
					between('startTime', dayBefore, dayAfter)
				}
			}
			appointmentId = appointmentInstance?.id
		}
		return appointmentId
	}

	def linkBatch(contactImportLinkInstance) {

		def batchId = null

		contactImportLinkInstance.with{
			def personInstance = Person.read(personId)
			def householdInstance = Household.read(householdId)
			def dwellingUnitInstance = DwellingUnit.read(dwellingUnitId)

			def instrumentInstance = Instrument.read(instrumentId)
			def instrumentFormatInstance = InstrumentFormat.read(instrumentTypeId)
			def batchDirectionInstance = BatchDirection.read(batchDirectionId)
			def isInitialInstance = IsInitial.read(1)

			if ( instrumentInstance && instrumentFormatInstance && batchDirectionInstance ) {

				Date lowDate = instrumentDate - 1
				Date highDate = instrumentDate + 1
				TrackedItem trackedItemInstance = null
				Batch batchInstance = null

				if (personInstance) {
					// If there's a person, look it up by the person
					trackedItemInstance = TrackedItem.createCriteria().get{
						and {
							batch {
								and {
									between("instrumentDate", lowDate, highDate)
									instruments {
										and {
											instrument { idEq(instrumentInstance.id) }
											isInitial { idEq(isInitialInstance.id) }
										}
									}
									direction { idEq(batchDirectionInstance.id) }
									format { idEq(instrumentFormatInstance.id) }
								}
							}
							person { eq("id", personInstance?.id) }
						}
						maxResults(1)
					}
				} else if (dwellingUnitInstance) {
					// if there is no person, search be dwelling unit
					trackedItemInstance = TrackedItem.createCriteria().get{
						and {
							batch {
								and {
									between("instrumentDate", lowDate, highDate)
									instruments {
										and {
											instrument { idEq(instrumentInstance.id) }
											isInitial { idEq(isInitialInstance.id) }
										}
									}
									direction { idEq(batchDirectionInstance.id) }
									format { idEq(instrumentFormatInstance.id) }
								}
							}
							dwellingUnit { eq("id", dwellingUnitInstance?.id) }
						}
						maxResults(1)
					}
				} else if (householdInstance) {
					// use household as a last resort
					trackedItemInstance = TrackedItem.createCriteria().get{
						and {
							batch {
								and {
									between("instrumentDate", lowDate, highDate)
									instruments {
										and {
											instrument { idEq(instrumentInstance.id) }
											isInitial { idEq(isInitialInstance.id) }
										}
									}
									direction { idEq(batchDirectionInstance.id) }
									format { idEq(instrumentFormatInstance.id) }
								}
							}
							household { eq("id", householdInstance?.id) }
						}
						maxResults(1)
					}
				}

				// If we got a hit, then we're done!
				if (trackedItemInstance) {
					return trackedItemInstance?.batch?.id
				} else {
					batchInstance = Batch.createCriteria().get{
						and {
							between("instrumentDate", lowDate, highDate)
							instruments {
								and {
									instrument { idEq(instrumentInstance.id) }
									isInitial { idEq(isInitialInstance.id) }
								}
							}
							direction { idEq(batchDirectionInstance.id) }
							format { idEq(instrumentFormatInstance.id) }
						}
						maxResults(1)
					}
					// save the batch ID if it was found
					batchId = batchInstance?.id
				}
			}
		}

		return batchId
	}

	def linkTrackedItem(contactImportLinkInstance) {
		def trackedItemId = null

		contactImportLinkInstance.with{
			def personInstance = Person.read(personId)
			def householdInstance = Household.read(householdId)
			def dwellingUnitInstance = DwellingUnit.read(dwellingUnitId)

			Batch batchInstance = Batch.read(batchId)

			if (batchInstance && dwellingUnitInstance ) {

				Date lowDate = instrumentDate - 1
				Date highDate = instrumentDate + 1
				TrackedItem trackedItemInstance = null

				// Look up the tracked item by the batch
				if (personInstance) {
					// If there's a person, look it up by the person
					trackedItemInstance = TrackedItem.createCriteria().get{
						and {
							batch { idEq(batchInstance.id) }
							person { eq("id", personInstance?.id) }
						}
						maxResults(1)
					}
				} else if (dwellingUnitInstance) {
					// if there is no person, search be dwelling unit
					trackedItemInstance = TrackedItem.createCriteria().get{
						and {
							batch { idEq(batchInstance.id) }
							dwellingUnit { eq("id", dwellingUnitInstance?.id) }
						}
						maxResults(1)
					}
				} else if (householdInstance) {
					// use household as a last resort
					trackedItemInstance = TrackedItem.createCriteria().get{
						and {
							batch { idEq(batchInstance.id) }
							household { eq("id", householdInstance?.id) }
						}
						maxResults(1)
					}
				}

				// If we got a hit, then we're done!
				if (trackedItemInstance) {
					trackedItemId = trackedItemInstance?.id
				}
			}
		}

		return trackedItemId
	}

	def linkEventOfInterest(contactImportLinkInstance) {
		def contactImportInstance = contactImportLinkInstance.contactImport
		def eventOfInterestId = null

		def personId = contactImportLinkInstance.personId
		def eventOfInterestStudyId = contactImportInstance.eventOfInterestStudyId 
		def eventOfInterestContactDate = contactImportInstance.eventOfInterestContactDate 
		def eventOfInterestTypeId = contactImportInstance.eventOfInterestTypeId 
		def eventOfInterestCode = contactImportInstance.eventOfInterestCode 
		def eventOfInterestDate = contactImportInstance.eventOfInterestDate 

		if (personId && eventOfInterestTypeId 
				&& eventOfInterestContactDate && eventOfInterestSourceId ) {

			def weekBefore = eventOfInterestContactDate - 7
			def weekAfter = eventOfInterestContactDate + 7

			def eventOfInterestInstance = EventOfInterest.createCriteria().get{
				and {
					type{ idEq(eventOfInterestTypeId) }
					eventReport{
						and {
							person { idEq(personId) }
							studies { idEq(eventOfInterestStudyId) }
							betweeen('contactDate', weekBefore, weekAfter)
						}
					}
					eq('eventCode', eventOfInterestCode)
					eq('eventDate', eventOfInterestDate)
				}
				maxResults(1)
			}
			eventOfInterestId = eventOfInterestInstance?.id
		}

		return eventOfInterestId 
	}

	// This is the SU_ID for the person
	def linkNorcSuId(contactImportLinkInstance) {

		def contactImportInstance = contactImportLinkInstance.contactImport
		def norcSuId = null
		def norcPrefix = ~/^norc_.*/
		def firstName = contactImportInstance.firstName

		// We only link NORC su_ids for NORC data sources
		if ( norcPrefix.matcher(contactImportInstance.sourceName).matches() ) {

			// if it's passed by the import query, just use it
			// This is the preferred method.
			norcSuId = norcSuId ?: contactImportInstance.norcPersonLink

			def dwellingUnitId  = contactImportLinkInstance.dwellingUnitId

			if ( ! norcSuId && dwellingUnitId  && firstName ) {
				// search by first name in household with person link record
				def personLinkInstance = PersonLink.createCriteria().get{
					person{
						and {
							ilike('firstName', firstName)
							households{
								dwellingUnit { idEq(dwellingUnitId) }
							}
						}
					}
					maxResults(1)
				}

				// grab the su_id if the person Link record was found
				norcSuId = personLinkInstance?.norcSuId
			}
		}

		return norcSuId
	}

	def linkNorcDwellingSuId(contactImportLinkInstance) {
		def contactImportInstance = contactImportLinkInstance.contactImport
		def norcDwellingSuId = null
		def norcPrefix = ~/^norc_.*/

		// We only link NORC su_ids for NORC data sources
		if ( norcPrefix.matcher(contactImportInstance.sourceName).matches() ) {
			// pull it from the import source if available
			norcDwellingSuId = contactImportInstance.sourceDwellingUnitKey

			if ( ! norcDwellingSuId && contactImportLinkInstance.personId ) {
				def dwellingUnitLinkInstance = DwellingUnitLink.createCriteria().get{
					dwellingUnit{ 
						households{
							people { idEq(contactImportLinkInstance.personId) }
						}
					}
					maxResults(1)
				}
				// get the su_id if the link for this person exists
				norcDwellingSuId = dwellingUnitLinkInstance?.norcSuId
			}
		}
		return norcDwellingSuId
	}

	def linkStudy(contactImportLinkInstance) {
		def contactImportInstance = contactImportLinkInstance.contactImport
		def studyId = null

		// if there's a study name.  Lookup the study ID
		if (contactImportInstance.studyName) {
			def studyInstance = Study.findByName(studyName)
			studyId = studyInstance?.id
		}
		return studyId
	}

	def linkSubject(contactImportLinkInstance) {
		def contactImportInstance = contactImportLinkInstance.contactImport
		def subjectId = null

		// No use looking up the subjectId if there is no study, right?
		if (contactImportLinkInstance.studyId && contactImportInstance.enrollmentTypeId) {
			def subjectInstance = Subject.createCriteria().get{
				and {
					person{ idEq(contactImportLinkInstance.personId) }
					enrollment{ idEq(contactImportLinkInstance.enrollmentTypeId) }
				}
				maxResults(1)
			}
			subjectId = subjectInstance?.subjectId
		}
		return subjectId
	}

	// END: PRIMARY LINK LOOKUP METHODS

	// BEGIN: SECONDARY LINK LOOKUP METHODS
	def helpLinkPersonByNorcSuId(contactImportLinkInstance) {
		def personId = null
		if (contactImportLinkInstance.norcSuId) { 
			def personLinkInstance = PersonLink.findByNorcSuId(contactImportLinkInstance.norcSuId)
			personId = personLinkInstance?.norcSuId
		}
		return personId
	}

	/** Lookup the person based on the dwelling unit / name */
	def helpLinkPersonByDwellingUnitName(contactImportLinkInstance) {
		return false
	}

	/** Lookup the person based on the name / address / phone */
	def helpLinkPersonByNameAddressPhone(contactImportLinkInstance) {
		return false
	}

	/** Lookup the person based on the name / address */
	def helpLinkPersonByNameAddress(contactImportLinkInstance) {
		return false
	}

	/** Lookup the person based on the name / phone */
	def helpLinkPersonByNamePhone(contactImportLinkInstance) {
		return false
	}

	/** Lookup the person by name / birthdate */
	def helpLinkPersonByNameBirthdate(contactImportLinkInstance) {
		return false
	}


	// END: SECONDARY LINK LOOKUP METHODS
}
