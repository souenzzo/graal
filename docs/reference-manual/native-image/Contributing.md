---
layout: docs
toc_group: contributing
link_title: For Contributors
permalink: /reference-manual/native-image/contributing/
---

# For Native Image Contributors

[GraalVM](https://github.com/oracle/graal/) is an open source project, so is [Substrate VM](https://github.com/oracle/graal/tree/master/substratevm) - a codename behind the Native Image technology.
We welcome contributors to the core!

There are two common ways to collaborate:

- By submitting [GitHub issues](https://github.com/oracle/graal/issues) for bug reports, questions, or requests for enhancements.
- By opening a [GitHub pull request](https://github.com/oracle/graal/pulls).

<!-- If you consider to contribute some changes to Native Image core, there is a ruleset grown over time and proven to be useful. See [Native Image Code Style](CodeStyle.md) to ensure the same standards of quality. -->

If you consider to contribute some changes to Native Image core, ensure you obey to the standards of quality that grown over time and proven to be useful. See [Native Image Code Style](CodeStyle.md).

There are some expert level options that a Native Image developer may find useful or needed, for example, the option to dump graphs of the `native-image` builder or enable assertions at image run time. This information can be found in [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md).

Finally, if you would like to ensure complete compatibility of your library with Native Image, consider contributing your library metadata to the [GraalVM Reachability Metadata Repository]((https://github.com/oracle/graalvm-reachability-metadata). Follow [contributing rules]((https://github.com/oracle/graalvm-reachability-metadata/CONTRIBUTING.md) for this repository. Using this open source repository, users can share the burden of maintaining metadata for third-party dependencies.

<!-- 
Finally, if you would like to include your library to the [GraalVM Reachability Metadata Repository]((https://github.com/oracle/graalvm-reachability-metadata), [follow the checklist]((https://github.com/oracle/graalvm-reachability-metadata/CONTRIBUTING.md) for adding your library metadata to this repository.  -->