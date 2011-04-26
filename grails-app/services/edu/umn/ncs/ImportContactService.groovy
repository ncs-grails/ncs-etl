package edu.umn.ncs

class ImportContactService {

	def dataSource
	
    static transactional = true

	def initializeLink() {
		
	}
	
    def processPhone() {
		def processName = 'phone'
		def dependsOn = []
		
		def sql = """SELECT ci.id, ci.primary_phone
			FROM ncs_staging.contact_import ci INNER JOIN 
				ncs_staging.import_link il ON ci.id = il.contact_import_id
			WHERE (il.primary_phone_id IS NULL)"""
		
		// Pull Records, Create Phones, 
    }

	def processEmail() {
		def processName = 'email'
		def dependsOn = []
	
	}

	def processAddress() {
		def processName = 'address'
		def dependsOn = ['standardize']
	}
	
	def processStandardize() {
		def processName = 'standardize'
		def dependsOn = []
		
	}
	
	def processNorc() {
		def processName = 'norc-link'
		def dependsOn = []
		
	}

	def processPeople() {
		def processName = 'people'
		def dependsOn = ['phone', 'email', 'address', 'norc-link']
	}
}
