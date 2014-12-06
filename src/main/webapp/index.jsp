<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri='http://java.sun.com/jsp/jstl/core' prefix='c'%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="WEB-INF/property.tld" prefix="rt" %>
<html>
	<head>
		<meta charset="utf-8">
		<title>Run</title>
		<link href="css/bootstrap.min.css" rel="stylesheet">
		<link href="css/styles.css" rel="stylesheet">
		<link href="css/bootstrap.vertical-tabs.min.css" rel="stylesheet">
		<script type="text/javascript" src="js/jquery-2.1.0.min.js"></script>
		<script type="text/javascript" src="js/script.js"></script>
		<script type="text/javascript" src="js/bootstrap.min.js"></script>
		<script type="text/javascript" src="js/jquery.cookie.js"></script>
	</head>
	<body>
		<nav class="navbar navbar-default" role="navigation">
			<div class="container-fluid">
				<div class="navbar-header">
					<button type="button" class="navbar-toggle collapsed" data-toggle="collapse"
						data-target="#main-navbar">
						<span class="sr-only">Toggle navigation</span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
					</button>
					<a id="logo" href="/"><img src="images/logo.jpg"></a>
					<a class="navbar-brand" href="/">Mongoose</a>
				</div>
				<div class="collapse navbar-collapse"
					id="main-navbar">
					<ul class="nav navbar-nav">
						<li class="active"><a href="/">Run<span class="sr-only">(current)</span></a></li>
						<li><a href="/charts">Charts</a></li>
					</ul>

					<ul class="nav navbar-nav navbar-right">
						<li><a href="about.html">About</a></li>
					</ul>
				</div>
			</div>
		</nav>

		<div class="content-wrapper">
			<div class="tabs-wrapper">
				<ul class="nav nav-tabs" role="presentation">
					<li class="active"><a href="#configuration" data-toggle="tab">Configuration</a></li>
					<c:forEach var="mode" items="${sessionScope.runmodes}">
						<c:set var="correctMode" value="${fn:replace(mode, '.', '_')}"/>
						<li><a href="#${correctMode}" data-toggle="tab">
							${mode}
							<span class="glyphicon glyphicon-remove" value="${correctMode}"></span>
						</a></li>
					</c:forEach>
				</ul>
			</div>

			<div class="tab-content">
				<div class="tab-pane active" id="configuration">
					<div id="menu">
						<div id="runmodes">
							<select>
								<option>standalone</option>
								<option>client</option>
								<option>server</option>
								<option>wsmock</option>
							</select>
							<button id="start" type="button">
								Start
							</button>
						</div>
						<ul class="folders">
							<li>
								<label for="properties">properties</label>
								<input type="checkbox" id="properties">
								<ul>
									<li>
										<label for="api">api</label>
										<input type="checkbox" id="api">
										<ul>
											<c:forEach var="files" items="${runTimeConfig.propertiesMap['api']}">
												<c:forEach var="file" items="${files.key}">
													<li class="file"><a href="#${file}">${file}</a></li>
												</c:forEach>
											</c:forEach>
										</ul>
									</li>
									<li>
										<label for="load">load</label>
										<input type="checkbox" id="load">
										<ul>
											<c:forEach var="files" items="${runTimeConfig.propertiesMap['load']}">
												<c:forEach var="file" items="${files.key}">
													<li class="file"><a href="#${file}">${file}</a></li>
												</c:forEach>
											</c:forEach>
										</ul>
									</li>
									<li>
										<label for="scenario">scenario</label>
										<input type="checkbox" id="scenario">
										<ul>
											<c:forEach var="files" items="${runTimeConfig.propertiesMap['scenario']}">
												<c:forEach var="file" items="${files.key}">
													<li class="file"><a href="#${file}">${file}</a></li>
												</c:forEach>
											</c:forEach>
										</ul>
									</li>
									<c:forEach var="files" items="${runTimeConfig.propertiesMap['properties']}">
										<c:forEach var="file" items="${files.key}">
											<li class="file"><a href="#${file}">${file}</a></li>
										</c:forEach>
									</c:forEach>
								</ul>
							</li>
						</ul>
					</div>

					<div id="main-content">
						<ol class="breadcrumb">
						</ol>

						<form id="main-form">
                            <input type="hidden" name="run.mode" id="run-mode" value="standalone">
							<div id="configuration-content">
								<c:forEach var="folders" items="${runTimeConfig.propertiesMap}">
									<c:forEach var="files" items="${folders.value}">
										<div id="${files.key}">
											<div class="property-labels">
												<c:forEach var="props" items="${files.value}">
													<label for="${props.key}">${props.value}</label>
												</c:forEach>
											</div>
											<div class="property-text">
												<c:forEach var="props" items="${files.value}">
													<input type="text" id="${props.key}" name="${props.key}">
													<br/>
												</c:forEach>
											</div>
										</div>
									</c:forEach>
								</c:forEach>
							</div>
						</form>
					</div>
				</div>
				<c:forEach var="mode" items="${sessionScope.runmodes}">
					<c:set var="correctMode" value="${fn:replace(mode, '.', '_')}"/>
					<div class="tab-pane table-pane" id="${correctMode}">
						<div class="left-side">
							<div class="menu-wrapper">
								<div class="col-xs-8">
									<ul class="nav nav-tabs tabs-left">
										<li class="active"><a href="#${correctMode}messages-csv" data-toggle="tab">messages.csv</a></li>
										<li><a href="#${correctMode}errors-log" data-toggle="tab">errors.log</a></li>
										<li><a href="#${correctMode}perf-avg-csv" data-toggle="tab">perf.avg.csv</a></li>
										<li><a href="#${correctMode}perf-sum-csv" data-toggle="tab">perf.sum.csv</a></li>
									</ul>
								</div>
							</div>
						</div>
						<div class="right-side">
							<c:if test="${empty sessionScope.stopped[mode]}">
								<button type="button" class="btn btn-default stop"><span>Stop</span></button>
							</c:if>
							<div class="log-wrapper">
								<div class="tab-content">
									<div class="tab-pane active" id="${correctMode}messages-csv">
										<table class="table">
											<thead>
												<tr>
													<th>Level</th>
													<th>LoggerName</th>
													<th>ThreadName</th>
													<th>TimeMillis</th>
													<th>Message</th>
												</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="btn btn-default clear">Clear</button>
									</div>
									<div class="tab-pane" id="${correctMode}errors-log">
										<table class="table">
											<thead>
												<tr>
													<th>Level</th>
													<th>LoggerName</th>
													<th>ThreadName</th>
													<th>TimeMillis</th>
													<th>Message</th>
												</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="btn btn-default clear">Clear</button>
									</div>
									<div class="tab-pane" id="${correctMode}perf-avg-csv">
										<table class="table">
											<thead>
												<tr>
													<th>Level</th>
													<th>LoggerName</th>
													<th>ThreadName</th>
													<th>TimeMillis</th>
													<th>Message</th>
												</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="btn btn-default clear">Clear</button>
									</div>
									<div class="tab-pane" id="${correctMode}perf-sum-csv">
										<table class="table">
											<thead>
												<tr>
													<th>Level</th>
													<th>LoggerName</th>
													<th>ThreadName</th>
													<th>TimeMillis</th>
													<th>Message</th>
												</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="btn btn-default clear">Clear</button>
									</div>
								</div>
							</div>
						</div>
					</div>
				</c:forEach>
			</div>
		</div>
	</body>
</html>