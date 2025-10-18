# 🎬 FFmpeg Batch Transcoder v1.0

> A comprehensive, all-in-one media processing tool with three powerful modes and three interfaces.

[![Python 3.7+](https://img.shields.io/badge/python-3.7+-blue.svg)](https://www.python.org/downloads/)
[![Java 17+](https://img.shields.io/badge/java-17+-orange.svg)](https://openjdk.org/)
[![FFmpeg](https://img.shields.io/badge/FFmpeg-required-green.svg)](https://ffmpeg.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 🎯 Three Modes, Three Interfaces

### 🎬 **Encoder Mode**
Convert videos with hardware acceleration (NVIDIA/AMD/Intel), smart codec selection, and batch processing.

### 💬 **Subtitle Mode**  
Generate AI subtitles with Whisper or download from OpenSubtitles.com in multiple languages.

### 📝 **Renamer Mode**
FileBot-style intelligent renaming using TMDB metadata with before/after preview.

---

## 🚀 Choose Your Interface

### 1. 🖥️ **Java GUI** (Recommended)
Modern desktop application with tabbed modes, real-time progress, and professional dark theme.

```bash
cd FFmpegGUI
./mvnw javafx:run
```

### 2. 💻 **Command Line Interface**
Perfect for automation, scripts, and power users.

```bash
# Encoder mode
python ffmpeg_cli.py encoder /path/to/videos --use-nvenc

# Subtitle mode
python ffmpeg_cli.py subtitle /path/to/videos --enable-subtitle-generation

# Renamer mode
python ffmpeg_cli.py renamer /path/to/videos --tmdb-api-key YOUR_KEY --preview-only
```

### 3. 🌐 **Web UI**
Browser-based interface powered by Streamlit - great for remote access.

```bash
# Windows
start_web_ui.bat

# Linux/Mac
./start_web_ui.sh
```

---

## ✨ Key Features

### 🚀 **Smart & Fast**
- **Stream copying by default** - No re-encoding = no quality loss + blazing speed
- **Multi-GPU support** - NVIDIA NVENC, AMD AMF, Intel Quick Sync, Apple VideoToolbox
- **Batch processing** - Process entire libraries automatically
- **Auto-fallback** - Intelligent codec selection when hardware unavailable

### 🤖 **AI-Powered**
- **Whisper AI** - Generate accurate subtitles in 90+ languages
- **OpenSubtitles** - Automatic subtitle download and matching
- **TMDB Integration** - Intelligent file renaming with metadata lookup
- **Smart detection** - Automatically detect TV shows vs movies

### 📊 **Professional Interface**
- **Three operation modes** - Encoder, Subtitle, Renamer (all interfaces)
- **Real-time progress** - See exactly what's happening
- **Preview before action** - Preview renames and subtitle changes
- **Queue management** - Add, remove, reorder files easily
- **Comprehensive logging** - Export logs, filter by level

### 🧠 **Actually Intelligent**
- **Auto-detects FFmpeg** - No more "command not found" errors
- **Proper metadata** - Clear track names like "Japanese Audio", "English Subtitles (SDH)"
- **All streams preserved** - Never randomly drops audio or subtitle tracks
- **Hardware detection** - Automatically detects available GPU acceleration

---

## 📦 Installation

### Prerequisites
- **Python 3.7+** - [Download](https://www.python.org/downloads/)
- **FFmpeg & FFprobe** - [Download](https://ffmpeg.org/download.html) (or auto-install via app)
- **Java 17+** (only for Java GUI) - [Download](https://adoptium.net/)

### Quick Start

```bash
# Clone the repository
git clone https://github.com/yourusername/ffmpeg-batch-transcoder.git
cd ffmpeg-batch-transcoder

# Install Python dependencies
pip install -r requirements.txt

# Optional: AI subtitle generation
pip install openai-whisper

# Choose your interface:
# 1. Java GUI
cd FFmpegGUI && ./mvnw javafx:run

# 2. Web UI
python -m streamlit run ffmpeg_webui.py

# 3. CLI
python ffmpeg_cli.py --help
```

---

## 📖 Usage Guide

### Mode 1: 🎬 Encoder

Convert video files with optimal settings.

**CLI Example:**
```bash
python ffmpeg_cli.py encoder /path/to/videos \
  --use-nvenc \
  --nvenc-cq 23 \
  --output-format mp4 \
  --delete-original
```

**Features:**
- Hardware acceleration (NVIDIA/AMD/Intel/Apple)
- Smart codec selection
- Optional subtitle generation during encoding
- Optional renaming after encoding
- Batch processing with progress tracking

### Mode 2: 💬 Subtitle

Generate or download subtitles for your videos.

**CLI Example:**
```bash
# Generate with Whisper AI
python ffmpeg_cli.py subtitle /path/to/videos \
  --enable-subtitle-generation \
  --whisper-model base \
  --subtitle-languages eng,spa,jpn

# Download from OpenSubtitles
python ffmpeg_cli.py subtitle /path/to/videos \
  --enable-subtitle-download \
  --opensubtitles-username your_username \
  --opensubtitles-password your_password \
  --subtitle-languages eng,spa
```

**Features:**
- Whisper AI generation (offline)
- OpenSubtitles.com download
- Multi-language support
- Subtitle preview and customization
- Replace or add to existing subtitles

### Mode 3: 📝 Renamer

Intelligently rename media files using online databases.

**CLI Example:**
```bash
# Preview renames first
python ffmpeg_cli.py renamer /path/to/videos \
  --tmdb-api-key YOUR_KEY \
  --preview-only

# Apply renames
python ffmpeg_cli.py renamer /path/to/videos \
  --tmdb-api-key YOUR_KEY \
  --pattern-tv "{title} - S{season}E{episode} - {episodeTitle}" \
  --pattern-movie "{title} ({year})"
```

**Features:**
- TMDB (The Movie Database) integration
- TV show and movie detection
- Before/after comparison
- Customizable naming patterns
- Batch renaming with preview

---

## ⚙️ Configuration

### API Keys

Some features require free API keys:

| Service | Purpose | Get Key |
|---------|---------|---------|
| **TMDB** | File renaming | [themoviedb.org](https://www.themoviedb.org/settings/api) |
| **OpenSubtitles** | Subtitle download | [opensubtitles.com](https://www.opensubtitles.com/en/users/sign_up) |

### Hardware Acceleration

The application automatically detects available hardware encoders:

| GPU | Encoder | Platforms |
|-----|---------|-----------|
| NVIDIA | NVENC (h264_nvenc, hevc_nvenc) | Windows, Linux |
| AMD | AMF (h264_amf, hevc_amf) | Windows |
| Intel | Quick Sync (h264_qsv, hevc_qsv) | Windows, Linux |
| Apple | VideoToolbox (h264_videotoolbox) | macOS |

### Naming Patterns

Customize how files are renamed:

**TV Shows:**
- `{title}` - Show title
- `{season}` - Season number (01, 02, ...)
- `{episode}` - Episode number (01, 02, ...)
- `{episodeTitle}` - Episode title
- `{S}` - Season with S prefix (S01)
- `{E}` - Episode with E prefix (E01)

**Movies:**
- `{title}` - Movie title
- `{year}` - Release year

**Examples:**
- TV: `Breaking Bad - S01E01 - Pilot.mkv`
- Movie: `Inception (2010).mp4`

---

## 🏗️ Architecture

```
FFmpeg Batch Transcoder v2.0
│
├── 🐍 Python Backend (Modular)
│   ├── ffmpeg_core.py          # Core functionality
│   ├── ffmpeg_manager.py        # FFmpeg detection & download
│   ├── whisper_manager.py       # Whisper AI integration
│   ├── media_renamer.py         # TMDB-based renaming
│   └── opensubtitles_manager.py # Subtitle downloads
│
├── 🖥️ Interfaces
│   ├── ffmpeg_cli.py            # Command-line interface
│   ├── ffmpeg_webui.py          # Streamlit web interface
│   ├── ffmpeg_api.py            # JSON API for Java
│   └── FFmpegGUI/               # JavaFX desktop app
│       ├── MainController.java
│       ├── SettingsController.java
│       └── PythonBridge.java
│
└── 📁 Helper Modules
    ├── start_web_ui.bat/sh      # Launch web UI
    ├── start_cli.bat/sh         # Launch CLI with examples
    └── requirements.txt         # Python dependencies
```

---

## 🎨 Screenshots

### Java GUI
- **Encoder Tab**: Queue management, real-time progress, hardware acceleration settings
- **Subtitle Tab**: Generate/download subtitles with preview
- **Renamer Tab**: Side-by-side before/after comparison

### Web UI
- **Modern Interface**: Streamlit-powered responsive design
- **Mode Switching**: Easy mode selection in sidebar
- **Settings Panel**: All options organized by category

### CLI
- **Color Output**: Clear progress indicators and status messages
- **Preview Mode**: See changes before applying
- **Batch Operations**: Process hundreds of files automatically

---

## 🐛 Troubleshooting

### FFmpeg Not Found
```bash
# The app can auto-download FFmpeg, or:
# Windows: Download from gyan.dev/ffmpeg
# Mac: brew install ffmpeg
# Linux: sudo apt install ffmpeg (Ubuntu) or sudo dnf install ffmpeg (Fedora)
```

### Hardware Acceleration Not Working
- **NVIDIA**: Requires GTX 600 series or newer + recent drivers
- **AMD**: Windows only, requires recent drivers
- **Intel**: Requires CPU with Quick Sync (6th gen or newer)
- **Apple**: macOS only, works on all modern Macs

The app automatically falls back to software encoding if hardware unavailable.

### Whisper Installation Fails
```bash
# Install manually:
pip install --upgrade pip
pip install openai-whisper

# Or use the app's built-in installer (Java GUI / Web UI)
```

### TMDB API Limit
- Free tier: 40 requests per 10 seconds
- Get a free API key - no credit card required
- Caches results to minimize requests

---

## 🤝 Contributing

Contributions are very welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Test thoroughly (all three interfaces if possible)
5. Commit (`git commit -m 'Add amazing feature'`)
6. Push (`git push origin feature/amazing-feature`)
7. Open a Pull Request

**Areas for contribution:**
- Additional subtitle sources
- More hardware acceleration options
- Additional naming patterns
- Translations/internationalization
- Bug fixes and improvements

---

## 📋 Roadmap

### Planned Features
- [ ] Profile system for saving presets
- [ ] Audio/subtitle sync detection and correction
- [ ] Batch metadata editing
- [ ] Chapter support
- [ ] Hardware decode acceleration
- [ ] Jellyfin/Plex direct integration
- [ ] Video quality comparison tools

### Under Consideration
- [ ] Docker container for easy deployment
- [ ] REST API for external integration
- [ ] Notification system (email, Discord, etc.)
- [ ] Schedule conversions for off-hours

---

## 📜 License

MIT License - Use it, modify it, share it!

See [LICENSE](LICENSE) for details.

---

## 🙏 Credits

**Built with:**
- 🐍 [Python](https://www.python.org/) - Backend and CLI
- ☕ [Java](https://openjdk.org/) + [JavaFX](https://openjfx.io/) - Desktop GUI
- 🌐 [Streamlit](https://streamlit.io/) - Web UI
- 🎥 [FFmpeg](https://ffmpeg.org/) - Video processing
- 🤖 [OpenAI Whisper](https://github.com/openai/whisper) - AI subtitles
- 🎬 [TMDB](https://www.themoviedb.org/) - Movie/TV metadata
- 💬 [OpenSubtitles](https://www.opensubtitles.com/) - Subtitle database

**Special thanks** to all contributors and users who've reported bugs and suggested features!

---

## 📬 Support

- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/yourusername/ffmpeg-batch-transcoder/issues)
- 💡 **Feature Requests**: [GitHub Discussions](https://github.com/yourusername/ffmpeg-batch-transcoder/discussions)
- 📧 **Email**: your.email@example.com
- 💬 **Discord**: [Join our server](https://discord.gg/yourserver)

---

<p align="center">
  <strong>Made with ❤️ by someone who was tired of juggling too many tools</strong>
</p>

<p align="center">
  <sub>If this saved you time, consider ⭐ starring the repo or contributing!</sub>
</p>
