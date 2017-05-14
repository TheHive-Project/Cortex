# Cortex analyzers

Analyzers are autonomous applications managed by and run through the Cortex core engine. Analyzers have their
[own dedicated GitHub repository](https://github.com/CERT-BDF/Cortex-Analyzers). They are included in the Cortex binary
package but you have to get them from the repository if you decide to build Cortex from sources or if you need to update
them.

## Pre-requisites
Currently, all provided analyzers are written in Python. They don't require any build phase but their dependencies have
to be installed. Before proceeding, you'll need to install the system package dependencies that are required by some of
them:

```
apt-get install python-pip python2.7-dev ssdeep libfuzzy-dev libfuzzy2 libimage-exiftool-perl libmagic1 build-essential
```

Each analyzer comes with its own, pip compatible `requirements.txt` file. You can install all requirements with the
following commands:

```
cd analyzers
sudo pip install $(cat */requirements.txt | sort -u)
```

## From repository
If you want to get up-to-date analyzers, you can clone the GitHub repository:

```
git clone https://github.com/CERT-BDF/Cortex-Analyzers
```

Next, you'll need to tell Cortex where to find the analyzers. Currently, all the analyzers must be in the same
directory. Add the following to the Cortex configuration file (`application.conf`):

```
analyzer {
  path = "path/to/analyzers"
}
```
## Configuration

Analyzers configuration is stored in Cortex configuration file (application.conf) in `analyzer.config` section. There is
one subsection for each analyzer group. The configuration provided to analyzer is the merge of:
 - the global configuration: all item in `analyzer.config.global` section. This settings are applied for all analyzers.
 It is particularly useful for proxy settings (cf. example below)
 - the analyzer group configuration. Some analyzers shares configuration items, VirusTotal API key for all VirusTotal
 analyzers for example. Group name can be found in JSON description file in analyzer folder, under `baseConfig` key.
 - the analyzer configuration defined in JSON description file, under `config` key.

Here is the complete configuration you should provide to make all analyzers work:


```
analyzer {
  path = "path/to/Cortex-Analyzers/analyzers"
  config {
    global {
      proxy {
        http="http://PROXY_ADDRESS:PORT",
        https="http://PROXY_ADDRESS:PORT"
      }
    }
    CIRCLPassiveDNS {
      user= "..."
      password= "..."
    }
    CIRCLPassiveSSL {
      user= "..."
      password= "..."
    }
    DomainTools {
      username="..."
      key="..."
    }
    DNSDB {
      server="https://api.dnsdb.info"
      key="..."
    }
    GoogleSafebrowsing {
       key = "..."
    }
    Hippocampe {
      url="..."
    }
    JoeSandbox {
      url = "..."
      apikey = "..."
    }
    Nessus {
     url ="..."
     login="..."
     password="..."
     policy="..."
     ca_bundle="..."
     allowed_network="..."
    }
    OTXQuery {
      key="..."
    }
    PassiveTotal {
      key="..."
      username="..."
    }
    PhishingInitiative {
      key="..."
    }
    PhishTank {
      key="..."
    }
    Virusshare {
      path = "..."
    }
    VirusTotal {
     key="..."
    }
    Yara {
      rules=["..."]
    }
  }
}
```
