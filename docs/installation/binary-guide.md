# Installation Guide for Ubuntu 16.04 LTS

This guide describes the manual installation of Cortex from binaries in Ubuntu 16.04.

# 1. Minimal Ubuntu Installation

Install a minimal Ubuntu 16.04  system with the following software:
 * Java runtime environment 1.8+ (JRE)

Make sure your system is up-to-date:

```
sudo apt-get update
sudo apt-get upgrade
```

# 2. Install a Java Virtual Machine
You can install either Oracle Java or OpenJDK.

## 2.1. Oracle Java
```
echo 'deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main' | sudo tee -a /etc/apt/sources.list.d/java.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-key EEA14886
sudo apt-get update
sudo apt-get install oracle-java8-installer
```

## 2.2 OpenJDK
```
sudo add-apt-repository ppa:openjdk-r/ppa
sudo apt-get update
sudo apt-get install openjdk-8-jre-headless

```

# 3. Install Cortex

Binary package can be downloaded at [thehive-cortex.zip](https://dl.bintray.com/cert-bdf/cortex/cortex-latest.zip)

Download and unzip the chosen binary package. TheHive files can be installed wherever you want on the filesystem. In
this guide, we decided to set it in `/opt`.

```
cd /opt
wget https://dl.bintray.com/cert-bdf/cortex/cortex-latest.zip
unzip cortex-latest.zip
ln -s cortex-x.x.x cortex
```


# 4. First start

Change your current directory to Cortex installation directory (`/opt/cortex` in this guide), then execute:

```
bin/cortex -Dconfig.file=/etc/cortex/application.conf
```

It is recommended to use a dedicated non-privilege user to start Cortex. If so, make sure that your user can create log file in `/opt/cortex/logs`

If you'd rather start the application as a service, do the following:
```
sudo addgroup cortex
sudo adduser --system cortex
sudo cp /opt/cortex/package/cortex.service /usr/lib/systemd/system
sudo chown -R cortex:cortex /opt/cortex
sudo chgrp cortex /etc/cortex/application.conf
sudo chmod 640 /etc/cortex/application.conf
sudo systemctl enable cortex
sudo service cortex start
```

Please note that the service may take some time to start.

Cortex comes with a simplistic frontend. Open your browser and connect to `http://YOUR_SERVER_ADDRESS:9000/`

# 5. Plug analysers

Now that Cortex starts successfully, downloads `Cortex-Analyzers` and edit the configuration file and set the path to
`Cortex-Analyzers/analyzers`. Follow details available in the [analyzers page](analyzers.md).

## 6. Update

To update Cortex from binaries, just stop the service, download the latest package, rebuild the link `/opt/cortex` and
restart the service.

```
service cortex stop
cd /opt
wget https://dl.bintray.com/cert-bdf/cortex/cortex-latest.zip
unzip cortex-latest.zip
rm /opt/cortex && ln -s cortex-x.x.x cortex
chown -R cortex:cortex /opt/cortex /opt/cortex-x.x.x
service cortex start
```
