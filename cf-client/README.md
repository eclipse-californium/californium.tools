# Cf-Client

A command line application that uses the Californium libraries to act as `coap-client` to discover and interact with `coap-servers`.

## Run

```sh
java -jar cf-client-2.5.0.jar
```

## Arguments

```sh
java -jar cf-client-2.5.0.jar -h

Usage: ConsoleClient [-hvV] [--help-auth] [--help-cipher] [--loop]
                     [--payload-format] [--cid-length=<cidLength>]
                     [--extended-method=<extendedMethod>] [-i=<identity>]
                     [--local-port=<localPort>] [-m=<method>] [--mtu=<mtu>]
                     [-N=FILE] [--proxy=<proxy>] [--psk-index=<pskIndex>]
                     [--psk-store=<pskStore>] [--record-size=<recordSizeLimit>]
                     [-a=<authenticationModes>[:<authenticationModes>...]]...
                     [--cipher=<cipherSuites>[:<cipherSuites>...]]...
                     [-c=<credentials> | --anonymous] [-t=<trusts>
                     [-t=<trusts>]... | --trust-all] [-s=<text> |
                     --secrethex=<hex> | --secret64=<base64>] [--json | --cbor
                     | --xml | --text | --octets | --ctype=TYPE]
                     [--payload=<text> | --payloadhex=<hex> |
                     --payload64=<base64> | --payload-file=<file>] [--con |
                     --non] [URI]
      [URI]                  destination URI. Default californium.eclipse.org
  -a, --auth=<authenticationModes>[:<authenticationModes>...]
                             use authentikation modes. '--help-auth' to list
                               available authentication modes.
      --anonymous            anonymous, no certificate.
  -c, --cert=<credentials>   certificate store. Format
                               keystore#hexstorepwd#hexkeypwd#alias or keystore.
                               pem
      --cbor                 use cbor payload.
      --cid-length=<cidLength>
                             Use cid with length. 0 to support cid only without
                               using it.
      --cipher=<cipherSuites>[:<cipherSuites>...]
                             use ciphersuites. '--help-cipher' to list
                               available cipher suites.
      --con                  send request confirmed.
      --ctype=TYPE           use content type for payload.
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
  -N, --netconfig=FILE       network config file. Default Californium.
                               properties.
      --non                  send request non-confirmed.
      --octets               use octet stream payload.
      --payload=<text>       payload, utf8
      --payload-file=<file>  payload from file
      --payload-format       apply format to payload.
      --payload64=<base64>   payload, base64
      --payloadhex=<hex>     payload, hexadecimal
      --proxy=<proxy>        use proxy. <address>:<port>[:<scheme>]. Default
                               env-value of COAP_PROXY.
      --psk-index=<pskIndex> Index of identity in PSK store. Starts at 0.
      --psk-store=<pskStore> PSK store. Lines format: identity=secretkey (in
                               base64).
      --record-size=<recordSizeLimit>
                             record size limit.
  -s, --secret=<text>        PSK secret, utf8
      --secret64=<base64>    PSK secret, base64
      --secrethex=<hex>      PSK secret, hexadecimal
  -t, --trusts=<trusts>      trusted certificates. Format
                               keystore#hexstorepwd#alias or truststore.pem
      --text                 use plain-text payload.
      --trust-all            trust all valid certificates.
  -v, --[no-]verbose         verbose
  -V, --version              display version info
      --xml                  use xml payload.
```
