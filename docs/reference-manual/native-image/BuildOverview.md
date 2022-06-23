---
layout: docs
toc_group: build-overview
link_title: Build Overview
permalink: /reference-manual/native-image/overview/Build-Overview/
redirect_from: /$version/reference-manual/native-image/Build-Overview/
---

# Native Image Build Overview

The syntax of the `native-image` command is:

- `native-image [options] <mainclass> [imagename] [options]` to build an image from `<mainclass>` class in the current working directory. Classpath may optionally be provided with the `-cp <classpath>` option where `<classpath>` is a colon-separated (on Windows, semicolon-separated) list of paths to directories and jars.
- `native-image [options] -jar jarfile [imagename] [options]` to build an image from a JAR file.
- `native-image [options] -m <module>/<mainClass> [imagename] [options]` to build an image from a Java module.

Native Image supports a wide range of options to configure the `native-image` tool. 
The options passed to `native-image` are evaluated left-to-right.

The options fall into three categories:
 - [Image generation options](BuildOptions.md#image-generation-options) - for the full list, run `native-image --help`
 - [Macro options](BuildOptions.md#macro-options)
 - [Non-standard options](BuildOptions.md#non-standard-options) - subject to change through a deprecation cycle, run `native-image --help-extra` for the full list.

Find a complete list of options for the `native-image` builder [here](BuildOptions.md).

For more native image build tweaks and how to properly configure the native image build, see [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation).

Native Image will output the progress and various statistics during the image build. To learn more about the output, and the different image build phases, see [Build Output](BuildOutput.md).

With Native Image you can build static or mostly static images, ideal for deploying in containers. Learn more [here](StaticImages.md).

If you are new to GraalVM Native Image or have with little experience using it, we recommend check the [Native Image Programming Model](ProgrammingModel.md) to better understand some key aspects before going deeper.