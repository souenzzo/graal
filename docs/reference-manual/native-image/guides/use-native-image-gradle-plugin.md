---
layout: ni-docs
toc_group: how-to-guides
link_title: Use Native Image Gradle Plugin
permalink: /reference-manual/native-image/guides/use-native-image-gradle-plugin/
---

# Use Gradle to build a Native Executable from a Java Application

You can use the Gradle plugin for GraalVM Native Image to build a native executable from a Java application in one step, in addition to a runnable JAR. 
The plugin is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses the [Gradle build tool](https://gradle.org/).

The Gradle plugin for GraalVM Native Image works with the `application` plugin and registers a number of tasks and extensions for you. For more information, see the [plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).

This guide shows you how to use the Native Image Gradle plugin to build a native executable from a Java application, add support for dynamic feature calls, and run JUnit tests.

You will use a **Fortune demo** application that simulates the traditional [fortune Unix program](https://en.wikipedia.org/wiki/Fortune_(Unix)). The data for the fortune phrases is provided by [YourFortune](https://github.com/your-fortune).

> You must have [GraalVM installed with Native Image support](../README.md#install-native-image). 

## Prepare a Demo Application

1. Create a new Java project with **Gradle** in your favorite IDE, called "Fortune", in the `demo` package. Rename the default filename `App.java` to `Fortune.java` and replace its contents with the following: 

    ```java
    package demo;

    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.JsonNode;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import java.io.BufferedReader;
    import java.io.IOException;
    import java.io.InputStream;
    import java.io.InputStreamReader;
    import java.nio.charset.StandardCharsets;
    import java.util.ArrayList;
    import java.util.Iterator;
    import java.util.Random;
    import java.util.logging.Level;
    import java.util.logging.Logger;

    public class Fortune {

        private static final Random RANDOM = new Random();
        private final ArrayList<String> fortunes = new ArrayList<>();

        public Fortune() throws JsonProcessingException {
            // Scan the file into the array of fortunes
            String json = readInputStream(ClassLoader.getSystemResourceAsStream("fortunes.json"));
            ObjectMapper omap = new ObjectMapper();
            JsonNode root = omap.readTree(json);
            JsonNode data = root.get("data");
            Iterator<JsonNode> elements = data.elements();
            while (elements.hasNext()) {
                JsonNode quote = elements.next().get("quote");
                fortunes.add(quote.asText());
            }      
        }
        
        private String readInputStream(InputStream is) {
            StringBuilder out = new StringBuilder();
            try (InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                }

            } catch (IOException e) {
                Logger.getLogger(Fortune.class.getName()).log(Level.SEVERE, null, e);
            }
            return out.toString();
        }
        
        private void printRandomFortune() throws InterruptedException {
            //Pick a random number
            int r = RANDOM.nextInt(fortunes.size());
            //Use the random number to pick a random fortune
            String f = fortunes.get(r);
            // Print out the fortune s.l.o.w.l.y
            for (char c: f.toCharArray()) {
                System.out.print(c);
                Thread.sleep(100);   
            }
            System.out.println();
        }
    
        /**
        * @param args the command line arguments
        * @throws java.lang.InterruptedException
        * @throws com.fasterxml.jackson.core.JsonProcessingException
        */
        public static void main(String[] args) throws InterruptedException, JsonProcessingException {
            Fortune fortune = new Fortune();
            fortune.printRandomFortune();
        }
    }
    ```

2. Copy and paste the following file, [fortunes.json](https://github.com/graalvm/graalvm-demos/blob/master/fortune-demo/fortune/src/main/resources/fortunes.json) under `resources/`. Your project tree should be:

    ```shell
    .
    ├── app
    │   ├── build.gradle
    │   └── src
    │       ├── main
    │       │   ├── java
    │       │   │   └── demo
    │       │   │       └── Fortune.java
    │       │   └── resources
    │       │       └── fortunes.json
    │       └── test
    │           ├── java
    │           │   └── demo
    │           │       └── AppTest.java
    │           └── resources
    ├── gradle
    │   └── wrapper
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle
    ```

3. Open the Gradle configuration file _build.gradle_, and define the main class for the application in the `application` section:

    ```groovy
    application {
        mainClass = 'demo.Fortune'
    }
    ```

4. Add explicit FasterXML Jackson dependencies that provide functionality to read and write JSON, data-binding (used in the demo application). Insert the following three lines in the `dependencies` section of _build.gradle_:

    ```groovy
    implementation 'com.fasterxml.jackson.core:jackson-core:2.13.2'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.13.2.2'
    implementation 'com.fasterxml.jackson.core:jackson-annotations:2.13.2'
    ```
    The next steps will be focused what you should do to enable the Maven plugin for GraalVM Native Image.

5. Register the Gradle plugin for GraalVM Native Image. Add the following to `plugins` section of your project’s _build.gradle_ file:

    ```groovy
    plugins {
    // ...

    id 'org.graalvm.buildtools.native' version '0.9.11'
    }
    ```
    The Native Image Gradle plugin discovers which JAR files it needs to pass to the `native-image` builder and what the executable main class should be. 

6. The plugin is not yet available on the Gradle Plugin Portal, so declare an additional plugin repository. Open the _settings.gradle_ file and replace the default content with this:

    ```groovy
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }

    rootProject.name = 'fortune'
    include('fortune')
    ```
    Note that the `pluginManagement {}` block must appear before any other statements in the file.   
    
7. Compile and build the application using Gradle. Open a terminal window and, from the root application directory, run:

    ```shell
    ./gradlew build
    ```
    
    This task compiles the source into a runnable JAR with all dependencies.
    
    Thanks to the Native Image Gradle plugin, you can already build a native executable directly by running `./gradlew nativeCompile` (if your application does not call any methods reflectively at run time). 

## Build a Native Executable with Gradle

This demo application is a little more complicated than `HelloWorld`, and [requires metadata](../ReachabilityMetadata.md) before building a native executable. 
In the real-world scenario, your application will, most likely, call either the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects, or class path resources - the dynamic features that require metadata. 

The Native Image Gradle plugin simplifies generation of the required metadata by injecting the agent automatically for you [Java agent](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#agent-support) at compile time. 
To enable the agent, just pass the `-Pagent` option to any Gradle tasks that extends `JavaForkOptions` (for example, `test`, `run`). 

Next steps show you how to collect metadata and build a native executable with Gradle

1. Run your application with the agent enabled:

    ```shell
    ./gradlew -Pagent
    ```

2. Once the metadata is collected, copy it into the project's `/META-INF/native-image` directory using the `metadataCopy` task:

    ```shell
    ./gradlew metadataCopy --task run --dir src/main/resources/META-INF/native-image
    ```

3. Build a native executable using metadata acquired by the agent with Gradle:

    ```shell
    ./gradlew nativeCompile
    ```
    The native executable, named _fortune_, is created in the _build/native/nativeCompile_ directory. 
    You can customize the name of the native executable and pass additional parameters to the plugin in the _build.gradle_ file, as follows:

    ```groovy
    graalvmNative {
        binaries {
            main {
                imageName.set('fortuneteller') 
                buildArgs.add('--verbose') 
            }
        }
    }
    ```
    The name for your native executable will be `fortuneteller`. 
    Notice how you can pass additional arguments to the `native-image` tool using the `buildArgs.add` syntax.

4. Run the application from the native executable:

    ```shell
    ./build/native/nativeCompile/fortune
    ```

To see the benefits of running your application as a native executable, `time` how long it takes and compare the results with running on the JVM.

## Add JUnit Testing

The Gradle plugin for GraalVM Native Image can run [JUnit Platform](https://junit.org/junit5/docs/current/user-guide/) tests on your native executable. This means that the tests will be compiled and run as native code.

1. Add support for JUnit in the _build.gradle_ file:

    ```groovy
    tasks.named('test') {
        // Use JUnit Platform for unit tests.
        useJUnitPlatform()
    } 
    ```

2. Run JUnit tests:

    ```shell
    ./gradlew nativeTest
    ```

    Currently, the plugin runs tests in "JVM" mode prior to running tests on the native executable. To disable testing support (which comes by default), add the following configuration to the _build.gradle_ file:

    ```groovy
    graalvmNative {
        testSupport = false
    }
    ```

### Run Tests with the Agent

If you need to test collecting metadata with the agent, add the `-Pagent` option to the `test` and `nativeTest` task invocations:

1. Run the tests on the JVM with the agent:

    ```shell
    ./gradlew -Pagent test
    ```
    It runs your applicaiton on the JVM with the agent, collects the metadata and uses it for testing on `native-image`.
    The generated configuration files can be found in the `${buildDir}/native/agent-output/${taskName}` directory, for example, `build/native/agent-output/run`. The Native Image Gradle plugin will also substitute `{output_dir}` in the agent options to point to this directory.

2. Test building a native executable using metadata acquired by the agent:

    ```shell
    ./gradlew -Pagent nativeTest
    ```

## Add GraalVM Reachability Metadata Support

Since release 0.9.11, the Native Image Gradle plugin adds experimental support for the [GraalVM Reachability Metadata repository](https://github.com/graalvm/graalvm-reachability-metadata/). 
This repository provides GraalVM configuration for libraries which do not officially support GraalVM native.
The support needs to be enabled explicitly.

Register the metadata repository in the _settings.gradle_ file:

```groovy
graalvmNative {
    metadataRepository {
        enabled = true
        version = "1.0.0"
    }
}
```
The plugin will automatically download the metadata from the repository.

### Summary

The configuration of Native Image Gradle plugin could go much further than this guide. Check the [plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).

Note that if your application does not call dynamically any classes at run time, the execution with the agent is needless. 
Your workflow in that case you just be:

```shell
./gradlew build
./gradlew nativeCompile
```

Another advantage of the plugin is that if you use GraalVM Enterprise as your `JAVA_HOME` environment, the plugin builds a native executable with enterprise features enabled.

### Related Documentation

- [Collect Metadata with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)
- [Gradle plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
