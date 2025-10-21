
#!/usr/bin/env python3
"""
FFmpeg API Bridge - JSON-based API for Java GUI
Provides stdin/stdout JSON communication for Java application
"""

import json
import logging
import logging.handlers
import os
import sys
import threading
from pathlib import Path
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
# Use user's home directory for logs to match settings location
log_dir = Path.home() / ".encodeforge" / "logs"
log_dir.mkdir(parents=True, exist_ok=True)
log_file = log_dir / 'encodeforge-api.log'

# Create rotating file handler
handler = logging.handlers.RotatingFileHandler(
    log_file,
    maxBytes=10 * 1024 * 1024,  # 10MB
    backupCount=5,  # Keep 5 backup files
    encoding='utf-8'
)
handler.setLevel(logging.DEBUG)

# Create formatter and add it to handler
formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)

# Configure root logger - IMPORTANT: force=True removes default handlers that output to stdout
logging.basicConfig(
    level=logging.DEBUG,
    handlers=[handler],
    force=True  # This removes any existing handlers including default StreamHandler
)

# Ensure no propagation to root logger's console handler
logger = logging.getLogger(__name__)
logger.propagate = False
logger.addHandler(handler)
logger.setLevel(logging.DEBUG)

try:
    from encodeforge_core import ConversionSettings, EncodeForgeCore  # type: ignore
    logger.info("Successfully imported encodeforge_core")
    CORE_AVAILABLE = True
except Exception as e:
    logger.error(f"Failed to import encodeforge_core: {e}", exc_info=True)
    logger.error("This means conversions will not work properly!")
    logger.error("Check that all required Python modules are available")
    CORE_AVAILABLE = False
    # Create minimal fallback classes with required attributes
    class ConversionSettings:
        def __init__(self):
            # FFmpeg paths
            self.ffmpeg_path = "ffmpeg"
            self.ffprobe_path = "ffprobe"
            
            # Hardware acceleration - start with all disabled, let Java set them
            self.use_nvenc = False
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
            self.omdb_api_key = ""
            self.trakt_api_key = ""
            self.fanart_api_key = ""
            
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
    
    class EncodeForgeCore:
        def __init__(self, settings=None):
            self.settings = settings or ConversionSettings()
            self.profile_mgr = ProfileManager()
            # Add missing attributes for fallback
            self.ffmpeg_mgr = None
            self.conversion_handler = None
        
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
        
        def download_subtitle(self, file_id, provider, video_path, language="eng", download_url=""):
            return {"status": "error", "message": "Subtitle download not available"}
        
        def download_subtitles(self, video_path, languages):
            return {"status": "error", "message": "Subtitle download not available"}
        
        def search_subtitles(self, video_path, languages, progress_callback=None):
            return {"status": "error", "message": "Subtitle search not available"}
        
        def advanced_search_subtitles(self, video_path, languages, anilist_url=""):
            return {"status": "error", "message": "Advanced subtitle search not available"}
        
        def rename_files(self, file_paths, dry_run=False, create_backup=False):
            return {"status": "error", "message": "File renaming not available"}
        
        def scan_directory(self, directory, recursive=True):
            return {"status": "error", "message": "Directory scanning not available"}
        
        def get_file_info(self, file_path):
            return {"status": "error", "message": "File info not available"}
        
        def convert_file(self, file_path, output_path=None, progress_callback=None):
            return {"status": "error", "message": "Conversion not available"}
        
        def convert_files(self, file_paths, progress_callback=None):
            logger.error("Using fallback EncodeForgeCore - real conversion not available")
            logger.error("Hardware encoder settings:")
            logger.error(f"  use_nvenc: {self.settings.use_nvenc}")
            logger.error(f"  use_amf: {self.settings.use_amf}")
            logger.error(f"  use_qsv: {self.settings.use_qsv}")
            logger.error(f"  use_videotoolbox: {self.settings.use_videotoolbox}")
            return {"status": "error", "message": "Conversion not available - core module not loaded"}
        
        def cancel_current(self):
            return {"status": "error", "message": "Cancel not available - core module not loaded"}
        
        def apply_subtitles(self, video_path, subtitle_path, output_path=None, mode="external", language="eng"):
            return {"status": "error", "message": "Subtitle application not available"}


