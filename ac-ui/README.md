Actinium UI
===========

Management user interface for the Actinium App Server.
The Web application communicates with Actinium using CoAP requests via the [Cf-Polyfill](../cf-polyfill) proxy. 

###[Start Actinium UI](http://eclipse.github.io/californium.tools/ac-ui/)

## Build Application

1. Install *Node.js* and *NPM* (Node.js is only used to compile the JavaScript Web application)
> Get *Grunt* with `npm install -g grunt-cli` and `npm install -g grunt-bower`

2. Install *Bower* and *Grunt*
> In case of problems try `npm install --save-dev bower`

        npm install -g bower
        npm install -g grunt-cli
        npm install -g grunt-bower

3. Download toolchain dependencies

        npm install

4. Download project dependencies
        
        bower install

5. Build project (the built project will be stored in the `dist` folder)

        grunt build
