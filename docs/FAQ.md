# Analyzers
- [What version of MaxMind TheHive uses?](https://github.com/CERT-BDF/Cortex/wiki/FAQ#what-version-of-maxmind-cortex-uses)
- [How often are the MaxMind databases refreshed?](https://github.com/CERT-BDF/Cortex/wiki/FAQ#how-often-are-the-databases-refreshed)
- [How shall I configure the MaxMind analyzer?](https://github.com/CERT-BDF/Cortex/wiki/FAQ#how-shall-i-configure-the-maxmind-analyzer)
- [Can I use the commercial versions of the databases?](https://github.com/CERT-BDF/Cortex/wiki/FAQ#can-i-use-the-commercial-versions-of-the-databases)

## MaxMind
### What version of MaxMind Cortex uses?
The MaxMind analyzer includes the GeoLite2 free City and Country databases.

### How often are the MaxMind databases refreshed?
Cortex does not refresh those databases. It is up to you to create a cron job to refresh them at the frequency you want. The files to update are:
- `analyzers/MaxMind/GeoLite2-City.mmdb`
- `analyzers/MaxMind/GeoLite2-Country.mmdb`

You can fetch up-to-date versions from <https://dev.maxmind.com/geoip/geoip2/geolite2/>.

### How shall I configure the MaxMind analyzer?
No configuration is required. If it looks like the analyzer is not working, please clear the cache of your browser and retry. If it still doesn't work, please join [TheHive User Discussion Forum](https://groups.google.com/a/thehive-project.org/d/forum/users) or [open an issue on GitHub](https://github.com/CERT-BDF/Cortex-analyzers/issues/new).

### Can I use the commercial versions of the databases?
The current version of Cortex does not offer that possibility. If you'd like to have it, please [request it](https://github.com/CERT-BDF/Cortex-analyzers/issues/new).

# Authentication
- [Does Cortex support authentication?](https://github.com/CERT-BDF/Cortex/wiki/FAQ/does-cortex-support-authentication)
- [How can I make sure that only authorized users get access to Cortex?](https://github.com/CERT-BDF/Cortex/wiki/FAQ/how-can-i-make-sure-that-only-authorized-users-get-access-to-cortex)
- [How can I make sure that only authorized services get access to the Cortex API?](https://github.com/CERT-BDF/Cortex/wiki/FAQ/how-can-i-make-sure-that-only-authorized-services-get-access-to-cortex-api)

### Does Cortex support authentication?
No. Cortex 1 does not support authentication. Cortex 2, slated for September 2017, [will support local, LDAP and AD authentication](https://github.com/CERT-BDF/Cortex/issues/7).

### How can I make sure that only authorized users get access to Cortex?
Cortex does not currently support authentication. The next major version (v2), slated for September 2017, [will implement it](https://github.com/CERT-BDF/Cortex/issues/7). In the meantime, you should either install an authenticating reverse proxy in front of Cortex or limit access to it using a firewall or an alternative filtering device.

If you do not protect your Cortex instance, anyone who has access to your network may run jobs or retrieve existing reports.

### How can I make sure that only authorized services get access to Cortex API?
Cortex does not currently support service authentication or API keys. The next major version (v2), slated for September 2017, [will implement it](https://github.com/CERT-BDF/Cortex/issues/7).

Any service may query Cortex without authentication. If you need to let only authorized services get access to your instance(s), make sure to use a filtering device and authorize only the IP addresses of those services.

# Miscellaneous Questions
- [Can I Enable HTTPS to Connect to Cortex?](https://github.com/CERT-BDF/Cortex/wiki/FAQ#can-i-enable-https-to-connect-to-cortex)

### Can I Enable HTTPS to Connect to Cortex?
#### TL;DR
Add the following lines to `/etc/cortex/application.conf`

    https.port: 9443
    play.server.https.keyStore {
      path: "/path/to/keystore.jks"
      type: "JKS"
      password: "password_of_keystore"
    }

HTTP can disabled by adding line `http.port=disabled`
#### Details
To enable HTTPS in the application, add the following lines to `/etc/cortex/application.conf`:
```
    https.port: 9443
    play.server.https.keyStore {
      path: "/path/to/keystore.jks"
      type: "JKS"
      password: "password_of_keystore"
    }
```
As HTTPS is enabled HTTP can be disabled by adding `http.port=disabled` in configuration.

To import your certificate in the keystore, depending on your situation, you can follow [Digital Ocean's tutorial](https://www.digitalocean.com/community/tutorials/java-keytool-essentials-working-with-java-keystores).

**More information**:
This is a setting of the Play framework that is documented on its website. Please refer to [https://www.playframework.com/documentation/2.5.x/ConfiguringHttps](https://www.playframework.com/documentation/2.5.x/ConfiguringHttps).