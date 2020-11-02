Californium (Cf) Tools
======================

These are CoAP tools based on the
[Californium (Cf)](https://github.com/eclipse/californium) CoAP framework.

* cf-browser - javaFx GUI based coap client
* cf-client - cli based coap client
* cf-coapbench - benchmark (currently not maintained/tested, use californium/demo-apps/cf-extplugtest-client instead).
* cf-polyfill - WebServer emits coap requests (currently not maintained/tested)
* cf-rd - coap resource directory
* cf-server - coap server

Maven
-----

Use `mvn clean install` in the root directory to build the tools. It requires at least a Java 8 SDK. The standalone JARs will be created in the `run` folder.

e.g. run with `java -jar cf-client-*.jar`.


Note: Building *cf-browser* using Java 8 requires the JavaFX libraries to be available on the system class path.
Oracle's JDK includes the JavaFX libraries by default. OpenJDK does not and OpenJFX must be installed separately.

For instructions regarding the usage of OpenJFX on Java 11 and later, refer to the [OpenJFX documentation](https://openjfx.io/).
On OpenJDK 8 the availability of OpenJFX depends on the linux distribution you are using. Later versions of Ubuntu seems to not longer provide OpenJFX for OpenJDK 8. Maybe the workaround described in this [GitHub issue](https://github.com/JabRef/help.jabref.org/issues/204)
works for you.

[cf-browser - README](cf-browser/README.md)

Eclipse
-------

The project also includes the project files for Eclipse. Make sure to have the
following before importing the Californium (Cf) projects:

* [Eclipse EGit](http://www.eclipse.org/egit/)
* [m2e - Maven Integration for Eclipse](http://www.eclipse.org/m2e/)
* UTF-8 workspace text file encoding (Preferences &raquo; General &raquo; Workspace)

Then choose *[Import... &raquo; Git &raquo; Projects from Git &raquo; Local]*
to import `californium.tools` into Eclipse.

e.g. run `org.eclipse.californium.tools.GUIClientFX` as Java application.

Usage
-----

e.g. java -jar cf-browser-*.jar

