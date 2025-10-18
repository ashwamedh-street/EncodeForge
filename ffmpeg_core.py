#!/usr/bin/env python3
"""
FFmpeg Core Module - Shared functionality for CLI, WebUI, and Java GUI
Handles all video conversion, subtitle management, and media renaming operations
"""

import argparse
import json
import logging
import os
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Callable
from dataclasses import dataclass, field

# Import helper modules
from ffmpeg_manager import FFmpegManager
from whisper_manager import WhisperManager
from media_renamer import MediaRenamer
from opensubtitles_manager import OpenSubtitlesManager
from subtitle_providers import SubtitleProviders
from profile_manager import ProfileManager

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
    use_amf: bool = False  # AMD
    use_qsv: bool = False  # Intel Quick Sync
    use_videotoolbox: bool = False  # Apple
    
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
    video_codec_fallback: str = "libx264"
    video_preset: str = "medium"
    video_crf: int = 23
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
        
    def check_ffmpeg(self) -> Dict:
        """Check FFmpeg availability"""
        success, info = self.ffmpeg_mgr.detect_ffmpeg()
        
        if success:
            self.settings.ffmpeg_path = info["ffmpeg_path"]
            self.settings.ffprobe_path = info["ffprobe_path"]
            
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
    
    def preview_rename(self, file_paths: List[str]) -> Dict:
        """
        Preview how files would be renamed
        Returns old and new names for comparison
        """
        previews = []
        
        for file_path in file_paths:
            path = Path(file_path)
            
            # Detect media type
            media_type = self.renamer.detect_media_type(path.name)
            
            info = None
            new_name = None
            
            if media_type == "tv":
                parsed = self.renamer.parse_tv_filename(path.name)
                if parsed and self.settings.tmdb_api_key:
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
            
            elif media_type == "movie":
                parsed = self.renamer.parse_movie_filename(path.name)
                if parsed and self.settings.tmdb_api_key:
                    info = self.renamer.search_movie(
                        parsed["title"],
                        parsed.get("year")
                    )
                    if info:
                        new_name = self.renamer.format_filename(
                            info,
                            self.settings.renaming_pattern_movie
                        ) + path.suffix
            
            previews.append({
                "original_path": file_path,
                "original_name": path.name,
                "new_name": new_name or path.name,
                "media_type": media_type,
                "metadata": info,
                "can_rename": new_name is not None
            })
        
        return {
            "status": "success",
            "previews": previews
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
    
    def get_file_info(self, file_path: str) -> Dict:
        """Get detailed information about a media file"""
        try:
            result = subprocess.run(
                [self.settings.ffprobe_path, "-v", "quiet", "-print_format", "json",
                 "-show_format", "-show_streams", file_path],
                capture_output=True,
                text=True,
                timeout=30
            )
            
            if result.returncode != 0:
                return {
                    "status": "error",
                    "message": "Failed to get file info"
                }
            
            data = json.loads(result.stdout)
            
            # Parse streams
            video_streams = []
            audio_streams = []
            subtitle_streams = []
            
            for stream in data.get("streams", []):
                stream_type = stream.get("codec_type")
                
                if stream_type == "video":
                    video_streams.append({
                        "index": stream.get("index"),
                        "codec": stream.get("codec_name"),
                        "resolution": f"{stream.get('width')}x{stream.get('height')}",
                        "fps": eval(stream.get("r_frame_rate", "0/1")),
                    })
                elif stream_type == "audio":
                    audio_streams.append({
                        "index": stream.get("index"),
                        "codec": stream.get("codec_name"),
                        "language": stream.get("tags", {}).get("language", "und"),
                        "title": stream.get("tags", {}).get("title", ""),
                    })
                elif stream_type == "subtitle":
                    subtitle_streams.append({
                        "index": stream.get("index"),
                        "codec": stream.get("codec_name"),
                        "language": stream.get("tags", {}).get("language", "und"),
                        "title": stream.get("tags", {}).get("title", ""),
                    })
            
            format_info = data.get("format", {})
            
            return {
                "status": "success",
                "file_path": file_path,
                "format": format_info.get("format_name"),
                "duration": float(format_info.get("duration", 0)),
                "size": int(format_info.get("size", 0)),
                "bitrate": int(format_info.get("bit_rate", 0)),
                "video_streams": video_streams,
                "audio_streams": audio_streams,
                "subtitle_streams": subtitle_streams
            }
        
        except Exception as e:
            logger.error(f"Error getting file info: {e}")
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
            
            if not input_file.exists():
                return {"status": "error", "message": f"Input file not found: {input_path}"}
            
            # Generate output path if not provided
            if output_path is None:
                output_dir = input_file.parent
                if self.settings.output_suffix:
                    output_path = output_dir / f"{input_file.stem}{self.settings.output_suffix}.{self.settings.output_format}"
                else:
                    output_path = output_dir / f"{input_file.stem}.{self.settings.output_format}"
            else:
                output_path = Path(output_path)
            
            # Check if output exists
            if output_path.exists() and not self.settings.overwrite_existing:
                return {"status": "error", "message": f"Output file already exists: {output_path}"}
            
            # Build FFmpeg command
            cmd = [self.ffmpeg_mgr.ffmpeg_path, "-i", str(input_file)]
            
            # Hardware acceleration
            if self.settings.use_nvenc:
                cmd.extend(["-c:v", "hevc_nvenc"])
                cmd.extend(["-preset", self.settings.nvenc_preset])
                cmd.extend(["-cq", str(self.settings.nvenc_cq)])
            elif self.settings.use_amf:
                cmd.extend(["-c:v", "hevc_amf"])
            elif self.settings.use_qsv:
                cmd.extend(["-c:v", "hevc_qsv"])
            elif self.settings.use_videotoolbox:
                cmd.extend(["-c:v", "hevc_videotoolbox"])
            else:
                # Software encoding fallback
                cmd.extend(["-c:v", self.settings.video_codec_fallback])
                cmd.extend(["-preset", self.settings.video_preset])
                cmd.extend(["-crf", str(self.settings.video_crf)])
            
            # Audio codec
            if self.settings.audio_codec == "copy":
                cmd.extend(["-c:a", "copy"])
            else:
                cmd.extend(["-c:a", self.settings.audio_codec])
                if self.settings.audio_bitrate:
                    cmd.extend(["-b:a", self.settings.audio_bitrate])
            
            # Subtitle handling
            if self.settings.convert_subtitles:
                cmd.extend(["-c:s", self.settings.subtitle_format])
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
            cmd.append(str(output_path))
            
            logger.info(f"Starting conversion: {input_file.name} -> {output_path.name}")
            logger.debug(f"Command: {' '.join(cmd)}")
            
            if self.settings.dry_run:
                return {
                    "status": "success",
                    "message": "Dry run - no actual conversion performed",
                    "output_path": str(output_path),
                    "command": " ".join(cmd)
                }
            
            # Execute FFmpeg
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
                bufsize=1
            )
            
            # Parse progress
            duration = 0
            for line in process.stdout:
                if line.startswith("out_time_ms="):
                    try:
                        time_ms = int(line.split("=")[1].strip())
                        time_s = time_ms / 1000000.0
                        
                        if duration == 0:
                            # Get duration from file info
                            info = self.get_file_info(str(input_file))
                            if info.get("status") == "success":
                                duration = info.get("duration", 0)
                        
                        if duration > 0:
                            progress = min(100, (time_s / duration) * 100)
                            
                            if progress_callback:
                                progress_callback({
                                    "file": str(input_file),
                                    "progress": progress,
                                    "time": time_s,
                                    "duration": duration
                                })
                    except (ValueError, IndexError):
                        pass
            
            # Wait for completion
            returncode = process.wait()
            
            if returncode == 0:
                logger.info(f"Conversion completed: {output_path.name}")
                
                # Delete original if requested
                if self.settings.delete_original:
                    try:
                        input_file.unlink()
                        logger.info(f"Deleted original file: {input_file.name}")
                    except Exception as e:
                        logger.warning(f"Failed to delete original file: {e}")
                
                return {
                    "status": "success",
                    "message": "Conversion completed successfully",
                    "output_path": str(output_path)
                }
            else:
                error_output = process.stderr.read()
                logger.error(f"Conversion failed: {error_output}")
                return {
                    "status": "error",
                    "message": f"FFmpeg error (code {returncode})",
                    "details": error_output
                }
        
        except Exception as e:
            logger.error(f"Error during conversion: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e)
            }
    
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

