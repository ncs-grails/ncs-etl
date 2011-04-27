package edu.umn.ncs

class ExtractTransformLoadController {

	def importContactService
	 
    def index = { }
	
	def importData = {
		
		importContactService.processContact()

		redirect(view:"/")
	}
	
}
