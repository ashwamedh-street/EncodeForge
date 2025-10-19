
#!/usr/bin/env python3
"""
FFmpeg API Bridge - JSON-based API for Java GUI
Provides stdin/stdout JSON communication for Java application
"""

import json
import logging
import os
import sys
from typing import Dict

# Force unbuffered output for real-time streaming
try:
    sys.stdout.reconfigure(line_buffering=True)  # type: ignore
except AttributeError:
    pass
try:
    sys.stderr.reconfigure(line_buffering=True)  # type: ignore
except AttributeError:
    pass

# Also set environment for maximum responsiveness
os.environ['PYTHONUNBUFFERED'] = '1'

# Setup logging to file (not stdout, as that's used for JSON communication)
log_file = os.path.join(os.path.dirname(__file__), 'ffmpeg-api.log')
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - %(message)s',
    filename=log_file
)
logger = logging.getLogger(__name__)

try:
    from ffmpeg_core import ConversionSettings, FFmpegCore  # type: ignore
    logger.info("Successfully imported ffmpeg_core")
except Exception as e:
    logger.error(f"Failed to import ffmpeg_core: {e}", exc_info=True)
    # Create minimal fallback classes with required attributes
    class ConversionSettings:
        def __init__(self):
            # FFmpeg paths
            self.ffmpeg_path = "ffmpeg"
            self.ffprobe_path = "ffprobe"
            
            # Hardware acceleration
            self.use_nvenc = True
            self.use_amf = False
            self.use_qsv = False
            self.use_videotoolbox = False
            self.nvenc_preset = "p4"
            self.nvenc_cq = 23
            self.nvenc_codec = "h264_nvenc"  # Default to H.264 NVENC
            
            # Video settings
            self.video_codec_fallback = "libx264"
            self.video_preset = "medium"
            self.video_crf = 23
            
            # Subtitle settings
            self.convert_subtitles = True
            self.subtitle_format = "srt"
            self.enable_subtitle_generation = False
            self.enable_subtitle_download = False
            self.subtitle_languages = ["eng"]
            self.whisper_model = "base"
            
            # API keys
            self.opensubtitles_api_key = ""
            self.opensubtitles_username = ""
            self.opensubtitles_password = ""
            self.tmdb_api_key = ""
            self.tvdb_api_key = ""
            
            # Renaming
            self.enable_renaming = False
            self.renaming_pattern_tv = "{title} - S{season}E{episode} - {episodeTitle}"
            self.renaming_pattern_movie = "{title} ({year})"
            
            # Audio settings
            self.audio_codec = "copy"
            self.audio_bitrate = None
            
            # General settings
            self.output_format = "mp4"
            self.delete_original = False
            self.overwrite_existing = False
            self.use_faststart = True
            self.output_suffix = ""
    
    class ProfileManager:
        def list_profiles(self):
            return []
        
        def load_profile(self, name):
            return None
        
        def save_profile(self, name, settings):
            return False
        
        def delete_profile(self, name):
            return False
    
    class FFmpegCore:
        def __init__(self, settings=None):
            self.settings = settings or ConversionSettings()
            self.profile_mgr = ProfileManager()
            # Add missing attributes for fallback
            self.ffmpeg_mgr = None
        
        def check_ffmpeg(self):
            return {"status": "error", "message": "FFmpeg core not available"}
        
        def check_whisper(self):
            return {"status": "error", "message": "Whisper not available"}
        
        def download_ffmpeg(self):
            return {"status": "error", "message": "Download not available"}
        
        def install_whisper(self):
            return {"status": "error", "message": "Install not available"}
        
        def download_whisper_model(self, model):
            return {"status": "error", "message": "Model download not available"}
        
        def get_media_info(self, file_path):
            return {"status": "error", "message": "Media info not available"}
        
        def preview_rename(self, file_paths, settings_dict=None):
            return {"status": "error", "message": "Rename preview not available"}
        
        def generate_subtitles(self, video_path, language=None):
            return {"status": "error", "message": "Subtitle generation not available"}
        
        def download_subtitles(self, video_path, languages):
            return {"status": "error", "message": "Subtitle download not available"}
        
        def search_subtitles(self, video_path, languages):
            return {"status": "error", "message": "Subtitle search not available"}
        
        def rename_files(self, file_paths, dry_run=False):
            return {"status": "error", "message": "File renaming not available"}
        
        def scan_directory(self, directory, recursive=True):
            return {"status": "error", "message": "Directory scanning not available"}
        
        def get_file_info(self, file_path):
            return {"status": "error", "message": "File info not available"}
        
        def convert_files(self, file_paths, progress_callback=None):
            return {"status": "error", "message": "Conversion not available", "total": 0, "success": [], "failed": []}

        def cancel_current(self):
            return {"status": "error", "message": "Cancel not available"}
        
        def convert_file(self, file_path, output_path=None, progress_callback=None):
            return {"status": "error", "message": "Conversion not available"}


