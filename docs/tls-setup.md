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

Replace `<LAN_IP>` with the value from step 2, then save as `san.cnf`.
`IP:10.0.2.2` is the fixed address the Android emulator uses to reach the host machine; keep it even
if you do not use the emulator today.

```
[req]
distinguished_name = req_distinguished_name
[req_distinguished_name]
[SAN]
subjectAltName = IP:<LAN_IP>,IP:10.0.2.2,DNS:localhost
basicConstraints = CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
```

```bash
# Quick way to create san.cnf (replace 192.168.1.42 with your LAN IP):
cat > san.cnf << 'EOF'
[req]
distinguished_name = req_distinguished_name
[req_distinguished_name]
[SAN]
subjectAltName = IP:192.168.1.42,IP:10.0.2.2,DNS:localhost
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

## Re-issuing the server certificate for a new LAN IP

If your LAN IP changes (DHCP reassignment), **the CA stays — repeat only steps 4–7**:

1. `openssl genrsa -out server.key 2048` (or reuse the existing key)
2. `openssl req -new -key server.key -out server.csr -subj "/CN=localhost"`
3. Update `san.cnf` with the new IP.
4. Sign a fresh `server.crt` (step 6).
5. Pack a new `newsdove.p12` (step 7).
6. Restart the server; update `server.keystore.path` if the filename changed.

The Android app does not need to be rebuilt — `ca.crt` (the trust anchor) is unchanged.

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

## USB Demo Without Wi-Fi

When you want to run the demo with a physical Android device connected via USB — no shared Wi-Fi
needed:

```bash
# Forward device port 8443 to the laptop's port 8443 (run once per USB reconnect):
adb reverse tcp:8443 tcp:8443
```

After this, the app on the phone can reach the server at `https://localhost:8443`.
No LAN IP is needed; the connection travels over the USB cable.

The certificate already covers `DNS:localhost`, so no new cert is required.
Remove the tunnel when done: `adb reverse --remove tcp:8443`

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

> **`--ssl-no-revoke`** is required on Windows: `curl.exe` uses Schannel, which tries to check
> certificate revocation (CRL/OCSP) and fails for a private CA with no published endpoint.

```bash
# Test the server responds on HTTPS (once the server is running):
curl.exe --ssl-no-revoke --cacert /c/newsdove_tls/ca.crt https://localhost:8443/health
# Expected: {"status":"ok"}

# Test that plain HTTP is blocked on API routes:
curl.exe http://localhost:8080/api/v1/feed
# Expected: 403 {"code":"HTTPS_REQUIRED",...}

# Test that /health is accessible on plain HTTP:
curl.exe http://localhost:8080/health
# Expected: {"status":"ok"}
```
