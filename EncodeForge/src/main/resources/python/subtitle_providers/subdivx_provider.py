#!/usr/bin/env python3
"""
SubDivX Provider
Spanish subtitles - largest Spanish subtitle database
"""

import logging
import re
import urllib.error
import urllib.parse
import urllib.request
from typing import Dict, List

from .base_provider import BaseSubtitleProvider

logger = logging.getLogger(__name__)


class SubDivXProvider(BaseSubtitleProvider):
    """SubDivX provider for Spanish subtitles"""
    
    def __init__(self):
        super().__init__()
        self.provider_name = "SubDivX"
    
    def search(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search SubDivX (Spanish subtitles)"""
        results = []
        
        try:
            if "spa" not in languages and "es" not in languages:
                logger.info("SubDivX skipped - no Spanish language requested")
                return results
            
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
                search_queries.append(f"{search_name} Temporada {season}")
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
                    logger.debug(f"SubDivX trying: '{search_term}'")
                    url = f"https://www.subdivx.com/index.php?buscar={urllib.parse.quote(search_term)}&accion=5&masdesc=&subtitulos=1&realiza_b=1"
                    
                    req = urllib.request.Request(url, headers=self.session_headers)
                    
                    with urllib.request.urlopen(req, timeout=10) as response:
                        html = response.read().decode('latin-1', errors='ignore')
                    
                    title_pattern = r'<a class="titulo_menu_izq"[^>]*>([^<]+)</a>'
                    download_pattern = r'<a[^>]*href="(/bajar\.php\?id=\d+&u=\d+)"'
                    
                    titles = re.findall(title_pattern, html)
                    downloads = re.findall(download_pattern, html)
                    
                    if not titles or not downloads:
                        logger.debug(f"SubDivX: No results for '{search_term}', trying next...")
                        continue
                    
                    found_any = True
                    logger.info(f"✅ SubDivX found {len(titles)} result(s)")
                    
                    for i, (title, download_url) in enumerate(zip(titles[:10], downloads[:10])):
                        results.append({
                            "provider": "SubDivX",
                            "file_name": f"{title}.srt",
                            "language": "spa",
                            "downloads": 0,
                            "rating": 0.0,
                            "file_id": f"subdivx_{i}_{download_url}",
                            "download_url": f"https://www.subdivx.com{download_url}",
                            "movie_name": title.strip(),
                            "format": "srt"
                        })
                    break
                    
                except urllib.error.URLError as e:
                    logger.debug(f"SubDivX network error for '{search_term}': {e}")
                    continue
                except Exception as e:
                    logger.debug(f"SubDivX error for '{search_term}': {e}")
                    continue
            
            logger.info(f"SubDivX found {len(results)} subtitle(s)")
            
        except Exception as e:
            logger.error(f"Error searching SubDivX: {e}", exc_info=True)
            
        return results
    
    def download(self, file_id: str, download_url: str, output_path: str) -> tuple:
        """Download from SubDivX (requires manual download)"""
        message = (f"SubDivX requires manual download. Please visit: {download_url}\n"
                  f"Download the subtitle manually and use 'External File' option.")
        logger.warning("⚠️ SubDivX manual download required")
        return False, message

