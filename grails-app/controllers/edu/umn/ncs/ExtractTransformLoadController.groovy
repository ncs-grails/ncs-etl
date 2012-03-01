package edu.umn.ncs

import grails.plugins.springsecurity.Secured

@Secured(['ROLE_NCS_IT'])
class ExtractTransformLoadController {

	def contactImportService
	 
    def index = { }
	
	def processContact = {
		
		contactImportService.processContact()

		redirect(view:"/")
	}
	
	def repairDupPreferredOrder = {
		
		contactImportService.cleanUpPreferredOrder()
		redirect(view:"/")
	}
	
	def zp4StandardizeImportData = {
		contactImportService.zp4StandardizeImportData()
		redirect(view:"/")
	}

	def cleanUpDirtyAddresses = {
		contactImportService.cleanUpDirtyAddresses()
		redirect(view:"/")
	}
}
