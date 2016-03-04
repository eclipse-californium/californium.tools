Actinium UI
===========

Management user interface for the Actinium App Server.
The Web application communicates with Actinium using CoAP requests via the [Cf-Polyfill](../cf-polyfill) proxy. 

###[Start Actinium UI](http://eclipse.github.io/californium.tools/ac-ui/)

## Build Application

1. Install *Node.js*, *NPM*, and *Grunt* (Node.js is only used to compile the JavaScript Web application)
> Get *Grunt* with `npm install -g grunt-cli` and `npm install -g grunt-bower`

2. Install project dependencies

        npm install

3. Install bower
> In case of problems try `npm install --save-dev bower`

        npm install -g bower

4. Download the projects dependencies
        
        bower install

5. Build project (the built project will be stored in the `dist` folder)

        grunt build
