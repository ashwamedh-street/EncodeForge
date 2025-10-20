#!/usr/bin/env python3
"""
Subf2m Provider
Good for movies and TV shows
"""

import logging
import re
import urllib.error
import urllib.parse
import urllib.request
from typing import Dict, List

from .base_provider import BaseSubtitleProvider

logger = logging.getLogger(__name__)


class Subf2mProvider(BaseSubtitleProvider):
    """Subf2m provider for movies and TV shows"""
    
    def __init__(self):
        super().__init__()
        self.provider_name = "Subf2m"
    
    def search(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search Subf2m (subf2m.co)"""
        results = []
        
        try:
            metadata = self.extract_media_metadata(video_path)
            search_name = metadata['clean_name']
            season = metadata.get('season')
            episode = metadata.get('episode')
            year = metadata.get('year')
            
            if not search_name:
                return results
            
            search_queries = []
            if season and episode:
                search_queries.append(f"{search_name}-S{season:02d}E{episode:02d}")
                search_queries.append(f"{search_name} S{season:02d}E{episode:02d}".replace(' ', '-'))
                search_queries.append(search_name.replace(' ', '-'))
            elif year:
                search_queries.append(f"{search_name}-{year}".replace(' ', '-'))
                search_queries.append(search_name.replace(' ', '-'))
            else:
                search_queries.append(search_name.replace(' ', '-'))
            
            found_any = False
            for search_term in search_queries:
                if found_any:
                    break
                    
                try:
                    logger.debug(f"Subf2m trying: '{search_term}'")
                    url = f"https://subf2m.co/subtitles/searchbytitle?query={urllib.parse.quote(search_term)}"
                    
                    req = urllib.request.Request(url, headers=self.session_headers)
                    
                    with urllib.request.urlopen(req, timeout=10) as response:
                        html = response.read().decode('utf-8', errors='ignore')
                    
                    title_pattern = r'<h2[^>]*><a[^>]*href="([^"]+)"[^>]*>([^<]+)</a>'
                    matches = re.findall(title_pattern, html)
                    
                    if not matches:
                        logger.debug(f"Subf2m: No results for '{search_term}', trying next...")
                        continue
                    
                    found_any = True
                    logger.info(f"✅ Subf2m found {len(matches)} result(s)")
                    
                    for subtitle_url, title in matches[:10]:
                        for lang in languages:
                            results.append({
                                "provider": "Subf2m",
                                "file_name": f"{title}.{lang}.srt",
                                "language": lang,
                                "downloads": 0,
                                "rating": 0.0,
                                "file_id": f"subf2m{subtitle_url}",
                                "download_url": f"https://subf2m.co{subtitle_url}",
                                "movie_name": title.strip(),
                                "format": "srt"
                            })
                    break
                    
                except urllib.error.URLError as e:
                    logger.debug(f"Subf2m network error for '{search_term}': {e}")
                    continue
                except Exception as e:
                    logger.debug(f"Subf2m error for '{search_term}': {e}")
                    continue
            
            logger.info(f"Subf2m found {len(results)} subtitle(s)")
            
        except Exception as e:
            logger.error(f"Error searching Subf2m: {e}", exc_info=True)
            
        return results
    
    def download(self, file_id: str, download_url: str, output_path: str) -> tuple:
        """Download from Subf2m (requires manual download)"""
        message = (f"Subf2m requires manual download. Please visit: {download_url}\n"
                  f"Download the subtitle manually and use 'External File' option.")
        logger.warning("⚠️ Subf2m manual download required")
        return False, message

