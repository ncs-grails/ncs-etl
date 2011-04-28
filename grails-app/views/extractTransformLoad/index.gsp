<html>
    <head>
        <title>National Children's Study - ETL</title>
        <meta name="layout" content="ncs" />
        <link rel="stylesheet" type="text/css" href="${resource(dir:'css',file:'main.css')}" />
        <script>
        	/* $(document).ready(function(){ }); */

          	function start(divId) {
          		$("#etl").fadeTo("slow", "0.4");
          		$("#" + divId + "-processing").show();
           		$("#" + divId + "-fail").hide();
          		$("#" + divId + "-success").hide();
            }
          	function stop(divId) {
          		$("#etl").fadeTo("fast", "1.0");
          		$("#" + divId + "-processing").hide();
            }
          	function success(divId) {
          		stop(divId);
          		$("#" + divId + "-success").show();
          	}
          	function fail(divId) {
          		stop(divId);
          		$("#" + divId + "-fail").show();
          	}
        </script>
    </head>
    <body>
        <div id="pageBody">
            <h1>NCS - Extract, Transform, Load</h1>
            <img id="etl" src="${resource(dir:'images',file:'etl.png')}" alt="Extract, Transform, Load" width="960" height="240" />
            
            <p>This application is used to extract data from a given source, transform it as needed, and load it to a different source.</p>

            <div class="dialog">
                <h2>Available ETL Processes:</h2>
                <div class="big maroonRoundRect" style="text-align: center;">
                	<g:remoteLink controller="extractTransformLoad" action="zp4StandardizeImportData" 
                		onLoading="start('zp4');" onSuccess="success('zp4');" onFailure="fail('zp4');">ZP4 Pre-import Process</g:remoteLink>
                	<span id="zp4-fail" style="display:none;"><img alt="Failed." src="${resource(dir:'images',file:'fail.png')}" /></span>
                	<span id="zp4-success" style="display:none;"><img alt="Success." src="${resource(dir:'images',file:'success.png')}" /></span>
                	<div id="zp4-processing" style="display:none;"><img alt="Processing..." src="${resource(dir:'images',file:'ajax-loader.gif')}" /></div>
                </div>
                <div class="big maroonRoundRect" style="text-align: center;">
                	<g:remoteLink controller="extractTransformLoad" action="processContact" 
                		onLoading="start('importData');" onSuccess="success('importData');" onFailure="fail('importData');">Person Import</g:remoteLink>
                	<span id="importData-fail" style="display:none;"><img alt="Failed." src="${resource(dir:'images',file:'fail.png')}" /></span>
                	<span id="importData-success" style="display:none;"><img alt="Success." src="${resource(dir:'images',file:'success.png')}" /></span>
                	<div id="importData-processing" style="display:none;"><img alt="Processing..." src="${resource(dir:'images',file:'ajax-loader.gif')}" /></div>
                </div>
            </div>
        </div>
    </body>
</html>
