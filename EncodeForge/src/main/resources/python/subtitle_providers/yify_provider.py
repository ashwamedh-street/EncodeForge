#!/usr/bin/env python3
"""
YIFY Subtitles Provider
Supports movies only (not TV shows)
"""

import logging
import re
import urllib.error
import urllib.parse
import urllib.request
from typing import Dict, List

from .base_provider import BaseSubtitleProvider

logger = logging.getLogger(__name__)


class YifyProvider(BaseSubtitleProvider):
    """YIFY Subtitles provider - movies only"""
    
    def __init__(self):
        super().__init__()
        self.provider_name = "YIFY"
    
    def search(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search YIFY Subtitles (yifysubtitles.ch)"""
        results = []
        
        try:
            metadata = self.extract_media_metadata(video_path)
            
            movie_name = metadata['clean_name']
            year = metadata.get('year')
            
            if not movie_name:
                return results
            
            # Note: YIFY is best for movies, but we'll try for everything
            if metadata['is_tv_show']:
                logger.debug("YIFY: TV show detected (YIFY is best for movies, may not find results)")
                # Continue anyway - don't skip
            
            # Try multiple search formats
            search_queries = [movie_name]
            if year:
                search_queries.insert(0, f"{movie_name} {year}")
            
            found_any = False
            for search_term in search_queries:
                if found_any:
                    break
                    
                logger.debug(f"YIFY trying: '{search_term}'")
                search_query = urllib.parse.quote(search_term)
                url = f"https://yifysubtitles.ch/search?q={search_query}"
                
                try:
                    req = urllib.request.Request(url, headers=self.session_headers)
                    
                    with urllib.request.urlopen(req, timeout=10) as response:
                        html = response.read().decode('utf-8', errors='ignore')
                    
                    movie_pattern = r'href="(/movie-imdb/[^"]+)"[^>]*>([^<]+)</a>'
                    matches = re.findall(movie_pattern, html)
                    
                    if not matches:
                        logger.debug(f"YIFY: No results for '{search_term}', trying next...")
                        continue
                    
                    found_any = True
                    logger.info(f"✅ YIFY found {len(matches)} movie(s)")
                    
                    for movie_url, movie_title in matches[:3]:
                        if year and year not in movie_title:
                            continue
                        
                        for lang in languages:
                            results.append({
                                "provider": "YIFY",
                                "file_name": f"{movie_title}.{lang}.srt",
                                "language": lang,
                                "downloads": 0,
                                "rating": 0.0,
                                "file_id": f"yify_{movie_url}_{lang}",
                                "download_url": f"https://yifysubtitles.ch{movie_url}",
                                "movie_name": movie_title,
                                "format": "srt"
                            })
                    break
                    
                except urllib.error.URLError as e:
                    logger.debug(f"YIFY network error for '{search_term}': {e}")
                    continue
                except Exception as e:
                    logger.debug(f"YIFY error for '{search_term}': {e}")
                    continue
            
            logger.info(f"YIFY found {len(results)} subtitle(s)")
            
        except Exception as e:
            logger.error(f"Error searching YIFY Subtitles: {e}", exc_info=True)
            
        return results
    
    def download(self, file_id: str, download_url: str, output_path: str) -> tuple:
        """Download from YIFY (requires manual download)"""
        message = (f"YIFY requires manual download. Please visit: {download_url}\n"
                  f"Download the subtitle manually and use 'External File' option.")
        logger.warning("⚠️ YIFY manual download required")
        return False, message

