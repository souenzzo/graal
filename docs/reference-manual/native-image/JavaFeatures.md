---
layout: docs
toc_group: java-features
link_title: Java Features
permalink: /reference-manual/native-image/java-features/
---

# Java Features in Native Image

When you build a native image, it only includes the reachable elements starting from your application entry point, its dependent libraries, and the JDK classes discovered through a static analysis. 
However, the reachability of some elements may not be discoverable due to Java’s dynamic features including reflection, resource access, etc. 
If an element is not reachable, it will not be included in the generated binary and this can lead to run time failures.

Thus, some Java features may require special "treatment" such as a command line option or provisioning metadata to be compatible with ahead-of-time compilation using Native Image. 

The reference information here explains how Native Image handles some Java features.

- [Accessing Resources](Resources.md)
- [Certificate Management](CertificateManagement.md)
- [Dynamic Proxy](DynamicProxy.md)
- [Java Native Interface (JNI)](JNI.md)
- [JCA Security Services](JCASecurityServices.md)
- [Logging](Logging.md)
- [Reflection](Reflection.md)
- [URL Protocols](URLProtocols.md)