class FFmpegAPI:
    """
    JSON API bridge for Java GUI
    Communicates via stdin/stdout using JSON messages
    """
    
    def __init__(self):
        self.core = None
        self.settings = ConversionSettings()
    
    def _send_response(self, response: Dict):
        """Send JSON response to stdout"""
        json.dump(response, sys.stdout)
        sys.stdout.write('\n')
        sys.stdout.flush()
    
    def _send_error(self, message: str, error_type: str = "error"):
        """Send error response"""
        self._send_response({
            "status": "error",
            "error_type": error_type,
            "message": message
        })
    
    def handle_request(self, request: Dict) -> Dict:
        """Handle incoming request and return response"""
        action = request.get("action")
        
        if not action:
            return {"status": "error", "message": "No action specified"}
        
        try:
            # Initialize core if needed
            if self.core is None:
                self.core = FFmpegCore(self.settings)
            
            # Route to appropriate handler
            if action == "check_ffmpeg":
                if self.core is None:
                    self.core = FFmpegCore(self.settings)
                return self.core.check_ffmpeg()
            
            elif action == "download_ffmpeg":
                return self.core.download_ffmpeg()
            
            elif action == "get_capabilities":
                return self.handle_get_capabilities()
            
            elif action == "check_whisper":
                return self.core.check_whisper()
            
            elif action == "check_opensubtitles":
                return self.handle_check_opensubtitles()
            
            elif action == "check_tmdb":
                return self.handle_check_tmdb()
            
            elif action == "check_tvdb":
                return self.handle_check_tvdb()
            
            elif action == "get_available_encoders":
                return self.handle_get_available_encoders()
            
            elif action == "install_whisper":
                return self.core.install_whisper()
            
            elif action == "download_whisper_model":
                model = request.get("model", "base")
                return self.core.download_whisper_model(model)
            
            elif action == "get_media_info":
                file_path = request.get("file_path")
                if not file_path:
                    return {"status": "error", "message": "file_path required"}
                return self.core.get_media_info(file_path)
            
            elif action == "generate_subtitles":
                video_path = request.get("video_path")
                language = request.get("language")
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.generate_subtitles(video_path, language)
            
            elif action == "search_subtitles":
                video_path = request.get("video_path")
                languages = request.get("languages", self.settings.subtitle_languages)
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.search_subtitles(video_path, languages)
            
            elif action == "download_subtitles":
                video_path = request.get("video_path")
                languages = request.get("languages", self.settings.subtitle_languages)
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.download_subtitles(video_path, languages)
            
            elif action == "preview_rename":
                file_paths = request.get("file_paths", [])
                settings_dict = request.get("settings", {})
                if not file_paths:
                    return {"status": "error", "message": "file_paths required"}
                return self.core.preview_rename(file_paths, settings_dict)
            
            elif action == "preview_rename_old_duplicate":
                file_paths = request.get("file_paths", [])
                
                if not file_paths:
                    return {"status": "error", "message": "file_paths required"}
                
                return self.core.preview_rename(file_paths)
            
            elif action == "rename_files":
                file_paths = request.get("file_paths", [])
                dry_run = request.get("dry_run", False)
                
                if not file_paths:
                    return {"status": "error", "message": "file_paths required"}
                
                return self.core.rename_files(file_paths, dry_run)
            
            elif action == "scan_directory":
                directory = request.get("directory")
                recursive = request.get("recursive", True)
                
                if not directory:
                    return {"status": "error", "message": "directory required"}
                
                return self.core.scan_directory(directory, recursive)
            
            elif action == "get_file_info":
                file_path = request.get("file_path")
                
                if not file_path:
                    return {"status": "error", "message": "file_path required"}
                
                return self.core.get_file_info(file_path)
            
            elif action == "update_settings":
                settings_dict = request.get("settings", {})
                self.update_settings(settings_dict)
                
                return {"status": "success", "message": "Settings updated"}
            
            elif action == "get_settings":
                return {
                    "status": "success",
                    "settings": self.settings_to_dict()
                }
            
            elif action == "convert_files":
                return self.handle_convert_files(request)
            
            elif action == "stop_conversion":
                # Force cancel current conversion
                try:
                    result = self.core.cancel_current()
                    logger.info(f"Stop conversion result: {result}")
                    return result
                except Exception as e:
                    logger.error(f"Error stopping conversion: {e}")
                    return {"status": "error", "message": str(e)}
            
            elif action == "pause_conversion":
                # Pause current conversion (for now, same as stop - FFmpeg doesn't support pause)
                try:
                    result = self.core.cancel_current()
                    logger.info(f"Pause conversion result: {result}")
                    return result
                except Exception as e:
                    logger.error(f"Error pausing conversion: {e}")
                    return {"status": "error", "message": str(e)}
            
            elif action == "list_profiles":
                profiles = self.core.profile_mgr.list_profiles()
                return {"status": "success", "profiles": profiles}
            
            elif action == "load_profile":
                profile_name = request.get("profile_name")
                if not profile_name:
                    return {"status": "error", "message": "profile_name required"}
                
                settings = self.core.profile_mgr.load_profile(profile_name)
                if settings:
                    self.settings = settings
                    self.core = FFmpegCore(self.settings)
                    return {"status": "success", "message": f"Loaded profile: {profile_name}"}
                else:
                    return {"status": "error", "message": "Profile not found"}
            
            elif action == "save_profile":
                profile_name = request.get("profile_name")
                if not profile_name:
                    return {"status": "error", "message": "profile_name required"}
                
                success = self.core.profile_mgr.save_profile(profile_name, self.settings)
                if success:
                    return {"status": "success", "message": f"Saved profile: {profile_name}"}
                else:
                    return {"status": "error", "message": "Failed to save profile"}
            
            elif action == "delete_profile":
                profile_name = request.get("profile_name")
                if not profile_name:
                    return {"status": "error", "message": "profile_name required"}
                
                success = self.core.profile_mgr.delete_profile(profile_name)
                if success:
                    return {"status": "success", "message": f"Deleted profile: {profile_name}"}
                else:
                    return {"status": "error", "message": "Cannot delete built-in profile or profile not found"}
            
            elif action == "shutdown":
                return self.handle_shutdown()
            
            else:
                return {"status": "error", "message": f"Unknown action: {action}"}
        
        except Exception as e:
            logger.error(f"Error handling request: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e)
            }
    
    def handle_get_capabilities(self) -> Dict:
        """Get system capabilities"""
        # Ensure core is initialized
        if self.core is None:
            self.core = FFmpegCore(self.settings)
        
        ffmpeg_status = self.core.check_ffmpeg()
        whisper_status = self.core.check_whisper()
        
        return {
            "status": "success",
            "ffmpeg": ffmpeg_status,
            "whisper": whisper_status
        }
    
    def handle_check_opensubtitles(self) -> Dict:
        """Check OpenSubtitles configuration"""
        try:
            has_api_key = bool(self.settings.opensubtitles_api_key and self.settings.opensubtitles_api_key.strip())
            has_credentials = (self.settings.opensubtitles_username and 
                             self.settings.opensubtitles_password)
            
            # OpenSubtitles API v1 only requires API key for basic usage
            # Username/password are optional for extended features
            configured = has_api_key
            
            return {
                "status": "success",
                "logged_in": has_credentials,
                "has_api_key": has_api_key,
                "configured": configured,
                "message": "API key only required for basic usage" if has_api_key and not has_credentials else ""
            }
        except Exception as e:
            logger.error(f"Error checking OpenSubtitles: {e}")
            return {
                "status": "error",
                "message": str(e),
                "logged_in": False,
                "configured": False
            }
    
    def handle_check_tmdb(self) -> Dict:
        """Check TMDB configuration"""
        try:
            has_api_key = bool(self.settings.tmdb_api_key and self.settings.tmdb_api_key.strip())
            
            return {
                "status": "success",
                "tmdb_available": has_api_key,
                "configured": has_api_key
            }
        except Exception as e:
            logger.error(f"Error checking TMDB: {e}")
            return {
                "status": "error",
                "message": str(e),
                "tmdb_available": False,
                "configured": False
            }
    
    def handle_check_tvdb(self) -> Dict:
        """Check TVDB configuration"""
        try:
            has_api_key = bool(self.settings.tvdb_api_key and self.settings.tvdb_api_key.strip())
            
            return {
                "status": "success",
                "tvdb_available": has_api_key,
                "configured": has_api_key
            }
        except Exception as e:
            logger.error(f"Error checking TVDB: {e}")
            return {
                "status": "error",
                "message": str(e),
                "tvdb_available": False,
                "configured": False
            }
    
    def handle_get_available_encoders(self) -> Dict:
        """Get available hardware encoders for the current system"""
        try:
            # Ensure core is initialized
            if self.core is None:
                self.core = FFmpegCore(self.settings)
            
            # Get hardware information
            if hasattr(self.core, 'ffmpeg_mgr') and self.core.ffmpeg_mgr:
                hardware_info = self.core.ffmpeg_mgr.detect_hardware()
                available_encoders = self.core.ffmpeg_mgr.version_info.get("encoders", [])
            else:
                # Fallback for when ffmpeg_mgr is not available
                hardware_info = {"gpu": {"nvidia": False, "amd": False, "apple": False}, "cpu": {"supports_qsv": False}}
                available_encoders = []
            
            # Build encoder availability map
            encoder_support = {
                "nvidia_h264": False,
                "nvidia_h265": False,
                "amd_h264": False,
                "amd_h265": False,
                "intel_h264": False,
                "intel_h265": False,
                "apple_h264": False,
                "apple_h265": False,
                "software": True  # Always available
            }
            
            # Check NVIDIA encoders
            if hardware_info["gpu"]["nvidia"] and "NVIDIA H.264" in available_encoders:
                encoder_support["nvidia_h264"] = True
            if hardware_info["gpu"]["nvidia"] and "NVIDIA H.265" in available_encoders:
                encoder_support["nvidia_h265"] = True
            
            # Check AMD encoders
            if hardware_info["gpu"]["amd"] and "AMD H.264" in available_encoders:
                encoder_support["amd_h264"] = True
            if hardware_info["gpu"]["amd"] and "AMD H.265" in available_encoders:
                encoder_support["amd_h265"] = True
            
            # Check Intel encoders
            if hardware_info["cpu"]["supports_qsv"] and "Intel Quick Sync H.264" in available_encoders:
                encoder_support["intel_h264"] = True
            if hardware_info["cpu"]["supports_qsv"] and "Intel Quick Sync H.265" in available_encoders:
                encoder_support["intel_h265"] = True
            
            # Check Apple encoders
            if hardware_info["gpu"]["apple"] and "Apple VideoToolbox H.264" in available_encoders:
                encoder_support["apple_h264"] = True
            if hardware_info["gpu"]["apple"] and "Apple VideoToolbox H.265" in available_encoders:
                encoder_support["apple_h265"] = True
            
            # Get recommended encoder
            if hasattr(self.core, 'ffmpeg_mgr') and self.core.ffmpeg_mgr:
                recommended_encoder = self.core.ffmpeg_mgr.get_recommended_encoder(hardware_info)
            else:
                recommended_encoder = "libx264"
            
            return {
                "status": "success",
                "encoder_support": encoder_support,
                "hardware_info": hardware_info,
                "available_encoders": available_encoders,
                "recommended_encoder": recommended_encoder
            }
            
        except Exception as e:
            logger.error(f"Error getting available encoders: {e}")
            return {
                "status": "error",
                "message": str(e),
                "encoder_support": {"software": True}
            }
    
    def handle_shutdown(self) -> Dict:
        """Handle application shutdown - terminate any running processes"""
        try:
            logger.info("Handling application shutdown")
            
            # Cancel any running conversions
            if self.core:
                result = self.core.cancel_current()
                logger.info(f"Shutdown cancellation result: {result}")
            
            return {"status": "success", "message": "Shutdown handled"}
            
        except Exception as e:
            logger.error(f"Error during shutdown: {e}")
            return {"status": "error", "message": str(e)}
    
    def handle_convert_files(self, request: Dict) -> Dict:
        """Handle file conversion request with streaming progress updates"""
        file_paths = request.get("file_paths", [])
        settings_dict = request.get("settings", {})
        
        if not file_paths:
            return {"status": "error", "message": "No files specified"}
        
        # Update settings
        self.update_settings(settings_dict)
        
        # Reinitialize core with updated settings
        self.core = FFmpegCore(self.settings)
        
        logger.info(f"Starting conversion of {len(file_paths)} files with settings: {settings_dict}")
        
        # Define progress callback that sends updates immediately
        def progress_callback(update: Dict):
            logger.info(f"Progress update: {update}")
            
            # Forward all progress fields to Java
            progress_update = {
                "type": "progress",
                "file": update.get("file", ""),
                "progress": update.get("progress", 0),
                "status": update.get("status", "processing")
            }
            
            # Add all optional fields if present (for full real-time metrics)
            optional_fields = ["time", "duration", "current", "total", "fps", "speed", "frame", "bitrate", "eta"]
            for field in optional_fields:
                if field in update:
                    progress_update[field] = update[field]
            
            logger.debug(f"Sending progress update with {len(progress_update)} fields")
            self._send_response(progress_update)
        
        # Start conversion
        try:
            result = self.core.convert_files(file_paths, progress_callback)
            
            # Send final result
            return {
                "status": result["status"],
                "message": f"Converted {len(result['success'])}/{result['total']} files",
                "success_count": len(result["success"]),
                "failed_count": len(result["failed"]),
                "details": result
            }
        except Exception as e:
            logger.error(f"Conversion error: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e)
            }
    
    def update_settings(self, settings_dict: Dict):
        """Update conversion settings from dictionary"""
        # FFmpeg paths
        if "ffmpeg_path" in settings_dict:
            self.settings.ffmpeg_path = settings_dict["ffmpeg_path"]
        if "ffprobe_path" in settings_dict:
            self.settings.ffprobe_path = settings_dict["ffprobe_path"]
        
        # Hardware acceleration
        if "use_nvenc" in settings_dict:
            self.settings.use_nvenc = settings_dict["use_nvenc"]
        if "use_amf" in settings_dict:
            self.settings.use_amf = settings_dict["use_amf"]
        if "use_qsv" in settings_dict:
            self.settings.use_qsv = settings_dict["use_qsv"]
        if "nvenc_preset" in settings_dict:
            self.settings.nvenc_preset = settings_dict["nvenc_preset"]
        if "nvenc_cq" in settings_dict:
            self.settings.nvenc_cq = settings_dict["nvenc_cq"]
        if "hardware_decoding" in settings_dict:
            # Map hardware_decoding to use_nvenc for now
            self.settings.use_nvenc = settings_dict["hardware_decoding"]
        
        # Video codec mapping from Java names to Python
        if "video_codec" in settings_dict:
            video_codec = settings_dict["video_codec"]
            # Reset all hardware acceleration flags first
            self.settings.use_nvenc = False
            self.settings.use_amf = False
            self.settings.use_qsv = False
            self.settings.use_videotoolbox = False
            
            if "H.264 NVENC" in video_codec or "h264_nvenc" in video_codec.lower():
                self.settings.use_nvenc = True
                self.settings.nvenc_codec = "h264_nvenc"  # Explicitly set H.264
                self.settings.video_codec_fallback = "libx264"
            elif "H.265 NVENC" in video_codec or "hevc_nvenc" in video_codec.lower():
                self.settings.use_nvenc = True
                self.settings.nvenc_codec = "hevc_nvenc"  # Explicitly set H.265
                self.settings.video_codec_fallback = "libx265"
            elif "AMF" in video_codec or "amf" in video_codec.lower():
                self.settings.use_amf = True
                self.settings.video_codec_fallback = "libx264"
            elif "QSV" in video_codec or "qsv" in video_codec.lower():
                self.settings.use_qsv = True
                self.settings.video_codec_fallback = "libx264"
            else:
                # Software encoding fallback
                self.settings.video_codec_fallback = "libx264"
        
        # Video quality settings
        if "crf" in settings_dict:
            self.settings.video_crf = settings_dict["crf"]
        if "quality_preset" in settings_dict:
            self.settings.video_preset = settings_dict["quality_preset"]
        
        # Subtitle options
        if "convert_subtitles" in settings_dict:
            self.settings.convert_subtitles = settings_dict["convert_subtitles"]
        if "subtitle_format" in settings_dict:
            self.settings.subtitle_format = settings_dict["subtitle_format"]
        if "enable_whisper" in settings_dict:
            self.settings.enable_subtitle_generation = settings_dict["enable_whisper"]
        if "download_subtitles" in settings_dict:
            self.settings.enable_subtitle_download = settings_dict["download_subtitles"]
        if "whisper_languages" in settings_dict:
            # Convert comma-separated string to list
            langs = settings_dict["whisper_languages"]
            if isinstance(langs, str):
                self.settings.subtitle_languages = [lang.strip() for lang in langs.split(",")]
            else:
                self.settings.subtitle_languages = langs
        if "whisper_model" in settings_dict:
            self.settings.whisper_model = settings_dict["whisper_model"]
        
        # OpenSubtitles
        if "opensubtitles_api_key" in settings_dict:
            self.settings.opensubtitles_api_key = settings_dict["opensubtitles_api_key"]
        if "opensubtitles_username" in settings_dict:
            self.settings.opensubtitles_username = settings_dict["opensubtitles_username"]
        if "opensubtitles_password" in settings_dict:
            self.settings.opensubtitles_password = settings_dict["opensubtitles_password"]
        
        # Renaming
        if "naming_pattern" in settings_dict:
            # Use the same pattern for both TV and movies for now
            self.settings.renaming_pattern_tv = settings_dict["naming_pattern"]
            self.settings.renaming_pattern_movie = settings_dict["naming_pattern"]
        if "tmdb_api_key" in settings_dict:
            self.settings.tmdb_api_key = settings_dict["tmdb_api_key"]
        if "tvdb_api_key" in settings_dict:
            self.settings.tvdb_api_key = settings_dict["tvdb_api_key"]
        
        # Audio options
        if "audio_codec" in settings_dict:
            self.settings.audio_codec = settings_dict["audio_codec"]
        if "audio_bitrate" in settings_dict:
            bitrate = settings_dict["audio_bitrate"]
            if bitrate and bitrate.lower() != "auto":
                self.settings.audio_bitrate = bitrate
            else:
                self.settings.audio_bitrate = None
        if "audio_languages" in settings_dict:
            # Store for potential future use
            pass
        if "audio_track_selection" in settings_dict:
            # Store for potential future use
            pass
        
        # General options
        if "output_format" in settings_dict:
            self.settings.output_format = settings_dict["output_format"]
        if "delete_original" in settings_dict:
            self.settings.delete_original = settings_dict["delete_original"]
        if "overwrite_existing" in settings_dict:
            self.settings.overwrite_existing = settings_dict["overwrite_existing"]
        if "preserve_date" in settings_dict:
            # Store for potential future use
            pass
        if "use_faststart" in settings_dict:
            self.settings.use_faststart = settings_dict["use_faststart"]
        if "two_pass" in settings_dict:
            # Store for potential future use
            pass
        if "threads" in settings_dict:
            # Store for potential future use
            pass
        if "custom_args" in settings_dict:
            # Store for potential future use
            pass
        if "copy_metadata" in settings_dict:
            # Store for potential future use
            pass
        if "strip_metadata" in settings_dict:
            # Store for potential future use
            pass
        if "create_subfolders" in settings_dict:
            # Store for potential future use
            pass
        if "output_directory" in settings_dict:
            # Store for potential future use
            pass
        
        # Recreate core with new settings
        self.core = FFmpegCore(self.settings)
    
    def settings_to_dict(self) -> Dict:
        """Convert settings to dictionary"""
        return {
            "ffmpeg_path": self.settings.ffmpeg_path,
            "ffprobe_path": self.settings.ffprobe_path,
            "use_nvenc": self.settings.use_nvenc,
            "use_amf": self.settings.use_amf,
            "use_qsv": self.settings.use_qsv,
            "nvenc_preset": self.settings.nvenc_preset,
            "nvenc_cq": self.settings.nvenc_cq,
            "convert_subtitles": self.settings.convert_subtitles,
            "enable_subtitle_generation": self.settings.enable_subtitle_generation,
            "enable_subtitle_download": self.settings.enable_subtitle_download,
            "subtitle_languages": self.settings.subtitle_languages,
            "whisper_model": self.settings.whisper_model,
            "enable_renaming": self.settings.enable_renaming,
            "renaming_pattern_tv": self.settings.renaming_pattern_tv,
            "renaming_pattern_movie": self.settings.renaming_pattern_movie,
            "audio_codec": self.settings.audio_codec,
            "output_format": self.settings.output_format,
            "delete_original": self.settings.delete_original,
            "overwrite_existing": self.settings.overwrite_existing
        }
    
    def run(self):
        """Main loop - read from stdin, process, write to stdout"""
        logger.info("FFmpeg API Bridge starting...")
        logger.info(f"Python version: {sys.version}")
        logger.info(f"Working directory: {os.getcwd()}")
        
        # Send startup confirmation
        self._send_response({
            "status": "ready",
            "message": "FFmpeg API Bridge initialized"
        })
        
        try:
            while True:
                try:
                    line = sys.stdin.readline()
                    if not line:
                        logger.info("stdin closed, exiting")
                        break
                    
                    line = line.strip()
                    if not line:
                        continue
                    
                    logger.debug(f"Received line: {line[:100]}...")
                    
                    try:
                        request = json.loads(line)
                        logger.info(f"Received request: {request.get('action')}")
                        
                        response = self.handle_request(request)
                        self._send_response(response)
                    
                    except json.JSONDecodeError as e:
                        logger.error(f"Invalid JSON: {e}")
                        self._send_error(f"Invalid JSON: {str(e)}")
                    
                    except Exception as e:
                        logger.error(f"Error processing request: {e}", exc_info=True)
                        self._send_error(f"Request error: {str(e)}")
                
                except EOFError:
                    logger.info("EOF received, exiting")
                    break
                except Exception as e:
                    logger.error(f"Error in main loop: {e}", exc_info=True)
                    self._send_error(f"Loop error: {str(e)}")
        
        except KeyboardInterrupt:
            logger.info("API bridge stopped by user")
        except Exception as e:
            logger.error(f"Fatal error: {e}", exc_info=True)
        finally:
            logger.info("API bridge shutting down")


def main():
    """Entry point"""
    try:
        logger.info("=== FFmpeg API Bridge Main Entry ===")
        api = FFmpegAPI()
        api.run()
    except Exception as e:
        logger.error(f"Failed to start API: {e}", exc_info=True)
        # Send error to stdout before exiting
        print(json.dumps({"status": "error", "message": f"Startup failed: {str(e)}"}))
        sys.stdout.flush()
        sys.exit(1)


if __name__ == "__main__":
    main()

