# Installation of TheHive using RPM package

RPM packages are published on Bintray repository. All packages are signed using the key 562CBC1C (fingerprint: 0CD5
AC59 DE5C 5A8E 0EE1  3849 3D99 BB18 562C BC1C)

First install rpm release package:
```
yum install install https://dl.bintray.com/cert-bdf/rpm/thehive-project-release-1.0.0-3.noarch.rpm
```
This will install TheHive Project repository (in /etc/yum.repos.d/thehive-rpm.repo) and the GPG public key (in
/etc/pki/rpm-gpg/GPG-TheHive-Project).
 
Then you will able to install Cortex package using yum
```
yum install cortex
```

After package installation, you should configure Cortex (see [configuration guide](../admin/configuration.md))