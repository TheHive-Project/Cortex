#!/bin/bash
# number of certs in the PEM file
openssl pkcs12 -export -inkey $PRIVATE_PEM -in $PUBLIC_PEM -out $KEYSTORE -password pass:$STORE_PASSWORD -certfile $CA_CERT;
CERTS=$(grep 'END CERTIFICATE' $CA_CERT| wc -l)

for N in $(seq 0 $(($CERTS - 1))); do
  ALIAS="${CA_CERT%.*}-$N"
  cat $CA_CERT |
    awk "n==$N { print }; /END CERTIFICATE/ { n++ }" |
           keytool -import -noprompt -keystore $TRUSTSTORE -storepass $STORE_PASSWORD -alias $ALIAS
done
