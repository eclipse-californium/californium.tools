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

Use `mvn clean install` in the root directory to build the tools. It requires at least java 1.8 JDK. The standalone JAR will be created in the ./run/ directory.

The dependencies are available from the default Maven repositories.

e.g. run with `java -jar cf-client-*.jar`.

Note: For openjdk, openjfx must be installed separately. That seems to be sometimes broken. If you use openjdk-8 ensure, that openjfx is also a java-8 version. May be this guide helps to fix it [openjfx-8](https://github.com/JabRef/help.jabref.org/issues/204).

> sudo apt install openjfx=8u161-b12-1ubuntu2 libopenjfx-jni=8u161-b12-1ubuntu2 libopenjfx-java=8u161-b12-1ubuntu2
> sudo apt-mark hold openjfx libopenjfx-jni libopenjfx-java

If your using an other java version or the guide didn't work for you, try an other jdk distributions with jfx.

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

