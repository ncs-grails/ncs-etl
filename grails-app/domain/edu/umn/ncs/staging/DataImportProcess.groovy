package edu.umn.ncs.staging

class DataImportProcess {

	/** This is the default label for this process.
		This field has a maximum size of 64 characters and is mandatory.
		*/
	String name
	/** This is the detailed description for this process.
		This field has a maximum length of 8000 characters,
		and it is optional.
		*/
	String description
	/** This field holds the date that this process was last run.
		It is used for reporting purposes, and is also used
		to see if a dependent query has been run with in a reasonable
		amount of time recently. */
	Date lastRun
	/** this field contains the optional sql query to be run. 
		This is the heart of the class instance. Either the sqlquery or
	   	the closure must be specified.  If both sqlquery and closure are 
		specified, they are run in the order sqlquery, then closure. */
	String sqlQuery
	/** this field contains the optional groovy closure to be run. 
		This is the heart of the class instance. Either the sqlquery must be
		specified, or the closure must be specified.  If both 
		sqlquery and closure are specified, they are run in the order
		sqlquery, then closure. */
	String closure

	/** This is the default string converter for this class.
		It simply returns the 'name' attribute */
	String toString() { name }

	/** If this process depends on the completion of the preceding services, 
		please define them here, by adding instances of the DataImportProcess
		class to this classes dependsOn attribute collection. */
	static hasMany = [ dependsOn: DataImportProcess ]

	static def validateClosure = { closure ->
		def gs = new GroovyShell()
		def emptySet = [ [itemId: 0], [itemId: 1] ]
		def result = []
		def compiledClosure
		def passed = true

		if (closure) {
			// verify that it compiles as valid Groovy Code
			try {
				compiledClosure = gs.evaluate(closure)
			} catch (ex) {
				passed = "compileError"
			}

			// verify we can run it with an emptyset as a parameter
			try {
				result = compiledClosure(emptySet)
			} catch (ex) {
				passed = "noSignatureOfMethod"
			}

			// verify that our output is the same type as input
			if ( result.class != emptySet.class ) {
				passed = "invalidReturnType"
			}

			// verify that we didn't loose rows
			if (result.size() != emptySet.size()) {
				passed = "invalidReturnDataSetSize"
			}
		}

		return passed
	}

    static constraints = {
		name(maxSize: 64)
		description(nullable: true, maxSize: 8000)
		lastRun(nullable: true)
		sqlQuery(maxSize: 8000, nullable: true)
		closure(maxSize: 8000, nullable: true, validator: validateClosure)
    }
}
