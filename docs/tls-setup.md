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

> **Important:** the LAN IP must be stable. If your router assigns a different address (DHCP lease change),
> the server cert becomes invalid for the new IP — you must regenerate it from step 4 onwards.
> Configure a DHCP reservation in your router for the laptop's MAC address so the IP never changes.

---

## Step 3 — Generate the local CA

```bash
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 3650 \
    -key ca.key \
    -out ca.crt \
    -subj "/CN=NewsDove Local CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"
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
basicConstraints = CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
```

```bash
# Quick way to create san.cnf (replace 192.168.1.42 with your IP):
cat > san.cnf << 'EOF'
[req]
distinguished_name = req_distinguished_name
[req_distinguished_name]
[SAN]
subjectAltName = IP:192.168.1.42,DNS:localhost
basicConstraints = CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
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
    -name newsdove
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

## Step 9 — Allow port 8443 through Windows Firewall

Run once in an elevated PowerShell (run as Administrator):

```powershell
New-NetFirewallRule `
    -DisplayName "NewsDove HTTPS 8443" `
    -Direction Inbound `
    -Protocol TCP `
    -LocalPort 8443 `
    -Action Allow `
    -Profile Private
```

This allows inbound connections on the Private profile only (home/work Wi-Fi).
Do **not** enable it on the Public profile.

---

## Step 10 — Trust the CA on Android

Bundle `ca.crt` inside the Android app — no install on the phone needed:

1. Copy `ca.crt` from `C:\newsdove_tls\` to `androidApp/src/main/res/raw/newsdove_ca.crt`.
2. Create (or update) `androidApp/src/main/res/xml/network_security_config.xml`:
   ```xml
   <network-security-config>
       <base-config>
           <trust-anchors>
               <certificates src="system"/>
               <certificates src="@raw/newsdove_ca"/>
           </trust-anchors>
       </base-config>
   </network-security-config>
   ```
3. Reference it in `AndroidManifest.xml`:
   ```xml
   <application android:networkSecurityConfig="@xml/network_security_config" ...>
   ```

The app trusts only your CA alongside the system store; no user-level CA install is required, and
the cert is baked into the APK rather than depending on per-device trust settings.

---

## Step 11 — Trust the CA in a browser (optional)

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
