# docker-maven-plugin

[![endorse](http://api.coderwall.com/rhuss/endorsecount.png)](http://coderwall.com/rhuss)
[![Build Status](https://secure.travis-ci.org/rhuss/docker-maven-plugin.png)](http://travis-ci.org/rhuss/docker-maven-plugin)
[![Flattr](http://api.flattr.com/button/flattr-badge-large.png)](http://flattr.com/thing/73919/Jolokia-JMX-on-Capsaicin)

This is a Maven plugin for managing Docker images and containers from your builds.

> *This document describes the configuration syntax for version >=
> 0.10.0. For older version (i.e. 0.9.x) please refer to the old
> [documentation](README-0.9.x.md). Migration to the new syntax is not
> difficult and described [separately](UPGRADE-FROM-0.9.x.md)*

* [Introduction](#introduction)
* [User Manual](#user-manual)
  - [Installation](#installation)
  - [Global Configuration](#global-configuration)
  - [Image Configuration](#image-configuration)
  - [Maven Goals](#maven-goals)
    * [`docker:start`](#dockerstart)
    * [`docker:stop`](#dockerstop)
    * [`docker:build`](#dockerbuild)
    * [`docker:push`](#dockerpush)
    * [`docker:remove`](#dockerremove)
* [Examples](#examples)

## Introduction 

It focuses on two major aspects:

* **Building** and pushing Docker images which contains build artifacts
* Starting and stopping Docker container for **integration testing** and
  development

Docker *images* are the central entity which can be configured. 
Containers on the other hand are more or less volatil. They are
created and destroyed on the fly from the configured images and are
completely managed internally.

### Building docker images

One purpose of this plugin is to create docker images holding the
actual application. This can be done with the `docker:build` goal.  It
is easy to include build artifacts and their dependencies into an
image. Therefor this plugin uses the
[assembly descriptor format](http://maven.apache.org/plugins/maven-assembly-plugin/assembly.html)
from the
[maven-assembly-plugin](http://maven.apache.org/plugins/maven-assembly-plugin/)
for specifying the content which will be added below a directory in
the image (`/maven` by default). Image which are build with this
plugin can be also pushed to public or private registries with
`docker:push`.

### Running containers

With this plugin it is possible to run completely isolated integration
tests so you don't need to take care of shared resources. Ports can be
mapped dynamically and made available as Maven properties to you
integration test code. 

Multiple containers can be managed at once which can be linked
together or share data via volumes. Containers are created and started
with the `docker:start` goal and stopped and destroyed with the
`docker:stop` goal. For integrations tests both goals are typically
bound to the the `pre-integration-test` and `post-integration-test`,
respectively. 

For proper isolation container exposed ports can be dynamically and
flexibly mapped to local host ports. It is easy to specify a Maven
property which will be filled in with a dynamically assigned port
after a container has been started and which can then be used as
parameter for integration tests to connect to the application.

### Configuration

You can use a single configuration for all goals (in fact, that's the
recommended way). The configuration contains a general part and a list
of image specific configuration, one for each image. 

The general part contains global configuration like the Docker URL or
the path to the SSL certificates for communication with the Docker Host.

Then, each image configuration has three parts:

* A general part containing the image's name and alias.
* A `<build>` configuration specifying how images are build.
* A `<run>` configuration telling how container should be created and started.

Either `<build>` or `<run>` can be also omitted.

Let's have a look at a plugin configuration example:

````xml
<configuration>
  <images>
    <image>
      <alias>service</alias>
      <name>jolokia/docker-demo:${project.version}</name>

      <build>
         <from>java:8</from>
         <assemblyDescriptor>docker-assembly.xml</assemblyDescriptor>
         <ports>
           <port>8080</port>
         </ports>
         <command>java -jar /maven/service.jar</command>
      </build>

      <run>
         <ports>
           <port>tomcat.port:8080</port>
         </ports>
         <wait>
           <url>http://localhost:${tomcat.port}/access</url>
           <time>10000</time>
         </wait>
         <links>
           <link>database:db</link>
         </links>
       </run>
    </image>

    <image>
      <alias>database</alias>
      <name>postgres:9</name>
      <run>
        <wait>
          <log>database system is ready to accept connections</log>
          <time>20000</time>
        </wait>
      </run>
    </image>
  </images>
</configuration>
````

Here two images are specified. One is the official PostgreSQL 9 image from
Docker Hub, which internally can be referenced as "*database*" (`<alias>`). It
only has a `<run>` section which declares that the startup should wait
until the given text pattern is matched in the log output. Next is a
"*service*" image, which is specified in its `<build>` section. It
creates an image which has artifacts and dependencies in the
`/maven` directory (and which are specified with an assembly
descriptor). Additionally it specifies the startup command for the
container which in this example fires up a microservices from a jar
file just copied over via the assmebly descriptor. Also it exposes
port 8080. In the `<run>` section this port is dynamically mapped to a
port out of the Docker range 49000 ... 49900 and then assigned to the
Maven property `${jolokia.port}`. This property could be used for an
integration test to access this micro service. An important part is
the `<links>` section which tells that the image aliased "*database*" is
linked into the "*service*" container, which can access the internal
ports in the usual Docker way (via environments variables prefixed
with `DB_`). 

Images can be specified in any order, the plugin will take care of the
proper startup order (and will bail out in case of circulara
dependencies). 

### Other highlights

Some other highlights in random order (and not complete):

* Auto pulling of images (with progress indicator)
* Waiting for a container to startup based on time, the reachability
  of an URL or a pattern in the log output
* Support for SSL authentication (since Docker 1.3)
* Specification of encrypted registry passwords for push and pull in
  `~/.m2/settings.xml` (i.e. outside the `pom.xml`)
* Color output ;-)

## User Manual

The following sections describe the installation of this plugin, the
available goals and its configuration options. 

### Installation

This plugin is available from Maven central and can be connected to
pre- and post-integration phase as seen below. The configuration and
available goals are described below. 

````xml
<plugin>
  <groupId>org.jolokia</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>0.10.4</version>

  <configuration>
     ....
     <images>
        <!-- A single's image configuration -->
        <image>
           ....
        </image>
        ....
     </images>
  </configuration>

  <!-- Connect start/stop to pre- and
       post-integration-test phase, respectively if you want to start
       your docker containers during integration tests -->
  <executions>
    <execution>
       <id>start</id>
       <phase>pre-integration-test</phase>
       <goals>
         <!-- "build" should be used to create the images with the
              artefacts --> 
         <goal>build</goal>
         <goal>start</goal>
       </goals>
    </execution>
    <execution>
       <id>stop</id>
       <phase>post-integration-test</phase>
       <goals>
         <goal>stop</goal>
      </goals>
    </execution>
  </executions>
</plugin>
````

### Global configuration

Global configuration parameters specify overall behavior like the
connection to the Docker host. The corresponding system properties
which can be used to set it from the outside are given in
parentheses. 

* **dockerHost** (`docker.host`) Use this variable to specify the URL
  to on your Docker Daemon is listening. This plugin requires the
  usage of the Docker remote API so this must be enabled. If this
  configuration option is not given, the environment variable
  `DOCKER_HOST` is evaluated. If this is also not set the plugin will
  stop with an error. The scheme of this URL can be either given
  directly as `http` or `https` depending on whether plain HTTP
  communication is enabled (< 1.3.0) or SSL should be used (>=
  1.3.0). Or the scheme could be `tcp` in which case the protocol is
  determined via the IANA assigned port: 2375 for `http` and 2376 for
  `https`. 
* **certPath** (`docker.certPath`) Since 1.3.0 Docker remote API requires
  communication via SSL and authentication with certificates. These
  certificates are normally stored
  in `~/.docker/`. With this configuration the path can be set
  explicitly. If not set, the fallback is first taken from the
  environment variable `DOCKER_CERT_PATH` and then as last resort
  `~/.docker/`. The keys in this are expected with it standard names
  `ca.pem`, `cert.pem` and `key.pem`. Please refer to the
  [Docker documentation]() for more information about SSL security
  with Docker. 
* **image** (`docker.image`) In order to temporarily restrict the
  operation of plugin goals this configuration option can be
  used. Typically this will be set via the system property
  `docker.image` when Maven is called. The value can be a single image
  name (either its alias or full name) or it can be a comma separated
  list with multiple image names. Any name which doesn't refer an
  image in the configuration will be ignored. 
* **useColor** (`docker.useColor`)
  If set to `true` the log output of this plugin will be colored. By
  default the output is colored if the build is running with a TTY,
  without color otherwise.
* **skip** (`docker.skip`)
  With this parameter the execution of this plugin can be skipped
  completely. 


Example:

````xml
<configuration>
   <dockerHost>https://localhost:2376</dockerHost>
   <certPath>src/main/dockerCerts</certPath>
   <useColor>true</userColor>
   .....
</configuration>
````

### Image configuration

The plugin's configuration is centered around *images*. These are
specified for each image within the `<images>` element of the
configuration with one `<image>` element per image to use. 

The `<image>` element can contain the following sub elements:

* **name** : Each `<image>` configuration has a mandatory, unique docker
  repository *name*. This can include registry and tag parts, too. For
  definition of the repository name please refer to the
  [Docker documentation]()
* **alias** is a shortcut name for an image which can be used for
  identifying the image within this configuration. This is used when
  linking images together or for specifying it with the global
  **image** configuration.
* **<build** is a complex element which contains all the configuration
  aspects when doing a `docker:build` or `docker:push`. This element
  can be omitted if the image is only pulled from a registry e.g. as
  support for integration tests like database images.
* **run** contains subelement which describe how containers should be
  created and run when `docker:start` or `docker:stop` is called. If
  this image is only used a *data container* for exporting artefacts
  via volumes this section can be missing.

Either `<build>` or `<run>` must be present. They are explained in
detail in the corresponding goal sections.

Example:

````xml
<configuration>
  ....
  <images>
    <image>
      <name>jolokia/docker-demo:0.1</name>
      <alias>service</alias>
      <run>....</run>
      <build>....</build>
    </image>  
  </images>
</configuration>
````

### Maven Goals

This plugin supports the following goals which are explained in detail
in the following sections.

| Goal           | Description                          |
| -------------- | ------------------------------------ |
| `docker:start` | Create and start containers          |
| `docker:stop`  | Stop and destroy containers          |
| `docker:build` | Build images                         |
| `docker:push`  | Push images to a registry            |
| `docker:remove`| Remove images from local docker host |

Note that all goals are orthogonal to each other. For example in order
to start a container for your application you typically have to build
its image before. `docker:start` does **not** imply building the image
so you should use it then in combination with `docker:build`.  

#### `docker:start`

Creates and starts docker containers. This goals evaluates
the configuration's `<run>` section of all given (and enabled images)

The `<run>` configuration knows the following sub elements:

* **command** is a command which should be executed at the end of the
  container's startup. If not given, the image's default command is
  used. 
* **env** can contain environment variables as subelements which are
  set during startup of the container. The are specified in the
  typical maven property format `<env_name>value</env_name>`
  (e.g. `<JAVA_OPTS>-Xmx512m</JAVA_OPTS>`)
* **ports** declares how container exposed ports should be
  mapped. This is described below in an extra
  [section](#port-mapping). 
* **portPropertyFile**, if given, specifies a file into which the
  mapped properties should be written to. The format of this file and
  its purpose are also described [below](#port-mapping)
* **volumes** can contain a list mit `<from>` elements which specify
  image names or aliases from which volumes should be imported.
* **wait** specifies condition which must be fulfilled for the startup
  to complete. See [below](#wait-during-startup) which subelements are
  available and how they can be specified.

Example:

````xml
<run>
  <command>...</command
  <env>
    ...
  </env>
</run>
````
##### Port Mapping

##### Wait during startup

#### Configuration

| Parameter    | Descriptions                                            | Property       | Default                 |
| ------------ | ------------------------------------------------------- | -------------- | ----------------------- |
| **url**      | URL to the docker daemon                                | `docker.url`   | `https://localhost:2376f` |
| **image**    | Name of the docker image (e.g. `jolokia/tomcat:7.0.52`) | `docker.image` | none, required          |
| **ports**    | List of ports to be mapped statically or dynamically.   |                |                         |
| **env**      | Additional environment variables used when creating a container |        |                         |
| **autoPull** | Set to `true` if an yet unloaded image should be automatically pulled | `docker.autoPull` | `true`      |
| **command**  | Command to execute in the docker container              |`docker.command`|                         |
| **assemblyDescriptor**  | Path to the data container assembly descriptor. See below for an explanation and example.              |                |                         |
| **assemblyDescriptorRef** | Predefined assemblies which can be directly used. For possible values, see below. | | |
| **mergeData** | If set to `true` create a new image based on the configured image and containing the assembly as described with `assemblyDescriptor` or `assemblyDescriptorRef` | `docker.mergeData` | `false` |
| **dataBaseImage** | Base for the data image (used only when `mergeData` is false) | `docker.baseImage` | `busybox:latest` |
| **dataImage** | Name to use for the created data image | `docker.dataImage` | `<group>/<artefact>:<version>` |
| **dataExportDir** | Name of the volume which gets exported | `docker.dataExportDir` | `/maven` |
| **authConfig** | Authentication configuration when autopulling images. See below for details. | | |
| **portPropertyFile** | Path to a file where dynamically mapped ports are written to |   |                         |
| **wait**     | Ramp up time in milliseconds                            | `docker.wait`  |                         |
| **waitHttp** | Wait until this URL is reachable with an HTTP HEAD request. Dynamic port variables can be given, too | `docker.waitHttp` | |
| **color**    | Set to `true` for colored output                        | `docker.color` | `true` if TTY connected  |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

### `docker:stop`

Stops and removes a docker container.

#### Configuration

| Parameter  | Descriptions                     | Property       | Default                 |
| ---------- | -------------------------------- | -------------- | ----------------------- |
| **url**    | URL to the docker daemon         | `docker.url`   | `http://localhost:4243` |
| **image** | Which image to stop. All containers for this named image are stopped | `docker.image` |  |
| **keepContainer** | Set to `true` for not automatically removing the container after stopping it. | `docker.keepContainer` | |
| **keepRunning** | Set to `true` for not stopping the container even when this goals runs. | `docker.keepRunning` | `false` |
| **keepData**  | Keep the data container and image after the build if set to `true` | `docker.keepData` |  `false`                       |
| **color**  | Set to `true` for colored output | `docker.color` | `true` if TTY connected |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

### `docker:push`

Push a data image to the registry. The data image is the same created during the `start` goal. See below for more information about how the data image is created. The registry to push is by
default `registry.hub.docker.io` but can be specified as part of the `dataImage` name the Docker way. E.g. `docker.test.org:5000/data:1.5` will push the repository `data` with tag `1.5` to
the registry `docker.test.org` at port `5000`. Security information (i.e. user and password) can be specified in multiple ways as described in an extra section.

#### Configuration

| Parameter    | Descriptions                                            | Property       | Default                 |
| ------------ | ------------------------------------------------------- | -------------- | ----------------------- |
| **url**      | URL to the docker daemon                                | `docker.url`   | `http://localhost:2375` |
| **image**    | Name of the docker base image (e.g. `consol/tomcat:7.0.52`) | `docker.image` | none         |
| **autoPull** | Set to `true` if an yet unloaded image should be automatically pulled | `docker.autoPull` | `true`      |
| **assemblyDescriptor**  | Path to the data container assembly descriptor. See below for an explanation and example               |                |                         |
| **assemblyDescriptorRef** | Predefined assemblies which can be directly used. Possible values are given below | | |
| **mergeData** | If set to `true` create a new image based on the configured image and containing the assembly as described with `assemblyDescriptor` or `assemblyDescriptorRef` | `docker.mergeData` | `false` |
| **dataBaseImage** | Base for the data image (used only when `mergeData` is false) | `docker.baseImage` | `busybox:latest` |
| **dataImage** | Name to use for the created data image | `docker.dataImage` | `<group>/<artefact>:<version>` |
| **dataExportDir** | Name of the volume which gets exported | `docker.dataExportDir` | `/maven` |
| **keepData**  | Keep the data image after the build if set to `true` | `docker.keepData` |  `true`                       |
| **authConfig** | Authentication configuration when pushing images. See below for details. | | |
| **color**    | Set to `true` for colored output                        | `docker.color` | `true` if TTY connected  |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

### `docker:build`

Build a data image without pushing. It works essentially the same as `docker:push` but does not push to a registry
and does not delete the image afterwards.

#### Configuration

| Parameter    | Descriptions                                            | Property       | Default                 |
| ------------ | ------------------------------------------------------- | -------------- | ----------------------- |
| **url**      | URL to the docker daemon                                | `docker.url`   | `http://localhost:2375` |
| **image**    | Name of the docker base image (e.g. `consol/tomcat:7.0.52`) | `docker.image` | none         |
| **autoPull** | Set to `true` if an yet unloaded base image should be automatically pulled | `docker.autoPull` | `true`      |
| **assemblyDescriptor**  | Path to the data container assembly descriptor. See below for an explanation and example               |                |                         |
| **assemblyDescriptorRef** | Predefined assemblies which can be directly used. Possible values are given below | | |
| **mergeData** | If set to `true` create a new image based on the configured image and containing the assembly as described with `assemblyDescriptor` or `assemblyDescriptorRef` | `docker.mergeData` | `false` |
| **dataBaseImage** | Base for the data image (used only when `mergeData` is false) | `docker.baseImage` | `busybox:latest` |
| **dataImage** | Name to use for the created data image | `docker.dataImage` | `<group>/<artefact>:<version>` |
| **dataExportDir** | Name of the volume which gets exported | `docker.dataExportDir` | `/maven` |
| **ports**    | List of ports to be exposed                             |                |  |
| **env**      | List of environment variables to use for building       |                |  |
| **color**    | Set to `true` for colored output                        | `docker.color` | `true` if TTY connected  |
| **skip**     | If set to `true` skip the execution of this goal        | `docker.skip`  |                          |

## Dynamic Port mapping

For the `start` goal, container port mapping may be configured using a `ports` declaration.

```xml
<ports>
  <port>18080:8080</port>
  <port>host.port:80</port>
<ports>
```

A `port` stanza may take one of two forms:
* A tuple consisting of two numeric values separated by a `:`. This form will result in an explicit mapping between the docker host and the corresponding port inside the container. In the above example, port 18080 would be exposed on the docker host and mapped to port 8080 in the running container.
* A tuple consisting of a string and a numeric value separated by a `:`. In this form, the string portion of the tuple will correspond to a Maven property. If the property is undefined when the `start` task executes, a port will be dynamically selected by Docker in the range 49000 ... 49900 and assigned to the property which may then be used later in the same POM file. If the property exists and has a numeric value, that value will be used as the exposed port on the docker host as in the previous form. In the above example, the docker service will elect a new port and assign the value to the property `host.port` which may then later be used in a property expression similar to `<value>${host.port}</value>`. This can be used to pin a port from the outside when doing some initial testing similar to:

    mvn -Dhost.port=10080 docker:start

Another useful configuration option is `portPropertyFile` with which a file can be specified to which the real port
mapping is written after all dynamic ports has been resolved. The keys of this property file are the variable names,
the values are the dynamically assgined host ports. This property file might be useful together with other maven
plugins which already resolved their maven variables earlier in the lifecycle than this plugin so that the port variables
might not be available to them.

## Setting environment variables

When creating a container one or more environment variables can be set via configuration with the `env` parameter

```xml
<env>
  <JAVA_HOME>/opt/jdk8</JAVA_HOME>
  <CATALINA_OPTS>-Djava.security.egd=file:/dev/./urandom</CATALINA_OPTS>
</env>
```

If you put this configuration into profiles you can easily create various test variants with a single image (e.g. by
switching the JDK or whatever).

## Getting your assembly into the container

With using the `assemblyDescriptor` or `assemblyDescriptorRef` option it is possible to bring local files, artifacts and dependencies into the running Docker container. This works as follows:

* `assemblyDescriptor` points to a file describing the data to assemble. It has the same format as for creating assemblies with the [maven-assembly-plugin](http://maven.apache.org/plugins/maven-assembly-plugin/) , with some restrictions (see below).
* Alternatively `assemblyDescriptorRef` can be used with the name of a predefined assembly descriptor. See below for possible values.
* This plugin will create the assembly and create a Docker image on the fly which exports the assembly below a directory `/maven`. Typically this will be an extra image, but if the configuration parameter `mergeData` is set then the image which was configured for the `start` goal is used as a base image so that the data and e.g. application server are contained in the same image. This is useful for distributing a complete image where artifacts and the server are baked together.
* From this image a (data) container is created and the 'real' container is started with a `volumesFrom` option pointing to this data container (if `mergeData` is not used).
* That way, the container started has access to all the data created from the directory `/maven/` within the container.
* The container command can check for the existence of this directory and deploy everything within this directory.

Let's have a look at an example. In this case, we are deploying a war-dependency into a Tomcat container. The assembly descriptor `src/main/docker-assembly.xml` option may look like

````xml
<assembly xmlns="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.2"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.2
                        http://maven.apache.org/xsd/assembly-1.1.2.xsd">
  <dependencySets>
    <dependencySet>
      <includes>
        <include>org.jolokia:jolokia-war</include>
      </includes>
      <outputDirectory>.</outputDirectory>
      <outputFileNameMapping>jolokia.war</outputFileNameMapping>
    </dependencySet>
</assembly>
````

Then you will end up with a data container which contains with a file `/maven/jolokia.war` which is mirrored into the main container.

The plugin configuration could look like

````xml
<plugin>
    <groupId>org.jolokia</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    ....
    <configuration>
      <image>jolokia/tomcat-7.0</image>
      <assemblyDescriptor>src/main/docker-assembly.xml</assemblyDescriptor>
      ...
    </configuration>
</plugin>
````

The image `jolokia/tomcat-7.0` is a [trusted build](https://github.com/rhuss/jolokia-it/tree/master/docker/tomcat/7.0) available from the central docker registry which uses a command `deploy-and-run.sh` that looks like this:

````bash
#!/bin/sh

DIR=${DEPLOY_DIR:-/maven}
echo "Checking *.war in $DIR"
if [ -d $DIR ]; then
  for i in $DIR/*.war; do
     file=$(basename $i)
     echo "Linking $i --> /opt/tomcat/webapps/$file"
     ln -s $i /opt/tomcat/webapps/$file
  done
fi
/opt/tomcat/bin/catalina.sh run
````

Before starting tomcat, this script will link every .war file it finds in `/maven` to `/opt/tomcat/webapps` which effectively will deploy them.

Alternatively, the parameter `mergeData` could have been set to `true` in the plugin configuration. In this case no separate data image is created but an image which is based on the specified image (`jolokia/tomcat-7.0` in this example) and the assembly are directly available from `/maven`. This has the advantage that only a single image needs to be pushed containing  both, the created artifact and application server.

It is really that easy to deploy your artifacts. And it's fast (less than 10s for starting, deploying, testing (1 test) and stopping the container on my 4years old MBP using boot2docker).

### Assembly Descriptor

The assembly descriptor has the same [format](http://maven.apache.org/plugins/maven-assembly-plugin/assembly.html) as the the maven-assembly-plugin with the following exceptions:

* `<formats>` are ignored, the assembly will allways use a directory when preparing the data container (i.e. the format is fixed to `dir`)
* The `<id>` is ignored since only a single assembly descriptor is used (no need to distinguish multiple descriptors)

This `docker-maven-plugin` comes with some predefined assembly descriptors which can be used with `assemblyDescritproRef`:

* **artifact-with-dependencies** will copy your project's artifact and all its dependencies
* **artifact** will copy only the project's artifact but no dependencies.
* **project** will copy over the whole Maven project but with out `target/` directory.
* **rootWar** will copy the artifact as `ROOT.war` to the exposed directory. I.e. Tomcat will then deploy the war under the root context.

## Cleanup

Various configuration parameters of this plugin are available for cleaning up after a build:

* `keepRunning` specifies that the container should not be stopped after the build. Obviously, the container and any data image created will be left alone as well. This option is especially useful when given as command line option `-Ddocker.keepRunning` for doing some debugging or developing integration tests.

* `keepContainer` tells the plugin to not remove the container created from the image after the build (the container is stopped, though). If a merged container was created via the option `mergeData` then this container will remain as well as the on-the-fly created image this container belongs to. This is useful for post-mortem analysis of the container by e.g. looking at the logs. This option can be switched on with `-Ddocker.keepContainer`. If a separate data container is used, this data container and its image will stay as well.

* `keepData` finally can be used to keep only the data container, but the other container should be be removed. This option has only an effect if `keepContainer` is `false`. That way, the created artifacts can be kept even after the build.

## Authentication

When pulling (via the `autoPull` mode of `docker:start` and `docker:push`) or pushing image, it might be necessary to authenticate against a Docker registry.

There are three different ways for providing credentials:

* Using a `<authConfig>` section in the plugin configuration with `<username>` and `<password>` elements.
* Providing system properties `docker.username` and `docker.password` from the outside
* Using a `<server>` configuration in the the `~/.m2/settings.xml` settings

Using the username and password directly in the `pom.xml` is not recommended since this is widely visible. This is most easiest and transparent way, though. Using an `<authConfig>` is straight forward:

````xml
<plugin>
  <configuration>
     <image>consol/tomcat-7.0</image>
     ...
     <authConfig>
         <username>jolokia</username>
         <password>s!cr!t</password>
     </authConfig>
  </configuration>
</plugin>
````

The system property provided credentials are a good compromise when using CI servers like Jenkins. You simply provide the credentials from the outside:

	mvn -Ddocker.username=jolokia -Ddocker.password=s!cr!t docker:push

The most secure and also the most *mavenish* way is to add a server to the Maven settings file `~/.m2/settings.xml`:

````xml
<servers>
  <server>
    <id>registry.hub.docker.io</id>
    <username>jolokia</username>
    <password>s!cr!t</password>
  </server>
  ....
</servers>
````

The server id must specify the registry to push to/pull from, which by default is central index `registry.hub.docker.io`. Here you should add you docker.io account for your repositories.

### Password encryption

Regardless which mode you choose you can encrypt password as described in the [Maven documentation](http://maven.apache.org/guides/mini/guide-encryption.html). Assuming that you have setup a *master password* in `~/.m2/security-settings.xml` you can create easily encrypted passwords:

````bash
	$ mvn --encrypt-password
	Password:
	{QJ6wvuEfacMHklqsmrtrn1/ClOLqLm8hB7yUL23KOKo=}
````

This password then can be used in `authConfig`, `docker.password` and/or the `<server>` setting configuration. However, putting an encrypted password into `authConfig` in the `pom.xml` doesn't make much sense, since this password is encrypted with an individual master password.

## SSL with keys and certificates

The plugin can communicate with the Docker Host via SSL, too. This is the default now for Docker 1.3 (and Boot2Docker).
SSL is switched on if the port used is `2376` which is the default, IANA registered SSL port of the Docker host
(and plain HTTP for `2375`). The directory holding `ca.pem`, `key.pem` and `cert.pem` can be configured with the
configuration parameter `certPath`. Alternatively, the environment variable `DOCKER_CERT_PATH` is evaluated and finally
`~/.docker` is used as the last fallback.

## Examples

This plugin comes with some commented examples in the `samples/` directory:

* [data-jolokia-demo](https://github.com/rhuss/docker-maven-plugin/tree/master/samples/data-jolokia-demo) is a setup for testing the [Jolokia](http://www.jolokia.org) HTTP-JMX bridge in a tomcat. It uses a Docker data container which is linked into the Tomcat container and contains the WAR files to deply
* [cargo-jolokia-demo](https://github.com/rhuss/docker-maven-plugin/tree/master/samples/cargo-jolokia-demo) is the same as above except that Jolokia gets deployed via [Cargo](http://cargo.codehaus.org/Maven2+plugin)

For a complete example please refer to `samples/data-jolokia-demo/pom.xml`.

In order to prove, that self contained builds are not a fiction, you might convince yourself by trying out this (on a UN*X like system):

````bash
# Move away your local maven repository for a moment
cd ~/.m2/
mv repository repository.bak

# Fetch docker-maven-plugin
cd /tmp/
git clone https://github.com/rhuss/docker-maven-plugin.git
cd docker-maven-plugin/

# Install plugin
# (This is only needed until the plugin makes it to maven central)
mvn install

# Goto the sample
cd samples/data-jolokia-demo

# Run the integration test
mvn verify

# Use a 'merged' data image
mvn -Pmerge-data verify

# Push the data image
mvn docker:push

# Please note, that first it will take some time to fetch the image
# from docker.io. The next time running it will be much faster.

# Restore back you .m2 repo
cd ~/.m2
mv repository /tmp/
mv repository.bak repository
````

## Misc

* [Script](https://gist.github.com/deinspanjer/9215467) for setting up NAT forwarding rules when using [boot2docker](https://github.com/boot2docker/boot2docker)
on OS X

* It is recommended to use the `maven-failsafe-plugin` for integration testing in order to
stop the docker container even when the tests are failing.

## Why another docker-maven-plugin ?

Spring feelings in 2014 seems to be quite fertile for the Java crowd's
Docker awareness
;-). [Not only I](https://github.com/bibryam/docker-maven-plugin/issues/1)
counted ~~5~~ 10 [maven-docker-plugins](https://github.com/search?q=docker-maven-plugin)
on GitHub as of ~~April~~ July 2014, tendency increasing. It seems, that all
of them have a slightly different focus, but all of them can do the
most important tasks: Starting and stopping containers.

So you might wonder, why I started this plugin if there were already
quite some out here ?

The reason is quite simple: I didn't knew them when I started and if
you look at the commit history you will see that they all started
their life roughly at the same time (March 2014).

I expect there will be some settling soon and even some merging of
efforts which I would highly appreciate and support.

For what it's worth, here are some of my motivations for this plugin
and what I want to achieve:

* I needed a flexible, **dynamic port mapping** from container to host
  ports so that truly isolated build can be achieved. This should
  work on indirect setups with VMs like
  [boot2docker](https://github.com/boot2docker/boot2docker) for
  running on OS X.

* It should be possible to **pull images** on the fly to get
  self-contained and repeatable builds with the only requirement to
  have docker installed.

* The configuration of the plugin should be **simple** since usually
  developers don't want to dive into specific Docker details only to
  start a container. So, only a handful options should be exposed
  which needs not necessarily map directly to docker config setup.

* The plugin should play nicely with
  [Cargo](http://cargo.codehaus.org/) so that deployments into
  containers can be easy.

* I want as **less dependencies** as possible for this plugin. So I
  decided to *not* use the
  Java Docker API [docker-java](https://github.com/docker-java/docker-java) which is
  external to docker and has a different lifecycle than Docker's
  [remote API](http://docs.docker.io/en/latest/reference/api/docker_remote_api/).
  That is probably the biggest difference to the other
  docker-maven-plugins since AFAIK they all rely on this API. Since
  for this plugin I really need only a small subset of the whole API,
  I think it is ok to do the REST calls directly. That way I only have
  to deal with Docker peculiarities and not also with docker-java's
  one. As a side effect this plugin has less transitive dependencies.
  FYI: There is now yet another Docker Java client library out, which
  might be used for plugins like this, too:
  [fabric-docker-api](https://github.com/fabric8io/fabric8/tree/master/fabric/fabric-docker-api). (Just
  in case somebody wants to write yet another plugin ;-)

In the meantime, enjoy this plugin, and please use the
[issue tracker](https://github.com/rhuss/docker-maven-plugin/issues)
for anything what hurts.

