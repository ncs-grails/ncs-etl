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
			studyId = studyId ?: linkStudy(contactImportInstance)
		}

		return contactImportInstance
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
	def linkPerson() {
	}

	def linkPhone(phone) {
		PhoneNumber.withTransaction{
			def phone = phoneNumber.replaceAll('[a-zA-Z-.]', '').trim()
			if (phone[0] == '1' && phone.length() == 11) {
				phone = phone - '1'
			}

			def phoneInstance = PhoneNumber.findWhere(countryCode:'1',
				phoneNumber: phone,
				extension: null)

			phoneInstanceId = phoneInstance?.id
		}
		return phoneInstanceId
	}

	def linkEmailAddress(contactImportInstance) {
	}

	def linkStreetAddress(contactImportInstance) {
	}

	def linkDwellingUnit(contactImportInstance) {
	}

	def linkHousehold(contactImportInstance) {
	}

	def linkAppointment(contactImportInstance) {
	}

	def linkBatch(contactImportInstance) {
	}

	def linkTrackedItem(contactImportInstance) {
	}

	def linkEventOfInterest(contactImportInstance) {
	}

	def linkNorcSuId(contactImportInstance) {
	}

	def linkNorcDwellingSuId(contactImportInstance) {
	}

	def linkStudy(contactImportInstance) {
	}

	// END: PRIMARY LINK LOOKUP METHODS

	// BEGIN: SECONDARY LINK LOOKUP METHODS
	def helpLinkPersonByNorcSuId(contactImportInstance) {
	}
	// END: SECONDARY LINK LOOKUP METHODS
}
