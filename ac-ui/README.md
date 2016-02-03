Actinium UI
===========

Management user interface for the Actinium App Server.
The Web application communicates with Actinium using CoAP requests via the [Cf-Polyfill](https://github.com/eclipse/californium.tools/tree/master/cf-polyfill) proxy. 

###[Start Actinium UI](http://ynh.github.io/actinium-ui/)

## Build Application

1. Install Node.js and NPM (Node.js is only used to compile the javascript web application)

2. Install project dependencies

        npm install

3. Install bower

        npm install -g bower

4. Download the projects dependencies
        
        bower install

5. Build project (the built project will be stored in the dist folder)

        grunt build