class FFmpegAPI:
    """
    JSON API bridge for Java GUI
    Communicates via stdin/stdout using JSON messages
    """
    
    def __init__(self):
        self.core = None
        self.settings = ConversionSettings()
        self._stdout_lock = threading.Lock()  # Protect stdout writes from multiple threads
        self.conversion_thread = None
        self.conversion_result = None
    
    def _send_response(self, response: Dict):
        """Send JSON response to stdout (thread-safe)"""
        with self._stdout_lock:
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
                self.core = EncodeForgeCore(self.settings)
            
            # Route to appropriate handler
            if action == "check_ffmpeg":
                if self.core is None:
                    self.core = EncodeForgeCore(self.settings)
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
            
            elif action == "get_all_status":
                return self.handle_get_all_status()
            
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
                streaming = request.get("streaming", False)
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                if streaming:
                    # Use streaming mode - send results as they come
                    return self.handle_streaming_subtitle_search(video_path, languages)
                else:
                    # Traditional mode - wait for all results
                    return self.core.search_subtitles(video_path, languages)
            
            elif action == "batch_search_subtitles":
                # New batch search endpoint for parallel processing
                files = request.get("files", [])
                if not files:
                    return {"status": "error", "message": "files array required"}
                return self.handle_batch_search_subtitles(files)
            
            elif action == "advanced_search_subtitles":
                video_path = request.get("video_path")
                languages = request.get("languages", self.settings.subtitle_languages)
                anilist_url = request.get("anilist_url", "")
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.advanced_search_subtitles(video_path, languages, anilist_url)
            
            elif action == "download_subtitle":
                file_id = request.get("file_id")
                provider = request.get("provider")
                video_path = request.get("video_path")
                language = request.get("language", "eng")
                download_url = request.get("download_url", "")
                
                if not file_id:
                    return {"status": "error", "message": "file_id required"}
                if not provider:
                    return {"status": "error", "message": "provider required"}
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.download_subtitle(file_id, provider, video_path, language, download_url)
            
            elif action == "download_subtitles":
                video_path = request.get("video_path")
                languages = request.get("languages", self.settings.subtitle_languages)
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.download_subtitles(video_path, languages)
            
            elif action == "apply_subtitles":
                video_path = request.get("video_path")
                subtitle_path = request.get("subtitle_path")
                output_path = request.get("output_path")
                mode = request.get("mode", "external")  # 'burn-in', 'embed', or 'external'
                language = request.get("language", "eng")
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                if not subtitle_path:
                    return {"status": "error", "message": "subtitle_path required"}
                
                return self.core.apply_subtitles(video_path, subtitle_path, output_path, mode, language)
            
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
                create_backup = request.get("create_backup", False)
                
                if not file_paths:
                    return {"status": "error", "message": "file_paths required"}
                
                return self.core.rename_files(file_paths, dry_run, create_backup)
            
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
            
            elif action == "check_ongoing_conversion":
                return self.check_ongoing_conversion()
            
            elif action == "stop_conversion":
                # Force cancel current conversion
                logger.info("Received stop_conversion command from Java")
                try:
                    logger.info("Calling core.cancel_current()...")
                    result = self.core.cancel_current()
                    logger.info(f"Stop conversion result: {result}")
                    return result
                except Exception as e:
                    logger.error(f"Error stopping conversion: {e}", exc_info=True)
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
                    self.core = EncodeForgeCore(self.settings)
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
    
    def handle_streaming_subtitle_search(self, video_path: str, languages: list):
        """Handle subtitle search with streaming results (send as they come)"""
        try:
            logger.info(f"Starting streaming subtitle search for: {video_path}")
            
            # Ensure core is initialized
            if self.core is None:
                self.core = EncodeForgeCore(self.settings)
            
            def progress_callback(progress_data: Dict):
                """Send progress updates immediately as they come"""
                logger.debug(f"Sending progress: {progress_data.get('provider', 'unknown')}")
                self._send_response(progress_data)
            
            # Call search with progress callback
            final_result = self.core.search_subtitles(video_path, languages, progress_callback=progress_callback)
            
            # Return final result (which also gets sent)
            return final_result
            
        except Exception as e:
            logger.error(f"Error in streaming subtitle search: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e)
            }
    
    def handle_batch_search_subtitles(self, files: list):
        """
        Handle batch subtitle search for multiple files in parallel.
        Each file dict should contain: file_id, file_name, video_path, languages
        
        Searches files in parallel (up to 3 concurrent) and sends progress updates
        for each file with file_id attached so Java knows which file it's for.
        """
        import os
        from concurrent.futures import ThreadPoolExecutor, as_completed
        
        try:
            logger.info(f"Starting batch subtitle search for {len(files)} files")
            
            # Ensure core is initialized
            if self.core is None:
                self.core = EncodeForgeCore(self.settings)
            
            # Track completion
            completed_count = 0
            total_files = len(files)
            
            def create_file_progress_callback(file_id, file_name):
                """Create a progress callback for a specific file"""
                def progress_callback(progress_data: Dict):
                    # Add file identification to progress data
                    progress_data['file_id'] = file_id
                    progress_data['file_name'] = file_name
                    logger.debug(f"Sending progress for file {file_id} ({file_name}): {progress_data.get('provider', 'unknown')}")
                    self._send_response(progress_data)
                return progress_callback
            
            # Search files in parallel using ThreadPoolExecutor
            with ThreadPoolExecutor(max_workers=3) as executor:
                # Submit all search tasks
                future_to_file = {}
                for file_info in files:
                    file_id = file_info.get('file_id')
                    file_name = file_info.get('file_name', os.path.basename(file_info.get('video_path', '')))
                    video_path = file_info.get('video_path')
                    languages = file_info.get('languages', self.settings.subtitle_languages)
                    
                    if not video_path:
                        logger.error(f"No video_path provided for file_id: {file_id}")
                        # Send error for this file (do NOT close the stream!)
                        self._send_response({
                            "file_id": file_id,
                            "file_name": file_name,
                            "status": "error",
                            "message": "video_path required",
                            "file_complete": True  # This file is done, but stream continues
                        })
                        completed_count += 1
                        continue
                    
                    logger.info(f"Submitting search task for file {file_id}: {file_name}")
                    
                    # Submit search task
                    future = executor.submit(
                        self.core.search_subtitles,
                        video_path,
                        languages,
                        create_file_progress_callback(file_id, file_name)
                    )
                    future_to_file[future] = (file_id, file_name)
                
                # Wait for all tasks to complete
                for future in as_completed(future_to_file):
                    file_id, file_name = future_to_file[future]
                    try:
                        result = future.result()
                        completed_count += 1
                        subtitle_count = len(result.get('subtitles', []))
                        logger.info(f"Search completed for file {file_id} ({file_name}): {subtitle_count} subtitles found")
                        
                        # Send final result with subtitles for this file
                        # NOTE: Do NOT set "complete": True here - that closes the stream!
                        # Only the final batch completion message should have complete: true
                        final_response = {
                            "file_id": file_id,
                            "file_name": file_name,
                            "status": "success" if result.get("status") == "success" else "complete",
                            "file_complete": True  # This file is done, but stream continues
                        }
                        
                        # Include subtitle data if present
                        if "subtitles" in result:
                            final_response["subtitles"] = result["subtitles"]
                        if "message" in result:
                            final_response["message"] = result["message"]
                        
                        logger.debug(f"Sending final result for file {file_id}: {subtitle_count} subtitles")
                        self._send_response(final_response)
                        
                    except Exception as e:
                        completed_count += 1
                        logger.error(f"Error searching file {file_id} ({file_name}): {e}", exc_info=True)
                        # Send error for this file
                        # NOTE: Do NOT set "complete": True here either!
                        self._send_response({
                            "file_id": file_id,
                            "file_name": file_name,
                            "status": "error",
                            "message": str(e),
                            "file_complete": True  # This file is done, but stream continues
                        })
            
            # Send final completion message
            logger.info(f"Batch search complete: {completed_count}/{total_files} files processed")
            return {
                "status": "success",
                "message": f"Batch search complete: {completed_count}/{total_files} files",
                "completed": completed_count,
                "total": total_files,
                "complete": True
            }
            
        except Exception as e:
            logger.error(f"Error in batch subtitle search: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e),
                "complete": True
            }
    
    def handle_get_capabilities(self) -> Dict:
        """Get system capabilities"""
        # Ensure core is initialized
        if self.core is None:
            self.core = EncodeForgeCore(self.settings)
        
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
            
            # OpenSubtitles API key is optional
            # Search works without it, downloads require it (5/day free, 200/day VIP)
            
            return {
                "status": "success",
                "has_api_key": has_api_key,
                "configured": True,  # Always configured - search works without API key
                "message": "Search enabled (API key optional, needed for downloads)" if not has_api_key else "Search & downloads enabled (5/day)"
            }
        except Exception as e:
            logger.error(f"Error checking OpenSubtitles: {e}")
            return {
                "status": "error",
                "message": str(e),
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
    
    def handle_get_all_status(self) -> Dict:
        """
        Consolidated endpoint that checks all provider/service statuses at once.
        This eliminates the need for multiple individual status check calls.
        """
        try:
            logger.info("Checking all provider statuses")
            
            # Ensure core is initialized
            if self.core is None:
                self.core = EncodeForgeCore(self.settings)
            
            # Check FFmpeg status
            ffmpeg_status = self.core.check_ffmpeg()
            ffmpeg_info = {
                "available": ffmpeg_status.get("ffmpeg_available", False),
                "version": ffmpeg_status.get("ffmpeg_version", "Unknown")
            }
            
            # Check Whisper status
            whisper_status = self.core.check_whisper()
            whisper_info = {
                "available": whisper_status.get("whisper_available", False),
                "version": whisper_status.get("whisper_version", "Unknown")
            }
            
            # Check OpenSubtitles configuration
            has_opensubtitles_creds = bool(
                self.settings.opensubtitles_username and 
                self.settings.opensubtitles_username.strip() and
                self.settings.opensubtitles_password and 
                self.settings.opensubtitles_password.strip()
            )
            opensubtitles_info = {
                "available": True,  # Always available (can search without login)
                "logged_in": has_opensubtitles_creds,
                "status": "Logged in (20/day)" if has_opensubtitles_creds else "Anonymous (5/day)"
            }
            
            # Check metadata provider configurations
            metadata_providers = {
                "tmdb": bool(self.settings.tmdb_api_key and self.settings.tmdb_api_key.strip()),
                "tvdb": bool(self.settings.tvdb_api_key and self.settings.tvdb_api_key.strip()),
                "omdb": bool(self.settings.omdb_api_key and self.settings.omdb_api_key.strip()),
                "trakt": bool(self.settings.trakt_api_key and self.settings.trakt_api_key.strip()),
                "fanart": bool(self.settings.fanart_api_key and self.settings.fanart_api_key.strip()),
                # Free providers (always available)
                "anilist": True,
                "kitsu": True,
                "jikan": True,
                "tvmaze": True
            }
            
            # Count subtitle providers (assuming we have multiple subtitle providers)
            # This is a simplified count - in reality we'd check each provider's availability
            subtitle_providers = {
                "count": 10,  # Default count of subtitle providers
                "opensubtitles": True,
                "whisper": whisper_info["available"]
            }
            
            result = {
                "status": "success",
                "ffmpeg": ffmpeg_info,
                "whisper": whisper_info,
                "opensubtitles": opensubtitles_info,
                "metadata_providers": metadata_providers,
                "subtitle_providers": subtitle_providers
            }
            
            logger.info("All provider statuses checked successfully")
            return result
            
        except Exception as e:
            logger.error(f"Error checking all statuses: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e)
            }
    
    def handle_get_available_encoders(self) -> Dict:
        """Get available hardware encoders for the current system"""
        try:
            # Ensure core is initialized
            if self.core is None:
                self.core = EncodeForgeCore(self.settings)
            
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
        """Handle application shutdown - save state but don't terminate processes"""
        try:
            logger.info("Handling application shutdown")
            
            logger.info("Java app closing - FFmpeg processes will continue in background")
            logger.info("State file will persist for recovery on next startup")
            
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
        if CORE_AVAILABLE:
            self.core = EncodeForgeCore(self.settings)
            logger.info("Using real EncodeForgeCore for conversion")
        else:
            self.core = EncodeForgeCore(self.settings)
            logger.error("Using fallback EncodeForgeCore - conversions will fail")
        
        logger.info(f"Starting conversion of {len(file_paths)} files with settings: {settings_dict}")
        
        # Define progress callback that sends updates immediately
        def progress_callback(update: Dict):
            logger.info(f"Progress callback received: {update}")
            
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
            
            logger.info(f"Sending progress update to Java: {progress_update}")
            self._send_response(progress_update)
        
        # Start conversion in a separate thread to allow stop commands
        self.conversion_thread = threading.Thread(
            target=self._run_conversion,
            args=(file_paths, progress_callback),
            daemon=True
        )
        self.conversion_result = None
        self.conversion_thread.start()
        
        # Return immediately - the conversion runs in background
        # Progress updates will be sent via the callback
        return {
            "status": "started",
            "message": f"Started conversion of {len(file_paths)} files"
        }
    
    def _run_conversion(self, file_paths, progress_callback):
        """Run conversion in separate thread"""
        try:
            logger.info(f"_run_conversion started with {len(file_paths)} files")
            if self.core is None:
                logger.error("Core is not initialized")
                error_result = {
                    "type": "conversion_complete",
                    "status": "error",
                    "message": "Core not initialized"
                }
                self._send_response(error_result)
                return
            
            logger.info("Calling self.core.convert_files with progress callback")
            result = self.core.convert_files(file_paths, progress_callback)
            logger.info(f"convert_files returned: {result}")
            
            # Send final result
            success_count = int(result.get("converted", 0))
            total_count = int(result.get("total", 0))
            failed_count = total_count - success_count
            
            final_result = {
                "type": "conversion_complete",
                "status": result["status"],
                "message": f"Converted {success_count}/{total_count} files",
                "success_count": success_count,
                "failed_count": failed_count,
                "details": result
            }
            
            logger.info(f"Conversion completed: {final_result}")
            self._send_response(final_result)
            self.conversion_result = final_result
            
        except Exception as e:
            logger.error(f"Conversion error: {e}", exc_info=True)
            error_result = {
                "type": "conversion_complete",
                "status": "error",
                "message": str(e)
            }
            self._send_response(error_result)
            self.conversion_result = error_result
    
    def check_ongoing_conversion(self) -> Dict:
        """Check if there's an ongoing conversion from a previous session"""
        try:
            # Initialize core if not already done
            if not hasattr(self, 'core') or not self.core:
                self.core = EncodeForgeCore(self.settings)
            
            # Check for saved process state
            if hasattr(self.core, 'conversion_handler') and self.core.conversion_handler:
                saved_state = self.core.conversion_handler.get_saved_process_state()
            else:
                saved_state = None
            
            if saved_state:
                logger.info("Found ongoing conversion from previous session")
                
                # Extract queue information with proper defaults
                queue = saved_state.get('queue', [])
                if not isinstance(queue, list):
                    queue = []
                
                queued_files = [f for f in queue if f.get('status') == 'queued']
                processing_files = [f for f in queue if f.get('status') == 'processing']
                completed_files = [f for f in queue if f.get('status') == 'completed']
                failed_files = [f for f in queue if f.get('status') == 'failed']
                
                logger.info(f"Recovery state: {len(queued_files)} queued, "
                          f"{len(processing_files)} processing, "
                          f"{len(completed_files)} completed, "
                          f"{len(failed_files)} failed")
                
                return {
                    "status": "ongoing",
                    "message": "Ongoing conversion detected",
                    "queue": queue,  # Full queue with all file statuses
                    "queued_files": queued_files,
                    "processing_files": processing_files,
                    "completed_files": completed_files,
                    "failed_files": failed_files,
                    "current_file": saved_state.get('current_file', ''),
                    "current_index": saved_state.get('current_index', 0),
                    "total_files": saved_state.get('total_files', 0),
                    "completed_count": saved_state.get('completed_count', 0),
                    "failed_count": saved_state.get('failed_count', 0),
                    "pid": saved_state.get('pid'),
                    "timestamp": saved_state.get('timestamp')
                }
            else:
                return {
                    "status": "none",
                    "message": "No ongoing conversion found"
                }
                
        except Exception as e:
            logger.error(f"Error checking ongoing conversion: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Failed to check ongoing conversion: {str(e)}"
            }
    
    def update_settings(self, settings_dict: Dict):
        """Update conversion settings from dictionary"""
        logger.info(f"Updating settings with: {settings_dict}")
        
        # FFmpeg paths
        if "ffmpeg_path" in settings_dict:
            self.settings.ffmpeg_path = settings_dict["ffmpeg_path"]
        if "ffprobe_path" in settings_dict:
            self.settings.ffprobe_path = settings_dict["ffprobe_path"]
        
        # Hardware acceleration
        if "use_nvenc" in settings_dict:
            self.settings.use_nvenc = settings_dict["use_nvenc"]
            logger.info(f"Set use_nvenc = {self.settings.use_nvenc}")
        if "use_amf" in settings_dict:
            self.settings.use_amf = settings_dict["use_amf"]
            logger.info(f"Set use_amf = {self.settings.use_amf}")
        if "use_qsv" in settings_dict:
            self.settings.use_qsv = settings_dict["use_qsv"]
            logger.info(f"Set use_qsv = {self.settings.use_qsv}")
        if "use_videotoolbox" in settings_dict:
            self.settings.use_videotoolbox = settings_dict["use_videotoolbox"]
            logger.info(f"Set use_videotoolbox = {self.settings.use_videotoolbox}")
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
            if video_codec != "Auto (Best Available)":
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
            else:
                # Auto mode - preserve individual hardware flags and set fallback
                self.settings.video_codec_fallback = "libx264"
                logger.info(f"Auto mode preserving hardware flags - NVENC: {self.settings.use_nvenc}, AMF: {self.settings.use_amf}, QSV: {self.settings.use_qsv}")
        
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
        self.core = EncodeForgeCore(self.settings)
    
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

