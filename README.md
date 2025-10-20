# Encode Forge

<div align="center">
  <img src="EncodeForge/src/main/resources/icons/app-icon.png" alt="Encode Forge Logo" width="128" height="128">
  
  **Version 0.2**
  
  *The all-in-one media processing application*
</div>

---

## What is Encode Forge?

Encode Forge is a comprehensive media processing application that combines video encoding, subtitle generation, and smart file renaming into a single, easy-to-use desktop application. Built with JavaFX and Python, it provides a modern interface for all your media processing needs.

## Features

### 🎬 Video Encoding
- **Hardware Acceleration** - Supports NVIDIA NVENC, AMD AMF, Intel Quick Sync, and Apple VideoToolbox
- **Smart Codec Selection** - Automatically chooses the best codec for your hardware
- **Batch Processing** - Process entire libraries with real-time progress tracking
- **Stream Copying** - Preserve quality while converting containers

### 💬 Subtitle Generation
- **AI-Powered Subtitles** - Generate subtitles using OpenAI Whisper (90+ languages)
- **9 Subtitle Providers** - Download from multiple sources including anime-specific providers
- **Multiple Language Support** - Handle multiple audio tracks and subtitle languages
- **Preview Mode** - Review subtitles before applying (WIP/ComingSoon)

### 📝 Smart File Renaming
- **10 Metadata Providers** - TMDB, TVDB, OMDB, Trakt, Fanart.tv + 5 free providers (no API key needed)
- **4 Free Providers Always Available** - AniList, Kitsu, Jikan/MAL, TVmaze (no configuration needed)
- **Anime Support** - Specialized anime detection with AniList, Kitsu, and Jikan/MAL
- **TV Show Detection** - Automatically detect and rename TV episodes using multiple databases
- **Custom Patterns** - Define your own naming conventions with powerful variables
- **Preview Changes** - See exactly what will be renamed before applying

### 🖥️ Modern Interface
- **Dark Theme** - Easy on the eyes during long processing sessions
- **Real-time Progress** - See exactly what's happening with detailed progress bars
- **Queue Management** - Add, remove, and reorder processing jobs
- **Comprehensive Logging** - Export and filter logs for troubleshooting

## Installation

### Desktop Application (Recommended)
Download the latest release for your platform:
- **Windows**: `.msi` installer
- **macOS**: `.dmg` package
- **Linux**: `.deb` or `.rpm` packages

The desktop application includes everything you need - no additional setup required.

### Alternative Interfaces
For developers and advanced users, Encode Forge also provides:

**Command Line Interface** - For automation and scripting
```bash
python ffmpeg_cli.py encoder /path/to/videos --use-nvenc
python ffmpeg_cli.py subtitle /path/to/videos --enable-subtitle-generation  
python ffmpeg_cli.py renamer /path/to/videos --tmdb-api-key YOUR_KEY --preview-only
```

**Web Interface** - For server deployment
```bash
./start_web_ui.sh  # or start_web_ui.bat on Windows
```

## Quick Start

1. **Download** the application for your platform
2. **Install** using the provided installer
3. **Launch** Encode Forge
4. **Add files** by dragging and dropping or using the file browser
5. **Configure** your processing options
6. **Start processing** and watch the real-time progress

## Configuration

### API Keys (Optional but Recommended)

**Metadata Providers (File Renaming)**

*Free Providers - No API Key Required:*
- ✅ **AniList** - Anime metadata (always available)
- ✅ **Kitsu** - Anime metadata (always available)
- ✅ **Jikan (MyAnimeList)** - Anime metadata (always available, read-only)
- ✅ **TVmaze** - TV show metadata (always available)

*API Key Providers - Free Keys Available:*

| Service | Purpose | Get it here |
|---------|---------|-------------|
| TMDB | Movies & TV metadata | [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) |
| TVDB | TV show metadata | [thetvdb.com/dashboard/account/apikey](https://thetvdb.com/dashboard/account/apikey) |
| OMDB | Movies & TV metadata | [omdbapi.com/apikey.aspx](http://www.omdbapi.com/apikey.aspx) |
| Trakt | Movies & TV tracking | [trakt.tv/oauth/applications](https://trakt.tv/oauth/applications) |
| Fanart.tv | Media artwork | [fanart.tv/get-an-api-key](https://fanart.tv/get-an-api-key/) |

**Subtitle Providers**

*Free Providers - No API Key Required:*
- ✅ **Addic7ed** - Movies, TV shows, and anime subtitles
- ✅ **SubDL** - Movie & TV subtitles
- ✅ **Subf2m** - Movie & TV subtitles
- ✅ **YIFY Subtitles** - Movie subtitles
- ✅ **Podnapisi** - Multilingual subtitles (all content types)
- ✅ **SubDivX** - Spanish subtitles
- ✅ **Kitsunekko** - Anime subtitles (English & Japanese)
- ✅ **Jimaku** - Anime subtitles (multiple languages)

*API Key Providers - Free Keys Available:*

| Service | Purpose | Limits | Get it here |
|---------|---------|--------|-------------|
| OpenSubtitles | Subtitle downloads | 5/day free, 200/day VIP | [opensubtitles.com/consumers](https://www.opensubtitles.com/en/consumers) |

> **Note**: Search works without any API key for OpenSubtitles. API key only needed for downloading subtitles.

### Hardware Acceleration
Encode Forge automatically detects and uses available hardware acceleration:
- **NVIDIA** - NVENC (GTX 600+)
- **AMD** - AMF (Windows only, recent cards)
- **Intel** - Quick Sync (6th gen+)
- **Apple** - VideoToolbox (all modern Macs)

## Screenshots

*Screenshots will be added here showing the main window with the three modes and the settings window.*

## Work in Progress

Encode Forge is actively being developed. Current work includes:
- **Plugin Support** - Extensible architecture for custom processing plugins, themes, and so on
- **Jellyfin Integration** - Direct integration with Jellyfin media servers
- **Plex Integration** - Direct integration with Plex media servers
- **UI Improvements** - Enhanced user experience and additional customization options
- **Performance Optimizations** - Faster processing and better resource management
- **Full MetaData Grabber** - Grab all missing metadata info for files, including artwork
- **Preview Window** - Visual Preview of applied subtitles
- **Audio Normalization** - Implement loudness normalization
- **Audio Syncing** - Fix Audio/Subtitle syncing intelligently
- **Update System** - Checks for updates and automatically updates Encode Forge

## System Requirements

- **Windows**: Windows 10 or later
- **macOS**: macOS 10.15 or later
- **Linux**: Ubuntu 18.04+ or equivalent
- **RAM**: 4GB minimum, 8GB recommended
- **Storage**: 500MB for application, additional space for temporary files

## Support

- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/yourusername/encodeforge/issues)
- 💡 **Feature Requests**: [GitHub Discussions](https://github.com/yourusername/encodeforge/discussions)
- 📖 **Documentation**: [Wiki](https://github.com/yourusername/encodeforge/wiki)

## License

MIT License - Use it, modify it, share it, sell it, whatever.

---

<div align="center">
  <sub>If Encode Forge has helped you, consider ⭐ starring the repository!</sub>
</div>

