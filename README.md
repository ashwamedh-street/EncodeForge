# Encode Forge

<div align="center">
  <img src="EncodeForge/src/main/resources/icons/app-icon.png" alt="Encode Forge Logo" width="128" height="128">
  
  **Version 0.4.1**
  
  *The all-in-one media processing application*
</div>

---

## What is Encode Forge?

Encode Forge is a comprehensive media processing application that combines video encoding, subtitle generation using AI directly on your computer, and smart file renaming into a single, easy-to-use desktop application. Built with JavaFX and Python, it provides a modern interface for all your media processing needs.

## Features

### Video Encoding
- **Hardware Acceleration** - Supports NVIDIA NVENC, AMD AMF, Intel Quick Sync, and Apple VideoToolbox
- **Smart Codec Selection** - Automatically chooses the best codec for your hardware
- **Batch Processing** - Process entire libraries with real-time progress tracking
- **Stream Copying** - Preserve quality while converting containers
- **Audio Normalization** - Ensure consistent audio levels across all media files

### Subtitle Generation
- **AI-Powered Subtitles** - Can't find subtitles you want? Generate subtitles using OpenAI Whisper (90+ languages)
- **GPU Acceleration** - 10x-20x faster subtitle generation with NVIDIA, AMD, or Apple Silicon GPUs
- **9 Subtitle Providers** - Download from multiple sources including anime-specific providers (Note: Most Web Scrapping Providers do not work. Currently only OpenSubtitles works.)
- **Multiple Language Support** - Handle multiple audio tracks and subtitle languages
- **Preview Mode** - Review subtitles before applying (WIP/ComingSoon)

