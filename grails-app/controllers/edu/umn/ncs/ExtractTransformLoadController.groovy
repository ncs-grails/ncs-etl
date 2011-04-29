package edu.umn.ncs

import org.codehaus.groovy.grails.plugins.springsecurity.Secured

@Secured(['ROLE_NCS_IT'])
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
