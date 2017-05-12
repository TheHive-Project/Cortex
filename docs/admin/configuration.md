# Configuration

Cortex back-end and analyzers can find their configuration in the same file.

The only required parameter in order to start Cortex is the key of the server (`play.crypto.secret`). This key is used
to authenticate cookies that contain data, and not only a session id. If Cortex runs in cluster mode, all instance must
share the same key.

You should generate a random key using the following command line:

```
sudo mkdir /etc/cortex
(cat << _EOF_
# Secret key
# ~~~~~
# The secret key is used to secure cryptographics functions.
# If you deploy your application to several instances be sure to use the same key!
play.crypto.secret="$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -n 1)"
_EOF_
) | sudo tee -a /etc/cortex/application.conf

```

Please, note that this secret key is mandatory to start Cortex application. With this configuration, you will only be
able to run analyzers that do not require any configuration parameter, an API key for instance. To configure other
analyzers, refer to [analyzers](../installation/analyzers.md).

**Warning**: By default, Cortex run an HTTP service on port `9000/tcp`. You can change the port by adding
`http.port=8080` in the configuration file or add the `-Dhttp.port=8080` parameter to the command line below. If you run
Cortex using a non-privileged user, you can't bind a port under 1024. If you run TheHive on the same system beware to
use two different TCP ports.