### Smart File Renaming
- **10 Metadata Providers** - TMDB, TVDB, OMDB, Trakt, Fanart.tv + 5 free providers (no API key needed)
- **4 Free Providers Always Available** - AniDB, Kitsu, Jikan/MAL, TVmaze (no configuration needed) (Note: AniDB is rate limited heavily)
- **Movie Support** - Automatically detect and rename movies using multiple database
- **Anime Support** - Specialized anime detection with AniDB, Kitsu, and Jikan/MAL
- **TV Show Detection** - Automatically detect and rename TV episodes using multiple databases
- **Custom Patterns** - Define your own naming conventions with powerful variables (Example: {title} - S##E## - {episode-title})
- **Preview Changes** - See exactly what will be renamed before applying

### Modern Interface
- **Dark Theme** - Easy on the eyes during long processing sessions
- **Real-time Progress** - See exactly what's happening with detailed progress bars
- **Queue Management** - Add, remove, and reorder processing jobs
- **Comprehensive Logging** - Export and filter logs for troubleshooting

## Installation

### Desktop Application (Recommended)
Download the latest release for your platform:
- **Windows**: `.exe` or `.msi` installer
- **macOS**: `.dmg` package
- **Linux**: `.deb` or `.rpm` packages

**First Launch Setup:**
On first launch, EncodeForge will automatically:
- Download and install FFmpeg (~100-150 MB)
- Install required Python libraries (~50 MB)
- Configure everything for you with a progress window

This one-time setup takes 2-5 minutes depending on your connection. After that, you're ready to go!

**Optional AI Features:**
AI subtitle generation (OpenAI Whisper) can be installed later through the Tools menu when needed. The installer automatically detects your GPU (NVIDIA, AMD, Apple Silicon) and downloads the appropriate PyTorch version for maximum performance.

### Alternative Interfaces (Not up to date - Will be updated once Java app is 1.0)
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

1. **Download** the installer for your platform from Releases
2. **Run the installer** and follow the installation wizard
3. **Launch** Encode Forge
4. **Wait for first-time setup** - EncodeForge will download FFmpeg and Python libraries (one-time, 2-5 minutes)
5. **Add files** by dragging and dropping or using the file browser
6. **Configure** your processing options
7. **Start processing** and watch the real-time progress

**Optional:** Install AI subtitle generation via Tools → Setup AI Subtitles for Whisper support.

## Configuration

### API Keys (Optional but Recommended)

**Metadata Providers (File Renaming)**

*Free Providers - No API Key Required:*
- **AniDB** - Anime metadata (always available)
- **Kitsu** - Anime metadata (always available)
- **Jikan (MyAnimeList)** - Anime metadata (always available, read-only)
- **TVmaze** - TV show metadata (always available)

*API Key Providers - Free Keys Available:*

| Service | Purpose | Get it here |
|---------|---------|-------------|
| TMDB | Movies & TV metadata | [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) |
| TVDB | TV show metadata | [thetvdb.com/dashboard/account/apikey](https://thetvdb.com/dashboard/account/apikey) |
| OMDB | Movies & TV metadata | [omdbapi.com/apikey.aspx](http://www.omdbapi.com/apikey.aspx) |
| Trakt | Movies & TV tracking | [trakt.tv/oauth/applications](https://trakt.tv/oauth/applications) |
| Fanart.tv | Media artwork | [fanart.tv/get-an-api-key](https://fanart.tv/get-an-api-key/) |

**Subtitle Providers**

*Free Providers - WIP*
- **Addic7ed** - Movies, TV shows, and anime subtitles
- **SubDL** - Movie & TV subtitles
- **Subf2m** - Movie & TV subtitles
- **YIFY Subtitles** - Movie subtitles
- **Podnapisi** - Multilingual subtitles (all content types)
- **SubDivX** - Spanish subtitles
- **Kitsunekko** - Anime subtitles (English & Japanese)
- **Jimaku** - Anime subtitles (multiple languages)

*Account Providers*

| Service | Purpose | Limits | Get it here |
|---------|---------|--------|-------------|
| OpenSubtitles | Subtitle downloads | 5/day free, 200/day VIP | [opensubtitles.com/consumers](https://www.opensubtitles.com/en/consumers) |

> **Note**: Search works without login unlimited, downloads limited to 5 free per day without account, 20 per day with account, and 200 per day with VIP.

### Hardware Acceleration
Encode Forge automatically detects and uses available hardware acceleration:
- **NVIDIA** - NVENC (GTX 600+)
- **AMD** - AMF (Windows only, recent cards)
- **Intel** - Quick Sync (6th gen+)
- **Apple** - VideoToolbox (all modern Macs)

## Screenshots

*Screenshots will be added here showing the main window with the three modes and the settings window.*

## Recent Updates (v0.4.0)

### What's New
- **Audio Normalization** - Ensure consistent volume levels across all your media files
- **GPU-Accelerated AI Subtitles** - 10x-20x faster Whisper subtitle generation with automatic GPU detection
- **Performance Improvements** - Faster startup times and reduced memory usage through lazy loading
- **UI Enhancements** - Improved visual depth, better component scaling, and enhanced dark theme

See [CHANGELOG.md](CHANGELOG.md) for complete release notes.

## Roadmap

Encode Forge is actively being developed. Planned features include:
- **Plugin Support** (v1.0) - Extensible architecture for custom processing plugins, themes, and so on
- **Jellyfin Integration** - Direct integration with Jellyfin media servers
- **Plex Integration** - Direct integration with Plex media servers
- **Preview Window** - Visual preview of applied subtitles
- **Full Metadata Grabber** - Grab all missing metadata info for files, including artwork
- **Audio Syncing** - Intelligent audio/subtitle synchronization fixes

## System Requirements

- **Windows**: Windows 10 or later
- **macOS**: macOS 10.15 or later
- **Linux**: Ubuntu 18.04+ or equivalent
- **RAM**: 4GB minimum, 8GB recommended for AI subtitle generation
- **Storage**: 
  - 100 MB for application installer
  - 250 MB for dependencies (FFmpeg + Python libraries, auto-downloaded)
  - 300 MB - 3 GB for AI models (optional, only if using Whisper)
  - Additional space for temporary files during processing
- **Internet**: Required for first-time setup and optional AI model downloads

## Support

- **Bug Reports**: [GitHub Issues](https://github.com/SirStig/EncodeForge/issues)
- **Feature Requests**: [GitHub Discussions](https://github.com/SirStig/EncodeForge/discussions)
- **Documentation**: [Wiki](https://github.com/SirStig/EncodeForge/wiki)

## License

MIT License - Use it, modify it, share it, sell it, whatever.

---
