#!/usr/bin/env python3
"""
FFmpeg Core Module - Shared functionality for CLI, WebUI, and Java GUI
Handles all video conversion, subtitle management, and media renaming operations
"""

import json
import logging
import os
import subprocess
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

# Import helper modules
from ffmpeg_manager import FFmpegManager
from media_renamer import MediaRenamer
from profile_manager import ProfileManager
from subtitle_providers import SubtitleProviders
from whisper_manager import WhisperManager

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


@dataclass
class ConversionSettings:
    """Settings for the conversion process"""
    # FFmpeg paths
    ffmpeg_path: str = "ffmpeg"
    ffprobe_path: str = "ffprobe"
    
    # Hardware acceleration
    use_nvenc: bool = True
    nvenc_preset: str = "p4"
    nvenc_cq: int = 23
    nvenc_codec: str = "h264_nvenc"  # Default to H.264 NVENC
    use_amf: bool = False  # AMD
    use_qsv: bool = False  # Intel Quick Sync
    use_videotoolbox: bool = False  # Apple
    hardware_decoding: bool = True  # Enable hardware decoding when available
    
    # Subtitle options
    convert_subtitles: bool = True
    subtitle_format: str = "srt"
    extract_forced_subs: bool = True
    extract_sdh_subs: bool = True
    
    # Subtitle generation/download
    enable_subtitle_generation: bool = False
    enable_subtitle_download: bool = False
    subtitle_languages: List[str] = field(default_factory=lambda: ["eng"])
    replace_existing_subtitles: bool = False
    whisper_model: str = "base"
    opensubtitles_api_key: str = ""
    opensubtitles_username: str = ""
    opensubtitles_password: str = ""
    
    # Media renaming
    enable_renaming: bool = False
    renaming_pattern_tv: str = "{title} - S{season}E{episode} - {episodeTitle}"
    renaming_pattern_movie: str = "{title} ({year})"
    tmdb_api_key: str = ""
    tvdb_api_key: str = ""
    anidb_api_key: str = ""
    
    # Audio options
    audio_codec: str = "copy"
    audio_bitrate: Optional[str] = None
    normalize_audio: bool = False
    
    # Video options
    video_codec: str = "Software H.264"  # User's codec selection from UI (e.g., "H.265 AMF (GPU)")
    video_codec_fallback: str = "libx264"
    video_preset: str = "medium"
    quality_preset: str = "medium"  # Quality preset from UI (highest/high/medium/low/very low)
    crf: int = 23  # CRF value from UI
    video_crf: int = 23  # Fallback CRF (deprecated, use 'crf' instead)
    target_resolution: Optional[str] = None
    
    # General options
    traverse_subdirs: bool = True
    delete_original: bool = True
    use_faststart: bool = True
    output_suffix: str = ""
    overwrite_existing: bool = False
    dry_run: bool = False
    output_format: str = "mp4"


@dataclass
class FileInfo:
    """Information about a media file"""
    path: Path
    size_mb: float
    duration: float = 0.0
    video_codec: str = "Unknown"
    audio_tracks: List[Dict[str, str]] = field(default_factory=list)
    subtitle_tracks: List[Dict[str, str]] = field(default_factory=list)


