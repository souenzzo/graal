---
layout: ni-docs
toc_group: how-to-guides
link_title: Use Native Image Gradle Plugin
permalink: /reference-manual/native-image/guides/use-native-image-gradle-plugin/
---

# Use Gradle to Build Java Applications into Native Executables

You can use Gradle plugin for GraalVM Native Image to build and convert a Java application into a native executable at one step, besides a runnable JAR. 
It is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses the [Gradle build tool](https://gradle.org/).


The Gradle plugin for GraalVM Native Image works with the `application` plugin and will register a number of tasks and extensions for you. The main tasks that you can execute are:
    
- `nativeCompile` to trigger the generation of a native executable of your application
- `nativeRun` to execute the generated native executable
- `nativeTestCompile` to build a native image with tests found in the test source set
- `nativeTest` to execute tests found in the test source set in native mode

For more information, check the plugin documentation.

In this guide you will learn how to enable Native Image Gradle plugin to convert a Java application into a native executable, add support for dynamic feature calls, and run JUnit tests.

You will use the **Fortune demo** which is a Java program that simulates the traditional [fortune Unix program](https://en.wikipedia.org/wiki/Fortune_(Unix)). 
The data for the fortune phrases is provided by [YourFortune](https://github.com/your-fortune).

## Prepare a Demo Application

> You are expected to have [GraalVM installed with Native Image support](../README.md#install-native-image). 

1. Crate a new Java project with **Gradle** in your favourite IDE, called "Fortune". Rename the default filename `App.java` to `Fortune.java` and replace its content with the following: 

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

2. Open a Gradle configuration file, _build.gradle_, anddefine the main class for the application in the `application` section:
    ```
    application {
        mainClass = 'org.yourcompany.yourproject.Fortune'
    }
    ```

3. Add explicit FasterXML Jackson dependencies that allows for reading and writing JSON, data-binding, used in the application. Insert the following in the `dependencies` section in _build.gradle_:

    ```xml
    implementation 'com.fasterxml.jackson.core:jackson-core:2.13.2'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.13.2.2'
    implementation 'com.fasterxml.jackson.core:jackson-annotations:2.13.2'
    ```

4. Test compiling and building the application. From the root application directory, execute:

    ```shell
    ./gradlew build -x test
    ```
    This task compiles, assembles the code into a JAR file. We explicitly disabled running the tests with `-x test`.
    When the build succeeds, go ahead and build a native version of this application with GraalVM Native Image and Gradle.

## Build a Java Application into a Native Executable with Gradle

1. Register the Gradle plugin for GraalVM Native Image. Add following to `plugins` section of your project’s `build.gradle`:

    ```
    plugins {
    // ...

    // Apply GraalVM Native Image plugin
    id 'org.graalvm.buildtools.native' version '0.9.11'
    }
    ```

2. The plugin is not available on the Gradle Plugin Portal yet, so you will need to declare a plugin repository in addition. Open the `settings.gradle` file and add the following:

    ```
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }
    ```
    Note that `pluginManagement {}` block must appear before any other statements in the script.

3. Execute the `nativeCompile` task to generate a native image using Gradle:

    ```shell
    ./gradlew nativeCompile
    ```
    
    The native executable, named `fortune` is created in `build/native/nativeCompile` directory.

4. Run your application from a native executable:

    ```shell
    ./build/native/nativeCompile/fortune
    ```

You can customize the name of the native executable and pass additional parameters to GraalVM in _build.gradle_:

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
The native image name for a native executable will now be `native-application`.
Notice how you can pass additional arguments to the `native-image` builder using the `buildArgs.add` syntax.

## Add Testing Support

Gradle plugin for GraalVM Native Image supports running tests on the [JUnit Platform](https://junit.org/junit5/docs/current/user-guide/) as native executables. This means that tests will be compiled and executed as native code.

The support for JUnit is explicit and you can see the following in _build.gradle_:
```
tasks.named('test') {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
} 
```

You do not need to configure anything additionally, and to execute the tests, run:

```shell
./gradlew nativeTest
```

Currently, the plugin executes tests in the classic "JVM" mode prior to the execution of tests in native mode. To disable testing support which comes by default, add the following configuration to _build.gradle_:

```
graalvmNative {
    testSupport = false
}
```

## Add Support for Tracing Agent

The provided demo does not call any dynamic features at run time. 
However, in the real world use cases, your application will, most likely, call either the Java Native Interface (JNI), or Java Reflection, or Dynamic Proxy objects (`java.lang.reflect.Proxy`), or class path resources (`Class.getResource`) - the dynamic features that need to be provided to the `native-image` tool in the form of configuration files.

The Native Image Gradle plugin simplifies generation of the required configuration files by injecting the Tracing Agent automatically for you.

To invoke the agent, add `-Pagent` to the `run` and `nativeBuild` commands, or `test` and `nativeTest` task invocations:

* `./gradlew -Pagent run` runs on JVM with the agent
* `./gradlew -Pagent nativeCompile` builds a native executable using configuration acquired by the agent
* `./gradlew -Pagent test` runs tests on JVM with the agent
* `./gradlew -Pagent nativeTest` builds a native executable  using configuration acquired by the agent

### Related Documentation

* [Gradle plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
* [Metadata Collection with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)