---
layout: ni-docs
toc_group: how-to-guides
link_title: Use Native Image Maven Plugin
permalink: /reference-manual/native-image/guides/use-native-image-maven-plugin/
---

# Use Maven to Build Java Applications into Native Executables

There is a Maven plugin for GraalVM Native Image to package Java applications into native executables, besides runnable JARs, at one step. 
It is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses Apache Maven™. 
The plugin makes use of Maven profiles to enable building and testing of native executables.

In this guide you will learn how to enable Native Image Maven plugin to build a Java application into a native executable, run JUnit tests, and add support for Java dynamic features.

For demonstration purposes, you will use a fortune teller application that simulates the traditional [fortune Unix program](https://en.wikipedia.org/wiki/Fortune_(Unix)). The data for the fortune phrases is provided by [YourFortune](https://github.com/your-fortune).

## Prepare a Demo Application

> You are expected to have [GraalVM installed with Native Image support](../README.md#install-native-image).

1. Crate a new Java project with **Maven** in your favourite IDE, called "Fortune", in the `org.example` package name. The application should contain a sole Java file with the following content:

    ```java
    package org.example;
    
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

2. Add an explicit FasterXML Jackson dependency that allows for reading and writing JSON, data-binding, used in the application. Open _pom.xml_, a Maven configuration file, and insert the following in the `<dependencies>` section:

    ```xml
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.12.6.1</version>
        </dependency>
    </dependencies>
    ```

3. Add regular Maven plugins for building and assembling a Maven project into an executable JAR. Insert the following into the `build` section in _pom.xml_:
    ```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.0.0</version>
                <executions>
                    <execution>
                        <id>java</id>
                        <goals>
                            <goal>java</goal>
                        </goals>
                        <configuration>
                            <mainClass>${mainClass}</mainClass>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>${maven.compiler.source}</source>
                    <target>${maven.compiler.source}</target>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.2.2</version>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                            <mainClass>${mainClass}</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                            <mainClass>${mainClass}</mainClass>
                        </manifest>
                    </archive>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                </configuration>
            </plugin>

        </plugins>
    </build>
    ```
4. Replace the default `<properties>` section in `pom.xml` with this content:

    <properties>
        <native.maven.plugin.version>0.9.12</native.maven.plugin.version>
        <junit.jupiter.version>5.8.1</junit.jupiter.version>
        <maven.compiler.source>${java.specification.version}</maven.compiler.source>
        <maven.compiler.target>${java.specification.version}</maven.compiler.target>
        <mainClass>Fortune</mainClass>
    </properties>
    
    You just "hardcoded" plugins versions and the entry point class to your application.

5. Now compile and run the application on the JVM (GraalVM SDK). From the root application directory, execute:

    ```shell
    mvn clean package
    ```
    When the build succeeds, run the application on the JVM. Since you have installed GraalVM, it will run on GraalVM JDK.

    ```shell
    mvn exec:java -Dexec.mainClass=Fortune
    ```
    The application should return a random saying,  every time you re-run - a new one.
    Now go ahead and build a native version of this application with GraalVM Native Image and Maven.

## Build a Java Application into a Native Executable with Maven

1. Register the Maven plugin for GraalVM Native Image, `native-maven-plugin`, in the profile called `native` by adding the following to _pom.xml_:
    ```xml
    <profiles>
        <profile>
            <id>native</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.graalvm.buildtools</groupId>
                        <artifactId>native-maven-plugin</artifactId>
                        <version>${native.maven.plugin.version}</version>
                        <extensions>true</extensions>
                        <executions>
                            <execution>
                                <id>build-native</id>
                                <goals>
                                    <goal>build</goal>
                                </goals>
                                <phase>package</phase>
                            </execution>
                            <execution>
                                <id>test-native</id>
                                <goals>
                                    <goal>test</goal>
                                </goals>
                                <phase>test</phase>
                            </execution>
                        </executions>
                        <configuration>
                            <fallback>false</fallback>
                            <agent>
                                <enabled>true</enabled>
                                <options>
                                    <option>experimental-class-loader-support</option>
                                </options>
                            </agent>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
    ```
    The plugin figures out which JAR files it needs to pass to the `native-image` builder and what the executable main class should be. With this plugin you could already build a native executable directly with Maven by running `mvn -Pnative package` (if your application does not call any methods reflectively at run time).
    
    This demo application is a little more complicated than `HelloWorld`, and requires pre-configuration before building a native executable. But you do not have to configure anything manually: the Native Image Maven plugin can generate the required configuration for you by injecting the [Java agent](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#agent-support) at package time. The agent is disabled by default, and can be enabled in project's `pom.xml` file or via the command line.
    - To enable the agent via `pom.xml`, specify `<enabled>true</enabled>` in the `native-maven-plugin` plugin configuration:

        ```xml
        <configuration>
        <agent>
            <enabled>true</enabled>
        </agent>
        </configuration>
        ```

    - To enable the agent via the command line, pass the `-Dagent=true` flag when running Maven.
    So your next step is to run with the agent.

2. Before running with the agent, register a separate Mojo execution in the `native` profile which allows forking the Java process. It is required to execute your application with the agent.
    ```xml
    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.0.0</version>
        <executions>
            <execution>
                <id>java-agent</id>
                <goals>
                    <goal>exec</goal>
                </goals>
                <configuration>
                    <executable>java</executable>
                    <workingDirectory>${project.build.directory}</workingDirectory>
                    <arguments>
                        <argument>-classpath</argument>
                        <classpath/>
                        <argument>${mainClass}</argument>
                    </arguments>
                </configuration>
            </execution>
            <execution>
                <id>native</id>
                <goals>
                    <goal>exec</goal>
                </goals>
                <configuration>
                    <executable>${project.build.directory}/${imageName}</executable>
                    <workingDirectory>${project.build.directory}</workingDirectory>
                </configuration>
            </execution>
        </executions>
    </plugin>
    ```

3. Run your application with the agent enabled:
    ```shell
    mvn -Pnative -Dagent exec:exec@java-agent
    <!-- mvn -Pnative -Dagent=true -DskipTests -DskipNativeBuild=true package exec:exec@java-agent -->
    ```
    The agent generates the configuration files in a subdirectory of `target/native/agent-output`. Those files will be automatically used if you run your build with the agent enabled.

4. Now build a native executable directly with Maven by applying the required configuration:

    ```shell
    mvn -Pnative -Dagent package
    <!-- mvn -Pnative -Dagent=true -DskipTests package exec:exec@native -->
    ```
    <!-- Notice the `-DskipTests` command which skips running tests on the JVM with Maven Surefire (enabled by default). -->
    When the command completes a native executable, `fortune`, is generated in the `/target` directory of the project and ready for use.

    The executable's name is derived from the artifact ID, but you can specify any custom name in the `native-maven-plugin` plugin within a <configuration> node:
    ```xml
    <configuration>
        <imageName>futureteller</imageName>
    </configuration>
    ```

5. Run the application as a native executable:

    ```shell
    mvn -Pnative exec:exec@native
    <!-- ./target/fortune -->
    ```

    To see the benefits of executing your application as a native executable, `time` the execution and compare with running on the JVM.

The configuration of Maven plugin for GraalVM Native Image building could go much further than in this guide. Check the [plugin documentation](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#agent-support).
To remind, if your application does not call dynamically any classes at run time, the execution with the agent is needless. 
Your workflow in that case you just be:

```shell
mvn clean compile
mvn -Pnative package
```

Another advantage of the plugin is that if you use GraalVM Enterprise as your `JAVA_HOME` environment, the plugin builds a native executable with enterprise features enabled.

## JUnit Testing Support

Maven plugin for GraalVM Native Image supports running tests on the [JUnit Platform](https://junit.org/junit5/docs/current/user-guide/) as native executables. This means that tests will be compiled and executed as native code.

This plugin requires JUnit Platform 1.8 or higher and Maven Surefire 2.22.0 or higher to run tests within a native executable.

1. Enable extensions in the plugin's configuration, `<extensions>true</extensions>`:

    ```xml
    <plugin>
        <groupId>org.graalvm.buildtools</groupId>
        <artifactId>native-maven-plugin</artifactId>
        <version>${native.maven.plugin.version}</version>
        <extensions>true</extensions>
    ```

2. Add an explicit dependency on the `junit-platform-launcher` artifact to the dependencies section of your native profile configuration as in the following example:
    ```xml
    <dependencies>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-launcher</artifactId>
            <version>1.8.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    ```

3. Run native tests:

    ```shell
    mvn -Pnative test
    ```
    Run `-Pnative` profile will then build and run native tests.

### Related Documentation

* [Maven plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html)
* [Metadata Collection with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)