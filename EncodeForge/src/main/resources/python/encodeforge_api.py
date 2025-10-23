
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
import warnings
from typing import Dict

# Add custom Python libs directory to sys.path FIRST (before any other imports)
# This ensures we can import packages installed to our custom directory
if 'PYTHONPATH' in os.environ:
    custom_lib_path = os.environ['PYTHONPATH']
    if custom_lib_path not in sys.path:
        sys.path.insert(0, custom_lib_path)
        # Also check for site-packages subdirectory (in case pip created it)
        import pathlib
        site_packages = pathlib.Path(custom_lib_path) / 'site-packages'
        if site_packages.exists() and str(site_packages) not in sys.path:
            sys.path.insert(0, str(site_packages))

# Suppress all warnings to prevent them from corrupting JSON stdout
# (Whisper library prints warnings to stdout which breaks JSON parsing)
warnings.filterwarnings('ignore')

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
# Use unified application data directory for logs
from path_manager import get_logs_dir

# Get worker ID from environment (set by Java PythonWorker)
WORKER_ID = os.environ.get('WORKER_ID', 'worker-0')

log_dir = get_logs_dir()
# Each worker gets its own log file for easier debugging
log_file = log_dir / f'encodeforge-api-{WORKER_ID}.log'

# Create rotating file handler
handler = logging.handlers.RotatingFileHandler(
    log_file,
    maxBytes=10 * 1024 * 1024,  # 10MB
    backupCount=5,  # Keep 5 backup files
    encoding='utf-8'
)
handler.setLevel(logging.DEBUG)

# Create formatter with worker ID for easier tracking
formatter = logging.Formatter(f'%(asctime)s - [{WORKER_ID}] - %(levelname)s - %(message)s')
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

# Lazy imports - don't import heavy modules until actually needed
CORE_AVAILABLE = None  # None = not checked yet, True = available, False = not available
EncodeForgeCore = None
ConversionSettings = None

def _ensure_core_imports():
    """Lazy import of encodeforge_core - only import when first needed"""
    global CORE_AVAILABLE, EncodeForgeCore, ConversionSettings
    
    if CORE_AVAILABLE is not None:
        return CORE_AVAILABLE
    
    try:
        from encodeforge_core import ConversionSettings as CS, EncodeForgeCore as EFC  # type: ignore
        ConversionSettings = CS
        EncodeForgeCore = EFC
        logger.info("Successfully imported encodeforge_core (lazy)")
        CORE_AVAILABLE = True
        return True
    except Exception as e:
        logger.error(f"Failed to import encodeforge_core: {e}", exc_info=True)
        logger.error("This means conversions will not work properly!")
        logger.error("Check that all required Python modules are available")
        CORE_AVAILABLE = False
        _setup_fallback_classes()
        return False

