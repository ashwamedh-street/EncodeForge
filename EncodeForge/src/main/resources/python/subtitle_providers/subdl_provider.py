#!/usr/bin/env python3
"""
SubDL Provider
Good for movies and TV shows with free API
"""

import gzip
import json
import logging
import urllib.error
import urllib.parse
import urllib.request
from typing import Dict, List

from .base_provider import BaseSubtitleProvider

logger = logging.getLogger(__name__)


class SubDLProvider(BaseSubtitleProvider):
    """SubDL provider with free API"""
    
    def __init__(self):
        super().__init__()
        self.provider_name = "SubDL"
    
    def search(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search SubDL (subdl.com)"""
        results = []
        
        try:
            metadata = self.extract_media_metadata(video_path)
            search_term = metadata['clean_name']
            season = metadata.get('season')
            episode = metadata.get('episode')
            
            if not search_term:
                return results
            
            searches = []
            if season and episode:
                searches.append({
                    'url': f"https://api.subdl.com/api/v1/subtitles?api_key=free&film_name={urllib.parse.quote(search_term)}&season_number={season}&episode_number={episode}",
                    'desc': f"'{search_term}' S{season:02d}E{episode:02d}"
                })
                search_with_ep = f"{search_term} S{season:02d}E{episode:02d}"
                searches.append({
                    'url': f"https://api.subdl.com/api/v1/subtitles?api_key=free&film_name={urllib.parse.quote(search_with_ep)}",
                    'desc': f"'{search_with_ep}' (in name)"
                })
            else:
                searches.append({
                    'url': f"https://api.subdl.com/api/v1/subtitles?api_key=free&film_name={urllib.parse.quote(search_term)}",
                    'desc': f"'{search_term}'"
                })
            
            data = None
            for search in searches:
                try:
                    logger.info(f"SubDL search attempt: {search['desc']}")
                    logger.debug(f"SubDL URL: {search['url']}")
                    
                    req = urllib.request.Request(search['url'], headers=self.session_headers)
                    
                    with urllib.request.urlopen(req, timeout=10) as response:
                        response_text = response.read().decode('utf-8')
                        logger.debug(f"SubDL response (first 500 chars): {response_text[:500]}")
                        data = json.loads(response_text)
                        
                        # Log the data structure for debugging
                        logger.debug(f"SubDL response keys: {data.keys() if isinstance(data, dict) else 'not a dict'}")
                        if isinstance(data, dict):
                            logger.debug(f"SubDL success field: {data.get('success')}")
                            logger.debug(f"SubDL subtitles count: {len(data.get('subtitles', [])) if data.get('subtitles') else 0}")
                            if data.get('subtitles'):
                                logger.debug(f"First subtitle: {data['subtitles'][0] if len(data['subtitles']) > 0 else 'none'}")
                        
                        if data.get('success') and data.get('subtitles') and len(data['subtitles']) > 0:
                            logger.info(f"✅ SubDL found {len(data['subtitles'])} results with this search")
                            break
                        else:
                            logger.info(f"SubDL search returned no results for this search (success={data.get('success')}, subtitles={len(data.get('subtitles', []))})")
                            data = None
                except Exception as e:
                    logger.warning(f"SubDL search failed: {e}", exc_info=True)
                    continue
            
            if not data:
                logger.info("SubDL: No results found for this file after all search strategies")
                return results
            
            for item in data['subtitles'][:20]:
                lang_code = item.get('lang', '').lower()
                
                requested_2letter = []
                for lang in languages:
                    if len(lang) == 3:
                        if lang.lower() == 'eng':
                            requested_2letter.append('en')
                        elif lang.lower() == 'spa':
                            requested_2letter.append('es')
                        elif lang.lower() == 'fre':
                            requested_2letter.append('fr')
                        elif lang.lower() == 'ger':
                            requested_2letter.append('de')
                        else:
                            requested_2letter.append(lang[:2].lower())
                    else:
                        requested_2letter.append(lang.lower())
                
                if lang_code in requested_2letter or lang_code in [lang.lower() for lang in languages]:
                    results.append({
                        "provider": "SubDL",
                        "file_name": item.get('release_name', 'Unknown'),
                        "language": lang_code if lang_code else 'unknown',
                        "downloads": int(item.get('downloads', 0)),
                        "rating": float(item.get('rating', 0)),
                        "file_id": str(item.get('sd_id', '')),
                        "download_url": item.get('url', ''),
                        "movie_name": item.get('name', search_term),
                        "format": "srt"
                    })
            
            logger.info(f"SubDL found {len(results)} subtitle(s)")
            
        except Exception as e:
            logger.error(f"Error searching SubDL: {e}", exc_info=True)
            
        return results
    
    def download(self, file_id: str, download_url: str, output_path: str) -> tuple:
        """Download from SubDL"""
        try:
            api_url = f"https://api.subdl.com/api/v1/subtitles/{file_id}"
            req = urllib.request.Request(api_url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=30) as response:
                data = json.loads(response.read().decode('utf-8'))
                
                if data.get('success') and data.get('download_url'):
                    download_link = data['download_url']
                    
                    req2 = urllib.request.Request(download_link, headers=self.session_headers)
                    with urllib.request.urlopen(req2, timeout=30) as dl_response:
                        content = dl_response.read()
                        
                        if download_link.endswith('.gz'):
                            content = gzip.decompress(content)
                        
                        with open(output_path, 'wb') as f:
                            f.write(content)
                        
                        logger.info(f"✅ Downloaded from SubDL: {output_path}")
                        return True, output_path
                else:
                    return False, "SubDL download link not available"
                    
        except Exception as e:
            logger.error(f"SubDL download error: {e}")
            return False, f"SubDL download failed: {str(e)}"

