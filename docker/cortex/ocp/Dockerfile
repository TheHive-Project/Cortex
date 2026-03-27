FROM registry.connect.redhat.com/ibm/ibmjava8-sdk-ubi8:latest
USER 0

RUN yum -y install https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm
RUN yum -y install autoconf automake binutils  gcc gcc-c++ glibc-devel libtool make pkgconf pkgconf-m4 pkgconf-pkg-config python2-pip python3-pip ssdeep perl-Image-ExifTool file-libs openssl-devel
RUN yum -y install git
RUN yum -y install python3-devel
RUN yum -y install procps

COPY cortex /cortex
COPY cortex-analyzers /Cortex-Analyzers
COPY application.conf /cortex/application.conf
ADD entrypoint /cortex/

RUN chgrp -R 0 /cortex && \
    chmod -R g=u /cortex

RUN chmod 755 /cortex/probe.sh

RUN for requirement in $(ls -1 /Cortex-Analyzers/analyzers/*/requirements.txt); do pip2 install -r ${requirement}; done
RUN for requirement in $(ls -1 /Cortex-Analyzers/analyzers/*/requirements.txt); do pip3 install -r ${requirement}; done

USER 1001

EXPOSE 3000
ENTRYPOINT ["/cortex/entrypoint"]
