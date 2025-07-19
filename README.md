# NetLens

**Stream your Android camera over HTTP in real-time**

NetLens is an open-source Android application that transforms your phone into a wireless camera by streaming live video over HTTP. Perfect for security monitoring, remote viewing, or creative projects.

## ✨ Features

- **Real-time MJPEG streaming** over HTTP
- **Multiple camera support** (front/back cameras)
- **Configurable resolutions** (HD 720p, Full HD 1080p)
- **Custom port configuration** (1024-65535)
- **Automatic orientation handling** for landscape/portrait modes
- **Keep-alive functionality** to prevent device sleep during streaming
- **Clean Material Design 3 UI**
- **Low latency streaming** optimized for local networks
- **Cross-platform viewing** - access from any device with a web browser

## 🚀 Quick Start

1. **Install** the app on your Android device
2. **Grant camera permissions** when prompted
3. **Configure settings** (optional):
   - Select camera (front/back)
   - Choose resolution
   - Set custom port
4. **Tap "START STREAMING"**
5. **Access the stream** from any device on the same network using the displayed URL

### Example Stream URL
```
http://192.168.1.100:8080/stream
```

## 📱 Screenshots

...

## 🔧 Technical Details

### Streaming Protocol
- **Format**: MJPEG (Motion JPEG)
- **Protocol**: HTTP multipart stream
- **Boundary**: `--boundarydonotcross`
- **Default Port**: 8080 (configurable)

### Supported Resolutions
- **HD**: 1280x720
- **Full HD**: 1920x1080

### Camera Features
- Auto-focus continuous picture mode
- Auto-exposure
- Automatic orientation correction
- Support for multiple camera sensors

### Network Requirements
- Device must be connected to WiFi or mobile hotspot
- Client devices must be on the same network
- No internet connection required (local streaming only)

## 🛠️ Installation

### Requirements
- Android 7.0+ (API level 24+)
- Camera permission
- Network access

### From Source
1. Clone the repository:
   ```bash
   git clone https://github.com/StroeFlorin/NetLens.git
   ```
2. Open in Android Studio
3. Build and install on your device

### APK Release
Download the latest APK from the [Releases](https://github.com/StroeFlorin/NetLens/releases) page.

## 🎯 Use Cases

- **Home Security**: Monitor your home remotely
- **Baby Monitor**: Keep an eye on your little ones
- **Pet Monitoring**: Watch your pets while away
- **Workshop Streaming**: Share your workspace in real-time
- **Educational**: Demonstrate experiments or procedures
- **Live Events**: Stream events to multiple devices
- **Development**: Test streaming applications

## 🌐 Viewing the Stream

The stream can be viewed on:
- **Web browsers** (Chrome, Firefox, Safari, Edge)
- **VLC Media Player**: Open network stream with the URL
- **OBS Studio**: Add browser source with the stream URL
- **Any MJPEG-compatible viewer**


## 🔒 Privacy & Security

- **Local network only**: Streams are not sent to external servers
- **No data collection**: NetLens doesn't collect or store personal data
- **Open source**: Full transparency of code and functionality
- **Permissions**: Only requires camera and network access

## 🤝 Contributing

We welcome contributions! Here's how you can help:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Development Setup
- Android Studio Narwhal or newer
- Kotlin 1.8+
- Target SDK: 36
- Min SDK: 24

## 🐛 Bug Reports

Found a bug? Please create an issue with:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable

## 🏗️ Architecture

NetLens is built with:
- **Jetpack Compose** for modern UI
- **Camera2 API** for camera control
- **Coroutines** for async operations
- **Material Design 3** components
- **Custom HTTP server** for MJPEG streaming

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Florin Stroe**
- Made with ❤️

## 🌟 Support

If you find NetLens useful, please consider:
- ⭐ Starring the repository
- 🐛 Reporting bugs
- 💡 Suggesting features
- 🤝 Contributing code

## 📞 Contact

- Create an [issue](https://github.com/StroeFlorin/NetLens/issues) for bug reports
- Start a [discussion](https://github.com/StroeFlorin/NetLens/discussions) for questions

---

**Disclaimer**: NetLens is designed for legitimate use cases. Users are responsible for complying with local laws and regulations regarding camera usage and privacy.
