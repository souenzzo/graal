---
layout: ni-docs
toc_group: native-image-guides
link_title: Use Native Image Maven Plugin
permalink: /reference-manual/native-image/guides/use-native-image-maven-plugin/
---

# Use Maven to Convert Java Applications into Native Executable

There is a Maven plugin for GraalVM Native Image to help package Java applications into native executables, besides runnable JARs, at one step. 
It is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses Apache Maven™. 
The plugin makes use of Maven profiles to enable building and testing of native executables.

In this guide you will learn how to enable Native Image Maven plugin to convert a Java application into a native executable, run JUnit tests, and add support for dynamic feature calls.

You will use the **Fortune demo** which is a Java program that simulates the traditional [fortune Unix program](https://en.wikipedia.org/wiki/Fortune_(Unix)). 
The data for the fortune phrases is provided by [YourFortune](https://github.com/your-fortune).

## Prepare a Demo Application

> You are expected to have [GraalVM installed with Native Image support](../getting-started.md). 

1. Crate a new Java project with **Maven** in your favourite IDE, called "Fortune". The application should contain a sole Java file with the following content:

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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.2.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                            <classpathPrefix>lib/</classpathPrefix>
                            <mainClass>specify your path</mainClass>
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
                            <mainClass>specify your path</mainClass>
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

    > Note: The `<mainClass>...</mainClass` value should provide the path to the main class, the same path as specified in `<exec.mainClass>...<exec.mainClass>` in the project configuration. 

4. Test packaging and running the application. From the root application directory, execute:

    ```shell
    mvn clean package
    ```
    When the build succeeds, go ahead and build a native version of this application with GraalVM Native Image and Maven.

## Build a Java Application into a Native Executable with Maven

1. Register the Maven plugin for GraalVM Native Image by add the following profile in _pom.xml_:
    ```xml
    <profiles>
        <profile>
            <id>native</id>
            <build>
            <plugins>
                <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <version>0.9.11</version>
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
                        <skip>false</skip>
                        <imageName>fortune</imageName>
                        <buildArgs>
                            --no-fallback
                        </buildArgs>
                    </configuration>
                </plugin>
            </plugins>
            </build>
        </profile>
    </profiles>
    ```

2. Then build a native executable directly with Maven:
    ```shell
    mvn -Pnative package
    ```
    The plugin figures out which JAR files it needs to pass to the `native-image` builder and what the executable main class should be. When `mvn -Pnative package` completes, a native executable is ready for use, generated in the `/target` directory of the project.
    
    This command will also run tests on the JVM with Maven Surefire. If you wish to skip testing within a native executable, pass the `-DskipNativeTests` option:

    ```shell
    mvn -Pnative -DskipTests package
    ```

    In around ~2 minutes, find a native executable version of your application in the `/target` directory. In this case, it is called `fortune`.

3. Run the application as a native executable:

    ```shell
    ./target/fortune
    ```
    It will print out a random saying with each invocation. 

If you use GraalVM Enterprise as the `JAVA_HOME` environment, the plugin builds a native executable with enterprise features enabled.

## Testing Support

Maven plugin for GraalVM Native Image also supports running tests on the [JUnit Platform](https://junit.org/junit5/docs/current/user-guide/) as native executables. This means that tests will be compiled and executed as native code.

This plugin requires JUnit Platform 1.8 or higher and Maven Surefire 2.22.0 or higher to run tests within a native executable.

1. To activate JUnit testing, enable extensions in the plugin's configuration, `<extensions>true</extensions>`. For example:

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

## Add Support for Tracing Agent

The provided demo does not call any dynamic features at run time, thus does not require to register those calls in the configuration file(s). 
However, in the real world use cases, your application will, most likely, call either the Java Native Interface (JNI), or Java Reflection, or Dynamic Proxy objects (`java.lang.reflect.Proxy`), or class path resources (`Class.getResource`) - the dynamic features that need to be provided to the `native-image` tool in the form of configuration files.

The Native Image Maven plugin will pick up all your application configuration file(s) stored below the `META-INF/native-image/` resource location by default. 
But how do you get these configuration file(s)? GraalVM provides  a **tracing agent** that tracks all usages of dynamic features during the execution on a regular Java VM and writes them down to configuration file(s). 

The agent is disabled by default, but it can be enabled within _pom.xml_ or via the command line.

- To enable the agent via _pom.xml_, specify `<enabled>true</enabled>` as follows in the configuration of the `native-maven-plugin` plugin:

    ```xml
    <configuration>
    <agent>
        <enabled>true</enabled>
    </agent>
    </configuration>
    ```

- To enable the agent via the command line, pass the `-Dagent=true` flag when running Maven.

### Related Documentation

* [Maven plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html)
* [Metadata Collection with the Tracing Agent](../AssistedConfiguration.md)