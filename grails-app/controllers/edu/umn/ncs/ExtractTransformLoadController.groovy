package edu.umn.ncs

import org.codehaus.groovy.grails.plugins.springsecurity.Secured

@Secured(['ROLE_NCS_IT'])
class ExtractTransformLoadController {

	def contactImportService
	 
    def index = { }
	
	def processContact = {
		
		contactImportService.processContact()

		redirect(view:"/")
	}
	
	def zp4StandardizeImportData = {
		contactImportService.zp4StandardizeImportData()
		
		redirect(view:"/")
	}
}
