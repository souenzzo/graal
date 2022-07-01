---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Generating Heap Dumps
permalink: /reference-manual/native-image/debugging-and-diagnostics/HeapDumps/
redirect_from: /$version/reference-manual/native-image/NativeImageHeapdump/
---

# Generating Heap Dumps from Native Images

# Creating Heap Dumps

With GraalVM Enterprise Edition, you can create heap dumps of Native Image processes to examine the heap contents and monitor the execution.

As a prerequisite, images must be built with GraalVM Enterprise Native Image using the `-H:+AllowVMInspection` option.
With this option enabled, there are different ways to create heap dumps, which can then be opened with [VisualVM](../../tools/visualvm.md) like any other Java heap dump.
As outlined in the following, [the initial heap of a native image can be dumped with `-XX:+DumpHeapAndExit`](#dump-the-initial-heap-of-a-native-image).
It is also possible to [create heap dumps sending `USR1` and other signals](#handle-sigusr1-signal).
Furthermore, [heap dumps can also be created programmatically](#create-a-heap-dump-from-within-a-java-application) through the [`org.graalvm.nativeimage.VMRuntime#dumpHeap`](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/VMInspection.java) API.

Note that GraalVM Native Image does not implement the JVMTI agent and thus, it is not possible to trigger heap dump creation using tools like _VisualVM_ or _jmap_.

## Dump the Initial Heap of a Native Image

The `-XX:+DumpHeapAndExit` option allows users to dump the initial heap of a native image.
This can be useful to understand which objects ended up in the heap as part of the Native Image build process. 
For a HelloWorld example, the option can be used as follows:

```shell
$GRAALVM_HOME/bin/native-image HelloWorld -H:+AllowVMInspection
./helloworld -XX:+DumpHeapAndExit
Heap dump created at '/path/to/helloworld.hprof'.
```

## Handle SIGUSR1 Signal
The following Java example is a simple multi-threaded application that runs for 60 seconds.
There is enough time to get its PID and send the SIGUSR1 signal which will create a heap dump in the application's working directory.
Save the following code as _SVMHeapDump.java_ file on your disk:
```java
import java.text.DateFormat;
import java.util.Date;

public class SVMHeapDump extends Thread {
    static int i = 0;
    static int runs = 60;
    static int sleepTime = 1000;
    @Override
    public void run() {
        System.out.println(DateFormat.getDateTimeInstance().format(new Date()) + ": Thread started, it will run for " + runs + " seconds");
        while (i < runs){
            System.out.println("Sleeping for " + (runs-i) + " seconds." );
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ie){
                System.out.println("Sleep interrupted.");
            }
            i++;
        }
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        StringBuffer sb1 = new StringBuffer(100);
        sb1.append(DateFormat.getDateTimeInstance().format(new Date()));
        sb1.append(": Hello GraalVM native image developer! \nGet PID of this process: ");
        sb1.append("'ps -C svmheapdump -o pid= '\n");
        sb1.append("then send it signal: ");
        sb1.append("'kill -SIGUSR1 <pid_printed_above>' \n");
        sb1.append("to get heap dump created in the working directory.\n");
        sb1.append("Starting thread!");
        System.out.println(sb1);
        SVMHeapDump t = new SVMHeapDump();
        t.start();
        while (t.isAlive()) {
            t.join(0);
        }
        sb1 = new StringBuffer(100);
        sb1.append(DateFormat.getDateTimeInstance().format(new Date()));
        sb1.append(": Thread finished after: ");
        sb1.append(i);
        sb1.append(" iterations.");
        System.out.println(sb1);
    }
}
```

Compile SVMHeapDump.java as following:
```shell
$GRAALVM_HOME/bin/javac SVMHeapDump.java
```
If you run it on `java`, you will see that it runs for 60 seconds and then finishes.

Build a native executable and provide the `-H:+AllowVMInspection` option for the builder:
```shell
$GRAALVM_HOME/bin/native-image SVMHeapDump -H:+AllowVMInspection
```

This way the native executable will accept SIGUSR1 signal to produce a heap dump.

The `native-image` builder analyzes the existing `SVMHeapDump.class` and creates from it an executable file.
When the command completes, `svmheapdump` is created in the current directory.

Run the application and check the heap dump:
```shell
./svmheapdump
May 15, 2020, 4:28:14 PM: Hello GraalVM native image developer!
Get PID of this process: 'ps -C svmheapdump -o pid= '
then send it signal: 'kill -SIGUSR1 <pid_printed_above>'
to get heap dump created in the working directory.
Starting thread!
May 15, 2020, 4:28:14 PM: Thread started, it will run for 60 seconds
```

Open the 2nd terminal to get the process ID of the running `svmheapdump` application using a command like `ps -C svmheapdump -o pid=` for Linux OS and `pgrep svmheapdump` for macOS. Copy the printed process ID, e.g. 100, and use it to send the signal to the running application:
```shell
kill -SIGUSR1 100
```
The heap dump will be available in the working directory while the application continues to run.

## Create a Heap Dump from within a Java Application

The following Java example shows how to create a heap dump from within a running Java application using [`VMRuntime.dumpHeap()`](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/VMInspection.java) after some condition is met.
The condition to create a heap dump is provided as an option on the command line.
Save the code snippet below as _SVMHeapDumpAPI.java_.

```java
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import org.graalvm.nativeimage.VMRuntime;

public class SVMHeapDumpAPI {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        StringBuffer sb1 = new StringBuffer(100);
        sb1.append(DateFormat.getDateTimeInstance().format(new Date()));
        sb1.append(": Hello GraalVM native image developer. \nYour command-line options are: ");
        String liveArg = "true";
        if (args.length > 0) {
            sb1.append(args[0]);
            System.out.println(sb1);
            if (args[0].equalsIgnoreCase("--heapdump")){
                if(args.length > 1 ) {
                  liveArg = args[1];
                }
                createHeapDump(Boolean.valueOf(liveArg));
            }
        } else {
            sb1.append("None");
            System.out.println(sb1);
        }
     }

    /**
     * Create heap dump and save it into temp file
     */
     private static void createHeapDump(boolean live) {
     try {
         File file = File.createTempFile("SVMHeapDump-", ".hprof");
         VMRuntime.dumpHeap(file.getAbsolutePath(), live);
         System.out.println("  Heap dump created " + file.getAbsolutePath() + ", size: " + file.length());
     } catch (UnsupportedOperationException unsupported) {
         System.out.println("  Heap dump creation failed." + unsupported.getMessage());
     } catch (IOException ioe) {
         System.out.println("IO went wrong: " + ioe.getMessage());
     }
 }
}
```
The application creates some data to have something to dump, checks the command-line to see if a heap dump has to be created, and then in the method `createHeapDump()` creates the actual heap dump, performing checks for the file's existence.

#### Building a Native Image
In the next step, compile _SVMHeapDumpAPI.java_ and build a native executable:
```shell
$GRAALVM_HOME/bin/javac SVMHeapDumpAPI.java
$GRAALVM_HOME/bin/native-image SVMHeapDumpAPI
```

When the command completes, the `svmheapdumpapi` executable is created in the current directory.

##### Run the application and check the heap dump
Now you can run your native image application and create a heap dump from it with an output similar to the one below:
```shell
./svmheapdumpapi --heapdump
Sep 15, 2020, 4:06:36 PM: Hello GraalVM native image developer.
Your command-line options are: --heapdump
  Heap dump created /var/folders/hw/s9d78jts67gdc8cfyq5fjcdm0000gp/T/SVMHeapDump-6437252222863577987.hprof, size: 8051959
```