# 💬 Termy

<div align="center">

```text
  ████████╗███████╗██████╗ ███╗   ███╗██╗   ██╗
  ╚══██╔══╝██╔════╝██╔══██╗████╗ ████║╚██╗ ██╔╝
     ██║   █████╗  ██████╔╝██╔████╔██║ ╚████╔╝ 
     ██║   ██╔══╝  ██╔══██╗██║╚██╔╝██║  ╚██╔╝  
     ██║   ███████╗██║  ██║██║ ╚═╝ ██║   ██║   
     ╚═╝   ╚══════╝╚═╝  ╚═╝╚═╝     ╚═╝   ╚═╝   
```

**A sleek, retro-modern Terminal UI (TUI) messenger engineered for keyboard purists.**

[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.9%2B-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![Lanterna](https://img.shields.io/badge/TUI-Lanterna%203-3b82f6?style=for-the-badge)](https://github.com/mabe02/lanterna)
[![Platform](https://img.shields.io/badge/Platform-Linux%20%7C%20macOS%20%7C%20Windows-555?style=for-the-badge)](#)

[Features](#-features) • [Quick Start](#-quick-start) • [Controls & Commands](#-controls--commands) • [Multi-Network Chat](#-connecting-across-different-networks) • [Roadmap](#-roadmap)

---
</div>

## ✨ Overview

**Termy** is a lightweight, distraction-free terminal chat application written in Java. Powered by [Lanterna](https://github.com/mabe02/lanterna), Termy brings a polished, full-screen console interface with rich color accents, live status tracking, non-blocking I/O, and keyboard-centric navigation to raw TCP socket messaging.

Whether you're pairing with a teammate over a local network, tunneling via ngrok, or chatting across a private Tailscale mesh, Termy gives you instant peer-to-peer communication right where you work: inside your terminal.

---

## 🚀 Features

### 🖥️ Polished Terminal UI (TUI)
- **Retro-Modern Theme**: Deep navy header, slate border frames, sky-blue input focus, and clean message typography.
- **Dynamic Header Bar**: Real-time status badge (`● ONLINE` / `○ OFFLINE`), target host/port indicator, active nickname, and a live clock.
- **Responsive Layout**: Dynamically recalculates message bounds and text flow on terminal resize.
- **Clean Terminal Restoration**: JVM shutdown hooks ensure your terminal cursor, colors, and alternate buffer are restored safely upon exit—no corrupted shells.

### ⚡ Smooth Message Flow
- **Auto Word-Wrapping**: Long sentences wrap cleanly without breaking mid-word or overflowing UI borders.
- **Color-Coded Message Types**:
  - 🟢 **Self**: Clean, distinguished styling for your own outgoing messages.
  - 🔵 **Peer / Server**: High-contrast display for incoming dialogue.
  - 🟡 **System**: Timestamped notifications for connection updates and commands.
  - 🔴 **Error**: Prominent visual alerts for network dropouts and bad inputs.
- **Precision Timestamps**: Every message is tagged with an accurate `[HH:mm:ss]` marker.

### ⌨️ Keyboard-First Navigation
- **Scrollable History**: Browse through previous conversations seamlessly using `PageUp`, `PageDown`, or `ArrowUp` / `ArrowDown`.
- **Full Text Editing**: Intuitive cursor movements (`Home`, `End`, `ArrowLeft`, `ArrowRight`, `Backspace`, `Delete`).
- **Quick Escape**: Instant exit with `F10` or `Ctrl+C`.

### 🛠️ Interactive Slash Commands
Built-in client utility commands for instant customization on the fly:
| Command | Description | Example |
| :--- | :--- | :--- |
| `/nick <name>` | Change your visible chat handle | `/nick Alice` |
| `/clear` | Clear the current on-screen message history | `/clear` |
| `/shrug` | Send the classic `¯\_(ツ)_/¯` macro | `/shrug` |
| `/help` | Display the interactive command helper | `/help` |
| `/exit` or `/quit` | Safely terminate connection and exit | `/exit` |

### 🌐 High-Performance Networking
- **Multi-Threaded Architecture**: Independent network receiver daemon thread decouples socket I/O from the 60+ FPS UI loop.
- **Raw TCP Sockets**: Zero web overhead, minimal latency, and full control over socket streams.

---

## 📦 Architecture

```text
                   +------------------------+
                   |      Termy Server      |
                   |   (ServerSocket:5000)  |
                   +-----------+------------+
                               ^
                               |  TCP Sockets
                               v
                   +------------------------+
                   |      Termy Client      |
                   +------------------------+
                   |  - Lanterna TUI Loop   |
                   |  - Async Network Thread|
                   |  - Command Processor   |
                   +------------------------+
```

---

## 🏁 Quick Start

### Prerequisites
- **Java Development Kit (JDK)**: Version 21 or higher (configured for Java 25 toolchain).
- **Gradle**: Bundled via the repository's `./gradlew` wrapper.

### 1. Launch the Server
In your first terminal window, start the chat server (listens on port `5000` by default):
```bash
./gradlew runServer
```

### 2. Launch the Client
In a second terminal window, launch the Termy TUI client:
```bash
# Connects to localhost:5000 by default
./gradlew runClient
```

To connect to a custom IP or port:
```bash
./gradlew runClient --args="<hostname_or_ip> <port>"
```

---

## 🌐 Connecting Across Different Networks

Want to chat between two computers across different Wi-Fi networks or the internet? Termy supports multiple connection methods:

### Option A: Tailscale VPN *(Recommended — Fast & Private)*
1. Install [Tailscale](https://tailscale.com/) on both machines.
2. Machine 1 starts the server:
   ```bash
   ./gradlew runServer
   ```
3. Machine 2 connects using Machine 1's Tailscale IP (e.g., `100.x.y.z`):
   ```bash
   ./gradlew runClient --args="100.85.12.34 5000"
   ```

### Option B: TCP Tunneling *(ngrok)*
1. Machine 1 exposes port `5000` with ngrok:
   ```bash
   ngrok tcp 5000
   ```
   *(Note the allocated TCP address, e.g., `tcp://4.tcp.ngrok.io:19421`)*
2. Machine 2 connects via the ngrok host and port:
   ```bash
   ./gradlew runClient --args="4.tcp.ngrok.io 19421"
   ```

### Option C: Router Port Forwarding
1. Forward external TCP port `5000` on your home router to your local IP (`192.168.x.x:5000`).
2. Client connects to your public internet IP:
   ```bash
   ./gradlew runClient --args="<PUBLIC_IP> 5000"
   ```

---

## 🎮 Controls & Shortcuts

| Key / Shortcut | Action |
| :--- | :--- |
| `Enter` | Send typed message or execute slash command |
| `Page Up` | Scroll up in chat history (5 lines) |
| `Page Down` | Scroll down in chat history (5 lines) |
| `Arrow Up / Down` | Scroll line-by-line (when input buffer is empty) |
| `Arrow Left / Right` | Move cursor within input text |
| `Home` / `End` | Jump to beginning / end of input |
| `Backspace` / `Delete` | Remove characters |
| `F10` or `Ctrl+C` | Gracefully quit Termy |

---

## 🗺️ Roadmap

- [x] **Phase 1: Project Foundation** — Gradle structure, Java 25 setup, Lanterna integration.
- [x] **Phase 2: Core Networking** — Multi-threaded client-server TCP sockets.
- [x] **Phase 3: TUI Polish** — Custom themes, timestamps, word wrapping, scrolling, status bar.
- [ ] **Phase 4: Multi-Client Hub** — Concurrent connection handler (`ClientHandler.java`).
- [ ] **Phase 5: Channels & Rooms** — `/join`, `/leave`, and `/rooms` channel segregation.
- [ ] **Phase 6: Direct Messaging** — `/msg <user> <message>` for private whispers.
- [ ] **Phase 7: End-to-End Encryption** — Secure payload encryption over open networks.

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
