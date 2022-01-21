Following [How to use logging](https://plugins.jetbrains.com/docs/teamcity/plugin-development-faq.html#How+to+use+logging) 
those are the categories and handlers for the plugin (at the moment *FinishedBuildListener*). *INFO* goes to *elastic-timeout-plugin.log* and the default *teamcity-server.log*
(assuming the *ROLL* appender wasn't changed from the default) while *DEBUG* just goes to *elastic-timeout-plugin.log*.

Existing *teamcity-server-log4j.xml* can be updated with appender and category as shown in the same named file here.
*debug-elastic-timeout-plugin.xml* is based on any of the given/existing Logging Presets, just got added the appender and category for the plugin.