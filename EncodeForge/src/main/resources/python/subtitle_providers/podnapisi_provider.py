#!/usr/bin/env python3
"""
Podnapisi Provider
Supports all movies, TV shows, and languages
"""

import gzip
import logging
import re
import urllib.error
import urllib.parse
import urllib.request
from typing import Dict, List

from .base_provider import BaseSubtitleProvider

logger = logging.getLogger(__name__)


class PodnapisiProvider(BaseSubtitleProvider):
    """Podnapisi.NET provider"""
    
    def __init__(self):
        super().__init__()
        self.provider_name = "Podnapisi"
    
    def search(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search Podnapisi.NET"""
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
                search_queries.append(f"{search_name} S{season:02d}E{episode:02d}")
                search_queries.append(f"{search_name} {season}x{episode:02d}")
                search_queries.append(search_name)
            elif year:
                search_queries.append(f"{search_name} {year}")
                search_queries.append(search_name)
            else:
                search_queries.append(search_name)
            
            found_any = False
            for search_term in search_queries:
                if found_any:
                    break
                    
                try:
                    logger.debug(f"Podnapisi trying: '{search_term}'")
                    url = f"https://podnapisi.net/en/subtitles/search/?keywords={urllib.parse.quote(search_term)}"
                    
                    req = urllib.request.Request(url, headers=self.session_headers)
                    
                    with urllib.request.urlopen(req, timeout=15) as response:
                        html = response.read().decode('utf-8', errors='ignore')
                    
                    subtitle_pattern = r'<a href="(/[^"]+/download)"[^>]*>.*?<span[^>]*>([^<]+)</span>'
                    matches = re.findall(subtitle_pattern, html, re.DOTALL)
                    
                    if not matches:
                        logger.debug(f"Podnapisi: No results for '{search_term}', trying next...")
                        continue
                    
                    found_any = True
                    logger.info(f"✅ Podnapisi found {len(matches)} result(s)")
                    
                    for download_url, title in matches[:10]:
                        for lang in languages:
                            full_download_url = download_url if download_url.startswith('http') else f"https://podnapisi.net/en{download_url}"
                            results.append({
                                "provider": "Podnapisi",
                                "file_name": f"{title}.srt",
                                "language": lang,
                                "downloads": 0,
                                "rating": 0.0,
                                "file_id": f"podnapisi{download_url}",
                                "download_url": full_download_url,
                                "movie_name": title,
                                "format": "srt"
                            })
                    break
                    
                except urllib.error.HTTPError as e:
                    if e.code == 415:
                        logger.debug(f"Podnapisi: HTTP 415 for '{search_term}'")
                    elif e.code == 403:
                        logger.warning("Podnapisi blocked request (HTTP 403) - may be rate limiting")
                    else:
                        logger.debug(f"Podnapisi HTTP {e.code} for '{search_term}'")
                    continue
                except Exception as e:
                    logger.debug(f"Podnapisi error for '{search_term}': {e}")
                    continue
            
            logger.info(f"Podnapisi found {len(results)} subtitle(s)")
            
        except Exception as e:
            logger.debug(f"Error searching Podnapisi: {e}", exc_info=True)
            
        return results
    
    def download(self, file_id: str, download_url: str, output_path: str) -> tuple:
        """Download from Podnapisi"""
        try:
            if not download_url:
                return False, "No download URL provided for Podnapisi"
            
            req = urllib.request.Request(download_url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=30) as response:
                content = response.read()
                
                if content[:2] == b'\x1f\x8b':
                    content = gzip.decompress(content)
                
                with open(output_path, 'wb') as f:
                    f.write(content)
                
                logger.info(f"✅ Downloaded from Podnapisi: {output_path}")
                return True, output_path
                
        except Exception as e:
            logger.error(f"Podnapisi download error: {e}")
            return False, f"Podnapisi download failed: {str(e)}"

