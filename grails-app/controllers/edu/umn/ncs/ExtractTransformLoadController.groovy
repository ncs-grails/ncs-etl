package edu.umn.ncs

class ExtractTransformLoadController {

	def importContactService
	 
    def index = { }
	
	def processContact = {
		
		importContactService.processContact()

		redirect(view:"/")
	}
	
	def zp4StandardizeImportData = {
		importContactService.zp4StandardizeImportData()
		
		redirect(view:"/")
	}
}
