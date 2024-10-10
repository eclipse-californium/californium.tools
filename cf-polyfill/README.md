Cf-Polyfill
===========

!!! Not longer maintained !!!

This tool enables the creation of CoAP request using client-side JavaScript running in the Web browser.
On the client side, it provides a CoAPRequest object with an API similar to the XMLHttpRequest.
The CoAPRequest object forwards the request definition to a cross-proxy running on the clients machine using HTTP.
This proxy performs the CoAP request on behalf of the client and returns the response.

## Usage

The CoAP polyfill can be enabled by including the `coap.js` file hosted by the proxy.

	<script src="http://localhost:8080/coap.js" type="application/javascript"></script>

Simple CoAP GET request:

    var request = new CoAPRequest();
    request.open('GET', 'coap://localhost:5683', true);
    request.onload = function () {
      alert(request.responseText);
    };
    request.send();

CoAP requests can also be created using the jQuery API.

    $.get("coap://localhost:5683", function( data ) {
        alert(data);
    });

## Security

The current version of the polyfill should only be used for development purposes.
To simplify the exchange of Web applications using CoAP requests, the proxy accepts cross-origin requests.
This enables any Web site to create CoAP requests (if the proxy server is running on the clients machine).

The security can be improved by implementing an origin-based autorization process, where the user needs to allow the site to access a specific CoAP endpoint (device/service).

## Features

- [x] GET, POST, PUT and DELETE requsts
- [ ] Observer resources (using websockets)
- [ ] Access permission model
