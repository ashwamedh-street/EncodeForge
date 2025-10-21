#!/usr/bin/env python3
"""
EncodeForge Core - Main orchestrator for all encoding operations
Lightweight orchestrator that delegates to specialized handlers
"""

import logging
from typing import Callable, Dict, List, Optional

from encodeforge_modules import (
    ConversionHandler,
    ConversionSettings,
    FileHandler,
    RenamingHandler,
    SubtitleHandler,
)
from ffmpeg_manager import FFmpegManager
from metadata_grabber import MetadataGrabber
from profile_manager import ProfileManager
from subtitle_manager import SubtitleProviders
from subtitle_providers.whisper_manager import WhisperManager

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class EncodeForgeCore:
    """
    Main EncodeForge orchestrator
    Delegates operations to specialized handlers:
    - FileHandler: File info and scanning
    - SubtitleHandler: Subtitle operations
    - RenamingHandler: File renaming
    - ConversionHandler: Video conversion
    """
    
    def __init__(self, settings: Optional[ConversionSettings] = None):
        self.settings = settings or ConversionSettings()
        
        # Initialize managers
        self.ffmpeg_mgr = FFmpegManager(self.settings.ffmpeg_path)
        self.whisper_mgr = WhisperManager()
        self.renamer = MetadataGrabber(
            tmdb_key=self.settings.tmdb_api_key,
            tvdb_key=self.settings.tvdb_api_key
        )
        self.subtitle_providers = SubtitleProviders(
            opensubtitles_key=self.settings.opensubtitles_api_key,
            username=self.settings.opensubtitles_username,
            password=self.settings.opensubtitles_password
        )
        self.profile_mgr = ProfileManager()
        
        # Initialize handlers
        self.file_handler = FileHandler(self.settings, self.ffmpeg_mgr)
        self.subtitle_handler = SubtitleHandler(self.settings, self.whisper_mgr, self.subtitle_providers)
        self.renaming_handler = RenamingHandler(self.settings, self.renamer)
        self.conversion_handler = ConversionHandler(self.settings, self.ffmpeg_mgr)
    
    # ======================
    # FFmpeg & Whisper Setup
    # ======================
    
    def check_ffmpeg(self) -> Dict:
        """Check FFmpeg availability"""
        success, info = self.ffmpeg_mgr.detect_ffmpeg()
        
        if success:
            self.settings.ffmpeg_path = str(info["ffmpeg_path"])
            self.settings.ffprobe_path = str(info["ffprobe_path"])
            
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
    
    def download_ffmpeg(self, progress_callback: Optional[Callable] = None) -> Dict:
        """Download and install FFmpeg"""
        success, message = self.ffmpeg_mgr.download_ffmpeg(progress_callback=progress_callback)
        return {
            "status": "success" if success else "error",
            "message": message
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
    
    # ==================
    # File Operations
    # ==================
    
    def get_file_info(self, file_path: str) -> Dict:
        """Get basic file information"""
        return self.file_handler.get_file_info(file_path)
    
    def get_media_info(self, file_path: str) -> Dict:
        """Get detailed media information"""
        return self.file_handler.get_media_info(file_path)
    
    def scan_directory(self, directory: str, recursive: bool = True,
                      progress_callback: Optional[Callable] = None) -> Dict:
        """Scan directory for media files"""
        return self.file_handler.scan_directory(directory, recursive, progress_callback)
    
    # ====================
    # Subtitle Operations
    # ====================
    
    def generate_subtitles(self, video_path: str, language: Optional[str] = None,
                          progress_callback: Optional[Callable] = None) -> Dict:
        """Generate subtitles using Whisper AI"""
        return self.subtitle_handler.generate_subtitles(video_path, language, progress_callback)
    
    def search_subtitles(self, video_path: str, languages: Optional[List[str]] = None,
                        progress_callback: Optional[Callable] = None) -> Dict:
        """Search for available subtitles"""
        return self.subtitle_handler.search_subtitles(video_path, languages, progress_callback)
    
    def advanced_search_subtitles(self, video_path: str, languages: Optional[List[str]] = None, 
                                  anilist_url: str = "") -> Dict:
        """Advanced subtitle search with multiple query variations"""
        return self.subtitle_handler.advanced_search_subtitles(video_path, languages, anilist_url)
    
    def download_subtitle(self, file_id: str, provider: str, video_path: str,
                         language: str = "eng", download_url: str = "") -> Dict:
        """Download a specific subtitle"""
        return self.subtitle_handler.download_subtitle(file_id, provider, video_path, language, download_url)
    
    def download_subtitles(self, video_path: str, languages: Optional[List[str]] = None) -> Dict:
        """Download best matching subtitles automatically"""
        return self.subtitle_handler.download_subtitles(video_path, languages)
    
    def apply_subtitles(self, video_path: str, subtitle_paths: List[str],
                       output_path: Optional[str] = None, burn_in: bool = False,
                       progress_callback: Optional[Callable] = None) -> Dict:
        """Apply/burn subtitles into video"""
        return self.subtitle_handler.apply_subtitles(video_path, subtitle_paths, output_path, 
                                                     burn_in, progress_callback)
    
    # ===================
    # Renaming Operations
    # ===================
    
    def preview_rename(self, file_paths: List[str], settings_dict: Optional[Dict] = None) -> Dict:
        """Preview how files would be renamed"""
        return self.renaming_handler.preview_rename(file_paths, settings_dict)
    
    def rename_files(self, file_paths: List[str], dry_run: bool = False, create_backup: bool = False) -> Dict:
        """Rename media files using metadata"""
        return self.renaming_handler.rename_files(file_paths, dry_run, create_backup)
    
    # =======================
    # Conversion Operations
    # =======================
    
    def convert_file(self, input_path: str, output_path: Optional[str] = None,
                    progress_callback: Optional[Callable] = None) -> Dict:
        """Convert a single video file"""
        return self.conversion_handler.convert_file(input_path, output_path, progress_callback)
    
    def convert_files(self, file_paths: List[str], progress_callback: Optional[Callable] = None) -> Dict:
        """Convert multiple files"""
        return self.conversion_handler.convert_files(file_paths, progress_callback)
    
    def cancel_current(self) -> Dict:
        """Cancel current conversion"""
        return self.conversion_handler.cancel_current()
    
    # ==================
    # Profile Management
    # ==================
    
    def save_profile(self, name: str, settings: Dict) -> Dict:
        """Save a conversion profile"""
        success = self.profile_mgr.save_profile(name, settings)
        return {
            "status": "success" if success else "error",
            "message": f"Profile '{name}' saved" if success else "Failed to save profile"
        }
    
    def load_profile(self, name: str) -> Dict:
        """Load a conversion profile"""
        profile = self.profile_mgr.load_profile(name)
        if profile:
            # Update settings from profile
            if isinstance(profile, dict):
                for key, value in profile.items():
                    if hasattr(self.settings, key):
                        setattr(self.settings, key, value)
            return {
                "status": "success",
                "profile": profile
            }
        else:
            return {
                "status": "error",
                "message": f"Profile '{name}' not found"
            }
    
    def list_profiles(self) -> Dict:
        """List all saved profiles"""
        profiles = self.profile_mgr.list_profiles()
        return {
            "status": "success",
            "profiles": profiles
        }
    
    def delete_profile(self, name: str) -> Dict:
        """Delete a profile"""
        success = self.profile_mgr.delete_profile(name)
        return {
            "status": "success" if success else "error",
            "message": f"Profile '{name}' deleted" if success else "Failed to delete profile"
        }


# Backward compatibility alias
FFmpegCore = EncodeForgeCore


def main():
    """Test the EncodeForge core"""
    core = EncodeForgeCore()
    
    # Check FFmpeg
    ffmpeg_status = core.check_ffmpeg()
    print(f"FFmpeg Status: {ffmpeg_status}")
    
    # Check Whisper
    whisper_status = core.check_whisper()
    print(f"Whisper Status: {whisper_status}")


if __name__ == "__main__":
    main()

