---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Creating Heap Dumps
permalink: /reference-manual/native-image/debugging-and-diagnostics/HeapDumps/
redirect_from: /$version/reference-manual/native-image/NativeImageHeapdump/
---

# Creating Heap Dumps

With GraalVM you can create a heap dump of a running executable to monitor its execution. Just like any other Java heap dump, it can be opened with the [VisualVM](../../../tools/visualvm.md) tool.

A executable created by the `native-image` tool does not implement JVMTI agent, so it is not possible to trigger a heap dump using conventional tools such as _VisualVM_ or _jmap_.
However, you can build a native executable so that it dumps its heap in three ways:

1. The initial heap of a native executable can be dumped using the `-XX:+DumpHeapAndExit` command-line option.
2. The native executable can create a heap dump when it receives a `USR1` signal (other supported signals are `QUIT/BREAK` for stack dumps and `USR2` to dump runtime compilation information). The command-line option to use is `-H:+AllowVMInspection`.
3. You can write a method that will create a heap dump at certain points in the lifetime of your application. For example, when certain conditions are met while running a native executable, your application code can trigger a heap dump. A dedicated [`org.graalvm.nativeimage.VMRuntime#dumpHeap`](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/VMInspection.java) API exists for this purpose.

Proceed to the [Create a Heap Dump from a Native Executable](guides/create-heap-dump-from-native-executable.md) to find examples for each approach.

>Note: Creating heap dumps is not available on the Microsoft Windows platform.

### Further Reading

* [Create a Heap Dump from a Native Executable](guides/create-heap-dump-from-native-executable.md)