def _setup_fallback_classes():
    """Setup minimal fallback classes if core import fails"""
    global ConversionSettings, EncodeForgeCore
    
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
            self.amf_qp = 23
            self.amf_preset = "balanced"
            self.qsv_quality = 23
            self.qsv_preset = "medium"
            self.videotoolbox_bitrate = "5M"
            
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
        # Don't import/initialize heavy modules yet - lazy load when needed
        _ensure_core_imports()  # Make sure classes are defined (fallback or real)
        self._core = None  # Private attribute for lazy initialization
        self.settings = ConversionSettings()
        self._stdout_lock = threading.Lock()  # Protect stdout writes from multiple threads
        self.conversion_thread = None
        self.conversion_result = None
        self.worker_id = WORKER_ID  # Store worker ID for logging
        # Cache for expensive operations (5 minute TTL)
        self._cache = {}
        self._cache_ttl = 300  # 5 minutes in seconds
        logger.info(f"FFmpegAPI initialized for {self.worker_id} (core lazy-loaded)")
    
    @property
    def core(self):
        """Lazy property for EncodeForgeCore - only initializes when first accessed"""
        if self._core is None:
            _ensure_core_imports()  # Ensure imports are done
            logger.info(f"{self.worker_id}: Initializing EncodeForgeCore (first use)")
            self._core = EncodeForgeCore(self.settings)
            logger.info(f"{self.worker_id}: EncodeForgeCore initialized")
        return self._core
    
    @core.setter
    def core(self, value):
        """Allow explicit setting of core (for reset operations)"""
        self._core = value
    
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
    
    def _get_cached(self, cache_key: str):
        """Get cached value if still valid"""
        if cache_key in self._cache:
            cached_data = self._cache[cache_key]
            import time
            if time.time() - cached_data['timestamp'] < self._cache_ttl:
                logger.debug(f"Cache hit for {cache_key}")
                return cached_data['value']
            else:
                logger.debug(f"Cache expired for {cache_key}")
                del self._cache[cache_key]
        return None
    
    def _set_cached(self, cache_key: str, value):
        """Set cached value with timestamp"""
        import time
        self._cache[cache_key] = {
            'value': value,
            'timestamp': time.time()
        }
        logger.debug(f"Cached {cache_key}")
    
    def handle_request(self, request: Dict) -> Dict:
        """Handle incoming request and return response"""
        action = request.get("action")
        
        if not action:
            return {"status": "error", "message": "No action specified"}
        
        try:
            # Route to appropriate handler
            if action == "check_ffmpeg":
                # Cache expensive FFmpeg detection
                cached = self._get_cached("check_ffmpeg")
                if cached:
                    return cached
                result = self.core.check_ffmpeg()
                self._set_cached("check_ffmpeg", result)
                return result
            
            elif action == "download_ffmpeg":
                # Clear cache after download
                if "check_ffmpeg" in self._cache:
                    del self._cache["check_ffmpeg"]
                return self.core.download_ffmpeg()
            
            elif action == "get_capabilities":
                # Cache capabilities check
                cached = self._get_cached("get_capabilities")
                if cached:
                    return cached
                result = self.handle_get_capabilities()
                self._set_cached("get_capabilities", result)
                return result
            
            elif action == "check_whisper":
                # Cache Whisper status (models might change, so shorter cache)
                cached = self._get_cached("check_whisper")
                if cached:
                    return cached
                result = self.core.check_whisper()
                self._set_cached("check_whisper", result)
                return result
            
            elif action == "check_opensubtitles":
                return self.handle_check_opensubtitles()
            
            elif action == "check_tmdb":
                return self.handle_check_tmdb()
            
            elif action == "check_tvdb":
                return self.handle_check_tvdb()
            
            elif action == "get_all_status":
                # Cache consolidated status
                cached = self._get_cached("get_all_status")
                if cached:
                    return cached
                result = self.handle_get_all_status()
                self._set_cached("get_all_status", result)
                return result
            
            elif action == "get_available_encoders":
                # Cache encoder detection (expensive operation)
                cached = self._get_cached("get_available_encoders")
                if cached:
                    return cached
                result = self.handle_get_available_encoders()
                self._set_cached("get_available_encoders", result)
                return result
            
            elif action == "reset_core":
                # Reset core to force reinitialization (useful after installing packages)
                logger.info("Resetting EncodeForgeCore to detect newly installed packages")
                self.core = None
                return {"status": "success", "message": "Core reset successfully"}
            
            elif action == "install_whisper":
                # Clear cache after Whisper installation
                if "check_whisper" in self._cache:
                    del self._cache["check_whisper"]
                if "get_all_status" in self._cache:
                    del self._cache["get_all_status"]
                return self.core.install_whisper()
            
            elif action == "download_whisper_model":
                model = request.get("model", "base")
                try:
                    # Define progress callback that sends streaming updates
                    def progress_callback(progress_info):
                        # Send progress update as a streaming response
                        self._send_response({
                            "type": "progress",
                            "progress": progress_info.get("progress", 0),
                            "status": progress_info.get("status", "downloading"),
                            "message": progress_info.get("message", "Downloading...")
                        })
                    
                    # Start download with progress callback
                    result = self.core.download_whisper_model(model, progress_callback=progress_callback)
                    
                    # Force re-check of Whisper on next access (detects newly installed packages)
                    self.core.reset_whisper_check()
                    
                    # Clear cache after model download so UI refreshes
                    if "check_whisper" in self._cache:
                        del self._cache["check_whisper"]
                    if "get_all_status" in self._cache:
                        del self._cache["get_all_status"]
                    return result
                except Exception as e:
                    logger.error(f"Error downloading Whisper model: {e}", exc_info=True)
                    return {"status": "error", "message": f"Failed to download model: {str(e)}"}
            
            elif action == "get_media_info":
                file_path = request.get("file_path")
                if not file_path:
                    return {"status": "error", "message": "file_path required"}
                return self.core.get_media_info(file_path)
            
            elif action == "generate_subtitles":
                video_path = request.get("video_path")
                language = request.get("language")
                
                if not video_path:
                    return {"status": "error", "message": "video_path required", "complete": True}
                
                # Define progress callback to stream updates back to Java
                # Each update is sent as a separate JSON line
                def progress_callback(progress_data):
                    # Send progress update (without "complete" flag so streaming continues)
                    self._send_response(progress_data)
                
                # Get the final result (now includes subtitle metadata)
                result = self.core.generate_subtitles(video_path, language, progress_callback)
                
                # Add "complete" flag to final response so Java knows we're done
                result["complete"] = True
                return result
            
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
                subtitle_paths = request.get("subtitle_paths", [])  # Now expects a list
                output_path = request.get("output_path")
                mode = request.get("mode", "external")  # 'burn-in', 'embed', or 'external'
                language = request.get("language", "eng")
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                if not subtitle_paths:
                    return {"status": "error", "message": "subtitle_paths required"}
                
                return self.core.apply_subtitles(video_path, subtitle_paths, output_path, mode, language)
            
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
            
            elif action == "update_system_resources":
                # Update system resources from Java measurements
                from resource_manager import update_system_resources
                
                cpu_count = request.get("cpu_count")
                physical_cores = request.get("physical_cores")
                total_ram_gb = request.get("total_ram_gb")
                available_ram_gb = request.get("available_ram_gb")
                
                if cpu_count and physical_cores and total_ram_gb is not None and available_ram_gb is not None:
                    update_system_resources(cpu_count, physical_cores, total_ram_gb, available_ram_gb)
                    return {"status": "success", "message": "System resources updated"}
                else:
                    return {"status": "error", "message": "Missing required resource parameters"}
            
            elif action == "get_system_resources":
                # Get system resource information for intelligent worker allocation
                # Note: This now returns info from Java-provided resources if available
                from resource_manager import get_resource_manager
                rm = get_resource_manager()
                
                return {
                    "status": "success",
                    "system_info": rm.get_system_info(),
                    "optimal_workers": {
                        "whisper": rm.get_optimal_worker_count("whisper"),
                        "encoding": rm.get_optimal_worker_count("encoding"),
                        "subtitle_search": rm.get_optimal_worker_count("subtitle_search"),
                        "download": rm.get_optimal_worker_count("download"),
                        "metadata": rm.get_optimal_worker_count("metadata")
                    },
                    "gpu_available": rm.should_use_gpu()
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
            
            elif action == "heartbeat":
                # Heartbeat check for health monitoring
                return {
                    "status": "success",
                    "worker_id": self.worker_id,
                    "timestamp": __import__('time').time()
                }
            
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
        
        Searches files in parallel with intelligent worker allocation and sends progress updates
        for each file with file_id attached so Java knows which file it's for.
        """
        import os
        from concurrent.futures import ThreadPoolExecutor, as_completed
        from resource_manager import get_resource_manager
        
        try:
            logger.info(f"Starting batch subtitle search for {len(files)} files")
            
            # Use resource manager to determine optimal parallel workers for subtitle search
            rm = get_resource_manager()
            max_workers = rm.get_optimal_worker_count("subtitle_search")
            logger.info(f"Using {max_workers} parallel workers for subtitle search")
            
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
            
            # Search files in parallel using ThreadPoolExecutor with intelligent worker allocation
            with ThreadPoolExecutor(max_workers=max_workers) as executor:
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
                
                # Wait for all tasks to complete (with 120 second timeout per file)
                try:
                    for future in as_completed(future_to_file, timeout=300):  # 5 minute overall timeout
                        file_id, file_name = future_to_file[future]
                        try:
                            # Get result with per-file timeout (120 seconds should be plenty for subtitle search)
                            result = future.result(timeout=120)
                            completed_count += 1
                            subtitle_count = len(result.get('subtitles', []))
                            logger.info(f"Search completed for file {file_id} ({file_name}): {subtitle_count} subtitles found")
                            
                            # Send final result with subtitles for this file
                            # NOTE: Do NOT set "complete": True or "status": "success" here - both close the stream!
                            # Only the final batch completion message should have complete: true
                            # Use "searching" status to keep stream alive while indicating this file is done
                            final_response = {
                                "file_id": file_id,
                                "file_name": file_name,
                                "status": "file_complete",  # Custom status that won't trigger stream closure
                                "file_complete": True  # This file is done, but stream continues
                            }
                            
                            # Include subtitle data if present
                            if "subtitles" in result:
                                final_response["subtitles"] = result["subtitles"]
                            if "message" in result:
                                final_response["message"] = result["message"]
                            
                            logger.debug(f"Sending final result for file {file_id}: {subtitle_count} subtitles")
                            self._send_response(final_response)
                            
                        except TimeoutError:
                            completed_count += 1
                            logger.error(f"Timeout searching file {file_id} ({file_name}): search exceeded 120 seconds")
                            # Send timeout error for this file
                            self._send_response({
                                "file_id": file_id,
                                "file_name": file_name,
                                "status": "error",
                                "message": "Search timeout (exceeded 120 seconds)",
                                "file_complete": True  # This file is done, but stream continues
                            })
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
                except TimeoutError:
                    # Overall batch timeout - handle any remaining uncompleted files
                    logger.warning(f"Batch search overall timeout exceeded (300 seconds)")
                    for future, (file_id, file_name) in future_to_file.items():
                        if not future.done():
                            completed_count += 1
                            logger.error(f"File {file_id} ({file_name}) did not complete within timeout")
                            self._send_response({
                                "file_id": file_id,
                                "file_name": file_name,
                                "status": "error",
                                "message": "Search timeout (overall batch timeout exceeded)",
                                "file_complete": True
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
    
    def _lightweight_whisper_check(self) -> Dict:
        """
        Lightweight Whisper check without triggering expensive core initialization.
        Checks if whisper package is importable and scans for model files.
        """
        try:
            # Check if whisper package exists (importlib check is fast)
            from importlib.util import find_spec
            whisper_spec = find_spec("whisper")
            whisper_available = whisper_spec is not None
            
            # Quick check for installed models by scanning filesystem
            installed_models = []
            if whisper_available:
                try:
                    from pathlib import Path
                    from path_manager import get_models_dir
                    
                    model_dir = get_models_dir() / "whisper"
                    if model_dir.exists():
                        # Standard Whisper model names
                        standard_models = ["tiny", "base", "small", "medium", "large", "large-v2", "large-v3"]
                        for model_file in model_dir.glob("*.pt"):
                            model_name = model_file.stem
                            if model_name in standard_models:
                                installed_models.append(model_name)
                        logger.info(f"Lightweight Whisper check: found {len(installed_models)} models in {model_dir}")
                except Exception as e:
                    logger.warning(f"Error scanning for Whisper models: {e}")
            
            return {
                "available": whisper_available,
                "version": "Installed" if whisper_available else "Not installed",
                "installed_models": installed_models,
                "available_models": ["tiny", "base", "small", "medium", "large", "large-v2", "large-v3"],
                "model_sizes": {
                    "tiny": "75 MB", "base": "142 MB", "small": "466 MB", 
                    "medium": "1.5 GB", "large": "2.9 GB", "large-v2": "2.9 GB", "large-v3": "2.9 GB"
                }
            }
        except Exception as e:
            logger.error(f"Error in lightweight Whisper check: {e}")
            return {
                "available": False,
                "version": "Check failed",
                "installed_models": [],
                "available_models": [],
                "model_sizes": {}
            }
    
    def handle_get_all_status(self) -> Dict:
        """
        Consolidated endpoint that checks all provider/service statuses at once.
        This eliminates the need for multiple individual status check calls.
        Uses cached results when available to avoid expensive operations.
        """
        try:
            logger.info("Checking all provider statuses (lightweight)")
            
            # Check FFmpeg status - use cache if available
            cached_ffmpeg = self._get_cached("check_ffmpeg")
            if cached_ffmpeg:
                ffmpeg_info = {
                    "available": cached_ffmpeg.get("ffmpeg_available", False),
                    "version": cached_ffmpeg.get("ffmpeg_version", "Unknown")
                }
            else:
                # Do lightweight check without initializing core
                ffmpeg_info = {
                    "available": False,
                    "version": "Not checked yet"
                }
            
            # Check Whisper status - use cache if available, otherwise do lightweight check
            cached_whisper = self._get_cached("check_whisper")
            if cached_whisper:
                installed_models = cached_whisper.get("installed_models", [])
                available_models = cached_whisper.get("available_models", [])
                whisper_info = {
                    "available": cached_whisper.get("whisper_available", False),
                    "version": cached_whisper.get("whisper_version", "Unknown"),
                    "installed_models": installed_models if isinstance(installed_models, list) else [],
                    "available_models": available_models if isinstance(available_models, list) else [],
                    "model_sizes": cached_whisper.get("model_sizes", {})
                }
            else:
                # Lightweight check: see if whisper is importable and check for model files
                whisper_info = self._lightweight_whisper_check()
            
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
            # Check for saved process state WITHOUT initializing core
            # Core initialization is expensive (2-3 seconds), so we access the state file directly
            from encodeforge_modules.conversion_handler import ConversionHandler
            handler = ConversionHandler(self.settings, None)  # Pass None for ffmpeg_mgr since we're just reading state
            saved_state = handler.get_saved_process_state()
            
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
        if "amf_qp" in settings_dict:
            self.settings.amf_qp = settings_dict["amf_qp"]
        if "amf_preset" in settings_dict:
            self.settings.amf_preset = settings_dict["amf_preset"]
        if "qsv_quality" in settings_dict:
            self.settings.qsv_quality = settings_dict["qsv_quality"]
        if "qsv_preset" in settings_dict:
            self.settings.qsv_preset = settings_dict["qsv_preset"]
        if "videotoolbox_bitrate" in settings_dict:
            self.settings.videotoolbox_bitrate = settings_dict["videotoolbox_bitrate"]
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
        
        # Only recreate core if it was already initialized
        # This avoids expensive init on every settings update
        if self._core is not None:
            logger.info("Settings changed - recreating core with new settings")
            self._core = EncodeForgeCore(self.settings)
        else:
            logger.debug("Settings updated - core will use new settings when initialized")
    
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

