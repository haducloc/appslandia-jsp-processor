# JSP Processor

 - Create JSP layout (master layout, non-master layout) using layout directives
 - Translates JSP master layouts, JSP pages, JSP fragments with layout directives to pure JSP pages, JSP fragments
 - Multi master layout supported
 - Easy, flexible, and fastest way to achieve JSP master layouts 
 
## Layout Directives

### Body Directive 
```
<body>
<!-- @doBody -->
</body>
```
### Section Directives
#### Use Section Holders
```
<!-- @header -->
<!-- @inc_js? -->
```
#### Define Sections
```
<!-- @header begin -->
<h2>This is a page header</h2>
<!-- @header end -->
```
```
<!-- @inc_js begin -->
<script type="text/javascript">
</script>
<!-- @inc_js end -->
```

### Variable Directives
#### Define variables
```
<!-- @variables
    __layout=layout1
    pageTitle= ${messages.login_title}
 -->
```
```
<!-- @variable __layout=layout1 -->
<!-- @variable pageTitle=${messages.login_title} -->
```
```
<!-- @variables: shared_variables.properties -->
```
#### Use Variables
```
<head>
<title>@{pageTitle}  OR  @(pageTitle)</title>
</head>
```

## JSP Structure
```
+ WebContent
  + WEB-INF
    + __config
      - layout1.jsp // Page Layout
      - layout2.jsp
      - shared_variables.properties
    + __jsp
      - login.jsp // Page body
      - registration.jsp
    + jsp
      - login.jsp // Page generated by __config/layout1.jsp & __jsp/login.jsp
      - login_inc.jsp // Body generated by __jsp/login.jsp
      - registration.jsp
      - registration_inc.jsp
```
## Installation

```XML
<build>
	<plugins>
		<plugin>
			<groupId>com.appslandia</groupId>
			<artifactId>appslandia-jsp-processor</artifactId>
			<version>{LATEST_VERSION}</version>
			<executions>
				<execution>
					<id>process-jsp</id>
					<phase>generate-sources</phase>
					<goals>
						<goal>process</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
	</plugins>
</build>
```

## Questions?
Please feel free to contact me if you have any questions or comments.
Email: haducloc13@gmail.com

## License
This code is distributed under the terms and conditions of the [MIT license](LICENSE).