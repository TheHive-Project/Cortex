#!/usr/bin/env bash
# Regenerates the TLS fixtures used by DBConfigurationSpec (DL-7123).
# Usage: ./gen.sh   (requires keytool from the JDK)
# Fixtures are valid ~100 years so the tests do not start failing when they expire.
set -euo pipefail
cd "$(dirname "$0")"

TS_PASS=ts-pass-7123       # trust stores
KS_PASS=ks-pass-7123       # client key stores
KEY_PASS=kp-pass-7123      # key password of client-keypass.jks (differs from its store password)
SRV_PASS=srv-pass-7123     # server.p12
CA_PASS=ca-pass-7123       # CA private keys (not used by tests)
V="-validity 36500 -keyalg RSA -keysize 2048"

rm -f ./*.crt ./*.jks ./*.p12 ./*.csr

make_ca() { # <alias> <dn>
  keytool -genkeypair -alias "$1" -dname "$2" $V -ext bc:c -storetype PKCS12 \
    -keystore "$1.p12" -storepass "$CA_PASS" -noprompt
  keytool -exportcert -rfc -alias "$1" -keystore "$1.p12" -storepass "$CA_PASS" -file "$1.crt"
}

# sign_leaf <ca> <alias> <dn> <storetype> <keystore> <storepass> <keypass> <extra gencert args...>
sign_leaf() {
  local ca=$1 alias=$2 dn=$3 type=$4 ks=$5 spass=$6 kpass=$7; shift 7
  keytool -genkeypair -alias "$alias" -dname "$dn" $V -storetype "$type" \
    -keystore "$ks" -storepass "$spass" -keypass "$kpass" -noprompt
  keytool -certreq -alias "$alias" -keystore "$ks" -storepass "$spass" -keypass "$kpass" -file "$alias.csr"
  keytool -gencert -alias "$ca" -keystore "$ca.p12" -storepass "$CA_PASS" -validity 36500 \
    -infile "$alias.csr" -outfile "$alias.signed.crt" -rfc "$@"
  keytool -importcert -alias "$ca" -file "$ca.crt" -keystore "$ks" -storepass "$spass" -noprompt
  keytool -importcert -alias "$alias" -file "$alias.signed.crt" -keystore "$ks" -storepass "$spass" -keypass "$kpass" -noprompt
  rm -f "$alias.csr" "$alias.signed.crt"
}

make_ca ca       "CN=Test CA,O=Cortex Tests"
make_ca other-ca "CN=Other CA,O=Cortex Tests"

# server certificate signed by ca, SAN localhost / 127.0.0.1
sign_leaf ca server "CN=localhost,O=Cortex Tests" PKCS12 server.p12 "$SRV_PASS" "$SRV_PASS" -ext san=dns:localhost,ip:127.0.0.1

# client certificate signed by ca
sign_leaf ca client "CN=cortex-client,O=Cortex Tests" PKCS12 client.p12 "$KS_PASS" "$KS_PASS"
keytool -importkeystore -srckeystore client.p12 -srcstorepass "$KS_PASS" -srcstoretype PKCS12 \
  -destkeystore client.jks -deststorepass "$KS_PASS" -deststoretype JKS -noprompt

# JKS client keystore whose key password differs from the store password
sign_leaf ca client "CN=cortex-client,O=Cortex Tests" JKS client-keypass.jks "$KS_PASS" "$KEY_PASS"

# trust stores
for t in "JKS jks" "PKCS12 p12"; do
  set -- $t
  keytool -importcert -alias ca -file ca.crt -storetype "$1" -keystore "truststore.$2" -storepass "$TS_PASS" -noprompt
done
keytool -importcert -alias other-ca -file other-ca.crt -storetype JKS -keystore other-truststore.jks -storepass "$TS_PASS" -noprompt

# not a keystore at all
echo "this is not a keystore" > corrupt.jks

# CA private keys are not needed once everything is signed
rm -f ca.p12 other-ca.p12
