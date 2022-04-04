# JMeter CoAP Sampler

## Build

```sh
mvn clean install
```
## Install

Copy "target/cf-jmeter-plugin-?.?.?.jar" into folder `<jmeter>/lib/ext`.
Please remove other, previous copied californium libraries from `<jmeter>/lib`.

## Usage

Start jmeter, e.g. `<jmeter>/bin/jmeter.sh`.
Your `testplan` requires a `Thread-Group`, add or select that. 
Then open menu "Edit -> Add -> Sampler -> Java Request". 
Select in the opened dialog "org.eclipse.californium.tools.jmeter.plugin.CoapSampler".
Adjust the configuration according your needs.

-  **CoapSampler** supports plain coap and PSK based encrypted coaps

**Note:** to run jmeter with other plugins (eg.g grovy) requires a huge heap size. Use

```sh
HEAP="-Xms1g -Xmx4g -XX:MaxMetaspaceSize=2g"
```

to prevent from Out Of Memory errors.

**Note:** the general specifications are
- [RFC 7252, CoAP](https://tools.ietf.org/html/rfc7252)
- [RFC 6347, DTLS 1.2](https://tools.ietf.org/html/rfc6347)
- [RFC 4279, PSK - (D)TLS 1.2](https://tools.ietf.org/html/rfc4279)

**Note:** the general documentations of the hono-coap-adapter are
- [Hono - User Guide - CoAP Adapter](https://www.eclipse.org/hono/docs/user-guide/coap-adapter/)

**Note:** if the URL contains "hono-ttd=<time>", please ensure, that this time is between 5-25 (seconds).
 `hono-ttd` defines the response timeout for the cloud internal messaging. That must be smaller than the coap-response timeout.
 Though for that coap-response timeout a value not larger than 30s is recommended, the `hono-ttd` should be kept in the range above.
 
```sh
device-client       Hono Coap-Adapter       Cloud-Messaging
+   ---- coap-request --->
|                           +   ---- (kafka) message --->
|                           |
|-- response-timeout        |-- hono-ttd, messaging timeout
|                           |
|                           +   <--- (kafka) message ----
+  <--- coap-response ----
```

Larger values may work, but may cause random communication issues. Too short values may cause more empty responses.

**Note:** if the URL contains "QoS", please remove that! QoS is not supported in the URL for CoAP, use the message-type CON (QoS1) or NON (QoS0) instead.

## Parameter

-  **SERVER_ENDPOINT** Destination URL, e.g. coaps://californium.eclipseprojects.io/telemetry
-  **METHOD** CoAP REST method, GET, PUT, POST or DELETET
-  **MESSAGE_PAYLOAD** message payload for PUT and POST, ignored for GET and DELETE
-  **CONTENT_TYPE** content type of payload
-  **ACCEPT_TYPE** accept type for response
-  **MESSAGE_TYPE** CON (for confirmed requests, retransmitted by CoAP, if no ACK/RST nor response is received), NON (for non-confirmed requests, not retransmitted by CoAP). If NON is used, ensure that the RETRY_503 and RETRY_EMPTY are setup according the expectations.
-  **IDENTITY** PSK identity (must be different for each simulated device!)
-  **SECRET_KEY64** PSK secret key in base64
-  **CONNECTION_TIME_OUT** request-response timeout in seconds, recommended range 5-30s - NOTE: if `hono-tdd` is used in url, ensure that this value is larger than `hono-ttd`. 
-  **CONNECTION_IDLE_TIME** dtls idle timeout in seconds. If expired dtls connector will be destroyed. That enables to reuse a dtls connection, if the request follow fast, but triggers a new dtls handshake after a idle time. This value should be larger than used delays between requests.
-  **AUTO_RESUMPTION_TIME_OUT** dtls idle timeout. If expired dtls connector will be destroyed. That enables to reuse a dtls connection, if the request follow fast, but triggers a new dtls handshake after a idle time.
-  **RECONNECT** reconnect dtls connection and retry request, if no ACK nor response is received.
-  **QUIET_MILLIS** quiet range in milliseconds. Add quiet period between last response and next request. e.g. "1000..6000" adds a quiet period range with random value between 1000 and 6000 milliseconds.
-  **RETRY_503** number of retries for 5.03 responses (hono back-pressure)
-  **RETRY_EMPTY** number of retries for 2.04 responses with empty payload (backend hick-up)
-  **EMPTY_AS_ERROR** report responses with empty payload as error. If hono-ttd is used in the url and the response doesn't contain payload, this indicates an error depending on the application's definition.

