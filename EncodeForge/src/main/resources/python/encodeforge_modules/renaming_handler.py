#!/usr/bin/env python3
"""
Renaming Handler - Media file renaming operations
"""

import logging
from pathlib import Path
from typing import Dict, List, Optional

from .models import ConversionSettings

logger = logging.getLogger(__name__)


class RenamingHandler:
    """Handles media file renaming with metadata lookups"""
    
    def __init__(self, settings: ConversionSettings, renamer):
        self.settings = settings
        self.renamer = renamer
    
    def preview_rename(self, file_paths: List[str], settings_dict: Optional[Dict] = None) -> Dict:
        """Preview how files would be renamed"""
        try:
            logger.info(f"=== Preview Rename for {len(file_paths)} file(s) ===")
            
            # Update settings if provided
            if settings_dict:
                if "tmdb_api_key" in settings_dict:
                    self.settings.tmdb_api_key = settings_dict["tmdb_api_key"]
                    self.renamer.tmdb_key = settings_dict["tmdb_api_key"]
                if "tvdb_api_key" in settings_dict:
                    self.settings.tvdb_api_key = settings_dict["tvdb_api_key"]
                    self.renamer.tvdb_key = settings_dict["tvdb_api_key"]
            
            # Log provider status
            has_tmdb = bool(self.settings.tmdb_api_key and self.settings.tmdb_api_key.strip())
            has_tvdb = bool(self.settings.tvdb_api_key and self.settings.tvdb_api_key.strip())
            logger.info(f"Providers: TMDB={'✓' if has_tmdb else '✗'}, TVDB={'✓' if has_tvdb else '✗'}, AniList=✓ (always available)")
            
            suggested_names = []
            providers = []
            errors = []
            
            for file_path in file_paths:
                try:
                    path = Path(file_path)
                    logger.info(f"Processing: {path.name}")
                    
                    # Detect media type
                    media_type = self.renamer.detect_media_type(path.name)
                    logger.info(f"  Detected type: {media_type}")
                    
                    info = None
                    new_name = None
                    provider = None
                    error = None
                    
                    if media_type == "unknown":
                        error = "❌ ERROR: Could not detect media type from filename."
                    elif media_type == "tv":
                        parsed = self.renamer.parse_tv_filename(path.name)
                        if not parsed:
                            error = "❌ ERROR: Could not parse TV show information."
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
                                    info = self.renamer.search_tv_show(
                                        parsed["title"],
                                        parsed["season"],
                                        parsed["episode"],
                                        provider="tvdb"
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
                            error = "❌ ERROR: Could not parse movie information."
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
                    
                    if new_name:
                        logger.info(f"  ✓ New name: {new_name} [via {provider}]")
                    elif error:
                        logger.warning(f"  {error}")
                except Exception as e:
                    logger.error(f"Error processing {file_path}: {e}", exc_info=True)
                    suggested_names.append(f"❌ ERROR: {str(e)}")
                    providers.append("Error")
                    errors.append(str(e))
            
            logger.info(f"=== Preview Complete: {len(suggested_names)} file(s) processed ===")
            
            return {
                "status": "success",
                "suggested_names": suggested_names,
                "providers": providers,
                "errors": errors
            }
        except Exception as e:
            logger.error(f"Error in preview_rename: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Failed to preview rename: {str(e)}",
                "suggested_names": [],
                "providers": [],
                "errors": []
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
                "original": file_path,
                "new_path": new_path,
                "success": success,
                "message": message
            })
        
        success_count = sum(1 for r in results if r["success"])
        
        return {
            "status": "success",
            "renamed": success_count,
            "total": len(file_paths),
            "results": results
        }

