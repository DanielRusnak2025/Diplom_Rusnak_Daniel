# TLS Setup — Self-Signed CA for Local Network

All keys, certificates, and keystores live **outside the repository** at `C:\newsdove_tls\`.
They are gitignored via `*.key`, `*.pem`, `*.p12`, `*.jks` patterns.

## Prerequisites

OpenSSL 3.x — available from Git Bash (`openssl version`) or as a standalone install.
Run all commands in Git Bash from the `C:\newsdove_tls\` directory (see step 1).

---

## Step 1 — Create the working directory

```bash
mkdir -p /c/newsdove_tls
cd /c/newsdove_tls
```

---

## Step 2 — Find your LAN IP

```bash
ipconfig.exe | grep -i "IPv4"
```

Note the address (e.g. `192.168.1.42`). Substitute it for `<LAN_IP>` in step 5.

---

## Step 3 — Generate the local CA

```bash
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 3650 \
    -key ca.key \
    -out ca.crt \
    -subj "/CN=NewsDove Local CA"
```

`ca.key` is the CA private key — keep it secret and outside the repo.
`ca.crt` is the CA certificate — distribute it to clients (Android, browser).

---

## Step 4 — Generate the server private key and CSR

```bash
openssl genrsa -out server.key 2048
openssl req -new \
    -key server.key \
    -out server.csr \
    -subj "/CN=localhost"
```

---

## Step 5 — Create the SAN extension file

Replace `<LAN_IP>` with the value from step 2, then save as `san.cnf`:

```
[req]
distinguished_name = req_distinguished_name
[req_distinguished_name]
[SAN]
subjectAltName = IP:<LAN_IP>,DNS:localhost
```

```bash
# Quick way to create san.cnf (replace 192.168.1.42 with your IP):
cat > san.cnf << 'EOF'
[req]
distinguished_name = req_distinguished_name
[req_distinguished_name]
[SAN]
subjectAltName = IP:192.168.1.42,DNS:localhost
EOF
```

---

## Step 6 — Sign the server certificate with the CA

```bash
openssl x509 -req -days 825 \
    -in server.csr \
    -CA ca.crt \
    -CAkey ca.key \
    -CAcreateserial \
    -out server.crt \
    -extfile san.cnf \
    -extensions SAN
```

Verify the SAN is present:
```bash
openssl x509 -in server.crt -noout -text | grep -A2 "Subject Alternative"
```

---

## Step 7 — Create the PKCS12 keystore

Choose a strong password and remember it; you will set it as `NEWSDOVE_KEYSTORE_PASSWORD`.

```bash
openssl pkcs12 -export \
    -out newsdove.p12 \
    -inkey server.key \
    -in server.crt \
    -certfile ca.crt \
    -name newsdove \
    -passout pass:CHANGE_ME_STRONG_PASSWORD
```

---

## Step 8 — Configure `server/config/server.properties`

```properties
server.port=8080
server.https.port=8443
server.keystore.path=C:/newsdove_tls/newsdove.p12
server.keystore.alias=newsdove
# Do NOT set server.keystore.password here — use the env var instead
```

Set the password as an environment variable (never commit it):
```bash
# Windows (CMD)
set NEWSDOVE_KEYSTORE_PASSWORD=CHANGE_ME_STRONG_PASSWORD

# Windows (PowerShell)
$env:NEWSDOVE_KEYSTORE_PASSWORD = "CHANGE_ME_STRONG_PASSWORD"
```

---

## Step 9 — Trust the CA on Android

1. Copy `ca.crt` to the device (via ADB or shared folder).
2. On Android: **Settings → Security → Encryption & credentials → Install a certificate → CA certificate**.
3. Select `ca.crt`.
4. Add a **Network Security Configuration** to `androidApp/src/main/res/xml/network_security_config.xml`:
   ```xml
   <network-security-config>
       <base-config>
           <trust-anchors>
               <certificates src="system"/>
               <certificates src="user"/>
           </trust-anchors>
       </base-config>
   </network-security-config>
   ```
   And reference it in `AndroidManifest.xml`:
   ```xml
   <application android:networkSecurityConfig="@xml/network_security_config" ...>
   ```

---

## Step 10 — Trust the CA in a browser (optional)

- **Chrome/Edge (Windows)**: Settings → Privacy → Security → Manage certificates → Trusted Root Certification Authorities → Import `ca.crt`.
- **Firefox**: Settings → Privacy & Security → Certificates → View Certificates → Authorities → Import `ca.crt`.

---

## Verification

```bash
# Test the server responds on HTTPS (once the server is running):
curl --cacert /c/newsdove_tls/ca.crt https://localhost:8443/health
# Expected: {"status":"ok"}

# Test that plain HTTP is blocked on API routes:
curl http://localhost:8080/api/v1/feed
# Expected: 403 {"code":"HTTPS_REQUIRED",...}

# Test that /health is accessible on plain HTTP:
curl http://localhost:8080/health
# Expected: {"status":"ok"}
```
