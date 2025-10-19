#!/usr/bin/env python3
"""
Multi-provider subtitle search and download
Supports: OpenSubtitles, Subscene, Addic7ed, and more
"""

import hashlib
import json
import logging
import re
import struct
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from opensubtitles_manager import OpenSubtitlesManager

logger = logging.getLogger(__name__)


class SubtitleProviders:
    """Manages multiple subtitle providers"""
    
    def __init__(self, opensubtitles_key: str = "", opensubtitles_user: str = "", opensubtitles_pass: str = ""):
        self.opensubtitles = OpenSubtitlesManager(opensubtitles_key, opensubtitles_user, opensubtitles_pass)
        self.providers = ["opensubtitles"]  # Only OpenSubtitles for now (has proper API)
        
    def search_podnapisi(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search Podnapisi.NET (basic implementation)"""
        # Podnapisi.NET doesn't have a public API, would need scraping
        # For now, return empty results
        return []
    
    def search_subdivx(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search SubDivX (Spanish subtitles)"""
        # SubDivX doesn't have a public API, would need scraping
        # For now, return empty results
        return []
    
    def search_all_providers(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search all providers and aggregate results"""
        all_results = []
        
        # OpenSubtitles
        success, results = self.opensubtitles.search_subtitles(video_path, languages)
        if success and results:
            for result in results:
                result["provider"] = "opensubtitles"
                all_results.append(result)
        
        # Subscene (basic support - no API, would need scraping)
        # For now, we'll focus on OpenSubtitles which has a proper API
        
        return all_results
    
    def download_best_subtitle(self, video_path: str, language: str = "en") -> Tuple[bool, Optional[str]]:
        """Download best subtitle from any provider"""
        results = self.search_all_providers(video_path, [language])
        
        if not results:
            return False, None
        
        # Sort by rating and downloads
        results.sort(key=lambda x: (x.get("rating", 0), x.get("downloads", 0)), reverse=True)
        best_sub = results[0]
        
        if best_sub["provider"] == "opensubtitles":
            video_file = Path(video_path)
            output_path = str(video_file.parent / f"{video_file.stem}.{language}.srt")
            
            success, message = self.opensubtitles.download_subtitle(
                best_sub["file_id"],
                output_path
            )
            
            if success:
                return True, output_path
        
        return False, None
    
    def batch_download(self, video_paths: List[str], languages: List[str]) -> Dict:
        """Download subtitles for multiple files"""
        results = {
            "success": [],
            "failed": [],
            "skipped": []
        }
        
        for video_path in video_paths:
            for language in languages:
                success, subtitle_path = self.download_best_subtitle(video_path, language)
                
                if success:
                    results["success"].append({
                        "video": video_path,
                        "subtitle": subtitle_path,
                        "language": language
                    })
                else:
                    results["failed"].append({
                        "video": video_path,
                        "language": language
                    })
        
        return results


def main():
    """Test subtitle providers"""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python subtitle_providers.py <video_file>")
        return
    
    video_file = sys.argv[1]
    
    providers = SubtitleProviders()
    
    print(f"Searching subtitles for: {Path(video_file).name}")
    results = providers.search_all_providers(video_file, ["en", "es"])
    
    print(f"\nFound {len(results)} subtitle(s):")
    for i, result in enumerate(results, 1):
        print(f"\n{i}. {result.get('file_name', 'Unknown')}")
        print(f"   Provider: {result.get('provider', 'Unknown')}")
        print(f"   Language: {result.get('language', 'Unknown')}")
        print(f"   Downloads: {result.get('downloads', 0)}")
        print(f"   Rating: {result.get('rating', 0)}")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()

