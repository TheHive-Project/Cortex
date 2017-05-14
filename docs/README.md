Cortex tries to solve a common problem frequently encountered by SOCs, CSIRTs and security researchers in the course of threat intelligence, digital forensics and incident response: how to analyze observables they have collected, **at scale, by querying a single tool** instead of several?

## Hardware pre-requisites
Cortex uses a Java VM. We recommend using a virtual machine with 8vCPU, 8 GB of RAM and 10 GB of disk. You can also use a
physical machine with similar specifications.

## What's new

- [Changelog](/CHANGELOG.md)

## Installation guides

Cortex can be installed using:
- An [RPM package](installation/rpm-guide.md)
- A [DEB package](installation/deb-guide.md)
- [Docker](installation/docker-guide.md)
- [Binary](installation/binary-guide.md)
- [Ansible script](https://github.com/drewstinnett/ansible-cortex) contributed by
[@drewstinnett](https://github.com/drewstinnett)

Cortex can also be [built from sources](installation/build-guide.md).

Once you have installed Cortex, you will to [install the analyzers](installation/analyzers.md).

## Developer guides

- [API documentation](api/README.md)
- [How to create an analyzer](api/how-to-create-an-analyzer.md)

## Other
- [FAQ](FAQ.md)
