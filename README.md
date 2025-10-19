# 🎬 EncodeForge

**The media processing tool that actually works the way you want it to.**

> ⚠️ **Work in Progress** - This project is actively being developed. Not all features are fully implemented yet, but the core functionality works. Check the issues page for current status and roadmap.

[![Python 3.7+](https://img.shields.io/badge/python-3.7+-blue.svg)](https://www.python.org/downloads/)
[![Java 17+](https://img.shields.io/badge/java-17+-orange.svg)](https://openjdk.org/)
[![FFmpeg](https://img.shields.io/badge/FFmpeg-required-green.svg)](https://ffmpeg.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## Why EncodeForge Exists

I got tired of the existing tools:
- **FileBot** - Too finicky, costs money, and half the time doesn't work right
- **FFmpeg commands** - Powerful but honestly, who has time to memorize all those flags?
- **HandBrake** - Slow as molasses and limited options
- **Subtitle tools** (SubGen, Bazarr, etc.) - Either overcomplicated, unreliable, or just don't work

I wanted something that **actually works** for what I need, how I want it, all in one place. So I built it and made it open source - because why should good tools cost money or suck to use?

---

## What It Does

**🎬 Video Encoding** - Convert videos fast with hardware acceleration. No more waiting hours for HandBrake.

**💬 Subtitle Generation** - AI-powered subtitles with Whisper, or download from OpenSubtitles. Actually works.

**📝 Smart Renaming** - FileBot-style renaming that doesn't make you want to throw your computer out the window.

---

## How To Use It

Pick your style - they all do the same thing:

**🖥️ Desktop App (Recommended)** - Clean GUI with tabs, progress bars, dark theme. Just works.
```bash
cd FFmpegGUI && ./mvnw javafx:run
```

**💻 Command Line** - For automation and power users who like typing.
```bash
python ffmpeg_cli.py encoder /path/to/videos --use-nvenc
python ffmpeg_cli.py subtitle /path/to/videos --enable-subtitle-generation  
python ffmpeg_cli.py renamer /path/to/videos --tmdb-api-key YOUR_KEY --preview-only
```

**🌐 Web Interface** - Run it on a server, access from anywhere.
```bash
./start_web_ui.sh  # or start_web_ui.bat on Windows
```

---

## Why It's Better

**🚀 Actually Fast**
- Stream copying by default (no quality loss, no waiting)
- Hardware acceleration that actually works (NVIDIA/AMD/Intel/Apple)
- Batch processing for entire libraries
- Smart fallbacks when hardware isn't available

**🧠 Actually Smart**  
- Auto-detects FFmpeg (no more "command not found" nonsense)
- Preserves ALL audio/subtitle tracks (doesn't randomly drop stuff)
- Proper metadata labeling ("Japanese Audio", "English Subtitles (SDH)")
- Hardware detection that works

**🤖 AI That Works**
- Whisper AI for subtitles in 90+ languages
- OpenSubtitles integration that actually finds matches
- TMDB movie/TV show detection and renaming
- Preview everything before it happens

**📊 Interface That Makes Sense**
- Real-time progress (see what's actually happening)
- Queue management (add, remove, reorder)
- Preview mode (see changes before applying)
- Proper logging (export, filter, actually useful)

---

## Getting Started

**What you need:**
- Python 3.7+ ([download here](https://www.python.org/downloads/))
- FFmpeg ([download here](https://ffmpeg.org/download.html) or let the app install it)
- Java 17+ for the desktop app ([download here](https://adoptium.net/))

**Install it:**
```bash
git clone https://github.com/yourusername/encodeforge.git
cd encodeforge
pip install -r requirements.txt

# For AI subtitles (optional):
pip install openai-whisper

# Pick your interface:
cd FFmpegGUI && ./mvnw javafx:run          # Desktop app
python -m streamlit run ffmpeg_webui.py    # Web interface  
python ffmpeg_cli.py --help                # Command line
```

---

## How To Use Each Mode

### 🎬 Video Encoding
Convert videos with hardware acceleration. Way faster than HandBrake.

```bash
python ffmpeg_cli.py encoder /path/to/videos --use-nvenc --output-format mp4
```

- Uses your GPU (NVIDIA/AMD/Intel/Apple) 
- Smart codec selection
- Can generate subtitles during encoding
- Can rename files after encoding
- Batch processing with real progress

### 💬 Subtitle Generation  
Generate AI subtitles or download existing ones. Actually works reliably.

```bash
# Generate with AI (offline)
python ffmpeg_cli.py subtitle /path/to/videos --enable-subtitle-generation --whisper-model base

# Download from OpenSubtitles
python ffmpeg_cli.py subtitle /path/to/videos --enable-subtitle-download --opensubtitles-username user
```

- Whisper AI for 90+ languages
- OpenSubtitles.com integration
- Preview before applying
- Multiple language support

### 📝 Smart Renaming
Rename files intelligently using movie/TV databases. Like FileBot but free and reliable.

```bash
# Preview first (always do this)
python ffmpeg_cli.py renamer /path/to/videos --tmdb-api-key YOUR_KEY --preview-only

# Apply renames  
python ffmpeg_cli.py renamer /path/to/videos --tmdb-api-key YOUR_KEY
```

- TMDB database integration
- Auto-detects TV shows vs movies
- Before/after preview
- Custom naming patterns

---

## Setup & Configuration

**Free API Keys (optional but recommended):**

| Service | What it's for | Get it here |
|---------|---------------|-------------|
| TMDB | File renaming | [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) |
| OpenSubtitles | Subtitle downloads | [opensubtitles.com/users/sign_up](https://www.opensubtitles.com/en/users/sign_up) |

**Hardware Acceleration:**
The app automatically detects what you have:
- **NVIDIA** - NVENC (Windows/Linux, GTX 600+)
- **AMD** - AMF (Windows only, recent cards)  
- **Intel** - Quick Sync (Windows/Linux, 6th gen+)
- **Apple** - VideoToolbox (macOS, all modern Macs)

**File Naming:**
Customize how renamed files look:
- TV Shows: `{title} - S{season}E{episode} - {episodeTitle}`
- Movies: `{title} ({year})`
- Examples: `Breaking Bad - S01E01 - Pilot.mkv`, `Inception (2010).mp4`

---

## Common Issues & Fixes

**"FFmpeg not found"**
- The app can auto-download it, or install manually:
- Windows: Download from [gyan.dev/ffmpeg](https://gyan.dev/ffmpeg)
- Mac: `brew install ffmpeg`  
- Linux: `sudo apt install ffmpeg` (Ubuntu) or `sudo dnf install ffmpeg` (Fedora)

**Hardware acceleration not working**
- **NVIDIA**: Need GTX 600+ and recent drivers
- **AMD**: Windows only, need recent drivers
- **Intel**: Need 6th gen CPU or newer with Quick Sync
- **Apple**: Works on all modern Macs
- App falls back to software encoding automatically

**Whisper won't install**
```bash
pip install --upgrade pip
pip install openai-whisper
# Or use the built-in installer in the GUI/Web UI
```

**TMDB API limits**
- Free tier: 40 requests per 10 seconds (plenty for normal use)
- Get a free API key - no credit card needed
- App caches results to minimize requests

---

## Contributing

Want to make it better? Awesome! Here's how:

1. Fork it
2. Make your changes  
3. Test it (try all three interfaces if you can)
4. Submit a pull request

**Good areas to contribute:**
- More subtitle sources
- Additional hardware acceleration  
- Better naming patterns
- Bug fixes
- Making the UI even nicer

---

## License & Support

**License:** MIT - Use it, modify it, share it, sell it, whatever.

**Support:**
- 🐛 Bug reports: [GitHub Issues](https://github.com/yourusername/encodeforge/issues)
- 💡 Feature requests: [GitHub Discussions](https://github.com/yourusername/encodeforge/discussions)

---

<p align="center">
  <sub>If this saved you time and frustration, consider ⭐ starring the repo!</sub>
</p>

