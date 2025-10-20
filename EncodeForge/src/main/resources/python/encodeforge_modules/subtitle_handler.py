#!/usr/bin/env python3
"""
Subtitle Handler - All subtitle-related operations
"""

import logging
import subprocess
from pathlib import Path
from typing import Callable, Dict, List, Optional

from .models import ConversionSettings

logger = logging.getLogger(__name__)


class SubtitleHandler:
    """Handles all subtitle generation, search, and download operations"""
    
    def __init__(self, settings: ConversionSettings, whisper_mgr, subtitle_providers):
        self.settings = settings
        self.whisper_mgr = whisper_mgr
        self.subtitle_providers = subtitle_providers
    
    def generate_subtitles(self, video_path: str, language: Optional[str] = None, 
                          progress_callback: Optional[Callable] = None) -> Dict:
        """Generate subtitles for a video file using Whisper AI"""
        logger.info("=== Starting Whisper AI Subtitle Generation ===")
        logger.info(f"Video: {video_path}")
        logger.info(f"Language: {language or 'auto-detect'}")
        logger.info(f"Model: {self.settings.whisper_model}")
        
        # Check if Whisper is available
        whisper_status = self.whisper_mgr.get_status()
        if not whisper_status.get("installed"):
            logger.error("Whisper AI is not installed")
            return {
                "status": "error",
                "message": "Whisper AI is not installed. Please install it from Settings > Subtitles.",
                "subtitle_path": None
            }
        
        # Check if requested model is available
        installed_models = whisper_status.get("models", [])
        requested_model = self.settings.whisper_model
        
        logger.info(f"Installed Whisper models: {installed_models}")
        
        if requested_model not in installed_models:
            available = whisper_status.get("available_models", [])
            logger.error(f"Requested model '{requested_model}' not installed. Available: {available}")
            return {
                "status": "error",
                "message": f"Whisper model '{requested_model}' is not installed. Please download it from Settings > Subtitles. Available models: {', '.join(available)}",
                "subtitle_path": None
            }
        
        try:
            # Generate output path with language code
            video_file = Path(video_path)
            lang_suffix = language if language else "auto"
            output_path = video_file.parent / f"{video_file.stem}.{lang_suffix}.srt"
            
            logger.info(f"Output subtitle path: {output_path}")
            logger.info("Starting Whisper transcription (this may take several minutes)...")
            
            success, message = self.whisper_mgr.generate_subtitles(
                video_path,
                str(output_path),
                model_name=self.settings.whisper_model,
                language=language,
                progress_callback=progress_callback
            )
            
            if success:
                logger.info(f"✅ Successfully generated subtitles: {output_path}")
                logger.info(f"Message: {message}")
            else:
                logger.error(f"❌ Failed to generate subtitles: {message}")
            
            return {
                "status": "success" if success else "error",
                "message": message,
                "subtitle_path": str(output_path) if success else None
            }
        except Exception as e:
            logger.error(f"❌ Error generating subtitles: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Failed to generate subtitles: {str(e)}",
                "subtitle_path": None
            }
    
    def search_subtitles(self, video_path: str, languages: Optional[List[str]] = None, 
                        progress_callback: Optional[Callable] = None) -> Dict:
        """Search for available subtitles without downloading"""
        try:
            logger.info("=== Starting Subtitle Search ===")
            logger.info(f"Video: {video_path}")
            
            if languages is None:
                languages = self.settings.subtitle_languages
            
            logger.info(f"Languages: {languages}")
            logger.info("Searching across multiple providers...")
            
            # Define callback wrapper for progress updates
            def handle_progress(provider_name: str, results: List[Dict], is_complete: bool):
                if progress_callback:
                    # Format results for UI
                    formatted = []
                    for result in results:
                        formatted.append({
                            "language": result.get("language", "unknown"),
                            "provider": result.get("provider", "unknown"),
                            "format": result.get("format", "srt"),
                            "download_url": result.get("download_url", ""),
                            "file_id": result.get("file_id", ""),
                            "score": result.get("score", 0),
                            "filename": result.get("file_name", "")
                        })
                    
                    # Send progress update
                    progress_callback({
                        "progress": True,
                        "provider": provider_name,
                        "complete": is_complete,
                        "subtitles": formatted if not is_complete else [],
                        "status": "success" if is_complete else "searching"
                    })
            
            # Search all providers with progress callback
            results = self.subtitle_providers.search_all_providers(
                video_path, 
                languages, 
                progress_callback=handle_progress if progress_callback else None
            )
            
            logger.info(f"Found {len(results)} subtitle(s) from {len(set(r.get('provider', 'unknown') for r in results))} providers")
            
            # Log provider breakdown
            provider_counts = {}
            for r in results:
                provider = r.get('provider', 'unknown')
                provider_counts[provider] = provider_counts.get(provider, 0) + 1
            
            for provider, count in provider_counts.items():
                logger.info(f"  - {provider}: {count} subtitle(s)")
            
            # Format results for UI
            formatted_results = []
            for result in results:
                formatted_results.append({
                    "language": result.get("language", "unknown"),
                    "provider": result.get("provider", "unknown"),
                    "format": result.get("format", "srt"),
                    "download_url": result.get("download_url", ""),
                    "file_id": result.get("file_id", ""),
                    "score": result.get("score", 0),
                    "filename": result.get("file_name", "")
                })
            
            return {
                "status": "success",
                "message": f"Found {len(results)} subtitle(s)",
                "count": len(results),
                "subtitles": formatted_results,
                "complete": True
            }
        except Exception as e:
            logger.error(f"Error searching subtitles: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Search failed: {str(e)}",
                "count": 0,
                "subtitles": []
            }
    
    def advanced_search_subtitles(self, video_path: str, languages: Optional[List[str]] = None, anilist_url: str = "") -> Dict:
        """Advanced subtitle search using multiple query variations for better results"""
        try:
            logger.info("=== Starting ADVANCED Subtitle Search ===")
            logger.info(f"Video: {video_path}")
            
            if languages is None:
                languages = self.settings.subtitle_languages
            
            logger.info(f"Languages: {languages}")
            if anilist_url:
                logger.info(f"AniList URL provided: {anilist_url}")
            logger.info("Using advanced search with multiple query variations...")
            
            # Use advanced search from subtitle providers
            results = self.subtitle_providers.advanced_search(video_path, languages, anilist_url)
            
            logger.info(f"Advanced search found {len(results)} unique subtitle(s)")
            
            # Log provider breakdown
            provider_counts = {}
            for r in results:
                provider = r.get('provider', 'unknown')
                provider_counts[provider] = provider_counts.get(provider, 0) + 1
            
            for provider, count in provider_counts.items():
                logger.info(f"  - {provider}: {count} subtitle(s)")
            
            # Format results for UI
            formatted_results = []
            for result in results:
                formatted_results.append({
                    "language": result.get("language", "unknown"),
                    "provider": result.get("provider", "unknown"),
                    "format": result.get("format", "srt"),
                    "download_url": result.get("download_url", ""),
                    "file_id": result.get("file_id", ""),
                    "score": result.get("score", 0),
                    "filename": result.get("filename", "")
                })
            
            return {
                "status": "success",
                "message": f"Advanced search found {len(results)} unique subtitle(s)",
                "count": len(results),
                "subtitles": formatted_results
            }
        except Exception as e:
            logger.error(f"Error in advanced subtitle search: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Advanced search failed: {str(e)}",
                "count": 0,
                "subtitles": []
            }
    
    def download_subtitle(
        self,
        file_id: str,
        provider: str,
        video_path: str,
        language: str = "eng",
        download_url: str = ""
    ) -> Dict:
        """Download a specific subtitle by file_id and provider"""
        logger.info("=== Starting Subtitle Download ===")
        logger.info(f"Provider: {provider}")
        logger.info(f"File ID: {file_id}")
        logger.info(f"Language: {language}")
        
        try:
            video_file = Path(video_path)
            if not video_file.exists():
                return {
                    "status": "error",
                    "message": f"Video file not found: {video_path}"
                }
            
            # Generate output path
            output_path = str(video_file.parent / f"{video_file.stem}.{language}.srt")
            
            # Download subtitle using SubtitleProviders
            success, result = self.subtitle_providers.download_subtitle(
                file_id,
                provider,
                output_path,
                download_url
            )
            
            if success:
                logger.info(f"✅ Successfully downloaded subtitle to: {result}")
                return {
                    "status": "success",
                    "message": f"Downloaded subtitle from {provider}",
                    "subtitle_path": result,
                    "provider": provider,
                    "language": language
                }
            else:
                logger.error(f"❌ Failed to download subtitle: {result}")
                return {
                    "status": "error",
                    "message": result,
                    "provider": provider,
                    "requires_manual_download": "manual download" in result.lower()
                }
        
        except Exception as e:
            logger.error(f"Error downloading subtitle: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Download failed: {str(e)}"
            }
    
    def download_subtitles(self, video_path: str, languages: Optional[List[str]] = None) -> Dict:
        """Search and download best matching subtitles automatically"""
        try:
            logger.info("=== Starting Auto-Download Best Subtitle ===")
            
            if languages is None:
                languages = self.settings.subtitle_languages
            
            # Search for subtitles
            search_results = self.search_subtitles(video_path, languages)
            
            if search_results["status"] != "success" or search_results["count"] == 0:
                return {
                    "status": "error",
                    "message": "No subtitles found",
                    "subtitles_downloaded": []
                }
            
            # Try to download the best subtitle for each language
            downloaded = []
            for subtitle in search_results["subtitles"]:
                result = self.download_subtitle(
                    subtitle["file_id"],
                    subtitle["provider"],
                    video_path,
                    subtitle["language"],
                    subtitle.get("download_url", "")
                )
                
                if result["status"] == "success":
                    downloaded.append(result)
                    break  # Successfully downloaded, stop
            
            if downloaded:
                return {
                    "status": "success",
                    "message": f"Downloaded {len(downloaded)} subtitle(s)",
                    "subtitles_downloaded": downloaded
                }
            else:
                return {
                    "status": "error",
                    "message": "Failed to download any subtitles",
                    "subtitles_downloaded": []
                }
                
        except Exception as e:
            logger.error(f"Error in download_subtitles: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e),
                "subtitles_downloaded": []
            }
    
    def apply_subtitles(
        self,
        video_path: str,
        subtitle_paths: List[str],
        output_path: Optional[str] = None,
        burn_in: bool = False,
        progress_callback: Optional[Callable] = None
    ) -> Dict:
        """Apply/burn subtitles into video file"""
        try:
            logger.info("=== Applying Subtitles to Video ===")
            logger.info(f"Video: {video_path}")
            logger.info(f"Subtitles: {subtitle_paths}")
            logger.info(f"Burn-in: {burn_in}")
            
            video_file = Path(video_path)
            if not video_file.exists():
                return {"status": "error", "message": "Video file not found"}
            
            if not subtitle_paths:
                return {"status": "error", "message": "No subtitle files provided"}
            
            # Generate output path
            if output_path is None:
                output_path = str(video_file.parent / f"{video_file.stem}.with_subs{video_file.suffix}")
            
            # Build ffmpeg command
            cmd = ["ffmpeg", "-i", video_path]
            
            # Add subtitle inputs
            for sub_path in subtitle_paths:
                cmd.extend(["-i", sub_path])
            
            # Add subtitle options
            if burn_in:
                # Burn subtitles into video
                cmd.extend([
                    "-vf", f"subtitles={subtitle_paths[0]}",
                    "-c:a", "copy",
                    output_path
                ])
            else:
                # Add as soft subtitles
                cmd.extend([
                    "-c", "copy",
                    "-c:s", "mov_text",  # For MP4
                    output_path
                ])
            
            logger.info(f"Running: {' '.join(cmd)}")
            
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True
            )
            
            stdout, stderr = process.communicate()
            
            if process.returncode == 0:
                logger.info(f"✅ Successfully applied subtitles to: {output_path}")
                return {
                    "status": "success",
                    "message": "Subtitles applied successfully",
                    "output_path": output_path
                }
            else:
                logger.error(f"❌ Failed to apply subtitles: {stderr}")
                return {
                    "status": "error",
                    "message": f"FFmpeg error: {stderr}"
                }
                
        except Exception as e:
            logger.error(f"Error applying subtitles: {e}", exc_info=True)
            return {
                "status": "error",
                "message": str(e)
            }

