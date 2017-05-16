# MISP integration

## Invoke MISP modules in Cortex

Since version 1.1.1, Cortex can analyze observable using
[MISP expansion modules](https://github.com/MISP/misp-modules#expansion-modules).

Follow [MISP documentation](https://github.com/MISP/misp-modules#how-to-install-and-start-misp-modules) to install MISP
modules. MISP modules service doesn't need to be started. Modules must be present in the same host than Cortex.
```
sudo apt-get install python3-dev python3-pip libpq5 libjpeg-dev
cd /usr/local/src/
sudo git clone https://github.com/MISP/misp-modules.git
cd misp-modules
sudo pip3 install -I -r REQUIREMENTS
sudo pip3 install -I .
```

Integration with MISP modules can then be enabled by adding the line `misp.modules.enabled = true` in
Cortex `application.conf`.

Most MISP modules require configuration. Settings must be placed in misp.modules.config key. If required some
configuration is missing, MISP module is not loaded.


```
misp.modules {
  enabled = true

  config {
    shodan {
      apikey = ""
    }
    dns {
      nameserver = "127.0.0.1"
    }
  }
}
```
Cortex uses Python wrapper to run MISP modules. It is located in `contrib` folder. Cortex should be able to locate it
automatically. You can force its location in configuraton under settings:
```
misp.modules.loader = /path/to/misp-modules-loader.py"
```

## Invoke Cortex in MISP

Cortex can be connected to a MISP instance. Under `Server settings` of MISP `Administration` menu, go to `Plugin
settings` and in Cortex section:
 - set `Plugin.Cortex_services_enable` to `true`
 - set `Plugin.Cortex_services_url` to `http://127.0.0.1` (replace 127.0.0.1 by Cortex IP address)
 - set `Plugin.Plugin.Cortex_services_port` to `9000` (replace 9000 by Cortex port)

Then Cortex analyzer list should appear in Cortex section. They must be enabled before being available to MISP users.

 