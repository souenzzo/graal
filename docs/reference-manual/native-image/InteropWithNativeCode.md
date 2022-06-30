---
layout: docs
toc_group: native-interoperability
link_title: Interoperability with Native Code
permalink: /reference-manual/native-image/native-code-interoperability/
redirect_from: /$version/reference-manual/native-image/ImplementingNativeMethodsInJavaWithSVM/
---

# Interoperability with Native Code

You can use Native Image to convert Java code into a **native shared library** and call it from a native (C/C++) application just like any C function. 
There are two mechanisms for calling natively compiled Java methods:
**Native Image C API** and **JNI Invocation API**. 

- [JNI Invocation API](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html), an API to load the JVM into an arbitrary native application. The advantage of using JNI Invocation API is support for multiple, isolated execution environments within the same process. 
- [Native Image C API](C-API.md), an API specific to GraalVM Native Image. The advantage of using Native Image C API is that you can determine what your API will look like, but parameter and return types must be non-object types.

To build a native shared library, pass the command-line argument `--shared` to the `native-image` tool, as follows: 

```shell
native-image <class name> --shared
```

To build a native shared library from a JAR file, use the following syntax:
```shell
native-image -jar <jarfile> --shared
```

Learn how to implement native methods in Java with Native Image using [JNI Invocation API](JNIInvocationAPI.md).

Learn how to manage Java objects from C with [JNI Invocation API](C-API.md).

Read the [Embedding Truffle Languages](https://nirvdrum.com/2022/05/09/truffle-language-embedding.html) blog post by Kevin Menard where he compares both mechanisms in Native Image for exposing Java methods.

<!-- Follow a step-by-step guide to build a native shared library and learn some Native Image C API tips on practice: [Build a Native Shared Library](guides/build-native-shared-library.md). -->