class FFmpegCore:
    """Core functionality for FFmpeg operations"""
    
    def __init__(self, settings: Optional[ConversionSettings] = None):
        self.settings = settings or ConversionSettings()
        self.ffmpeg_mgr = FFmpegManager(self.settings.ffmpeg_path)
        self.whisper_mgr = WhisperManager()
        self.renamer = MediaRenamer(
            tmdb_key=self.settings.tmdb_api_key,
            tvdb_key=self.settings.tvdb_api_key,
            anidb_key=self.settings.anidb_api_key
        )
        self.subtitle_providers = SubtitleProviders(
            opensubtitles_key=self.settings.opensubtitles_api_key,
            opensubtitles_user=self.settings.opensubtitles_username,
            opensubtitles_pass=self.settings.opensubtitles_password
        )
        self.profile_mgr = ProfileManager()
        # Track current process for cancellation
        self.current_process: Optional[subprocess.Popen] = None
        self.cancel_requested: bool = False
        
    def check_ffmpeg(self) -> Dict:
        """Check FFmpeg availability"""
        success, info = self.ffmpeg_mgr.detect_ffmpeg()
        
        if success:
            self.settings.ffmpeg_path = str(info["ffmpeg_path"])
            self.settings.ffprobe_path = str(info["ffprobe_path"])
            
            # Check hardware acceleration capabilities
            hwaccel = self.ffmpeg_mgr.get_hwaccel_options()
            
            return {
                "status": "success",
                "ffmpeg_available": True,
                "ffmpeg_version": info["version"],
                "ffmpeg_path": info["ffmpeg_path"],
                "ffprobe_path": info["ffprobe_path"],
                "hardware_encoders": info["encoders"],
                "hardware_decoders": info["decoders"],
                "hwaccel_options": hwaccel
            }
        else:
            return {
                "status": "error",
                "ffmpeg_available": False,
                "message": info.get("error", "FFmpeg not found")
            }
    
    def download_ffmpeg(self, progress_callback: Optional[Callable] = None) -> Dict:
        """Download and install FFmpeg"""
        success, message = self.ffmpeg_mgr.download_ffmpeg(progress_callback=progress_callback)
        
        return {
            "status": "success" if success else "error",
            "message": message
        }
    
    def check_whisper(self) -> Dict:
        """Check Whisper availability"""
        status = self.whisper_mgr.get_status()
        
        return {
            "status": "success",
            "whisper_available": status["installed"],
            "installed_models": status["models"],
            "available_models": status["available_models"],
            "model_sizes": status["model_sizes"]
        }
    
    def get_file_info(self, file_path: str) -> Dict:
        """Get basic file information (duration, format) - faster version"""
        try:
            if not os.path.exists(file_path):
                return {"status": "error", "message": "File not found", "duration": 0}
            
            # Get ffprobe path from manager (it's detected and stored there)
            ffprobe_path = self.ffmpeg_mgr.ffprobe_path if self.ffmpeg_mgr.ffprobe_path else "ffprobe"
            
            # Use ffprobe to get basic info
            cmd = [
                ffprobe_path,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                file_path
            ]
            
            logger.debug(f"Running ffprobe: {' '.join(cmd)}")
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            
            if result.returncode != 0:
                return {"status": "error", "message": "Failed to probe file", "duration": 0}
            
            import json
            probe_data = json.loads(result.stdout)
            format_info = probe_data.get("format", {})
            
            duration = float(format_info.get("duration", 0))
            
            return {
                "status": "success",
                "duration": duration,
                "size": format_info.get("size", "Unknown"),
                "format": format_info.get("format_name", "Unknown")
            }
            
        except Exception as e:
            logger.error(f"Error getting file info: {e}")
            return {"status": "error", "message": str(e), "duration": 0}
    
    def get_media_info(self, file_path: str) -> Dict:
        """Get detailed media information for a file"""
        try:
            if not os.path.exists(file_path):
                return {"status": "error", "message": "File not found"}
            
            # Validate file is readable
            try:
                with open(file_path, 'rb') as f:
                    # Try to read first few bytes to ensure file is accessible
                    f.read(1024)
            except Exception as e:
                return {"status": "error", "message": f"File not readable: {e}"}
            
            # Get ffprobe path from manager (it's detected and stored there)
            ffprobe_path = self.ffmpeg_mgr.ffprobe_path if self.ffmpeg_mgr.ffprobe_path else "ffprobe"
            
            # Use ffprobe to get media info - properly quote file path
            cmd = [
                ffprobe_path,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                file_path  # subprocess.run handles path quoting automatically
            ]
            
            logger.debug(f"Running ffprobe for media info: {' '.join(cmd)}")
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
            
            if result.returncode != 0:
                error_msg = result.stderr.strip() if result.stderr else "Unknown ffprobe error"
                logger.error(f"ffprobe failed with code {result.returncode}: {error_msg}")
                return {"status": "error", "message": f"Failed to probe file: {error_msg}"}
            
            import json
            probe_data = json.loads(result.stdout)
            
            # Extract track information
            video_tracks = []
            audio_tracks = []
            subtitle_tracks = []
            
            for stream in probe_data.get("streams", []):
                codec_type = stream.get("codec_type", "")
                
                if codec_type == "video":
                    video_tracks.append({
                        "codec": stream.get("codec_name", "Unknown"),
                        "resolution": f"{stream.get('width', '?')}x{stream.get('height', '?')}",
                        "fps": str(eval(stream.get("r_frame_rate", "0/1"))) if "/" in stream.get("r_frame_rate", "") else stream.get("r_frame_rate", "Unknown"),
                        "bitrate": stream.get("bit_rate", "Unknown")
                    })
                
                elif codec_type == "audio":
                    audio_tracks.append({
                        "codec": stream.get("codec_name", "Unknown"),
                        "language": stream.get("tags", {}).get("language", "und"),
                        "channels": str(stream.get("channels", "?")),
                        "sample_rate": stream.get("sample_rate", "Unknown"),
                        "bitrate": stream.get("bit_rate", "Unknown")
                    })
                
                elif codec_type == "subtitle":
                    subtitle_tracks.append({
                        "codec": stream.get("codec_name", "Unknown"),
                        "language": stream.get("tags", {}).get("language", "und"),
                        "forced": stream.get("disposition", {}).get("forced", 0) == 1,
                        "title": stream.get("tags", {}).get("title", "")
                    })
            
            format_info = probe_data.get("format", {})
            
            return {
                "status": "success",
                "video_tracks": video_tracks,
                "audio_tracks": audio_tracks,
                "subtitle_tracks": subtitle_tracks,
                "duration": format_info.get("duration", "Unknown"),
                "size": format_info.get("size", "Unknown"),
                "bitrate": format_info.get("bit_rate", "Unknown"),
                "format": format_info.get("format_name", "Unknown")
            }
            
        except Exception as e:
            logger.error(f"Error getting media info: {e}")
            return {"status": "error", "message": str(e)}
    
    def install_whisper(self, progress_callback: Optional[Callable] = None) -> Dict:
        """Install Whisper"""
        success, message = self.whisper_mgr.install_whisper(progress_callback=progress_callback)
        
        return {
            "status": "success" if success else "error",
            "message": message
        }
    
    def download_whisper_model(self, model: str, progress_callback: Optional[Callable] = None) -> Dict:
        """Download a Whisper model"""
        success, message = self.whisper_mgr.download_model(model, progress_callback=progress_callback)
        
        return {
            "status": "success" if success else "error",
            "message": message
        }
    
    def generate_subtitles(self, video_path: str, language: Optional[str] = None, 
                          progress_callback: Optional[Callable] = None) -> Dict:
        """Generate subtitles for a video file using Whisper AI"""
        # Check if Whisper is available
        whisper_status = self.check_whisper()
        if not whisper_status.get("whisper_available"):
            return {
                "status": "error",
                "message": "Whisper AI is not installed. Please install it from Settings > Subtitles.",
                "subtitle_path": None
            }
        
        # Check if requested model is available
        installed_models = whisper_status.get("installed_models", [])
        requested_model = self.settings.whisper_model
        
        if requested_model not in installed_models:
            available = whisper_status.get("available_models", [])
            return {
                "status": "error",
                "message": f"Whisper model '{requested_model}' is not installed. Please download it from Settings > Subtitles. Available models: {', '.join(available)}",
                "subtitle_path": None
            }
        
        try:
            output_path = Path(video_path).with_suffix(f".{language or 'auto'}.srt")
            
            success, message = self.whisper_mgr.generate_subtitles(
                video_path,
                str(output_path),
                model_name=self.settings.whisper_model,
                language=language,
                progress_callback=progress_callback
            )
            
            return {
                "status": "success" if success else "error",
                "message": message,
                "subtitle_path": str(output_path) if success else None
            }
        except Exception as e:
            logger.error(f"Error generating subtitles: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Failed to generate subtitles: {str(e)}",
                "subtitle_path": None
            }
    
    def download_subtitles(self, video_path: str, languages: Optional[List[str]] = None) -> Dict:
        """Download subtitles from multiple providers"""
        if languages is None:
            languages = self.settings.subtitle_languages
        
        # Use multi-provider search
        results = self.subtitle_providers.batch_download([video_path], languages)
        
        success_count = len(results.get("success", []))
        failed_count = len(results.get("failed", []))
        
        subtitle_paths = [item["subtitle"] for item in results.get("success", [])]
        
        return {
            "status": "success" if success_count > 0 else "error",
            "message": f"Downloaded {success_count} subtitle(s), {failed_count} failed",
            "subtitle_paths": subtitle_paths,
            "details": results
        }
    
    def preview_rename(self, file_paths: List[str], settings_dict: Optional[Dict] = None) -> Dict:
        """
        Preview how files would be renamed
        Returns old and new names for comparison with provider info
        """
        # Update settings if provided
        if settings_dict:
            if "tmdb_api_key" in settings_dict:
                self.settings.tmdb_api_key = settings_dict["tmdb_api_key"]
                self.renamer.tmdb_key = settings_dict["tmdb_api_key"]
            if "tvdb_api_key" in settings_dict:
                self.settings.tvdb_api_key = settings_dict["tvdb_api_key"]
                self.renamer.tvdb_key = settings_dict["tvdb_api_key"]
        
        suggested_names = []
        providers = []
        errors = []
        
        for file_path in file_paths:
            path = Path(file_path)
            
            # Detect media type
            media_type = self.renamer.detect_media_type(path.name)
            
            info = None
            new_name = None
            provider = None
            error = None
            
            # Check if any metadata providers are available
            # AniList doesn't require API key, so always available
            has_tmdb = self.settings.tmdb_api_key and self.settings.tmdb_api_key.strip()
            has_tvdb = self.settings.tvdb_api_key and self.settings.tvdb_api_key.strip()
            has_anilist = True  # Always available
            
            if not has_tmdb and not has_tvdb and not has_anilist:
                error = "❌ ERROR: No metadata providers available."
            elif media_type == "unknown":
                error = "❌ ERROR: Could not detect media type from filename. Expected TV show (S01E01) or Movie format."
            elif media_type == "tv":
                parsed = self.renamer.parse_tv_filename(path.name)
                if not parsed:
                    error = "❌ ERROR: Could not parse TV show information from filename."
                else:
                    # Try TMDB first, then TVDB
                    if self.settings.tmdb_api_key:
                        try:
                            info = self.renamer.search_tv_show(
                                parsed["title"],
                                parsed["season"],
                                parsed["episode"]
                            )
                            if info:
                                new_name = self.renamer.format_filename(
                                    info,
                                    self.settings.renaming_pattern_tv
                                ) + path.suffix
                                provider = "TMDB"
                        except Exception as e:
                            logger.error(f"TMDB lookup failed: {e}")
                    
                    if not info and self.settings.tvdb_api_key:
                        try:
                            info = self.renamer.search_tv_show_tvdb(
                                parsed["title"],
                                parsed["season"],
                                parsed["episode"]
                            )
                            if info:
                                new_name = self.renamer.format_filename(
                                    info,
                                    self.settings.renaming_pattern_tv
                                ) + path.suffix
                                provider = "TVDB"
                        except Exception as e:
                            logger.error(f"TVDB lookup failed: {e}")
                    
                    if not info:
                        error = f"❌ ERROR: No metadata found for '{parsed['title']}' S{parsed['season']:02d}E{parsed['episode']:02d}'"
            
            elif media_type == "movie":
                parsed = self.renamer.parse_movie_filename(path.name)
                if not parsed:
                    error = "❌ ERROR: Could not parse movie information from filename."
                else:
                    if self.settings.tmdb_api_key:
                        try:
                            info = self.renamer.search_movie(
                                parsed["title"],
                                parsed.get("year")
                            )
                            if info:
                                new_name = self.renamer.format_filename(
                                    info,
                                    self.settings.renaming_pattern_movie
                                ) + path.suffix
                                provider = "TMDB"
                        except Exception as e:
                            logger.error(f"TMDB movie lookup failed: {e}")
                    
                    if not info:
                        year_str = f" ({parsed.get('year')})" if parsed.get('year') else ""
                        error = f"❌ ERROR: No metadata found for movie '{parsed['title']}'{year_str}"
            
            suggested_names.append(new_name if new_name else error if error else path.name)
            providers.append(provider if provider else "None")
            errors.append(error if error else "")
        
        return {
            "status": "success",
            "suggested_names": suggested_names,
            "providers": providers,
            "errors": errors
        }
    
    def rename_files(self, file_paths: List[str], dry_run: bool = False) -> Dict:
        """Rename media files using metadata"""
        results = []
        
        for file_path in file_paths:
            pattern = self.settings.renaming_pattern_tv
            media_type = self.renamer.detect_media_type(file_path)
            
            if media_type == "movie":
                pattern = self.settings.renaming_pattern_movie
            
            success, message, new_path = self.renamer.rename_file(
                file_path,
                pattern,
                auto_detect=True,
                dry_run=dry_run
            )
            
            results.append({
                "original_path": file_path,
                "new_path": new_path,
                "success": success,
                "message": message
            })
        
        return {
            "status": "success",
            "results": results
        }
    
    def scan_directory(self, directory: str, recursive: bool = True, 
                      file_extensions: Optional[List[str]] = None) -> Dict:
        """Scan directory for media files"""
        if file_extensions is None:
            file_extensions = [".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".webm"]
        
        path = Path(directory)
        
        if not path.exists():
            return {
                "status": "error",
                "message": f"Directory not found: {directory}"
            }
        
        files = []
        
        try:
            if recursive:
                for ext in file_extensions:
                    files.extend(path.rglob(f"*{ext}"))
            else:
                for ext in file_extensions:
                    files.extend(path.glob(f"*{ext}"))
            
            file_paths = [str(f.absolute()) for f in files]
            
            return {
                "status": "success",
                "files": file_paths,
                "count": len(file_paths)
            }
        
        except Exception as e:
            logger.error(f"Error scanning directory: {e}")
            return {
                "status": "error",
                "message": str(e)
            }
    
    
    def convert_file(
        self,
        input_path: str,
        output_path: Optional[str] = None,
        progress_callback: Optional[Callable] = None
    ) -> Dict:
        """
        Convert a single video file with the current settings
        
        Args:
            input_path: Path to input video file
            output_path: Path to output file (auto-generated if None)
            progress_callback: Callback function for progress updates
            
        Returns:
            Dict with status, message, and output_path
        """
        try:
            input_file = Path(input_path)
            
            # Validate input file exists and is readable
            if not input_file.exists():
                return {"status": "error", "message": f"Input file not found: {input_path}"}
            
            if not input_file.is_file():
                return {"status": "error", "message": f"Input path is not a file: {input_path}"}
            
            # Test if file is readable by trying to get basic info
            try:
                file_size = input_file.stat().st_size
                if file_size == 0:
                    return {"status": "error", "message": f"Input file is empty: {input_path}"}
                logger.info(f"Input file size: {file_size} bytes")
            except Exception as e:
                return {"status": "error", "message": f"Cannot access input file: {input_path} - {e}"}
            
            # Generate output path if not provided
            if output_path is None:
                output_dir = input_file.parent
                base_name = input_file.stem
                
                # If input and output formats are the same, add suffix to avoid overwriting
                if input_file.suffix.lower().lstrip('.') == self.settings.output_format.lower():
                    if self.settings.output_suffix:
                        output_path_obj = output_dir / f"{base_name}{self.settings.output_suffix}.{self.settings.output_format}"
                    else:
                        output_path_obj = output_dir / f"{base_name}_converted.{self.settings.output_format}"
                else:
                    if self.settings.output_suffix:
                        output_path_obj = output_dir / f"{base_name}{self.settings.output_suffix}.{self.settings.output_format}"
                    else:
                        output_path_obj = output_dir / f"{base_name}.{self.settings.output_format}"
                
                # If file still exists and we can't overwrite, generate unique name
                if output_path_obj.exists() and not self.settings.overwrite_existing:
                    counter = 1
                    while output_path_obj.exists():
                        if self.settings.output_suffix:
                            output_path_obj = output_dir / f"{base_name}{self.settings.output_suffix}_{counter}.{self.settings.output_format}"
                        else:
                            output_path_obj = output_dir / f"{base_name}_{counter}.{self.settings.output_format}"
                        counter += 1
                    logger.info(f"Generated unique output filename: {output_path_obj.name}")
            else:
                output_path_obj = Path(output_path)
                
                # Check if output exists for explicit paths
                if output_path_obj.exists() and not self.settings.overwrite_existing:
                    return {"status": "error", "message": f"Output file already exists: {output_path_obj}"}
            
            # Build FFmpeg command
            # Place global flags early to reduce stderr chatter and improve progress behavior
            cmd = [self.settings.ffmpeg_path, "-hide_banner", "-loglevel", "error"]
            
            # Determine best encoder with automatic fallback (do this BEFORE input to check hardware support)
            encoder_info = self._select_best_encoder()
            
            # Hardware decoding for input (only if using hardware encoder to avoid compatibility issues)
            if self.settings.hardware_decoding and encoder_info["type"] == "hardware":
                cmd.extend(["-hwaccel", "auto"])
                # Only use cuda output format if we're actually using NVENC
                if encoder_info["codec"].endswith("nvenc"):
                    cmd.extend(["-hwaccel_output_format", "cuda"])
                    cmd.extend(["-extra_hw_frames", "8"])
                    logger.info("Hardware decoding enabled with NVENC optimizations")
                else:
                    logger.info("Hardware decoding enabled (basic)")
            
            # Input file
            cmd.extend(["-i", str(input_file)])
            
            if encoder_info["type"] == "hardware":
                cmd.extend(["-c:v", encoder_info["codec"]])
                
                # Comprehensive hardware optimizations for ALL GPU types
                if encoder_info["codec"].endswith("nvenc"):
                    # NVIDIA NVENC optimizations (RTX, GTX, Quadro, Tesla)
                    cmd.extend(["-preset", "p1"])  # Fastest preset (p1 = fastest, p7 = slowest)
                    cmd.extend(["-tune", "hq"])  # High quality tune
                    cmd.extend(["-rc", "vbr"])  # Variable bitrate for better quality/speed balance
                    cmd.extend(["-cq", str(self.settings.nvenc_cq)])
                    cmd.extend(["-b:v", "0"])  # Unconstrained VBR
                    cmd.extend(["-multipass", "qres"])  # Quarter resolution first pass for speed
                    cmd.extend(["-spatial-aq", "1"])  # Spatial AQ for quality
                    cmd.extend(["-temporal-aq", "1"])  # Temporal AQ for better compression
                    cmd.extend(["-surfaces", "64"])  # Maximum surfaces for RTX cards
                    cmd.extend(["-forced-idr", "1"])  # Force IDR frames for seeking
                    cmd.extend(["-gpu", "any"])  # Use any available GPU
                    cmd.extend(["-delay", "0"])  # Minimize encoding delay
                    cmd.extend(["-bf", "3"])  # B-frames for better compression
                    cmd.extend(["-b_ref_mode", "middle"])  # B-frame reference mode
                    
                elif encoder_info["codec"].endswith("amf"):
                    # AMD AMF optimizations (RX 400+, APU, Pro series)
                    cmd.extend(["-quality", "speed"])  # Prioritize encoding speed
                    cmd.extend(["-rc", "cqp"])  # Constant quantization for consistent quality
                    cmd.extend(["-qp_i", str(self.settings.nvenc_cq)])  # I-frame quality
                    cmd.extend(["-qp_p", str(self.settings.nvenc_cq + 2)])  # P-frame quality
                    cmd.extend(["-qp_b", str(self.settings.nvenc_cq + 4)])  # B-frame quality
                    cmd.extend(["-preanalysis", "1"])  # Pre-analysis for better encoding
                    cmd.extend(["-vbaq", "1"])  # Variance based adaptive quantization
                    cmd.extend(["-frame_skipping", "0"])  # Never skip frames
                    cmd.extend(["-filler_data", "0"])  # No filler data for speed
                    
                elif encoder_info["codec"].endswith("qsv"):
                    # Intel Quick Sync optimizations (HD Graphics 2000+, Arc, Iris)
                    cmd.extend(["-preset", "veryfast"])  # Fastest QSV preset
                    cmd.extend(["-global_quality", str(self.settings.nvenc_cq)])
                    cmd.extend(["-look_ahead", "1"])  # Enable lookahead for better quality
                    cmd.extend(["-look_ahead_depth", "60"])  # Maximum lookahead for Arc
                    cmd.extend(["-async_depth", "6"])  # Higher async depth for speed
                    cmd.extend(["-low_power", "0"])  # Use full power mode for speed
                    cmd.extend(["-extbrc", "1"])  # Extended bitrate control
                    
                elif encoder_info["codec"].endswith("videotoolbox"):
                    # Apple VideoToolbox optimizations (M1/M2/Intel Macs)
                    cmd.extend(["-q:v", str(self.settings.nvenc_cq)])
                    cmd.extend(["-realtime", "1"])  # Real-time encoding
                    cmd.extend(["-frames:v", "0"])  # No frame limit
                    cmd.extend(["-allow_sw", "1"])  # Allow software fallback if needed
                    
                # Universal hardware optimizations
                # FFmpeg thread management (0 = auto-detect and use optimal count)
                import os
                cpu_count = os.cpu_count() or 8
                # For hardware encoding, use all logical cores
                cmd.extend(["-threads", str(cpu_count)])
                cmd.extend(["-thread_type", "slice"])  # Slice-based threading for speed
                logger.info(f"Using {cpu_count} threads for hardware encoding")
                
            else:
                # Software encoding fallback with maximum optimizations
                cmd.extend(["-c:v", encoder_info["codec"]])
                
                # Get CRF value from user settings (prefer 'crf' field from UI)
                crf_value = getattr(self.settings, 'crf', self.settings.video_crf)
                
                # Get optimal thread count
                import os
                cpu_count = os.cpu_count() or 8
                optimal_threads = cpu_count  # Use all logical cores for software encoding
                
                if encoder_info["codec"] == "libx264":
                    # x264 speed optimizations
                    cmd.extend(["-preset", "veryfast"])  # Much faster than "faster"
                    cmd.extend(["-tune", "fastdecode"])  # Optimize for fast decoding
                    cmd.extend(["-crf", str(crf_value)])
                    cmd.extend(["-threads", str(optimal_threads)])  # Use all CPU threads
                    cmd.extend(["-slices", str(min(8, optimal_threads))])  # Optimize slice count
                    cmd.extend(["-x264-params", 
                              "aq-mode=1:me=hex:subme=4:ref=2:bframes=2:b-adapt=0:direct=spatial:rc-lookahead=20"])
                    logger.info(f"x264: Using {optimal_threads} threads with {min(8, optimal_threads)} slices")
                    
                elif encoder_info["codec"] == "libx265":
                    # x265 speed optimizations with thread pools
                    cmd.extend(["-preset", "veryfast"])  # Fastest practical preset
                    cmd.extend(["-crf", str(crf_value)])
                    cmd.extend(["-threads", str(optimal_threads)])
                    # x265 benefits from more aggressive threading
                    cmd.extend(["-x265-params", 
                              f"pools={max(1, optimal_threads // 4)}:aq-mode=1:me=0:subme=1:ref=1:bframes=2:rd=1:pmode:pme:no-cutree:no-sao:no-strong-intra-smoothing:frame-threads={min(6, optimal_threads // 2)}"])
                    logger.info(f"x265: Using {optimal_threads} threads with {max(1, optimal_threads // 4)} pools")
                    
                else:
                    # Generic software encoder
                    cmd.extend(["-preset", self.settings.video_preset])
                    cmd.extend(["-crf", str(crf_value)])
                    cmd.extend(["-threads", str(optimal_threads)])
                    logger.info(f"Software encoder: Using {optimal_threads} threads")
            
            # Universal performance optimizations for ALL encoders
            cmd.extend(["-movflags", "+write_colr+faststart"])  # Better color + web optimization
            cmd.extend(["-pix_fmt", "yuv420p"])  # Universal pixel format
            
            # Aggressive performance optimizations
            cmd.extend(["-max_muxing_queue_size", "9999"])  # Prevent queue overflow
            cmd.extend(["-fflags", "+genpts+igndts"])  # Generate PTS, ignore DTS for speed
            cmd.extend(["-avoid_negative_ts", "make_zero"])  # Fix timestamp issues
            cmd.extend(["-fps_mode", "cfr"])  # Constant frame rate for predictable encoding
            
            logger.info(f"Using video encoder: {encoder_info['codec']} ({encoder_info['type']})")
            if encoder_info.get("reason"):
                logger.info(f"Encoder selection reason: {encoder_info['reason']}")
            
            # Enhanced stream mapping with proper subtitle handling
            if self.settings.audio_codec == "copy":
                # Copy all audio streams when copying
                cmd.extend(["-map", "0:v:0"])  # Map first video stream
                cmd.extend(["-map", "0:a"])    # Map ALL audio streams
                cmd.extend(["-c:a", "copy"])   # Copy audio without re-encoding
                
                # Handle subtitle mapping - copy ALL subtitle streams
                if self.settings.convert_subtitles:
                    cmd.extend(["-map", "0:s"])   # Map ALL subtitle streams
                else:
                    cmd.extend(["-sn"])  # No subtitles
                    
            else:
                # Re-encode audio - map first audio stream only to avoid conflicts
                cmd.extend(["-map", "0:v:0"])     # Map first video stream
                cmd.extend(["-map", "0:a:0?"])    # Map first audio stream only
                cmd.extend(["-c:a", self.settings.audio_codec])
                
                if self.settings.audio_bitrate:
                    cmd.extend(["-b:a", self.settings.audio_bitrate])
            
                # Handle subtitle mapping - try to preserve all subtitle streams
                if self.settings.convert_subtitles:
                    cmd.extend(["-map", "0:s"])   # Map ALL subtitle streams
                else:
                    cmd.extend(["-sn"])  # No subtitles
            
            # Enhanced subtitle handling with error tolerance
            if self.settings.convert_subtitles:
                # Determine best subtitle codec for output format
                output_format = self.settings.output_format.lower()
                
                if output_format in ["mp4", "m4v"]:
                    # MP4 containers require mov_text for subtitle support
                    subtitle_codec = "mov_text"
                elif output_format in ["mkv", "webm"]:
                    # MKV/WebM can handle ASS/SSA better, try copy first
                    subtitle_codec = "copy"
                elif output_format == "avi":
                    # AVI typically uses SRT
                    subtitle_codec = "srt"
                else:
                    # Default to copy for compatibility, fallback to SRT
                    subtitle_codec = "copy"
                
                # Override with user preference if specified
                if hasattr(self.settings, 'subtitle_format') and self.settings.subtitle_format != "auto":
                    subtitle_codec_map = {
                        "srt": "srt",
                        "ass": "ass",
                        "ssa": "ass",
                        "mov_text": "mov_text",
                        "webvtt": "webvtt",
                        "copy": "copy"
                    }
                    subtitle_codec = subtitle_codec_map.get(self.settings.subtitle_format, subtitle_codec)
                
                # Set subtitle codec for all subtitle streams
                cmd.extend(["-c:s", subtitle_codec])
                
                # Add subtitle conversion options for problematic subtitle types
                if subtitle_codec != "copy":
                    cmd.extend(["-avoid_negative_ts", "make_zero"])  # Fix timestamp issues
                    # Use source video resolution for subtitle canvas (dynamically detected)
                    # Note: video_resolution will be set later when we get media info
                
                logger.info(f"Mapping subtitle streams with codec: {subtitle_codec} for {output_format} container")
            else:
                logger.info("Subtitle conversion disabled - subtitles will be stripped")
            
            # Already added in universal optimizations
            
            # Fast start for web playback
            if self.settings.use_faststart and self.settings.output_format in ["mp4", "m4v"]:
                cmd.extend(["-movflags", "+faststart"])
            
            # Overwrite if needed
            if self.settings.overwrite_existing:
                cmd.append("-y")
            
            # Progress reporting
            cmd.extend(["-progress", "pipe:1"])
            
            # Output file
            cmd.append(str(output_path_obj))
            
            logger.info(f"Starting conversion: {input_file.name} -> {output_path_obj.name}")
            logger.info(f"Command: {' '.join(cmd)}")
            
            # Ensure FFmpeg is detected and available
            ffmpeg_check = self.check_ffmpeg()
            if ffmpeg_check["status"] != "success":
                return {
                    "status": "error",
                    "message": f"FFmpeg not available: {ffmpeg_check.get('message', 'Unknown error')}"
                }
            
            logger.info(f"Using FFmpeg: {self.settings.ffmpeg_path}")
            
            if self.settings.dry_run:
                return {
                    "status": "success",
                    "message": "Dry run - no actual conversion performed",
                    "output_path": str(output_path_obj),
                    "command": " ".join(cmd)
                }
            
            # Execute FFmpeg
            try:
                logger.info("Starting FFmpeg subprocess...")
                logger.info(f"FFmpeg command: {' '.join(cmd)}")
                
                # Ensure we're using the correct FFmpeg path
                if not os.path.exists(self.settings.ffmpeg_path) and self.settings.ffmpeg_path != "ffmpeg":
                    logger.warning(f"FFmpeg path not found: {self.settings.ffmpeg_path}, trying system ffmpeg")
                    cmd[0] = "ffmpeg"
                
                process = subprocess.Popen(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,  # Keep stderr separate for better error handling
                    universal_newlines=True,
                    bufsize=1,
                    cwd=None  # Use current working directory
                )
                logger.info(f"FFmpeg process started with PID: {process.pid}")
                self.current_process = process
            except Exception as e:
                logger.error(f"Failed to start FFmpeg process: {e}")
                return {
                    "status": "error",
                    "message": f"Failed to start FFmpeg: {e}"
                }
            
            # Get duration, FPS, and resolution from file info first
            duration = 0
            video_resolution = None
            source_fps = 23.976  # Default FPS fallback
            total_frames = 0
            logger.info(f"Getting media info for: {input_file}")
            info = self.get_media_info(str(input_file))
            if info.get("status") == "success":
                duration = float(info.get("duration", 0))
                logger.info(f"Input file duration: {duration} seconds")
                
                # Extract video resolution and FPS for accurate progress tracking
                video_tracks = info.get("video_tracks", [])
                if video_tracks and len(video_tracks) > 0:
                    resolution_str = video_tracks[0].get("resolution", "")
                    if resolution_str and "x" in resolution_str:
                        try:
                            width, height = resolution_str.split("x")
                            video_resolution = f"{width}x{height}"
                            logger.info(f"Input video resolution: {video_resolution}")
                        except (ValueError, IndexError):
                            pass
                    
                    # Get source FPS for frame-based progress calculation
                    fps_str = video_tracks[0].get("fps", "")
                    if fps_str:
                        try:
                            source_fps = float(fps_str.split()[0])
                            total_frames = int(duration * source_fps)
                            logger.info(f"Source FPS: {source_fps}, Total frames: {total_frames}")
                        except (ValueError, IndexError):
                            pass
            else:
                logger.warning(f"Could not get media info: {info.get('message', 'Unknown error')}")
                # Don't fail conversion, just proceed without duration info
            
            # Send initial progress update
            if progress_callback:
                progress_callback({
                    "file": str(input_file),
                    "progress": 0,
                    "status": "starting",
                    "fps": 0,
                    "speed": "0x",
                    "eta": "Unknown"
                })
            
            # Parse progress with timeout
            logger.info("Starting progress monitoring...")
            error_output = ""
            last_progress_time = 0
            last_update_sent = 0
            import time
            start_time = time.time()
            
            # Progress tracking state with frame-based fallback
            progress_data = {
                "frame": 0,
                "fps": 0.0,
                "speed": "0x",
                "bitrate": "0kbits/s",
                "total_size": 0,
                "out_time_ms": 0,
                "out_time": "00:00:00.00",
                "progress": "continue",
                "last_out_time_ms": 0,  # Track last seen time for stuck detection
                "stuck_counter": 0  # Count how many times time has been stuck
            }
            
            try:
                while True:
                    if self.cancel_requested:
                        logger.info("Cancellation requested - terminating FFmpeg process")
                        if self.current_process and self.current_process.poll() is None:
                            try:
                                self.current_process.terminate()
                                time.sleep(0.1)
                                if self.current_process.poll() is None:
                                    self.current_process.kill()
                            except Exception:
                                pass
                        return {
                            "status": "cancelled",
                            "message": "Conversion cancelled by user"
                        }
                    
                    # Check if process is still running
                    if process.poll() is not None:
                        logger.info("FFmpeg process completed")
                        break
                    
                    # Read output and parse progress
                    if process.stdout:
                        try:
                            line = process.stdout.readline()
                            if not line:
                                # Only send heartbeat if we haven't received updates in a while (prevent stale data flooding)
                                current_time = time.time()
                                if current_time - last_update_sent > 2.0:  # Heartbeat only every 2 seconds if no data
                                    if progress_callback:
                                        # Send a simple heartbeat to show we're still alive
                                        if progress_data["out_time_ms"] > 0:
                                            time_s = progress_data["out_time_ms"] / 1000000.0
                                            progress_pct = min(100, (time_s / duration) * 100) if duration > 0 else 0
                                            progress_callback({
                                                "file": str(input_file),
                                                "progress": progress_pct,
                                                "status": "encoding",
                                                "fps": progress_data["fps"],
                                                "speed": progress_data["speed"],
                                                "frame": progress_data["frame"]
                                            })
                                        else:
                                            progress_callback({
                                                "file": str(input_file),
                                                "progress": 0,
                                                "status": "starting"
                                            })
                                        last_update_sent = current_time
                                
                                time.sleep(0.01)  # Small sleep to prevent CPU spinning
                                continue
                                
                            line = line.strip()
                            if not line:
                                continue
                        
                            # Parse FFmpeg progress output (key=value format)
                            if "=" in line:
                                try:
                                    key, value = line.split("=", 1)
                                    key = key.strip()
                                    value = value.strip()
                                    
                                    # Update progress data
                                    if key == "frame":
                                        progress_data["frame"] = int(value)
                                    elif key == "fps":
                                        progress_data["fps"] = float(value)
                                    elif key == "speed":
                                        progress_data["speed"] = value
                                    elif key == "bitrate":
                                        progress_data["bitrate"] = value
                                    elif key == "total_size":
                                        progress_data["total_size"] = int(value)
                                    elif key == "out_time_ms":
                                        if value != "N/A":
                                            new_time_ms = int(value)
                                            # Track if time is stuck
                                            if new_time_ms == progress_data["last_out_time_ms"]:
                                                progress_data["stuck_counter"] += 1
                                            else:
                                                progress_data["stuck_counter"] = 0
                                                progress_data["out_time_ms"] = new_time_ms
                                                progress_data["last_out_time_ms"] = new_time_ms
                                    elif key == "out_time":
                                        progress_data["out_time"] = value
                                    elif key == "progress":
                                        progress_data["progress"] = value
                                        
                                        # When we see "progress=continue" or "progress=end", send update
                                        if value in ["continue", "end"]:
                                            current_time = time.time()
                                            
                                            # Calculate progress with fallback to frame-based calculation
                                            progress_pct = 0
                                            time_s = 0
                                            
                                            # Check if time-based progress is working
                                            if progress_data["out_time_ms"] > 0 and duration > 0:
                                                time_s = progress_data["out_time_ms"] / 1000000.0
                                                progress_pct = min(100, (time_s / duration) * 100)
                                                
                                                # If time is stuck but frames are progressing, use frame-based fallback
                                                if progress_data["stuck_counter"] > 5 and total_frames > 0 and progress_data["frame"] > 0:
                                                    frame_progress = min(100, (progress_data["frame"] / total_frames) * 100)
                                                    # Use frame progress if it's higher (indicates time is stuck)
                                                    if frame_progress > progress_pct + 1:
                                                        progress_pct = frame_progress
                                                        logger.debug(f"Using frame-based progress: {progress_pct:.2f}% (time stuck)")
                                            
                                            # Fallback to frame-based if we have no time data but have frame data
                                            elif total_frames > 0 and progress_data["frame"] > 0:
                                                progress_pct = min(100, (progress_data["frame"] / total_frames) * 100)
                                                time_s = progress_data["frame"] / source_fps if source_fps > 0 else 0
                                            
                                            # Send update immediately if we have any meaningful progress
                                            if progress_pct > 0 or progress_data["frame"] > 10:
                                                # Calculate ETA
                                                eta_str = "Calculating..."
                                                if progress_pct > 1:
                                                    elapsed = current_time - start_time
                                                    if elapsed > 1:  # Need at least 1 second of data
                                                        total_time = elapsed * (100 / progress_pct)
                                                        remaining = total_time - elapsed
                                                        if remaining > 3600:
                                                            eta_str = f"{int(remaining // 3600)}h {int((remaining % 3600) // 60)}m"
                                                        elif remaining > 60:
                                                            eta_str = f"{int(remaining // 60)}m {int(remaining % 60)}s"
                                                        else:
                                                            eta_str = f"{int(remaining)}s"
                                                
                                                if progress_callback:
                                                    status = "finalizing" if value == "end" else "encoding"
                                                    progress_callback({
                                                        "file": str(input_file),
                                                        "progress": progress_pct,
                                                        "time": time_s,
                                                        "duration": duration,
                                                        "status": status,
                                                        "fps": progress_data["fps"],
                                                        "speed": progress_data["speed"],
                                                        "frame": progress_data["frame"],
                                                        "bitrate": progress_data["bitrate"],
                                                        "eta": eta_str
                                                    })
                                                    last_update_sent = current_time
                                                    last_progress_time = current_time
                                            
                                            # Log progress periodically
                                            if current_time - last_progress_time > 2.0:
                                                logger.info(f"Progress: frame={progress_data['frame']}, fps={progress_data['fps']:.1f}, speed={progress_data['speed']}")
                                                last_progress_time = current_time
                                
                                except (ValueError, IndexError) as e:
                                    logger.debug(f"Error parsing progress line '{line}': {e}")
                            else:
                                # Non-key=value line, log it
                                if line:
                                    logger.debug(f"FFmpeg output: {line}")
                        
                        except Exception as e:
                            logger.error(f"Error reading FFmpeg output: {e}", exc_info=True)
                            time.sleep(0.05)
                    
                # Also read stderr for errors
                if process.stderr:
                    try:
                        import select
                        if hasattr(select, 'select'):
                            ready, _, _ = select.select([process.stderr], [], [], 0)
                            if ready:
                                error_line = process.stderr.readline()
                                if error_line:
                                    error_output += error_line
                                    logger.debug(f"FFmpeg stderr: {error_line.strip()}")
                    except Exception:
                        # select not available on Windows, skip stderr monitoring
                        pass
                            
            except Exception as e:
                logger.error(f"Error during progress monitoring: {e}")
            
            # Wait for completion and get remaining output
            logger.info("Waiting for FFmpeg to complete...")
            try:
                stdout, stderr = process.communicate(timeout=30)
                if stderr:
                    error_output += stderr
            except subprocess.TimeoutExpired:
                logger.error("FFmpeg process timed out")
                process.kill()
                stdout, stderr = process.communicate()
                error_output += stderr if stderr else ""
            
            returncode = process.returncode
            logger.info(f"FFmpeg completed with return code: {returncode}")
            self.current_process = None
            self.cancel_requested = False
            
            if returncode == 0:
                logger.info(f"Conversion completed: {output_path_obj.name}")
                
                # Send final completion update
                if progress_callback:
                    progress_callback({
                        "file": str(input_file),
                        "progress": 100,
                        "status": "completed",
                        "fps": progress_data.get("fps", 0),
                        "speed": progress_data.get("speed", "N/A"),
                        "frame": progress_data.get("frame", 0),
                        "bitrate": progress_data.get("bitrate", "0kbits/s"),
                        "eta": "Done"
                    })
                
                # Verify file integrity
                logger.info("Verifying output file integrity...")
                integrity_check = self._verify_file_integrity(str(input_file), str(output_path_obj))
                
                if not integrity_check.get("valid", False):
                    error_msg = integrity_check.get("error", "Unknown integrity error")
                    logger.error(f"Integrity check failed: {error_msg}")
                    
                    # Clean up corrupted output file
                    try:
                        output_path_obj.unlink()
                        logger.info("Removed corrupted output file")
                    except Exception as e:
                        logger.warning("Failed to remove corrupted file: %s", e)
                    
                    return {
                        "status": "error",
                        "message": f"File integrity check failed: {error_msg}",
                        "details": integrity_check
                    }
                
                # Log integrity check results
                compression_ratio = integrity_check.get("compression_ratio", 0)
                if compression_ratio > 0:
                    logger.info(f"Integrity check passed - Compression ratio: {compression_ratio:.2f}x")
                
                # Delete original if requested (only after integrity check passes)
                if self.settings.delete_original:
                    try:
                        input_file.unlink()
                        logger.info(f"Deleted original file: {input_file.name}")
                    except Exception as e:
                        logger.warning(f"Failed to delete original file: {e}")
                
                return {
                    "status": "success",
                    "message": "Conversion completed successfully and verified",
                    "output_path": str(output_path_obj),
                    "integrity_check": integrity_check
                }
            else:
                # Use accumulated error output
                if not error_output.strip():
                    error_output = "Unknown error"
                
                logger.error(f"Conversion failed with code {returncode}: {error_output}")
                
                # Check if this was a hardware encoder failure and retry with software
                if encoder_info["type"] == "hardware" and self._is_hardware_encoder_error(error_output):
                    logger.warning("Hardware encoder failed, retrying with software encoding...")
                    return self._retry_with_software_encoder(input_file, output_path_obj, progress_callback)
                
                return {
                    "status": "error",
                    "message": f"FFmpeg error (code {returncode}): {error_output}",
                    "details": error_output
                }
        
        except Exception as e:
            logger.error(f"Error during conversion: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Conversion failed: {str(e)}"
            }

    def _test_encoder_quick(self, encoder_name: str) -> bool:
        """
        Quick test if an encoder actually works by encoding a tiny test video.
        
        Args:
            encoder_name: FFmpeg encoder name (e.g., "h264_nvenc", "h264_amf")
        
        Returns:
            True if encoder works, False otherwise
        """
        try:
            cmd = [
                self.settings.ffmpeg_path,
                "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "color=black:size=320x240:duration=0.1",
                "-c:v", encoder_name,
                "-f", "null", "-"
            ]
            
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=5
            )
            
            success = result.returncode == 0
            if not success:
                logger.debug(f"Encoder {encoder_name} test failed: {result.stderr[:200]}")
            
            return success
            
        except Exception as e:
            logger.debug(f"Error testing encoder {encoder_name}: {e}")
            return False
    
    def _select_best_encoder(self) -> Dict[str, str]:
        """Select the best available encoder with automatic fallback"""
        # Get hardware information
        hardware_info = self.ffmpeg_mgr.detect_hardware()
        available_encoders = self.ffmpeg_mgr.version_info.get("encoders", [])
        
        logger.info(f"Hardware detection: GPU={hardware_info['gpu']}, CPU={hardware_info['cpu']['vendor']}")
        logger.info(f"Available encoders: {available_encoders}")
        logger.info(f"User selected codec: {self.settings.video_codec}")
        
        # Map UI-friendly names to FFmpeg codec names
        codec_map = {
            "Auto (Best Available)": ("auto", "auto"),
            "H.264 NVENC (GPU)": ("h264_nvenc", "hardware"),
            "H.265 NVENC (GPU)": ("hevc_nvenc", "hardware"),
            "H.264 AMF (GPU)": ("h264_amf", "hardware"),
            "H.265 AMF (GPU)": ("hevc_amf", "hardware"),
            "H.264 Intel QSV (CPU)": ("h264_qsv", "hardware"),
            "H.265 Intel QSV (CPU)": ("hevc_qsv", "hardware"),
            "H.264 VideoToolbox (GPU)": ("h264_videotoolbox", "hardware"),
            "H.265 VideoToolbox (GPU)": ("hevc_videotoolbox", "hardware"),
            "Software H.264": ("libx264", "software"),
            "Software H.265": ("libx265", "software"),
            "Copy": ("copy", "copy")
        }
        
        # Get the user's selected codec from the UI
        selected_codec_name = self.settings.video_codec
        
        # Handle "Auto" selection - test encoders and choose best available
        if selected_codec_name == "Auto (Best Available)" or selected_codec_name == "auto":
            logger.info("Auto codec selection - testing encoders to find best option")
            
            # Priority list of encoders to test (best to worst)
            encoders_to_test = [
                ("h264_nvenc", "NVIDIA H.264 NVENC", hardware_info["gpu"]["nvidia"]),
                ("hevc_nvenc", "NVIDIA H.265 NVENC", hardware_info["gpu"]["nvidia"]),
                ("h264_amf", "AMD H.264 AMF", hardware_info["gpu"]["amd"]),
                ("hevc_amf", "AMD H.265 AMF", hardware_info["gpu"]["amd"]),
                ("h264_qsv", "Intel H.264 QSV", hardware_info["cpu"]["supports_qsv"]),
                ("hevc_qsv", "Intel H.265 QSV", hardware_info["cpu"]["supports_qsv"]),
                ("h264_videotoolbox", "Apple H.264 VideoToolbox", hardware_info["gpu"]["apple"]),
                ("hevc_videotoolbox", "Apple H.265 VideoToolbox", hardware_info["gpu"]["apple"])
            ]
            
            # Test encoders in priority order
            for encoder_codec, encoder_name, has_hardware in encoders_to_test:
                # Skip if hardware not detected
                if not has_hardware:
                    continue
                
                # Test if encoder actually works
                logger.info(f"Testing encoder: {encoder_name}")
                if self._test_encoder_quick(encoder_codec):
                    logger.info(f"Auto-selected {encoder_codec} ({encoder_name} - tested and working)")
                    return {
                        "codec": encoder_codec,
                        "type": "hardware",
                        "reason": f"Auto-selected: {encoder_name} (tested and functional)"
                    }
                else:
                    logger.debug(f"Encoder {encoder_codec} failed test, trying next option")
            
            # Fallback to software if no hardware encoders work
            logger.info("Auto-selected libx264 (no functional hardware encoders found)")
            return {
                "codec": "libx264",
                "type": "software",
                "reason": "Auto-selected: Software H.264 (no GPU available)"
            }
        
        # Map the UI name to the actual FFmpeg codec
        if selected_codec_name in codec_map:
            ffmpeg_codec, codec_type = codec_map[selected_codec_name]
            
            # If it's a hardware codec, validate it's actually available
            if codec_type == "hardware":
                # Check if encoder is in the available list
                encoder_available = False
                encoder_check_name = None
                
                if "nvenc" in ffmpeg_codec:
                    encoder_check_name = "NVIDIA H.264" if "h264" in ffmpeg_codec else "NVIDIA H.265"
                    encoder_available = encoder_check_name in available_encoders and hardware_info["gpu"]["nvidia"]
                elif "amf" in ffmpeg_codec:
                    encoder_check_name = "AMD H.264" if "h264" in ffmpeg_codec else "AMD H.265"
                    encoder_available = encoder_check_name in available_encoders and hardware_info["gpu"]["amd"]
                elif "qsv" in ffmpeg_codec:
                    encoder_check_name = "Intel Quick Sync H.264" if "h264" in ffmpeg_codec else "Intel Quick Sync H.265"
                    encoder_available = encoder_check_name in available_encoders and hardware_info["cpu"]["supports_qsv"]
                elif "videotoolbox" in ffmpeg_codec:
                    encoder_check_name = "Apple VideoToolbox H.264" if "h264" in ffmpeg_codec else "Apple VideoToolbox H.265"
                    encoder_available = encoder_check_name in available_encoders and hardware_info["gpu"]["apple"]
                
                if encoder_available:
                    logger.info(f"Using user-selected codec: {ffmpeg_codec} (validated)")
                    return {
                        "codec": ffmpeg_codec,
                        "type": "hardware",
                        "reason": f"User selection: {selected_codec_name} (validated)"
                    }
                else:
                    # Hardware encoder not available, fallback to software
                    logger.warning(f"{selected_codec_name} requested but not available - falling back to software encoding")
                    fallback_codec = "libx265" if "265" in selected_codec_name or "hevc" in ffmpeg_codec else "libx264"
                    return {
                        "codec": fallback_codec,
                        "type": "software",
                        "reason": f"{selected_codec_name} not available, using {fallback_codec} fallback"
                    }
            else:
                # Software or copy - always available
                logger.info(f"Using user-selected codec: {ffmpeg_codec}")
                return {
                    "codec": ffmpeg_codec,
                    "type": codec_type,
                    "reason": f"User selection: {selected_codec_name}"
                }
        
        # Fallback: If codec name not recognized, use old logic for backward compatibility
        logger.warning(f"Unrecognized codec selection: {selected_codec_name}, falling back to legacy detection")
        
        if self.settings.use_nvenc:
            nvenc_codec = getattr(self.settings, 'nvenc_codec', 'h264_nvenc')
            if hardware_info["gpu"]["nvidia"] and "NVIDIA H.264" in available_encoders:
                return {
                    "codec": nvenc_codec,
                    "type": "hardware",
                    "reason": "Legacy flag: NVENC (validated)"
                }
        
        elif self.settings.use_amf:
            if hardware_info["gpu"]["amd"] and "AMD H.264" in available_encoders:
                return {
                    "codec": "h264_amf",
                    "type": "hardware",
                    "reason": "Legacy flag: AMD AMF (validated)"
                }
        
        elif self.settings.use_qsv:
            if hardware_info["cpu"]["supports_qsv"] and "Intel Quick Sync H.264" in available_encoders:
                return {
                    "codec": "h264_qsv",
                    "type": "hardware",
                    "reason": "Legacy flag: Intel Quick Sync (validated)"
                }
        
        elif self.settings.use_videotoolbox:
            if hardware_info["gpu"]["apple"] and "Apple VideoToolbox H.264" in available_encoders:
                return {
                    "codec": "h264_videotoolbox",
                    "type": "hardware",
                    "reason": "Legacy flag: Apple VideoToolbox (validated)"
                }
        
        # Auto-select best available encoder
        recommended = self.ffmpeg_mgr.get_recommended_encoder(hardware_info)
        
        if recommended != "libx264":
            return {
                "codec": recommended,
                "type": "hardware",
                "reason": "Auto-selected based on detected hardware"
            }
        else:
            return {
                "codec": "libx264",
                "type": "software",
                "reason": "No hardware acceleration available, using software encoding"
            }
    
    def _is_hardware_encoder_error(self, error_output: str) -> bool:
        """Check if the error is related to hardware encoder failure"""
        hardware_error_patterns = [
            "cannot load nvcuda.dll",
            "nvenc",
            "amf",
            "qsv",
            "videotoolbox",
            "encoder not found",
            "no device available",
            "hardware acceleration",
            "gpu",
            "cuda",
            "opencl"
        ]
        
        error_lower = error_output.lower()
        return any(pattern in error_lower for pattern in hardware_error_patterns)
    
    def _retry_with_software_encoder(self, input_file: Path, output_path_obj: Path, progress_callback: Optional[Callable] = None) -> Dict:
        """Retry conversion with software encoder"""
        try:
            # Build software encoding command
            cmd = [self.settings.ffmpeg_path, "-hide_banner", "-loglevel", "error", "-i", str(input_file)]
            
            # Force software encoding
            crf_value = getattr(self.settings, 'crf', self.settings.video_crf)
            cmd.extend(["-c:v", "libx264"])
            cmd.extend(["-preset", self.settings.video_preset])
            cmd.extend(["-crf", str(crf_value)])
            
            # Audio codec and streams (same as original)
            if self.settings.audio_codec == "copy":
                if self.settings.output_format.lower() in ["mp4", "m4v"]:
                    cmd.extend(["-map", "0:v:0", "-map", "0:a:0?", "-map", "0:s:0?"])
                else:
                    cmd.extend(["-map", "0:v:0", "-map", "0:a:0?", "-map", "0:s:0?"])
                    cmd.extend(["-max_muxing_queue_size", "1024"])
                cmd.extend(["-c:a", "copy"])
            else:
                cmd.extend(["-map", "0:v:0", "-map", "0:a:0?", "-map", "0:s:0?"])
                cmd.extend(["-c:a", self.settings.audio_codec])
                if self.settings.audio_bitrate:
                    cmd.extend(["-b:a", self.settings.audio_bitrate])
            
            # Subtitle handling
            if self.settings.convert_subtitles:
                subtitle_codec_map = {
                    "auto": "mov_text" if self.settings.output_format in ["mp4", "m4v"] else "srt",
                    "srt": "srt",
                    "ass": "ass",
                    "ssa": "ass",
                    "mov_text": "mov_text",
                    "webvtt": "webvtt"
                }
                subtitle_codec = subtitle_codec_map.get(self.settings.subtitle_format, "mov_text")
                cmd.extend(["-c:s", subtitle_codec])
            else:
                cmd.extend(["-sn"])
            
            # Fast start for web playback
            if self.settings.use_faststart and self.settings.output_format in ["mp4", "m4v"]:
                cmd.extend(["-movflags", "+faststart"])
            
            # Overwrite if needed
            if self.settings.overwrite_existing:
                cmd.append("-y")
            
            # Progress reporting
            cmd.extend(["-progress", "pipe:1"])
            
            # Output file
            cmd.append(str(output_path_obj))
            
            logger.info(f"Retrying with software encoder: {' '.join(cmd)}")
            
            # Execute FFmpeg with software encoding
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
                bufsize=1
            )
            
            self.current_process = process
            
            # Simple progress monitoring for retry (no complex parsing)
            if progress_callback:
                progress_callback({
                    "file": str(input_file),
                    "progress": 50,
                    "status": "retrying with software encoding"
                })
            
            # Wait for completion
            stdout, stderr = process.communicate(timeout=3600)  # 1 hour timeout
            returncode = process.returncode
            
            self.current_process = None
            
            if returncode == 0:
                logger.info(f"Software encoding retry successful: {output_path_obj.name}")
                
                # Delete original if requested
                if self.settings.delete_original:
                    try:
                        input_file.unlink()
                        logger.info(f"Deleted original file: {input_file.name}")
                    except Exception as e:
                        logger.warning(f"Failed to delete original file: {e}")
                
                return {
                    "status": "success",
                    "message": "Conversion completed successfully (software encoding fallback)",
                    "output_path": str(output_path_obj)
                }
            else:
                error_msg = stderr.strip() if stderr else "Unknown error"
                logger.error(f"Software encoding retry also failed: {error_msg}")
                return {
                    "status": "error",
                    "message": f"Both hardware and software encoding failed: {error_msg}",
                    "details": error_msg
                }
                
        except Exception as e:
            logger.error(f"Error during software encoding retry: {e}")
            return {
                "status": "error",
                "message": f"Software encoding retry failed: {str(e)}"
            }

    def cancel_current(self) -> Dict:
        """Bulletproof process cancellation for all platforms"""
        self.cancel_requested = True
        if self.current_process and self.current_process.poll() is None:
            try:
                import os
                import platform
                import signal
                
                pid = self.current_process.pid
                logger.info(f"Terminating FFmpeg process {pid}")
                
                # Cross-platform process termination
                if platform.system() == "Windows":
                    # Windows-specific termination
                    try:
                        # Try graceful termination first
                        self.current_process.terminate()
                        try:
                            self.current_process.wait(timeout=2)
                            logger.info("FFmpeg process terminated gracefully (Windows)")
                        except subprocess.TimeoutExpired:
                            # Force kill with taskkill
                            logger.warning("Graceful termination failed, using taskkill")
                            os.system(f"taskkill /F /PID {pid}")
                            try:
                                self.current_process.wait(timeout=3)
                            except subprocess.TimeoutExpired:
                                logger.error("Even taskkill failed, process may be stuck")
                    except Exception as e:
                        logger.error(f"Windows termination failed: {e}")
                        # Last resort - kill process tree
                        os.system(f"taskkill /F /T /PID {pid}")
                        
                else:
                    # Unix-like systems (Linux, macOS)
                    try:
                        # Send SIGTERM first
                        os.kill(pid, signal.SIGTERM)
                        try:
                            self.current_process.wait(timeout=2)
                            logger.info("FFmpeg process terminated gracefully (Unix)")
                        except subprocess.TimeoutExpired:
                            # Send SIGKILL if SIGTERM didn't work (Unix only)
                            logger.warning("SIGTERM failed, sending SIGKILL")
                            if hasattr(signal, 'SIGKILL'):
                                os.kill(pid, getattr(signal, 'SIGKILL'))
                            else:
                                # Fallback for systems without SIGKILL
                                self.current_process.kill()
                            try:
                                self.current_process.wait(timeout=3)
                                logger.info("FFmpeg process force killed (Unix)")
                            except subprocess.TimeoutExpired:
                                logger.error("Even SIGKILL failed, process may be zombie")
                    except ProcessLookupError:
                        logger.info("Process already terminated")
                    except Exception as e:
                        logger.error(f"Unix termination failed: {e}")
                
                # Clean up
                self.current_process = None
                self.cancel_requested = False
                return {"status": "success", "message": "Process terminated successfully"}
                
            except Exception as e:
                logger.error(f"Failed to terminate process: {e}")
                # Emergency cleanup
                self.current_process = None
                self.cancel_requested = False
                return {"status": "error", "message": f"Failed to terminate process: {e}"}
        
        return {"status": "success", "message": "No active process to cancel"}
    
    def _verify_file_integrity(self, input_path: str, output_path: str) -> Dict[str, Any]:
        """Verify the integrity of the encoded file"""
        try:
            from pathlib import Path
            
            input_file = Path(input_path)
            output_file = Path(output_path)
            
            # Basic file existence and size checks
            if not output_file.exists():
                return {"valid": False, "error": "Output file does not exist"}
            
            if output_file.stat().st_size == 0:
                return {"valid": False, "error": "Output file is empty"}
            
            # Get input file info for comparison
            input_info = self.get_media_info(input_path)
            output_info = self.get_media_info(output_path)
            
            if input_info.get("status") != "success" or output_info.get("status") != "success":
                return {"valid": False, "error": "Could not analyze file properties"}
            
            # Compare durations (allow 1% difference for encoding variations)
            input_duration = float(input_info.get("duration", 0))
            output_duration = float(output_info.get("duration", 0))
            
            if input_duration > 0:
                duration_diff = abs(input_duration - output_duration) / input_duration
                if duration_diff > 0.01:  # More than 1% difference
                    return {
                        "valid": False, 
                        "error": f"Duration mismatch: input {input_duration:.1f}s vs output {output_duration:.1f}s"
                    }
            
            # Check if output has video tracks
            output_video_tracks = output_info.get("video_tracks", [])
            if len(output_video_tracks) == 0:
                return {"valid": False, "error": "Output file has no video tracks"}
            
            # Verify audio tracks if copying audio
            if self.settings.audio_codec == "copy":
                input_audio_tracks = input_info.get("audio_tracks", [])
                output_audio_tracks = output_info.get("audio_tracks", [])
                
                if len(input_audio_tracks) > 0 and len(output_audio_tracks) == 0:
                    return {"valid": False, "error": "Audio tracks missing in output"}
            
            # Verify subtitle tracks if copying subtitles
            if self.settings.convert_subtitles:
                input_subtitle_tracks = input_info.get("subtitle_tracks", [])
                output_subtitle_tracks = output_info.get("subtitle_tracks", [])
                
                # Only warn about missing subtitles, don't fail (some formats don't support them)
                if len(input_subtitle_tracks) > 0 and len(output_subtitle_tracks) == 0:
                    logger.warning("Subtitle tracks missing in output (may not be supported by format)")
            
            # File size sanity check (output shouldn't be more than 10x larger or smaller)
            input_size = input_file.stat().st_size
            output_size = output_file.stat().st_size
            
            if output_size > input_size * 10:
                return {"valid": False, "error": "Output file suspiciously large"}
            
            if output_size < input_size / 100:  # Less than 1% of original size
                return {"valid": False, "error": "Output file suspiciously small"}
            
            return {
                "valid": True, 
                "input_duration": input_duration,
                "output_duration": output_duration,
                "input_size": input_size,
                "output_size": output_size,
                "compression_ratio": input_size / output_size if output_size > 0 else 0
            }
            
        except Exception as e:
            logger.error(f"Error verifying file integrity: {e}")
            return {"valid": False, "error": f"Verification failed: {str(e)}"}
    
    def convert_files(
        self,
        file_paths: List[str],
        progress_callback: Optional[Callable] = None
    ) -> Dict:
        """
        Convert multiple video files
        
        Args:
            file_paths: List of input file paths
            progress_callback: Callback function for progress updates
            
        Returns:
            Dict with status, successes, and failures
        """
        results = {
            "status": "success",
            "total": len(file_paths),
            "success": [],
            "failed": []
        }
        
        for i, file_path in enumerate(file_paths):
            logger.info(f"Processing file {i+1}/{len(file_paths)}: {file_path}")
            
            if progress_callback:
                progress_callback({
                    "status": "processing",
                    "file": file_path,
                    "current": i + 1,
                    "total": len(file_paths)
                })
            
            result = self.convert_file(file_path, progress_callback=progress_callback)
            
            if result["status"] == "success":
                results["success"].append({
                    "input": file_path,
                    "output": result.get("output_path")
                })
            else:
                results["failed"].append({
                    "input": file_path,
                    "error": result.get("message")
                })
        
        if len(results["failed"]) == len(file_paths):
            results["status"] = "error"
        elif len(results["failed"]) > 0:
            results["status"] = "partial"
        
        if progress_callback:
            progress_callback({
                "status": "complete",
                "results": results
            })
        
        return results


def main():
    """Main entry point for testing"""
    core = FFmpegCore()
    
    # Test FFmpeg detection
    print("Checking FFmpeg...")
    result = core.check_ffmpeg()
    print(json.dumps(result, indent=2))
    
    # Test Whisper detection
    print("\nChecking Whisper...")
    result = core.check_whisper()
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()

