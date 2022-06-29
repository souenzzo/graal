---
layout: docs
toc_group: dynamic-features
link_title: URL Protocols
permalink: /reference-manual/native-image/dynamic-features/URLProtocols/
redirect_from: /$version/reference-manual/native-image/URLProtocols/
---

# URL Protocols in Native Image

URL Protocols in Native Image can be divided into three classes:

* supported and enabled by default
* supported and disabled by default
* HTTPS support

URL Protocols that are supported and enabled by default will be included into every generated native image.
Currently, `file` and `resource` are the only supported URL protocols, enabled by default.

There are URL Protocols that are supported but not enabled by default when building a native image.
They must be enabled during at build time by adding `--enable-url-protocols=<protocols>` to the `native-image` builder.
The option accepts a list of comma-separated protocols.

The rationale behind enabling protocols on-demand is that you can start with a minimal image and add features as you need them.
This way your image will only include the features you use, which helps keep the overall size small.
Currently `http` and `https` are the only URL protocols that are supported and can be enabled on demand.
They can be enabled using the `--enable-http` and `--enable-https` options.

## HTTPS Support
Support for the `https` URL protocol relies on the Java Cryptography Architecture (JCA) framework.
Thus enabling `https` will add to the generated image the code required by the JCA, including statically linking native libraries that the JCA may depend on.
See the [documentation on security services](JCASecurityServices.md) for more details.

No other URL protocols are currently tested.
They can still be enabled using `--enable-url-protocols=<protocols>`, however they might not work as expected.

### Related Documentation

- [JCA Security Services in Native Image](JCASecurityServices.md)