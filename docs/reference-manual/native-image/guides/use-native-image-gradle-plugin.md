---
layout: ni-docs
toc_group: how-to-guides
link_title: Use Native Image Gradle Plugin
permalink: /reference-manual/native-image/guides/use-native-image-gradle-plugin/
---

# Use Gradle to Build a Native Executable from a Java Application

You can use the Gradle plugin for GraalVM Native Image to build a native executable from a Java application in one step, in addition to a runnable JAR. 
The plugin is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses the [Gradle build tool](https://gradle.org/).

The Gradle plugin for GraalVM Native Image works with the `application` plugin and registers a number of tasks and extensions for you. For more information, see the [plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).

This guide shows you how to use the Native Image Gradle plugin to build a native executable from a Java application, add support for dynamic features, and run JUnit tests.

You will use a **Fortune demo** application that simulates the traditional [fortune Unix program](https://en.wikipedia.org/wiki/Fortune_(Unix)). The data for the fortune phrases is provided by [YourFortune](https://github.com/your-fortune).

We recommend that you follow the instructions and create the application step-by-step. Alternatively, you can use an existing project: clone the [GraalVM demos repository](https://github.com/graalvm/graalvm-demos) and navigate into the `fortune-demo/gradle/fortune` directory:
```shell
git clone https://github.com/graalvm/graalvm-demos && cd fortune-demo/gradle/fortune
```

> You must have [GraalVM installed with Native Image support](../README.md#install-native-image). 

## Prepare a Demo Application

1. Create a new Java project with **Gradle** using the following command (alternatively, you can use your IDE to generate a project):

    ```shell
    gradle init --project-name fortune --type java-application --package demo --test-framework junit-jupiter --dsl groovy
    ```

2. Rename the default `app` directory to `fortune`, then rename the default filename `App.java` to `Fortune.java` and replace its contents with the following: 

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

        public String randomFortune() {
            //Pick a random number
            int r = RANDOM.nextInt(fortunes.size());
            //Use the random number to pick a random fortune
            return fortunes.get(r);
        }

        private void printRandomFortune() throws InterruptedException {
            String f = randomFortune();
            // Print out the fortune s.l.o.w.l.y
            for (char c : f.toCharArray()) {
                System.out.print(c);
                Thread.sleep(100);
            }
            System.out.println();
        }

        /**
         * @param args the command line arguments
         */
        public static void main(String[] args) throws InterruptedException, JsonProcessingException {
            Fortune fortune = new Fortune();
            fortune.printRandomFortune();
        }
    }
    ```

3. Delete the `fortune/src/test/java` directory, you will add tests in a later stage.

4. Copy and paste the following file, [fortunes.json](https://github.com/graalvm/graalvm-demos/blob/master/fortune-demo/fortune/src/main/resources/fortunes.json) under `fortune/src/main/resources/`. Your project tree should be:

    ```shell
    .
    ├── fortune
    │   ├── build.gradle
    │   └── src
    │       ├── main
    │       │   ├── java
    │       │   │   └── demo
    │       │   │       └── Fortune.java
    │       │   └── resources
    │       │       └── fortunes.json
    │       └── test
    │           └── resources
    ├── gradle
    │   └── wrapper
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle
    ```

5. Open the Gradle configuration file _build.gradle_, and update the main class in the `application` section:

    ```groovy
    application {
        mainClass = 'demo.Fortune'
    }
    ```

6. Add explicit FasterXML Jackson dependencies that provide functionality to read and write JSON, data-binding (used in the demo application). Insert the following three lines in the `dependencies` section of _build.gradle_:

    ```groovy
    implementation 'com.fasterxml.jackson.core:jackson-core:2.13.2'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.13.2.2'
    implementation 'com.fasterxml.jackson.core:jackson-annotations:2.13.2'
    ```

    Also, remove the dependency on `guava` that will not be used.

    The next steps will be focused what you should do to enable the Native Image Gradle plugin.

7. Register the Native Image Gradle plugin. Add the following to `plugins` section of your project’s _build.gradle_ file:

    ```groovy
    plugins {
    // ...

    id 'org.graalvm.buildtools.native' version '0.9.12'
    }
    ```
    The plugin discovers which JAR files it needs to pass to the `native-image` builder and what the executable main class should be. 

8. The plugin is not yet available on the Gradle Plugin Portal, so declare an additional plugin repository. Open the _settings.gradle_ file and replace the default content with this:

    ```groovy
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }

    rootProject.name = 'fortune-parent'
    include('fortune')
    ```
    Note that the `pluginManagement {}` block must appear before any other statements in the file.
    
## Build a Native Executable with Gradle

You can already build a native executable by running `./gradlew nativeCompile` or run it directly by invoking `./gradlew nativeRun`. However, at this stage, running the native executable will fail, because this application requires additional metadata: you need to provide it with a list of resources to load.

1. Instruct the plugin to automatically detect resources to be included in the native executable. Add this to your `build.gradle` file:

    ```groovy
    graalvmNative {
        binaries.all {
            resources.autodetect()
        }
        toolchainDetection = false
    }
    ```
    Another thing to note here, the plugin may not be able to properly detect the GraalVM installation, because of limitations in Gradle. By default, the plugin selects a Java 11 GraalVM Community Edition. If you want to use GraalVM Enterprise, or a particular version of GraalVM and Java, you need to explicitly tell in plugin's configuration. For example: 

    ```groovy
    graalvmNative {
        binaries {
            main {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(8)
                    vendor = JvmVendorSpec.matching("GraalVM Community")
                }
            }
        }
    }
    ```
    Another workaround to this is to disable toolchain detection with this command `toolchainDetection = false`.

2. Compile the project and build a native executable at one step:

    ```shell
    ./gradlew nativeRun
    ```

    The native executable, named _fortune_, is created in the _/fortune/build/native/nativeCompile_ directory.

3.  Run the application from the native executable:

    ```shell
    ./fortune/build/native/nativeCompile/fortune
    ```
    The application starts and prints a random quote.

To see the benefits of running your application as a native executable, `time` how long it takes and compare the results with running on the JVM.

## Provide Metadata with the Agent

In the real-world scenario, your application will, most likely, call either the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects, or class path resources - the [dynamic features that require metadata](../ReachabilityMetadata.md). 

The Native Image Gradle plugin simplifies generation of the required metadata by injecting the [Java agent](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#agent-support) automatically for you at compile time. 
To enable the agent, just pass the `-Pagent` option to any Gradle tasks that extends `JavaForkOptions` (for example, `test`, `run`). 

The configuration block you added before took care of detecting resources, but it would potentially add more than what you need, and would not be able to deal with more advanced use cases like Dynamic Proxy.
For the sake of the demonstration, remove the `resources.autodetect()` confguration block so that we can try an alternative variant.

See below how to collect metadata with the agent, and build a native executable applying the provided configuration.

1. Run your application with the agent enabled:

    ```shell
    ./gradlew -Pagent run
    ```

2. Once the metadata is collected, copy it into the project's `/META-INF/native-image` directory using the `metadataCopy` task:

    ```shell
    ./gradlew metadataCopy --task run --dir src/main/resources/META-INF/native-image
    ```

3. Build a native executable using metadata acquired by the agent with Gradle. 

    ```shell
    ./gradlew nativeCompile
    ```
    The native executable, named _fortune_, is created in the _build/native/nativeCompile_ directory.

4. Run the application from the native executable:

    ```shell
    ./fortune/build/native/nativeCompile/fortune
    ```

You can customize the plugin. For example, change the name of the native executable and pass additional parameters to the plugin in the _build.gradle_ file, as follows:

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
The native executable then will be called `fortuneteller`. 
Notice how you can pass additional arguments to the `native-image` tool using the `buildArgs.add` syntax.

## Add JUnit Testing

The Gradle plugin for GraalVM Native Image can run [JUnit Platform](https://junit.org/junit5/docs/current/user-guide/) tests on your native executable. 
This means that the tests will be compiled and run as native code.

1. Create the following test in the `fortunate/src/test/java/demo/FortuneTest.java` file:

    ```java
    package demo;

    import com.fasterxml.jackson.core.JsonProcessingException;
    import org.junit.jupiter.api.DisplayName;
    import org.junit.jupiter.api.Test;

    import static org.junit.jupiter.api.Assertions.assertTrue;

    class FortuneTest {
        @Test
        @DisplayName("Returns a fortune")
        void testItWorks() throws JsonProcessingException {
            Fortune fortune = new Fortune();
            assertTrue(fortune.randomFortune().length()>0);
        }
    }
    ```

2. Run JUnit tests:

    ```shell
    ./gradlew nativeTest
    ```

    The plugin runs tests on the JVM prior to running tests from the native executable. To disable testing support (which comes by default), add the following configuration to the _build.gradle_ file:

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
    It runs your application on the JVM with the agent, collects the metadata and uses it for testing on `native-image`.
    The generated configuration files can be found in the `${buildDir}/native/agent-output/${taskName}` directory.In this case, for example, `build/native/agent-output/test`. The Native Image Gradle plugin will also substitute `{output_dir}` in the agent options to point to this directory.

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
Your workflow in that case is just:

```shell
./gradlew nativeRun
```

Lastly, if you use GraalVM Enterprise as your `JAVA_HOME` environment, the plugin builds a native executable with enterprise features enabled.

### Related Documentation

- [Collect Metadata with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)
- [Gradle plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
