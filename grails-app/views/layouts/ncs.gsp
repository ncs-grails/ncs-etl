<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
	<title><g:layoutTitle default="Batch Tracking" /></title>
	<link rel="stylesheet" type="text/css" href="${resource(dir:'css',plugin:'ncs-web-template',file:'umn_reset.css')}" />
	<link rel="stylesheet" type="text/css" href="${resource(dir:'css',plugin:'ncs-web-template',file:'umn_text.css')}" />
	<link rel="stylesheet" type="text/css" href="${resource(dir:'css',plugin:'ncs-web-template',file:'umn_template.css')}" />
	<link rel="stylesheet" type="text/css" href="${resource(dir:'css',plugin:'ncs-web-template',file:'umn_optional.css')}" />
	<link rel="stylesheet" type="text/css" href="${resource(dir:'css',plugin:'ncs-web-template',file:'umn_grails.css')}" />
	<link rel="stylesheet" type="text/css" href="${createLink(controller:'css',action:'template')}" />
	<link rel="stylesheet" type="text/css" href="${resource(dir:'css',plugin:'ncs-web-template',file:'umn_print.css')}" media="print" />
	<link rel="stylesheet" type="text/css" href="${createLink(controller:'css',action:'print')}" media="print" />
	<link rel="shortcut icon" href="${resource(dir:'images',plugin:'ncs-web-template',file:'favicon.ico')}" type="image/x-icon" />
<g:javascript plugin="ncs-web-template" src="umn_searchfield.js" />

<style type="text/css">
#main_wrapper{width:95%; max-width:1280px; min-width:720px}
</style>

<!-- STYLE SHEETS TO FIX THE EVIL IE -->
<!--[if IE 6]>
<style type="text/css" media="screen">
@import url("${resource(dir:'css',plugin:'ncs-web-template',file:'umn_IE6.css')}");
	</style>
	<![endif]-->
	<!--[if IE 7]>
	<style type="text/css" media="screen">
	@import url("${resource(dir:'css',plugin:'ncs-web-template',file:'umn_IE7.css')}");
	</style>
	  <![endif]-->

	  <g:layoutHead />
	  <g:javascript library="application" />
  </head>
  <body class="center">

    <!-- * Skip Links * -->
	<p id="skipLinks">
	  <a href="#main_nav">Main navigation</a> |
	  <a href="#maincontent">Main content</a>
	</p>

    <div id="header">

	  <!-- BEGNIN CAMPUS LINKS -->
	  <div id="campus_links">
		<p>Campuses: </p>
		<ul>
		  <li><a href="http://www.umn.edu">Twin Cities</a></li>
		  <li><a href="http://www.crk.umn.edu">Crookston</a></li>
		  <li><a href="http://www.d.umn.edu">Duluth</a></li>
		  <li><a href="http://www.morris.umn.edu">Morris</a></li>
		  <li><a href="http://www.r.umn.edu">Rochester</a></li>
		  <li><a href="http://www.umn.edu/campuses.php">Other Locations</a></li>
		</ul>
	  </div>
	  <!-- END CAMPUS LINKS -->


	  <!-- * BEGIN TEMPLATE HEADER (MAROON BAR)* -->
	  <div id="headerUofM">
		<div id="logo_uofm"><a href="http://www.umn.edu/">Go to the U of M home page</a></div>

		<!--BEGIN search div-->
		<div id="search_area">
		  <div id="search_nav"><a href="http://onestop.umn.edu/" id="btn_onestop">OneStop</a> <a href="https://www.myu.umn.edu/" id="btn_myu">myU</a></div>
		  <div class="search">
			<form action="http://google.umn.edu/search" method="get" name="gsearch" id="gsearch" title="Search U of M Web sites">
			  <label for="search_field">Search U of M Web sites</label>
			  <input type="text" id="search_field" name="q" value="Search U of M Web sites"  title="Search text"  />
			  <input class="search_btn" type="image" src="${resource(dir:'images',plugin:'ncs-web-template',file:'search_button.gif')}" alt="Submit Search" value="Search" />
			  <input name="client" value="searchumn" type="hidden" />
			  <input name="proxystylesheet" value="searchumn" type="hidden" />
			  <input name="output" value="xml_no_dtd" type="hidden" />
			</form>
		  </div>
		</div>
	  </div>
	  <!-- end "search" area -->
    </div>
    <!-- End search div -->

    <!--END UofM TEMPLATE HEADER-->
	<div id="spinner" class="spinner" style="display:none;">
	  <img src="${resource(dir:'images',plugin:'ncs-web-template',file:'spinner.gif')}" alt="Spinner" />
	</div>

	<!-- BEGIN Unit Graphic Header for home page -->
	<div class="main_head">
	  <h1 class="nopadding" id="nospace">
		<img src="${resource(dir:'images',plugin:'ncs-web-template',file:'ncs_header_960x95.jpg')}" alt="National Children's Study" width="960" height="95" />
	  </h1>
	</div>
	<!-- END Unit Graphic Header for home page -->

	<g:layoutBody />

    <!-- BEGIN OPTIONAL UNIT FOOTER -->
    <div class="grid_12" id="unit_footer2">
	  <ul class="unit_footer_links">
		<li>Address: 200 Oak <acronym class="acronym_border" title="Street Southeast">St SE</acronym>, Minneapolis, MN 55455-2008 Phone: 612-626-8160 Fax: 612-625-4363</li>
		<li><a href="mailto:${'info@ncs.umn.edu'}">Contact Health Studies</a></li>
	  </ul>
    </div>
    <!-- END OPTIONAL UNIT FOOTER -->

    <!-- BEGIN UofM FOOTER -->
    <div class="grid_7 alpha" id="footer_inner">
	  <ul class="copyright"><li>&copy; ${new Date().year + 1900} Regents of the University of Minnesota. All rights reserved.</li>
        <li>The University of Minnesota is an equal opportunity educator and employer</li>
        <li>Last modified on January 9, 2009</li></ul>
    </div>
    <div class="grid_5 omega" id="footer_right">
	  <ul class="footer_links">
        <li>Twin Cities Campus: </li>
        <li><a href="http://www1.umn.edu/pts/">Parking &amp; Transportation</a></li>
        <li><a href="http://www.umn.edu/twincities/maps/index.html">Maps &amp; Directions</a></li></ul>
	  <br class="clearabove" />
	  <ul class="footer_links"><li><a href="http://www.directory.umn.edu/">Directories</a></li>
        <li><a href="http://www.umn.edu/twincities/contact/">Contact U of M</a></li>
        <li><a href="http://www.privacy.umn.edu/">Privacy</a></li>
	  </ul>
	  <p>&nbsp;</p>
	  <br class="clearabove" />
    </div>
    <!-- END UofM FOOTER -->
  </body>
</html>
