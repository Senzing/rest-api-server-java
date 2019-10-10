# senzing-api-server

## Overview

The Senzing Rest API Server implemented in Java.  The API specification is
defined in by the [Senzing Rest API Proposal](https://github.com/Senzing/senzing-rest-api).

The [Senzing API OAS specification](http://editor.swagger.io/?url=https://raw.githubusercontent.com/Senzing/senzing-rest-api/master/senzing-rest-api.yaml)
documents the available API methods, their parameters and the response formats.

### Related artifacts

1. [DockerHub](https://hub.docker.com/r/senzing/senzing-api-server)
1. [Helm Chart](https://github.com/Senzing/charts/tree/master/charts/senzing-api-server)

### Contents

1. [Demonstrate using Command Line](#demonstrate-using-command-line)
    1. [Dependencies](#dependencies)
    1. [Building](#building)
    1. [Running](#running)
1. [Demonstrate using Docker](#demonstrate-using-docker)
    1. [Expectations for docker](#expectations-for-docker)
    1. [Initialize Senzing](#initialize-senzing)
    1. [Configuration](#configuration)
    1. [Volumes](#volumes)
    1. [Docker network](#docker-network)
    1. [External database](#external-database)
    1. [Docker user](#docker-user)
    1. [Run docker container](#run-docker-container)
    1. [Test docker container](#test-docker-container)
1. [Develop](#develop)
    1. [Prerequisite software](#prerequisite-software)
    1. [Clone repository](#clone-repository)
    1. [Build docker image for development](#build-docker-image-for-development)
1. [Examples](#examples)
1. [Errors](#errors)
1. [References](#references)

## Demonstrate using Command Line

### Dependencies

To build the Senzing REST API Server you will need Apache Maven (recommend version 3.5.4 or later)
as well as Java 1.8.x (recommend version 1.8.0_171 or later).

You will also need the Senzing "g2.jar" file installed in your Maven repository.
The Senzing REST API Server requires version 1.7.x or later of the Senzing API
and Senzing App.  In order to install g2.jar you must:

 1. Locate your [`${SENZING_G2_DIR}` directory](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/create-senzing-dir.md).
    The default locations are:
    - Linux Archive Extraction: `/opt/senzing/g2` (see [Install Instructions](https://github.com/Senzing/hello-senzing-springboot-java/blob/master/doc/debian-based-installation.md#install))
    - Windows MSI Installer: `C:\Program Files\Senzing\`

 1. Determine your `${SENZING_VERSION}` version number:
    - Locate your `g2BuildVersion.json` file:
        - Linux: `${SENZING_G2_DIR}/data/g2BuildVersion.json`
        - Windows: `${SENZING_G2_DIR}\data\g2BuildVersion.json`
    - Find the value for the `"VERSION"` property in the JSON contents.
      Example:

        ```console
        {
            "PLATFORM": "Linux",
            "VERSION": "1.7.19095",
            "BUILD_NUMBER": "2019_04_05__02_00"
        }
        ```

 1. Install the g2.jar file in your local Maven repository, replacing the
    `${SENZING_G2_DIR}` and `${SENZING_VERSION}` variables as determined above:

     - Linux:

       ```console
             export SENZING_G2_DIR=/opt/senzing/g2
             export SENZING_VERSION=1.7.19095

             mvn install:install-file \
                 -Dfile=${SENZING_G2_DIR}/lib/g2.jar \
                 -DgroupId=com.senzing \
                 -DartifactId=g2 \
                 -Dversion=${SENZING_VERSION} \
                 -Dpackaging=jar
       ```

     - Windows:

       ```console
             set SENZING_G2_DIR="C:\Program Files\Senzing\g2"
             set SENZING_VERSION=1.7.19095

             mvn install:install-file \
                 -Dfile="%SENZING_G2_DIR%\lib\g2.jar" \
                 -DgroupId=com.senzing \
                 -DartifactId=g2 \
                 -Dversion="%SENZING_VERSION%" \
                 -Dpackaging=jar
       ```

 1. Setup your environment.  The API's rely on native libraries and the
    environment must be properly setup to find those libraries:

    - Linux

       ```console
          export SENZING_G2_DIR=/opt/senzing/g2

          export LD_LIBRARY_PATH=${SENZING_G2_DIR}/lib:${SENZING_G2_DIR}/lib/debian:$LD_LIBRARY_PATH
       ```

    - Windows

      ```console
          set SENZING_G2_DIR="C:\Program Files\Senzing\g2"

          set Path=%SENZING_G2_DIR%\lib;%Path%
      ```

### Building

To build simply execute:

   ```console
      mvn install
   ```

The JAR file will be contained in the `target` directory under the name `senzing-api-server-[version].jar`.

Where `[version]` is the version number from the `pom.xml` file.

### Running

To execute the server you will use `java -jar`.  It assumed that your environment
is properly configured as described in the "Dependencies" section above.

To start up you must provide the initialization parameters for the Senzing
native API.  This is done through one of: `-initFile`, `-initEnvVar` or the
`-initJson` options to specify how to obtain the initialization JSON parameters.
The `G2CONFIGFILE` path is excluded from the initialization parameters in favor
of loading the default configuration that has been set for the repository.

The deprecated `-iniFile` option can also be used to startup with a deprecated
INI file with a `G2CONFIGFILE` parameter referencing a configuration on the
file system.  However, when starting up this way you do not get auto
reinitialization of the configuration when it changes (i.e.: when the default
configuration changes) and you will be responsible for keeping the configuration
in sync across multiple processes that may be using it.

***NOTE:*** *In lieu of using `java -jar` directly and the `-iniFile` option to
specify your entity repository, you can use the
[Senzing App Integration Scripts](./app-scripts/README.md) to start the
Senzing REST API Server using an entity repository from the
[Senzing app](https://senzing.com/#download).  The scripts are provided in the
`app-scripts` sub-directory.  See the [associated README.md file](./app-scripts/README.md)
for version compatibility and usage information.*

Other command-line options may be useful to you as well.  Execute
`java -jar target/senzing-api-server-1.7.2.jar -help` to obtain a help message
describing all available options.  For example:

  ```console

    $ java -jar target/senzing-api-server-1.7.2.jar -help

    java -jar senzing-api-server-1.7.2.jar <options>

    <options> includes:

    [ Standard Options ]

       -help
            Should be the first and only option if provided.
            Causes this help message to be displayed.
            NOTE: If this option is provided, the server will not start.

       -version
            Should be the first and only option if provided.
            Causes the version of the G2 REST API Server to be displayed.
            NOTE: If this option is provided, the server will not start.

       -httpPort <port-number>
            Sets the port for HTTP communication.  Defaults to 2080.
            Specify 0 for a randomly selected port number.

       -bindAddr <ip-address|loopback|all>
            Sets the port for HTTP bind address communication.
            Defaults to the loopback address.

       -allowedOrigins <url-domain>
            Sets the CORS Access-Control-Allow-Origin header for all endpoints.
            There is no default value.

       -concurrency <thread-count>
            Sets the number of threads available for executing
            Senzing API functions (i.e.: the number of engine threads).
            If not specified, then this defaults to 8.

       -moduleName <module-name>
            The module name to initialize with.  Defaults to 'ApiServer'.

       -iniFile <ini-file-path>
            The path to the Senzing INI file to with which to initialize.
            *** DEPRECATED: Use -initFile, -initEnvVar or -initJson instead.

       -initFile <json-init-file>
            The path to the file containing the JSON text to use for Senzing
            initialization.

       -initEnvVar <environment-variable-name>
            The environment variable from which to extract the JSON text
            to use for Senzing initialization.
            *** SECURITY WARNING: If the JSON text contains a password
            then it may be visible to other users via process monitoring.

       -initJson <json-init-text>
            The JSON text to use for Senzing initialization.
            *** SECURITY WARNING: If the JSON text contains a password
            then it may be visible to other users via process monitoring.

       -configId <config-id>
            Use with the -initFile, -initEnvVar or -initJson options to
            force a specific configuration ID to use for initialization.
            NOTE: This will disable the auto-detection of config changes

       -verbose If specified then initialize in verbose mode.

       -monitorFile [filePath]
            Specifies a file whose timestamp is monitored to determine
            when to shutdown.

    [ Advanced Options ]

       --configmgr [config manager options]...
            Should be the first option if provided.  All subsequent options
            are interpreted as configuration manager options.  If this option
            is specified by itself then a help message on configuration manager
            options will be displayed.
            NOTE: If this option is provided, the server will not start.

  ```

If you wanted to run the server on port 8080 and bind to all
network interfaces with a concurrency of 16 you would use:

  ```console
     java -jar target/senzing-api-server-[version].jar \
        -concurrency 16 \
        -httpPort 8080 \
        -bindAddr all \
        -initFile ~/senzing/data/g2-init.json
  ```

#### Restart for Configuration Changes

It is important to note that the Senzing configuration is currently read by the
Senzing API Server on startup.  If the configuration changes, the changes will
not be detected until the Server is restarted.  This may cause stale values to
be returned from some operations and may cause other operations to completely
fail.

Be sure to restart the API server when the configuration changes to guarantee
stability and accurate results from the API server.

## Demonstrate using Docker

### Expectations for docker

#### Space for docker

This repository and demonstration require 6 GB free disk space.

#### Time for docker

Budget 40 minutes to get the demonstration up-and-running, depending on CPU and network speeds.

#### Background knowledge for docker

This repository assumes a working knowledge of:

1. [Docker](https://github.com/Senzing/knowledge-base/blob/master/WHATIS/docker.md)

### Initialize Senzing

1. If Senzing has not been initialized, visit
   [HOWTO - Initialize Senzing](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/initialize-senzing.md).

### Configuration

Configuration values specified by environment variable or command line parameter.

- **[SENZING_API_SERVICE_PORT](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_api_service_port)**
- **[SENZING_DATA_VERSION_DIR](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_data_version_dir)**
- **[SENZING_DATABASE_URL](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_database_url)**
- **[SENZING_DEBUG](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_debug)**
- **[SENZING_ETC_DIR](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_etc_dir)**
- **[SENZING_G2_DIR](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_g2_dir)**
- **[SENZING_NETWORK](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_network)**
- **[SENZING_RUNAS_USER](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_runas_user)**
- **[SENZING_VAR_DIR](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_var_dir)**

### Volumes

:thinking: The output of
[HOWTO - Initialize Senzing](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/initialize-senzing.md)
placed files in different directories.
Identify each output directory.

1. :pencil2: **Option #1**
   To mimic an actual RPM installation,
   identify directories for RPM output in this manner:

    ```console
    export SENZING_DATA_VERSION_DIR=/opt/senzing/data/1.0.0
    export SENZING_ETC_DIR=/etc/opt/senzing
    export SENZING_G2_DIR=/opt/senzing/g2
    export SENZING_VAR_DIR=/var/opt/senzing
    ```

1. :pencil2: **Option #2**
   If Senzing directories were put in alternative directories,
   set environment variables to reflect where the directories were placed.
   Example:

    ```console
    export SENZING_VOLUME=/opt/my-senzing

    export SENZING_DATA_VERSION_DIR=${SENZING_VOLUME}/data/1.0.0
    export SENZING_ETC_DIR=${SENZING_VOLUME}/etc
    export SENZING_G2_DIR=${SENZING_VOLUME}/g2
    export SENZING_VAR_DIR=${SENZING_VOLUME}/var
    ```

1. :thinking: If internal database is used, permissions may need to be changed in `/var/opt/senzing`.
   Example:

    ```console
    sudo chmod -R 777 ${SENZING_VAR_DIR}
    ```

1. :thinking: Unless previously created, the following files need to be created from their templates.
   Example:

    ```console
    sudo cp --no-clobber ${SENZING_ETC_DIR}/cfgVariant.json.template ${SENZING_ETC_DIR}/cfgVariant.json
    sudo cp --no-clobber ${SENZING_ETC_DIR}/g2config.json.template   ${SENZING_ETC_DIR}/g2config.json
    sudo cp --no-clobber ${SENZING_ETC_DIR}/G2Module.ini.template    ${SENZING_ETC_DIR}/G2Module.ini
    sudo cp --no-clobber ${SENZING_ETC_DIR}/stb.config.template      ${SENZING_ETC_DIR}/stb.config
    ```

### Docker network

:thinking: **Optional:**  Use if docker container is part of a docker network.

1. List docker networks.
   Example:

    ```console
    sudo docker network ls
    ```

1. :pencil2: Specify docker network.
   Choose value from NAME column of `docker network ls`.
   Example:

    ```console
    export SENZING_NETWORK=*nameofthe_network*
    ```

1. Construct parameter for `docker run`.
   Example:

    ```console
    export SENZING_NETWORK_PARAMETER="--net ${SENZING_NETWORK}"
    ```

### External database

:thinking: **Optional:**  Use if storing data in an external database.

1. :pencil2: Specify database.
   Example:

    ```console
    export DATABASE_PROTOCOL=postgresql
    export DATABASE_USERNAME=postgres
    export DATABASE_PASSWORD=postgres
    export DATABASE_HOST=senzing-postgresql
    export DATABASE_PORT=5432
    export DATABASE_DATABASE=G2
    ```

1. Construct Database URL.
   Example:

    ```console
    export SENZING_DATABASE_URL="${DATABASE_PROTOCOL}://${DATABASE_USERNAME}:${DATABASE_PASSWORD}@${DATABASE_HOST}:${DATABASE_PORT}/${DATABASE_DATABASE}"
    ```

1. Construct parameter for `docker run`.
   Example:

    ```console
    export SENZING_DATABASE_URL_PARAMETER="--env SENZING_DATABASE_URL=${SENZING_DATABASE_URL}"
    ```

### Docker user

:thinking: **Optional:**  The docker container runs as "USER 1001".
Use if a different userid is required.

1. :pencil2: Identify user.
   User "0" is root.
   Example:

    ```console
    export SENZING_RUNAS_USER="0"
    ```

1. Construct parameter for `docker run`.
   Example:

    ```console
    export SENZING_RUNAS_USER_PARAMETER="--user ${SENZING_RUNAS_USER}"
    ```

### Run docker container

1. :pencil2: Set environment variables.
   Example:

    ```console
    export SENZING_API_SERVICE_PORT=8250
    ```

1. Run docker container.
   Example:

    ```console
    sudo docker run \
      ${SENZING_RUNAS_USER_PARAMETER} \
      ${SENZING_DATABASE_URL_PARAMETER} \
      ${SENZING_NETWORK_PARAMETER} \
      --interactive \
      --publish ${SENZING_API_SERVICE_PORT}:8250 \
      --rm \
      --tty \
      --volume ${SENZING_DATA_VERSION_DIR}:/opt/senzing/data \
      --volume ${SENZING_ETC_DIR}:/etc/opt/senzing \
      --volume ${SENZING_G2_DIR}:/opt/senzing/g2 \
      --volume ${SENZING_VAR_DIR}:/var/opt/senzing \
      senzing/senzing-api-server \
        -allowedOrigins "*" \
        -bindAddr all \
        -concurrency 10 \
        -httpPort 8250 \
        -iniFile /etc/opt/senzing/G2Module.ini
    ```

### Test Docker container

1. Wait for the following message in the terminal showing docker log.

    ```console
    Started Senzing REST API Server on port 8250.

    Server running at:

    http://0.0.0.0/0.0.0.0:8250/
    ```

1. Test Senzing REST API server.
   *Note:* port 8250 on the localhost has been mapped to port 8250 in the docker container.
   See `SENZING_API_SERVICE_PORT` definition.
   Example:

    ```console
    export SENZING_API_SERVICE=http://localhost:8250

    curl -X GET ${SENZING_API_SERVICE}/heartbeat
    curl -X GET ${SENZING_API_SERVICE}/license
    curl -X GET ${SENZING_API_SERVICE}/entities/1
    ```

1. To exit, press `control-c` in terminal showing docker log.

## Develop

### Prerequisite software

The following software programs need to be installed:

1. [git](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/install-git.md)
1. [make](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/install-make.md)
1. [docker](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/install-docker.md)

### Clone repository

For more information on environment variables,
see [Environment Variables](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md).

1. Set these environment variable values:

    ```console
    export GIT_ACCOUNT=senzing
    export GIT_REPOSITORY=senzing-api-server
    export GIT_ACCOUNT_DIR=~/${GIT_ACCOUNT}.git
    export GIT_REPOSITORY_DIR="${GIT_ACCOUNT_DIR}/${GIT_REPOSITORY}"
    ```

1. Follow steps in [clone-repository](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/clone-repository.md) to install the Git repository.

### Build docker image for development

1. :pencil2: Set environment variables.
   Example:

    ```console
    export SENZING_G2_DIR=/opt/senzing/g2
    ```

1. Build docker image.

    ```console
    cd ${GIT_REPOSITORY_DIR}

    sudo make \
        SENZING_G2_JAR_PATHNAME=${SENZING_G2_DIR}/lib/g2.jar \
        SENZING_G2_JAR_VERSION=$(cat ${SENZING_G2_DIR}/g2BuildVersion.json | jq --raw-output '.VERSION') \
        docker-build
    ```

    Note: `sudo make docker-build-development-cache` can be used to create cached docker layers.

## Examples

1. Examples of use:
    1. [docker-compose-stream-loader-kafka-demo](https://github.com/Senzing/docker-compose-stream-loader-kafka-demo)
    1. [kubernetes-demo](https://github.com/Senzing/kubernetes-demo)
    1. [rancher-demo](https://github.com/Senzing/rancher-demo/tree/master/docs/db2-cluster-demo.md)

## Errors

1. See [docs/errors.md](docs/errors.md).

## References
