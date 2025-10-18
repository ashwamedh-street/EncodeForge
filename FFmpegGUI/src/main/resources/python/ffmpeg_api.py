#!/usr/bin/env python3
"""
FFmpeg API Bridge - JSON-based API for Java GUI
Provides stdin/stdout JSON communication for Java application
"""

import json
import logging
import sys
import os
from typing import Dict, Optional
from pathlib import Path

# Setup logging to file (not stdout, as that's used for JSON communication)
log_file = os.path.join(os.path.dirname(__file__), 'ffmpeg-api.log')
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - %(message)s',
    filename=log_file
)
logger = logging.getLogger(__name__)

try:
    from ffmpeg_core import FFmpegCore, ConversionSettings
    logger.info("Successfully imported ffmpeg_core")
except Exception as e:
    logger.error(f"Failed to import ffmpeg_core: {e}", exc_info=True)
    # Create minimal fallback
    class ConversionSettings:
        pass
    class FFmpegCore:
        def __init__(self, settings=None):
            pass


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
            
            elif action == "preview_rename":
                file_paths = request.get("file_paths", [])
                settings_dict = request.get("settings", {})
                if not file_paths:
                    return {"status": "error", "message": "file_paths required"}
                return self.core.preview_rename(file_paths, settings_dict)
            
            elif action == "generate_subtitles":
                video_path = request.get("video_path")
                language = request.get("language")
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.generate_subtitles(video_path, language)
            
            elif action == "download_subtitles":
                video_path = request.get("video_path")
                languages = request.get("languages", self.settings.subtitle_languages)
                
                if not video_path:
                    return {"status": "error", "message": "video_path required"}
                
                return self.core.download_subtitles(video_path, languages)
            
            elif action == "preview_rename":
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
        
        # Define progress callback that sends updates immediately
        def progress_callback(update: Dict):
            # Ensure all expected fields are present
            progress_update = {
                "type": "progress",
                "file": update.get("file", ""),
                "progress": update.get("progress", 0),
                "status": update.get("status", "processing")
            }
            
            # Add optional fields if present
            if "time" in update:
                progress_update["time"] = update["time"]
            if "duration" in update:
                progress_update["duration"] = update["duration"]
            if "current" in update:
                progress_update["current"] = update["current"]
            if "total" in update:
                progress_update["total"] = update["total"]
            
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
        
        # Subtitle options
        if "convert_subtitles" in settings_dict:
            self.settings.convert_subtitles = settings_dict["convert_subtitles"]
        if "enable_subtitle_generation" in settings_dict:
            self.settings.enable_subtitle_generation = settings_dict["enable_subtitle_generation"]
        if "enable_subtitle_download" in settings_dict:
            self.settings.enable_subtitle_download = settings_dict["enable_subtitle_download"]
        if "subtitle_languages" in settings_dict:
            self.settings.subtitle_languages = settings_dict["subtitle_languages"]
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
        if "enable_renaming" in settings_dict:
            self.settings.enable_renaming = settings_dict["enable_renaming"]
        if "renaming_pattern_tv" in settings_dict:
            self.settings.renaming_pattern_tv = settings_dict["renaming_pattern_tv"]
        if "renaming_pattern_movie" in settings_dict:
            self.settings.renaming_pattern_movie = settings_dict["renaming_pattern_movie"]
        if "tmdb_api_key" in settings_dict:
            self.settings.tmdb_api_key = settings_dict["tmdb_api_key"]
        
        # Audio options
        if "audio_codec" in settings_dict:
            self.settings.audio_codec = settings_dict["audio_codec"]
        if "audio_bitrate" in settings_dict:
            self.settings.audio_bitrate = settings_dict["audio_bitrate"]
        
        # Video options
        if "video_codec_fallback" in settings_dict:
            self.settings.video_codec_fallback = settings_dict["video_codec_fallback"]
        if "target_resolution" in settings_dict:
            self.settings.target_resolution = settings_dict["target_resolution"]
        
        # General options
        if "output_format" in settings_dict:
            self.settings.output_format = settings_dict["output_format"]
        if "delete_original" in settings_dict:
            self.settings.delete_original = settings_dict["delete_original"]
        if "overwrite_existing" in settings_dict:
            self.settings.overwrite_existing = settings_dict["overwrite_existing"]
        
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

