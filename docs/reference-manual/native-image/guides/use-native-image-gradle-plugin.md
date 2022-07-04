---
layout: ni-docs
toc_group: how-to-guides
link_title: Use Native Image Gradle Plugin
permalink: /reference-manual/native-image/guides/use-native-image-gradle-plugin/
---

# Use Gradle to build a Native Executable from a Java Application

You can use the Gradle plugin for GraalVM Native Image to build a native executable from a Java application in one step, in addition to a runnable JAR. 
The plugin is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses the [Gradle build tool](https://gradle.org/).


The Gradle plugin for GraalVM Native Image works with the `application` plugin and registers a number of tasks and extensions for you. The main tasks that you can run are:
    
- `nativeCompile` to trigger the generation of a native executable from your Java application
- `nativeRun` to run the generated native executable
- `nativeTestCompile` to build a native executable with tests found in the test source set
- `nativeTest` to run tests found in the test source set against the native executable

For more information, see the [plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).

This guide shows you how to use the Native Image Gradle plugin to build a native executable from a Java application, add support for dynamic feature calls, and run JUnit tests.

The **Fortune demo** is a demo Java application that simulates the traditional [fortune Unix program](https://en.wikipedia.org/wiki/Fortune_(Unix)). 
The data for the fortune phrases is provided by [YourFortune](https://github.com/your-fortune).

## Prepare a Demo Application

> You must have [GraalVM installed with Native Image support](../README.md#install-native-image). 

1. Create a new Java project with **Gradle** in your favorite IDE, called "Fortune". Rename the default filename `App.java` to `Fortune.java` and replace its contents with the following: 

    ```java
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

<!--NOTE: where is the fortunes.json file? -->

2. Open the Gradle configuration file _build.gradle_, and define the main class for the application in the `application` section:
    ```
    application {
        mainClass = 'org.yourcompany.yourproject.Fortune'
    }
    ```

3. Add explicit FasterXML Jackson dependencies that provide functionality to read and write JSON, data-binding (used in the demo application). Insert the following three lines in the `dependencies` section of _build.gradle_:

    ```xml
    implementation 'com.fasterxml.jackson.core:jackson-core:2.13.2'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.13.2.2'
    implementation 'com.fasterxml.jackson.core:jackson-annotations:2.13.2'
    ```

4. Compile and build the application using Gradle. From the root application directory, run the following command:

    ```shell
    ./gradlew build -x test
    ```
    This task compiles the source assembles the results into a JAR file. (The `-x test` option explicitly disables tests.)
    When the build succeeds, build a native version of your application with GraalVM Native Image and Gradle.

## Build a native executable from a Java application using Gradle

1. Register the Gradle plugin for GraalVM Native Image. Add the following to `plugins` section of your project’s _build.gradle_ file:

    ```
    plugins {
    // ...

    // Apply GraalVM Native Image plugin
    id 'org.graalvm.buildtools.native' version '0.9.11'
    }
    ```

2. The plugin is not yet available on the Gradle Plugin Portal, so declare an additional plugin repository. Add the following to the _settings.gradle_ file:

    ```
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }
    ```
    Note that the `pluginManagement {}` block must appear before any other statements in the file.

3. Run the `nativeCompile` task to generate a native executable using Gradle:

    ```shell
    ./gradlew nativeCompile
    ```
    
    The native executable, named _fortune_, is created in the _build/native/nativeCompile_ directory.

4. Run your native executable:

    ```shell
    ./build/native/nativeCompile/fortune
    ```

You can customize the name of the native executable and pass additional parameters to GraalVM in the _build.gradle_ file, as follows:

```
graalvmNative {
    binaries {
        main {
            imageName.set('native-application') 
            buildArgs.add('--verbose') 
        }
    }
}
```
The name for your native executable will be `native-application`.
Notice how you can pass additional arguments to the `native-image` tool using the `buildArgs.add` syntax.

## Add Testing Support

The Gradle plugin for GraalVM Native Image can run [JUnit Platform](https://junit.org/junit5/docs/current/user-guide/) tests on your native executable. This means that the tests will be compiled and run as native code.

Support for JUnit is explicit and you can see the following in _build.gradle_:
```
tasks.named('test') {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
} 
```

You do not need to configure anything additionally, and to run the tests, run:

```shell
./gradlew nativeTest
```

Currently, the plugin runs tests in "JVM" mode prior to running tests on the native executable. To disable testing support (which comes by default), add the following configuration to the _build.gradle_ file:

```
graalvmNative {
    testSupport = false
}
```

## Add Support for Tracing Agent

The demo does not call any dynamic features at run time. 
However, in many real-world use cases, your application will, most likely, call either the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects (`java.lang.reflect.Proxy`), or class path resources (`Class.getResource`) - the dynamic features that need to be provided to the `native-image` tool in the form of configuration files.

The Native Image Gradle plugin simplifies generation of configuration files by injecting the Tracing Agent automatically for you.

To invoke the agent, add `-Pagent` to the `run` and `nativeBuild` commands, or `test` and `nativeTest` task invocations:

* `./gradlew -Pagent run` runs on JVM with the agent
* `./gradlew -Pagent nativeCompile` builds a native executable using configuration acquired by the agent
* `./gradlew -Pagent test` runs tests on JVM with the agent
* `./gradlew -Pagent nativeTest` builds a native executable  using configuration acquired by the agent

### Related Documentation

- [Collect Metadata with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)
- [Gradle plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
