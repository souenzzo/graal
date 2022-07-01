---
layout: docs
toc_group: build-overview
link_title: Static Linking
permalink: /reference-manual/native-image/overview/StaticLinking/
---

# Static Linking
 
GraalVM Native Image by default builds dynamically linked binaries: at build time it loads your application classes and interfaces and hooks them together in a process of dynamic linking.

However, with GraalVM Native Image, you can also create static or mostly static images, depending on the purpose.

**Static native images** are statically linked binaries which can be used without any additional library dependencies.
This makes them easier to distribute and to deploy on slim or distroless container images.
They are created by statically linking against [musl-libc](https://musl.libc.org/), a lightweight `libc` implementation.

**Mostly static native images** statically link against all libraries except `libc`.
This approach is ideal for deploying such native images on distroless container images.
Note that it currently only works when linking against `glibc`.

## Prerequisites

- Linux AMD64 operating system
- GraalVM distribution for Java 11 with Native Image support
- A 64-bit `musl` toolchain, `make`, and `configure`
- The latest `zlib` library

You should get the `musl` toolchain first, and then compile and install `zlib` into the toolchain.

1. Download the `musl` toolchain from [musl.cc](https://musl.cc/). [This one](http://more.musl.cc/10/x86_64-linux-musl/x86_64-linux-musl-native.tgz) is recommended. Extract the toolchain to a directory of your choice. This directory will be referred as `$TOOLCHAIN_DIR`.
2. Download the latest `zlib` library sources from [here](https://zlib.net/) and extract them. This guide uses `zlib-1.2.11`.
3. Set the following environment variable:
    ```bash
    CC=$TOOLCHAIN_DIR/bin/gcc
    ```
4. Change into the `zlib` directory, and then run the following commands to compile and install `zlib` into the toolchain:
    ```bash
    ./configure --prefix=$TOOLCHAIN_DIR --static
    make
    make install
    ```

## Build a Static Native Image

1. First, ensure `$TOOLCHAIN_DIR/bin` is present on your `PATH` variable.
    To verify this, run:
    ```bash
    x86_64-linux-musl-gcc
    ```
    You should get a similar output printed:
    ```bash
    x86_64-linux-musl-gcc: fatal error: no input files
    compilation terminated.
    ```

2. Build a static native image by using this command:
    ```shell
    native-image --static --libc=musl Class
    ```

## Build a Mostly Static Native Image

You can build a mostly static native image which statically links everything except `libc`.
Statically linking all your libraries except `glibc` ensures your application has all the libraries it needs to run on any Linux `glibc`-based distribution.

To build a mostly static native image, use this command:
```shell
native-image -H:+StaticExecutableWithDynamicLibC Class
```

> Note: This currently only works for `glibc`.

### Further Reading

* Run an interactive lab and practice creating small Distroless containers in Oracle Linux environment with GraalVM Native Image and a simple a Spring boot application: [GraalVM Native Image, Spring and Containerisation](https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68).
