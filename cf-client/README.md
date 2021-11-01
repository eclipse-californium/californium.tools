# Cf-Client

A command line application that uses the Californium libraries to act as `coap-client` to discover and interact with `coap-servers`.

## Download pre-build jar

Prebuild jars are available from the Eclipse Repository.

[Eclipse Repository - cf-client-3.0.0-RC2.jar](https://repo.eclipse.org/content/repositories/californium-releases/org/eclipse/californium/cf-client/3.0.0-RC2/cf-client-3.0.0-RC2.jar)

Additional experimental TCP/TLS support (copy it to the same folder as the cf-client-3.0.0-RC2.jar): 
[Eclipse Repository - cf-cli-tcp-netty-3.0.0-RC2.jar](https://repo.eclipse.org/content/repositories/californium-releases/org/eclipse/californium/cf-cli-tcp-netty/3.0.0-RC2/cf-cli-tcp-netty-3.0.0-RC2.jar)

## Run

```sh
java -jar cf-client-3.0.0-RC2.jar
```

## Arguments

```sh
java -jar cf-client-3.0.0-RC2.jar -h

Usage: ConsoleClient [-hvV] [--help-auth] [--help-cipher] [--loop] [--[no-]
                     subject-verification] [--payload-format] [-C=FILE]
                     [--cid-length=<cidLength>]
                     [--dtls-auto-handshake=<dtlsAutoHandshake>]
                     [--extended-method=<extendedMethod>] [-i=<identity>]
                     [--local-port=<localPort>] [-m=<method>] [--mtu=<mtu>]
                     [--proxy=<proxy>] [--psk-index=<pskIndex>]
                     [--psk-store=<pskStore>] [--record-size=<recordSizeLimit>]
                     [--tag=<tag>] [-a=<authenticationModes>[:
                     <authenticationModes>...]]... [--cipher=<cipherSuites>[:
                     <cipherSuites>...]]... [--anonymous | [[-c=<certificate>]
                     [--private-key=<privateKey>]]] [-t=<trusts>
                     [-t=<trusts>]... | --trust-all] [-s=<text> |
                     --secrethex=<hex> | --secret64=<base64>] [--json | --cbor
                     | --xml | --text | --octets | --ctype=TYPE]
                     [--payload=<text> | --payloadhex=<hex> |
                     --payload64=<base64> | --payload-random=<size> |
                     --payload-file=<filename>] [--con | --non] [URI]
      [URI]                  destination URI. Default californium.
                               eclipseprojects.io
  -a, --auth=<authenticationModes>[:<authenticationModes>...]
                             use authentikation modes. '--help-auth' to list
                               available authentication modes.
      --anonymous            anonymous, no certificate.
  -c, --cert=<certificate>   certificate store. Format
                               keystore#hexstorepwd#hexkeypwd#alias or keystore.
                               pem
  -C, --config=FILE          configuration file. Default Californium3.
                               properties.
      --cbor                 use cbor payload.
      --cid-length=<cidLength>
                             Use cid with length. 0 to support cid only without
                               using it.
      --cipher=<cipherSuites>[:<cipherSuites>...]
                             use ciphersuites. '--help-cipher' to list
                               available cipher suites.
      --con                  send request confirmed.
      --ctype=TYPE           use content type for payload.
      --dtls-auto-handshake=<dtlsAutoHandshake>
                             enable dtls auto-handshake with provided timeout.
                               Value in format time[unit], e.g. the recommended
                               value of "30[s]". Or time|unit, e.g. 30s.
                               Default disabled.
      --extended-method=<extendedMethod>
                             Extended method.
  -h, --help                 display a help message
      --help-auth            display a help message for authentication modes
      --help-cipher          display a help message for cipher suites
  -i, --identity=<identity>  PSK identity
      --json                 use json payload.
      --local-port=<localPort>
                             local porty. Default ephemeral port.
      --loop                 keep console after request.
  -m, --method=<method>      use method. GET|PUT|POST|DELETE|FETCH|PATCH|IPATCH.
      --mtu=<mtu>            MTU.
      --[no-]subject-verification
                             enable/disable verification of server
                               certificate's subject.
      --non                  send request non-confirmed.
      --octets               use octet stream payload.
      --payload=<text>       payload, utf8
      --payload-file=<filename>
                             payload from file
      --payload-format       apply format to payload.
      --payload-random=<size>
                             random payload size
      --payload64=<base64>   payload, base64
      --payloadhex=<hex>     payload, hexadecimal
      --private-key=<privateKey>
                             private key store. Format
                               keystore#hexstorepwd#hexkeypwd#alias or keystore.
                               pem
      --proxy=<proxy>        use proxy. <address>:<port>[:<scheme>]. Default
                               env-value of COAP_PROXY.
      --psk-index=<pskIndex> Index of identity in PSK store. Starts at 0.
      --psk-store=<pskStore> PSK store. Lines format: identity=secretkey (in
                               base64).
      --record-size=<recordSizeLimit>
                             record size limit.
  -s, --secret=<text>        PSK secret, UTF-8
      --secret64=<base64>    PSK secret, base64
      --secrethex=<hex>      PSK secret, hexadecimal
  -t, --trusts=<trusts>      trusted certificates. Format
                               keystore#hexstorepwd#alias or truststore.pem
      --tag=<tag>            use logging tag.
      --text                 use plain-text payload.
      --trust-all            trust all valid certificates.
  -v, --[no-]verbose         verbose
  -V, --version              display version info
      --xml                  use xml payload.
```
