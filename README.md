# Rocket VPN Server ( version 0.0.3 )
 ![Book Cover](screenshots/logo.png)
 
A Spring Boot application to manage a WireGuard VPN server with a modern web interface.

## Features
- **Easy Client Management**: Create, disable, and delete VPN clients.
- **QR Code Support**: Scan to connect mobile devices instantly.
- **Monitoring**: View real-time connection status and data usage.
- **Secure**: Admin login required.

---  
**Set Up Page**
 ![Book Cover](screenshots/1.jpg)
---
**Login Page**
 ![Book Cover](screenshots/2.jpg)
--- 
 **Dashboard**
 ![Book Cover](screenshots/3.jpg)
---
**Scan QR**
 ![Book Cover](screenshots/4.jpg)
---
 **Settings**
 ![Book Cover](screenshots/5.jpg)
---
 **Rate Limiting**
 ![Book Cover](screenshots/6.jpg)
---
 **Reset Settings**
 ![Book Cover](screenshots/7.jpg)
---



## Prerequisites
- Ubuntu 24.04 LTS
- Root privileges

### Native Image Build Requirements
- **CPU**: 4 cores (For Native Image Build, 2 cores is enough for standard JAR)
- **RAM**: 8GB (For Native Image Build, 4GB is enough for standard JAR)
- **Swap**: 8GB (For Native Image Build)

## Quick Start

### 1. Build the Application

**Install SDK Manager**
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

**Install GraalVM 25**
```bash
sdk list java
sdk install java 25.0.1-graal
```
***Install Packages***
```bash
sudo apt update && sudo apt upgrade -y && sudo apt-get install -y wireguard wireguard-tools iproute2 qrencode iptables curl -y

Enabling IP Forwarding..."
 Uncomment net.ipv4.ip_forward=1 in /etc/sysctl.conf if not already enabled
sudo sysctl -p

```

**Install Maven**
```bash
sudo apt update && sudo apt install maven -y
mvn wrapper:wrapper
```

### Why Native Image?
- **Performance**: Near-instant startup times and significantly reduced memory footprint compared to a standard JVM.
- **Security**: Reduced attack surface by excluding unused code and disabling dynamic class loading at runtime.
- **Portability**: No need to install Java Runtime Environment (JRE) on the target system.

#### Performance Comparison (Typical)
| Feature | Standard JVM | Native Image |
|---------|--------------|--------------|
| **Startup Time** | ~1-2 seconds | **<100 ms** |
| **Memory Footprint** | ~120 MB+ | **~30-50 MB** |
| **Executable Size** | Requires JRE | **Standalone (~30MB)** |


**Increase Swap Memory**
(Recommended for native image build to prevent OOM errors)
```bash
sudo swapoff /swapfile || true && sudo rm -f /swapfile && sudo fallocate -l 8G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile && sudo swapon --show && free -h
```

**Convert Native Image**
```bash
./mvnw -Pnative native:compile
```

**Run Native Image**
```bash
./pj
```

### Alternative: Build Standard JAR
If you do not want to convert to a native image, you can generate a standard JAR file:
```bash
./mvnw clean package -DskipTests
```

### 2. Run

**For Native Image:**
```bash
./target/pj
```

**For Standard JAR:**
```bash
java -jar target/pj-0.0.1-SNAPSHOT.jar
```
The application will start automatically on port 8080.

### 3. Access the Dashboard
Open your browser and navigate to:
`http://<YOUR_SERVER_IP>:8080`



## Troubleshooting
- **No Internet**: Ensure IP forwarding is enabled.
- **Cannot Connect**: Check if UDP port `51820` is open in your firewall.

