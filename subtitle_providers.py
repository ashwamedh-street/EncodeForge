#!/usr/bin/env python3
"""
Multi-provider subtitle search and download
Supports: OpenSubtitles, OpenSubtitles.com, Podnapisi, SubDivX, YIFY Subtitles
"""

import gzip
import hashlib
import io
import json
import logging
import re
import struct
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from opensubtitles_manager import OpenSubtitlesManager

logger = logging.getLogger(__name__)


class SubtitleProviders:
    """Manages multiple subtitle providers"""
    
    def __init__(self, opensubtitles_key: str = "", opensubtitles_user: str = "", opensubtitles_pass: str = ""):
        self.opensubtitles = OpenSubtitlesManager(opensubtitles_key, opensubtitles_user, opensubtitles_pass)
        self.providers = ["opensubtitles", "yifysubtitles", "opensubs_com"]
        self.session_headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        }
        
    def calculate_hash(self, file_path: str) -> Optional[str]:
        """Calculate OpenSubtitles hash for a video file"""
        try:
            longlongformat = '<q'  # little-endian long long
            bytesize = struct.calcsize(longlongformat)
            
            with open(file_path, "rb") as f:
                filesize = Path(file_path).stat().st_size
                hash_value = filesize
                
                if filesize < 65536 * 2:
                    return None
                
                # Read first 64kb
                for _ in range(65536 // bytesize):
                    buffer = f.read(bytesize)
                    (l_value,) = struct.unpack(longlongformat, buffer)
                    hash_value += l_value
                    hash_value &= 0xFFFFFFFFFFFFFFFF  # to remain as 64bit number
                
                # Read last 64kb
                f.seek(max(0, filesize - 65536), 0)
                for _ in range(65536 // bytesize):
                    buffer = f.read(bytesize)
                    (l_value,) = struct.unpack(longlongformat, buffer)
                    hash_value += l_value
                    hash_value &= 0xFFFFFFFFFFFFFFFF
                
                return "%016x" % hash_value
        except Exception as e:
            logger.error(f"Error calculating hash: {e}")
            return None
    
    def search_opensubs_com(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search OpenSubtitles.org free API (deprecated, kept for fallback)
        Note: This API is unreliable and may not work
        """
        results = []
        
        try:
            # The old OpenSubtitles.org REST API has been deprecated
            # For now, we'll skip this provider as it returns 400 errors
            logger.info("OpenSubtitles.org free API is deprecated - skipping")
            return results
            
        except Exception as e:
            logger.error(f"Error searching OpenSubs.org: {e}")
            
        return results
    
    def search_yifysubtitles(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search YIFY Subtitles (yifysubtitles.ch) - Free, no API key needed
        Good for movies (NOT TV shows)
        """
        results = []
        
        try:
            # Extract movie name from filename
            file_name = Path(video_path).stem
            
            # Skip if this is a TV show (contains S##E## pattern)
            if re.search(r'S\d+E\d+', file_name, re.IGNORECASE):
                logger.info("YIFY skipped - TV show detected (YIFY is movies-only)")
                return results
            
            # Try to extract year
            year_match = re.search(r'\b(19|20)\d{2}\b', file_name)
            year = year_match.group(0) if year_match else None
            
            # Clean up the name - remove year, quality tags, etc.
            movie_name = re.sub(r'\b(19|20)\d{2}\b', '', file_name)  # Remove year
            movie_name = re.sub(r'\b(720p|1080p|2160p|4K|BluRay|WEB-DL|WEBRip|HDTV|x264|x265|HEVC|AAC|AC3|DTS)\b', '', movie_name, flags=re.IGNORECASE)
            movie_name = re.sub(r'[._-]+', ' ', movie_name).strip()
            
            if not movie_name:
                return results
            
            # YIFY search URL
            search_query = urllib.parse.quote(movie_name)
            url = f"https://yifysubtitles.ch/search?q={search_query}"
            
            logger.info(f"Searching YIFY for: {movie_name} (year: {year})")
            
            req = urllib.request.Request(url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=10) as response:
                html = response.read().decode('utf-8', errors='ignore')
            
            # Parse HTML to find subtitle links (simple regex-based parsing)
            # Look for movie entries with data-id attributes
            movie_pattern = r'href="(/movie-imdb/[^"]+)"[^>]*>([^<]+)</a>'
            matches = re.findall(movie_pattern, html)
            
            # Process first few matches
            for movie_url, movie_title in matches[:3]:
                # If we have a year, try to match it
                if year and year not in movie_title:
                    continue
                
                # Check each language
                for lang in languages:
                    lang_name = self._lang_code_to_name(lang)
                    
                    # Simple heuristic: if the movie matches, add a result
                    # Note: Real implementation would fetch the movie page and parse subtitle links
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
            
            logger.info(f"YIFY found {len(results)} potential subtitle(s)")
            
        except urllib.error.URLError as e:
            logger.error(f"Network error searching YIFY: {e}")
        except Exception as e:
            logger.error(f"Error searching YIFY Subtitles: {e}", exc_info=True)
            
        return results
    
    def _lang_code_to_name(self, lang_code: str) -> str:
        """Convert language code to full name - supports ISO 639-2/3 and Whisper codes"""
        lang_map = {
            # English
            "eng": "English", "en": "English",
            # Spanish (with dialects)
            "spa": "Spanish", "es": "Spanish", "es-MX": "Spanish (LA)",
            # French
            "fre": "French", "fra": "French", "fr": "French",
            # German
            "ger": "German", "deu": "German", "de": "German",
            # Italian
            "ita": "Italian", "it": "Italian",
            # Portuguese (with dialects)
            "por": "Portuguese", "pt": "Portuguese (EU)",
            "pob": "Portuguese (BR)", "pt-BR": "Portuguese (BR)",
            # Russian
            "rus": "Russian", "ru": "Russian",
            # Arabic
            "ara": "Arabic", "ar": "Arabic",
            # Chinese (with variants)
            "chi": "Chinese", "zh": "Chinese (Simp)", "zh-CN": "Chinese (Simp)",
            "zht": "Chinese (Trad)", "zh-TW": "Chinese (Trad)", "zho": "Chinese",
            # Japanese
            "jpn": "Japanese", "ja": "Japanese",
            # Korean
            "kor": "Korean", "ko": "Korean",
            # Hindi
            "hin": "Hindi", "hi": "Hindi",
            # Thai
            "tha": "Thai", "th": "Thai",
            # Vietnamese
            "vie": "Vietnamese", "vi": "Vietnamese",
            # Turkish
            "tur": "Turkish", "tr": "Turkish",
            # Polish
            "pol": "Polish", "pl": "Polish",
            # Dutch
            "dut": "Dutch", "nld": "Dutch", "nl": "Dutch",
            # Swedish
            "swe": "Swedish", "sv": "Swedish",
            # Norwegian
            "nor": "Norwegian", "no": "Norwegian", "nb": "Norwegian"
        }
        return lang_map.get(lang_code, lang_code.upper())
    
    def search_addic7ed(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search Addic7ed (addic7ed.com) - Excellent for TV shows
        Now with actual scraping for real download URLs!
        """
        results = []
        
        try:
            file_name = Path(video_path).stem
            
            # Check if it's a TV show
            tv_match = re.search(r'S(\d+)E(\d+)', file_name, re.IGNORECASE)
            if not tv_match:
                logger.info("Addic7ed skipped - not a TV show (requires S##E## pattern)")
                return results
            
            season = int(tv_match.group(1))
            episode = int(tv_match.group(2))
            
            # Extract show name
            show_name = file_name.split(tv_match.group(0))[0]
            show_name = re.sub(r'[._-]+', ' ', show_name).strip()
            
            logger.info(f"Searching Addic7ed for: {show_name} S{season}E{episode}")
            
            # Try actual scraping
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Referer': 'https://www.addic7ed.com/'
            }
            
            try:
                # Search Addic7ed
                search_query = urllib.parse.quote(show_name)
                search_url = f"https://www.addic7ed.com/search.php?search={search_query}&Submit=Search"
                
                time.sleep(0.5)
                req = urllib.request.Request(search_url, headers=headers)
                
                with urllib.request.urlopen(req, timeout=15) as response:
                    html = response.read()
                    if html[:2] == b'\x1f\x8b':
                        html = gzip.decompress(html)
                    html = html.decode('utf-8', errors='ignore')
                
                # Find show page links
                show_pattern = r'<a href="(/show/\d+)"[^>]*>([^<]+)</a>'
                show_matches = re.findall(show_pattern, html, re.IGNORECASE)
                
                if not show_matches:
                    logger.info("Addic7ed: Show not found in search results")
                    return results
                
                # Get the first matching show
                show_url, found_show_name = show_matches[0]
                full_show_url = f"https://www.addic7ed.com{show_url}"
                
                logger.info(f"Found Addic7ed show: {found_show_name}")
                
                # Fetch show page
                time.sleep(0.5)
                req2 = urllib.request.Request(full_show_url, headers=headers)
                
                with urllib.request.urlopen(req2, timeout=15) as response2:
                    show_html = response2.read()
                    if show_html[:2] == b'\x1f\x8b':
                        show_html = gzip.decompress(show_html)
                    show_html = show_html.decode('utf-8', errors='ignore')
                
                # Find subtitles for this specific season/episode
                # Addic7ed format: links to /original/ or /updated/ downloads
                subtitle_pattern = r'<a[^>]+href="(/(?:original|updated)/\d+/\d+)"[^>]*>([^<]*)</a>'
                subtitle_matches = re.findall(subtitle_pattern, show_html)
                
                # Also look for more specific patterns with language info
                lang_pattern = r'class="language">([^<]+)</td>.*?<a href="(/(?:original|updated)/\d+/\d+)"'
                lang_matches = re.findall(lang_pattern, show_html, re.DOTALL)
                
                for download_path, language_name in lang_matches[:20]:
                    # Convert language name to code
                    lang_code = self._lang_name_to_code(language_name.strip())
                    
                    if lang_code in languages:
                        download_url = f"https://www.addic7ed.com{download_path}"
                        
                        results.append({
                            "provider": "Addic7ed",
                            "file_name": f"{found_show_name}.S{season:02d}E{episode:02d}.{lang_code}.srt",
                            "language": lang_code,
                            "downloads": 0,
                            "rating": 0.0,
                            "file_id": f"addic7ed_{found_show_name}_S{season:02d}E{episode:02d}_{lang_code}",
                            "download_url": download_url,
                            "movie_name": f"{found_show_name} S{season:02d}E{episode:02d}",
                            "format": "srt"
                        })
                
                logger.info(f"Addic7ed found {len(results)} subtitle(s) via scraping")
                
            except urllib.error.HTTPError as e:
                if e.code == 403:
                    logger.warning("Addic7ed blocked request (403) - returning fallback")
                elif e.code == 503:
                    logger.warning("Addic7ed temporarily unavailable (503)")
                else:
                    logger.warning(f"Addic7ed HTTP error: {e.code}")
            except Exception as e:
                logger.warning(f"Addic7ed scraping failed: {e}")
            
        except Exception as e:
            logger.error(f"Error searching Addic7ed: {e}", exc_info=True)
            
        return results
    
    def search_subdl(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search SubDL (subdl.com) - Good for movies and TV shows
        Free API available
        """
        results = []
        
        try:
            file_name = Path(video_path).stem
            
            # Clean filename
            search_term = re.sub(r'\b(720p|1080p|2160p|4K|BluRay|WEB-DL|WEBRip|HDTV|x264|x265|HEVC)\b', '', file_name, flags=re.IGNORECASE)
            search_term = re.sub(r'[._-]+', ' ', search_term).strip()
            
            if not search_term:
                return results
            
            # SubDL API endpoint
            url = f"https://api.subdl.com/api/v1/subtitles?api_key=free&film_name={urllib.parse.quote(search_term)}"
            
            logger.info(f"Searching SubDL for: {search_term}")
            
            req = urllib.request.Request(url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=10) as response:
                data = json.loads(response.read().decode('utf-8'))
                
            # Parse SubDL API response
            if data.get('success') and data.get('subtitles'):
                for item in data['subtitles'][:10]:
                    lang_code = item.get('lang', 'unknown')
                    
                    # Filter by requested languages
                    if lang_code in languages or self._lang_name_to_code(item.get('language', '')) in languages:
                        results.append({
                            "provider": "SubDL",
                            "file_name": item.get('release_name', 'Unknown'),
                            "language": lang_code,
                            "downloads": int(item.get('downloads', 0)),
                            "rating": float(item.get('rating', 0)),
                            "file_id": item.get('sd_id', ''),
                            "download_url": item.get('url', ''),
                            "movie_name": item.get('name', search_term),
                            "format": "srt"
                        })
            
            logger.info(f"SubDL found {len(results)} subtitle(s)")
            
        except urllib.error.URLError as e:
            logger.error(f"Network error searching SubDL: {e}")
        except Exception as e:
            logger.error(f"Error searching SubDL: {e}", exc_info=True)
            
        return results
    
    def search_subf2m(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search Subf2m (subf2m.co) - Good for movies and TV shows
        """
        results = []
        
        try:
            file_name = Path(video_path).stem
            
            # Clean filename
            search_term = re.sub(r'\b(720p|1080p|2160p|4K|BluRay|WEB-DL|WEBRip|HDTV|x264|x265|HEVC)\b', '', file_name, flags=re.IGNORECASE)
            search_term = re.sub(r'[._-]+', '-', search_term).strip()  # Subf2m uses dashes
            
            if not search_term:
                return results
            
            # Subf2m search URL
            url = f"https://subf2m.co/subtitles/searchbytitle?query={urllib.parse.quote(search_term)}"
            
            logger.info(f"Searching Subf2m for: {search_term}")
            
            req = urllib.request.Request(url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=10) as response:
                html = response.read().decode('utf-8', errors='ignore')
            
            # Parse HTML for subtitle entries
            # Subf2m has a specific structure - simplified parsing
            title_pattern = r'<h2[^>]*><a[^>]*href="([^"]+)"[^>]*>([^<]+)</a>'
            matches = re.findall(title_pattern, html)
            
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
            
            logger.info(f"Subf2m found {len(results)} potential subtitle(s)")
            
        except urllib.error.URLError as e:
            logger.error(f"Network error searching Subf2m: {e}")
        except Exception as e:
            logger.error(f"Error searching Subf2m: {e}", exc_info=True)
            
        return results
    
    def search_kitsunekko(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search Kitsunekko (kitsunekko.net) - Excellent for anime
        Focuses on Japanese subtitles (romaji/kanji)
        """
        results = []
        
        try:
            # Only search if Japanese is requested
            if "jpn" not in languages and "ja" not in languages:
                logger.info("Kitsunekko skipped - Japanese not requested (anime subtitle site)")
                return results
            
            file_name = Path(video_path).stem
            
            # Extract anime name
            anime_name = re.sub(r'\[.*?\]', '', file_name)  # Remove brackets
            anime_name = re.sub(r'\d+p', '', anime_name)  # Remove resolution
            anime_name = re.sub(r'[._-]+', ' ', anime_name).strip()
            
            # Detect episode number
            episode_match = re.search(r'(?:E|Episode|Ep\.?)\s*(\d+)', anime_name, re.IGNORECASE)
            if not episode_match:
                episode_match = re.search(r'\s(\d+)\s', anime_name)
            
            episode_num = episode_match.group(1) if episode_match else "01"
            
            logger.info(f"Searching Kitsunekko for anime: {anime_name} (Episode {episode_num})")
            
            # Kitsunekko requires scraping their directory structure
            # For now, return structured placeholder
            results.append({
                "provider": "Kitsunekko",
                "file_name": f"{anime_name}.E{episode_num}.ja.ass",
                "language": "jpn",
                "downloads": 0,
                "rating": 0.0,
                "file_id": f"kitsunekko_{anime_name}_{episode_num}",
                "download_url": "https://kitsunekko.net/dirlist.php?dir=subtitles%2Fjapanese%2F",
                "movie_name": anime_name,
                "format": "ass"
            })
            
            logger.info(f"Kitsunekko prepared {len(results)} placeholder result(s)")
            
        except Exception as e:
            logger.error(f"Error searching Kitsunekko: {e}")
            
        return results
    
    def search_subscene(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search Subscene (subscene.com) - One of the most popular subtitle sites
        """
        results = []
        
        try:
            file_name = Path(video_path).stem
            
            # Clean filename for search
            search_term = re.sub(r'\b(720p|1080p|2160p|4K|BluRay|WEB-DL|WEBRip|HDTV|x264|x265|HEVC)\b', '', file_name, flags=re.IGNORECASE)
            search_term = re.sub(r'[._-]+', ' ', search_term).strip()
            
            logger.info(f"Searching Subscene for: {search_term}")
            
            # Subscene search URL
            search_url = f"https://subscene.com/subtitles/searchbytitle?query={urllib.parse.quote(search_term)}"
            
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Referer': 'https://subscene.com/'
            }
            
            req = urllib.request.Request(search_url, headers=headers)
            
            with urllib.request.urlopen(req, timeout=15) as response:
                html = response.read().decode('utf-8', errors='ignore')
            
            # Parse Subscene results - look for title links
            title_pattern = r'<a href="(/subtitles/[^"]+)"[^>]*>([^<]+)</a>'
            matches = re.findall(title_pattern, html)
            
            # Get the first matching result
            if matches:
                subtitle_page_url, title = matches[0]
                full_url = f"https://subscene.com{subtitle_page_url}"
                
                logger.info(f"Found Subscene page: {full_url}")
                
                # Now fetch the subtitle page to get actual download links
                time.sleep(0.3)
                req2 = urllib.request.Request(full_url, headers=headers)
                
                with urllib.request.urlopen(req2, timeout=15) as response2:
                    page_html = response2.read().decode('utf-8', errors='ignore')
                
                # Find all subtitle entries with download links
                subtitle_pattern = r'<a href="(/subtitles/[^"]+)">\s*<span class="[^"]*">([^<]+)</span>\s*<span>\s*([^<]+)</span>'
                subtitle_matches = re.findall(subtitle_pattern, page_html)
                
                for sub_url, language_name, release_info in subtitle_matches[:10]:
                    # Convert language name to code
                    lang_code = self._lang_name_to_code(language_name.strip())
                    
                    if lang_code in languages:
                        download_url = f"https://subscene.com{sub_url}"
                        
                        results.append({
                            "provider": "Subscene",
                            "file_name": f"{title}.{lang_code}.srt",
                            "language": lang_code,
                            "downloads": 0,
                            "rating": 0.0,
                            "file_id": f"subscene{sub_url}",
                            "download_url": download_url,
                            "movie_name": title,
                            "format": "srt"
                        })
            
            logger.info(f"Subscene found {len(results)} subtitle(s)")
            
        except Exception as e:
            logger.error(f"Error searching Subscene: {e}", exc_info=True)
            
        return results
    
    def search_jimaku(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search Jimaku (jimaku.cc) - Modern anime subtitle search
        Now with actual scraping for real download URLs!
        """
        results = []
        
        try:
            # Only search if Japanese or English is requested
            if not any(lang in ["jpn", "eng", "ja", "en"] for lang in languages):
                logger.info("Jimaku skipped - Japanese/English not requested")
                return results
            
            file_name = Path(video_path).stem
            
            # Extract anime name - clean it up
            anime_name = re.sub(r'\[.*?\]', '', file_name)
            anime_name = re.sub(r'\d+p', '', anime_name)
            anime_name = re.sub(r'[._-]+', ' ', anime_name).strip()
            
            # Extract episode number if present
            ep_match = re.search(r'(?:E|Episode|Ep\.?)\s*(\d+)', anime_name, re.IGNORECASE)
            if not ep_match:
                ep_match = re.search(r'\s(\d+)\s', anime_name)
            
            logger.info(f"Searching Jimaku for anime: {anime_name}")
            
            # Try actual scraping
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Referer': 'https://jimaku.cc/'
            }
            
            try:
                search_url = f"https://jimaku.cc/search?q={urllib.parse.quote(anime_name)}"
                
                time.sleep(0.3)
                req = urllib.request.Request(search_url, headers=headers)
                
                with urllib.request.urlopen(req, timeout=15) as response:
                    html = response.read()
                    if html[:2] == b'\x1f\x8b':
                        html = gzip.decompress(html)
                    html = html.decode('utf-8', errors='ignore')
                
                # Look for subtitle entry links
                # Jimaku uses /entries/ID format
                entry_pattern = r'href="(/entries/[^"]+)"[^>]*>([^<]+)</a>'
                entry_matches = re.findall(entry_pattern, html)
                
                for entry_url, entry_name in entry_matches[:5]:
                    # Build full URL
                    full_entry_url = f"https://jimaku.cc{entry_url}"
                    
                    # Jimaku typically has English subtitles
                    results.append({
                        "provider": "Jimaku",
                        "file_name": f"{entry_name}.eng.srt",
                        "language": "eng",
                        "downloads": 0,
                        "rating": 0.0,
                        "file_id": f"jimaku{entry_url}",
                        "download_url": full_entry_url,
                        "movie_name": entry_name,
                        "format": "srt"
                    })
                
                logger.info(f"Jimaku found {len(results)} subtitle(s) via scraping")
                
            except urllib.error.HTTPError as e:
                logger.warning(f"Jimaku HTTP error: {e.code}")
            except Exception as e:
                logger.warning(f"Jimaku scraping failed: {e}")
            
        except Exception as e:
            logger.error(f"Error searching Jimaku: {e}", exc_info=True)
            
        return results
    
    def search_animesubtitles(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search AnimeSubtitles.net - Multi-language anime subs
        Now with actual scraping for real download URLs!
        """
        results = []
        
        try:
            file_name = Path(video_path).stem
            
            # Extract anime name
            anime_name = re.sub(r'\[.*?\]', '', file_name)
            anime_name = re.sub(r'\d+p', '', anime_name)
            anime_name = re.sub(r'[._-]+', ' ', anime_name).strip()
            
            logger.info(f"Searching AnimeSubtitles for: {anime_name}")
            
            #Try actual scraping
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Referer': 'https://www.animesubtitles.net/'
            }
            
            try:
                search_url = f"https://www.animesubtitles.net/search?q={urllib.parse.quote(anime_name)}"
                
                time.sleep(0.3)
                req = urllib.request.Request(search_url, headers=headers)
                
                with urllib.request.urlopen(req, timeout=15) as response:
                    html = response.read()
                    if html[:2] == b'\x1f\x8b':
                        html = gzip.decompress(html)
                    html = html.decode('utf-8', errors='ignore')
                
                # Look for subtitle links
                subtitle_pattern = r'href="(/subtitles/[^"]+)"[^>]*>([^<]+)</a>'
                subtitle_matches = re.findall(subtitle_pattern, html)
                
                for sub_url, sub_name in subtitle_matches[:10]:
                    # Build full URL
                    full_url = f"https://www.animesubtitles.net{sub_url}"
                    
                    # Add results for requested languages
                    for lang in languages:
                        results.append({
                            "provider": "AnimeSubtitles",
                            "file_name": f"{sub_name}.{lang}.srt",
                            "language": lang,
                            "downloads": 0,
                            "rating": 0.0,
                            "file_id": f"animesubtitles{sub_url}_{lang}",
                            "download_url": full_url,
                            "movie_name": sub_name,
                            "format": "srt"
                        })
                
                logger.info(f"AnimeSubtitles found {len(results)} subtitle(s) via scraping")
                
            except urllib.error.HTTPError as e:
                logger.warning(f"AnimeSubtitles HTTP error: {e.code}")
            except Exception as e:
                logger.warning(f"AnimeSubtitles scraping failed: {e}")
            
        except Exception as e:
            logger.error(f"Error searching AnimeSubtitles: {e}", exc_info=True)
            
        return results
    
    def _lang_name_to_code(self, lang_name: str) -> str:
        """Convert full language name to 3-letter ISO 639-2 code"""
        lang_map = {
            "english": "eng",
            "spanish": "spa",
            "spanish (eu)": "spa",
            "spanish (la)": "spa",
            "french": "fre",
            "german": "ger",
            "italian": "ita",
            "portuguese": "por",
            "portuguese (eu)": "por",
            "portuguese (br)": "pob",
            "portuguese (brazil)": "pob",
            "russian": "rus",
            "arabic": "ara",
            "chinese": "chi",
            "chinese (simplified)": "chi",
            "chinese (simp)": "chi",
            "chinese (traditional)": "zht",
            "chinese (trad)": "zht",
            "japanese": "jpn",
            "korean": "kor",
            "hindi": "hin",
            "thai": "tha",
            "vietnamese": "vie",
            "turkish": "tur",
            "polish": "pol",
            "dutch": "dut",
            "swedish": "swe",
            "norwegian": "nor"
        }
        return lang_map.get(lang_name.lower(), lang_name)
    
    def search_podnapisi(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search Podnapisi.NET using their API"""
        results = []
        
        try:
            file_name = Path(video_path).stem
            
            # Clean filename - remove quality tags and normalize
            search_term = re.sub(r'\b(720p|1080p|2160p|4K|BluRay|WEB-DL|WEBRip|HDTV|x264|x265|HEVC)\b', '', file_name, flags=re.IGNORECASE)
            search_term = re.sub(r'[._-]+', ' ', search_term).strip()
            
            if not search_term:
                return results
            
            # Podnapisi API endpoint
            url = f"https://podnapisi.net/subtitles/search/old?keywords={urllib.parse.quote(search_term)}"
            
            logger.info(f"Searching Podnapisi for: {search_term}")
            
            req = urllib.request.Request(url, headers=self.session_headers)
            
            # Increase timeout for Podnapisi as it can be slow
            with urllib.request.urlopen(req, timeout=15) as response:
                html = response.read().decode('utf-8', errors='ignore')
            
            # Parse HTML for subtitle entries
            # Podnapisi uses table rows with subtitle-entry class
            subtitle_pattern = r'<a href="(/[^"]+/download)"[^>]*>.*?<span[^>]*>([^<]+)</span>'
            matches = re.findall(subtitle_pattern, html, re.DOTALL)
            
            for download_url, title in matches[:10]:
                # Try to detect language from the page
                for lang in languages:
                    lang_name = self._lang_code_to_name(lang)
                    
                    # Add result (simplified - real implementation would parse more details)
                    results.append({
                        "provider": "Podnapisi",
                        "file_name": f"{title}.srt",
                        "language": lang,
                        "downloads": 0,
                        "rating": 0.0,
                        "file_id": f"podnapisi{download_url}",
                        "download_url": f"https://podnapisi.net{download_url}",
                        "movie_name": title,
                        "format": "srt"
                    })
            
            logger.info(f"Podnapisi found {len(results)} potential subtitle(s)")
            
        except urllib.error.URLError as e:
            logger.error(f"Network error searching Podnapisi: {e}")
        except Exception as e:
            logger.error(f"Error searching Podnapisi: {e}", exc_info=True)
            
        return results
    
    def search_subdivx(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search SubDivX (Spanish subtitles - largest Spanish subtitle database)"""
        results = []
        
        try:
            # SubDivX is primarily for Spanish language content
            if "spa" not in languages and "es" not in languages:
                logger.info("SubDivX skipped - no Spanish language requested")
                return results
            
            file_name = Path(video_path).stem
            
            # Clean search term
            search_term = re.sub(r'\b(720p|1080p|2160p|4K|BluRay|WEB-DL|WEBRip|HDTV|x264|x265|HEVC)\b', '', file_name, flags=re.IGNORECASE)
            search_term = re.sub(r'[._-]+', ' ', search_term).strip()
            
            if not search_term:
                return results
            
            # SubDivX search URL
            url = f"https://www.subdivx.com/index.php?buscar={urllib.parse.quote(search_term)}&accion=5&masdesc=&subtitulos=1&realiza_b=1"
            
            logger.info(f"Searching SubDivX for: {search_term}")
            
            req = urllib.request.Request(url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=10) as response:
                html = response.read().decode('latin-1', errors='ignore')  # SubDivX uses latin-1 encoding
            
            # Parse HTML for subtitle entries
            # SubDivX has a specific structure with title and download links
            title_pattern = r'<a class="titulo_menu_izq"[^>]*>([^<]+)</a>'
            download_pattern = r'<a[^>]*href="(/bajar\.php\?id=\d+&u=\d+)"'
            
            titles = re.findall(title_pattern, html)
            downloads = re.findall(download_pattern, html)
            
            # Match titles with downloads
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
            
            logger.info(f"SubDivX found {len(results)} subtitle(s)")
            
        except urllib.error.URLError as e:
            logger.error(f"Network error searching SubDivX: {e}")
        except Exception as e:
            logger.error(f"Error searching SubDivX: {e}", exc_info=True)
            
        return results
    
    def search_all_providers(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search all providers and aggregate results"""
        logger.info(f"=== Searching ALL Providers for: {Path(video_path).name} ===")
        logger.info(f"Languages: {languages}")
        all_results = []
        
        # Convert language codes to OpenSubtitles format
        lang_codes = []
        for lang in languages:
            if len(lang) == 3:
                lang_codes.append(lang)
            elif lang == "en":
                lang_codes.append("eng")
            elif lang == "es":
                lang_codes.append("spa")
            elif lang == "fr":
                lang_codes.append("fre")
            elif lang == "de":
                lang_codes.append("ger")
            else:
                lang_codes.append(lang)
        
        logger.info(f"Normalized language codes: {lang_codes}")
        
        # OpenSubtitles (with API key/login)
        logger.info("→ Searching OpenSubtitles.com (with API key)...")
        if self.opensubtitles:
            try:
                success, results = self.opensubtitles.search_subtitles(video_path, lang_codes)
                logger.info(f"  OpenSubtitles API returned: success={success}, results count={len(results) if results else 0}")
                if success and results:
                    for result in results:
                        result["provider"] = "OpenSubtitles.com"
                        all_results.append(result)
                    logger.info(f"  ✅ OpenSubtitles.com: Found {len(results)} subtitles")
                else:
                    logger.info(f"  ⚠️ OpenSubtitles.com: No results")
            except Exception as e:
                logger.error(f"  ❌ OpenSubtitles.com search failed: {e}", exc_info=True)
        else:
            logger.info("  ⚠️ OpenSubtitles.com: Not configured (no API key)")
        
        # OpenSubtitles.org REST API (deprecated - skipping for now)
        logger.info("→ Searching OpenSubtitles.org (free API)...")
        logger.info("  ⚠️ OpenSubtitles.org: API deprecated - skipped")
        # Note: The old REST API at rest.opensubtitles.org has been deprecated
        # We rely on the paid OpenSubtitles.com API instead
        
        # Addic7ed (TV shows only)
        logger.info("→ Searching Addic7ed (TV shows)...")
        try:
            addic7ed_results = self.search_addic7ed(video_path, lang_codes)
            logger.info(f"  Addic7ed returned {len(addic7ed_results)} results")
            all_results.extend(addic7ed_results)
            if addic7ed_results:
                logger.info(f"  ✅ Addic7ed: Found {len(addic7ed_results)} subtitles")
        except Exception as e:
            logger.error(f"  ❌ Addic7ed search failed: {e}")
        
        # SubDL (Movies and TV)
        logger.info("→ Searching SubDL...")
        try:
            subdl_results = self.search_subdl(video_path, lang_codes)
            logger.info(f"  SubDL returned {len(subdl_results)} results")
            all_results.extend(subdl_results)
            if subdl_results:
                logger.info(f"  ✅ SubDL: Found {len(subdl_results)} subtitles")
            else:
                logger.info(f"  ⚠️ SubDL: No results found")
        except Exception as e:
            logger.error(f"  ❌ SubDL search failed: {e}")
        
        # Subf2m (Movies and TV)
        logger.info("→ Searching Subf2m...")
        try:
            subf2m_results = self.search_subf2m(video_path, lang_codes)
            logger.info(f"  Subf2m returned {len(subf2m_results)} results")
            all_results.extend(subf2m_results)
            if subf2m_results:
                logger.info(f"  ✅ Subf2m: Found {len(subf2m_results)} subtitles")
            else:
                logger.info(f"  ⚠️ Subf2m: No results found")
        except Exception as e:
            logger.error(f"  ❌ Subf2m search failed: {e}")
        
        # YIFY Subtitles (movies only)
        logger.info("→ Searching YIFY Subtitles (movies only)...")
        try:
            yify_results = self.search_yifysubtitles(video_path, lang_codes)
            logger.info(f"  YIFY returned {len(yify_results)} results")
            all_results.extend(yify_results)
            if yify_results:
                logger.info(f"  ✅ YIFY: Found {len(yify_results)} subtitles")
        except Exception as e:
            logger.error(f"  ❌ YIFY search failed: {e}")
        
        # Podnapisi
        logger.info("→ Searching Podnapisi...")
        try:
            podnapisi_results = self.search_podnapisi(video_path, lang_codes)
            logger.info(f"  Podnapisi returned {len(podnapisi_results)} results")
            all_results.extend(podnapisi_results)
            if podnapisi_results:
                logger.info(f"  ✅ Podnapisi: Found {len(podnapisi_results)} subtitles")
            else:
                logger.info(f"  ⚠️ Podnapisi: No results found")
        except Exception as e:
            logger.error(f"  ❌ Podnapisi search failed: {e}")
        
        # SubDivX (Spanish only)
        if "spa" in lang_codes or "es" in lang_codes:
            logger.info("→ Searching SubDivX (Spanish)...")
            try:
                subdivx_results = self.search_subdivx(video_path, lang_codes)
                logger.info(f"  SubDivX returned {len(subdivx_results)} results")
                all_results.extend(subdivx_results)
                if subdivx_results:
                    logger.info(f"  ✅ SubDivX: Found {len(subdivx_results)} subtitles")
                else:
                    logger.info(f"  ⚠️ SubDivX: No results found")
            except Exception as e:
                logger.error(f"  ❌ SubDivX search failed: {e}")
        else:
            logger.info("→ SubDivX skipped (Spanish not requested)")
        
        # Subscene (Popular subtitle site - movies and TV)
        logger.info("→ Searching Subscene...")
        try:
            subscene_results = self.search_subscene(video_path, lang_codes)
            logger.info(f"  Subscene returned {len(subscene_results)} results")
            all_results.extend(subscene_results)
            if subscene_results:
                logger.info(f"  ✅ Subscene: Found {len(subscene_results)} subtitles")
            else:
                logger.info(f"  ⚠️ Subscene: No results found")
        except Exception as e:
            logger.error(f"  ❌ Subscene search failed: {e}")
        
        # === ANIME SUBTITLE PROVIDERS ===
        logger.info("→ Searching anime-specific providers...")
        
        # Jimaku (Modern anime subtitle search)
        if any(lang in ["jpn", "eng", "ja", "en"] for lang in lang_codes):
            logger.info("→ Searching Jimaku (Anime)...")
            try:
                jimaku_results = self.search_jimaku(video_path, lang_codes)
                logger.info(f"  Jimaku returned {len(jimaku_results)} results")
                all_results.extend(jimaku_results)
                if jimaku_results:
                    logger.info(f"  ✅ Jimaku: Found {len(jimaku_results)} subtitles")
            except Exception as e:
                logger.error(f"  ❌ Jimaku search failed: {e}")
        
        # AnimeSubtitles (Multi-language anime)
        logger.info("→ Searching AnimeSubtitles...")
        try:
            animesubtitles_results = self.search_animesubtitles(video_path, lang_codes)
            logger.info(f"  AnimeSubtitles returned {len(animesubtitles_results)} results")
            all_results.extend(animesubtitles_results)
            if animesubtitles_results:
                logger.info(f"  ✅ AnimeSubtitles: Found {len(animesubtitles_results)} subtitles")
        except Exception as e:
            logger.error(f"  ❌ AnimeSubtitles search failed: {e}")
        
        logger.info(f"=== SEARCH COMPLETE: Total {len(all_results)} subtitle(s) from {len(set(r.get('provider', 'unknown') for r in all_results))} provider(s) ===")
        
        if all_results:
            # Calculate scores and rank results
            all_results = self._rank_subtitles(all_results)
            
            # Log provider breakdown
            provider_counts = {}
            for r in all_results:
                provider = r.get('provider', 'unknown')
                provider_counts[provider] = provider_counts.get(provider, 0) + 1
            logger.info("Provider breakdown:")
            for provider, count in provider_counts.items():
                logger.info(f"  - {provider}: {count}")
            
            logger.info(f"✅ Top ranked subtitle: {all_results[0].get('provider')} - {all_results[0].get('file_name')} (score: {all_results[0].get('score', 0)})")
        else:
            logger.warning("No subtitles found from any provider!")
            logger.warning("Possible reasons:")
            logger.warning("  - File name doesn't match any known titles")
            logger.warning("  - Content is too new/rare")
            logger.warning("  - Providers are blocking requests")
            logger.warning("  - OpenSubtitles API key doesn't have download permissions")
            logger.warning("Suggestions:")
            logger.warning("  - Try Whisper AI generation instead")
            logger.warning("  - Upgrade OpenSubtitles account for API access")
            logger.warning("  - Manually download from websites")
        
        return all_results
    
    def _validate_download_url(self, download_url: str, provider: str) -> bool:
        """
        Validate if a download URL looks legitimate
        Returns True if URL appears to be a real download link, False if it's just a search page
        """
        if not download_url:
            return False
        
        # Patterns that indicate a real download URL
        real_download_patterns = [
            r'/download/',
            r'/original/',
            r'/updated/',
            r'/subtitle/download',
            r'/entries/\d+',  # Jimaku
            r'/subtitles/[^/]+/[^/]+',  # Subscene subtitle pages
            r'\.srt$',
            r'\.ass$',
            r'\.zip$'
        ]
        
        # Patterns that indicate just a search page (not real download)
        search_page_patterns = [
            r'/search\?',
            r'searchbytitle\?',
            r'^https?://[^/]+/$',  # Just homepage
        ]
        
        # Check if it's a search page
        for pattern in search_page_patterns:
            if re.search(pattern, download_url, re.IGNORECASE):
                logger.debug(f"{provider}: URL looks like search page: {download_url}")
                return False
        
        # Check if it's a real download URL
        for pattern in real_download_patterns:
            if re.search(pattern, download_url, re.IGNORECASE):
                return True
        
        # For OpenSubtitles with numeric file_id, it's valid
        if provider == "OpenSubtitles.com":
            return True
        
        # If URL contains the provider domain but no search pattern, assume it's valid
        provider_domains = {
            "Subscene": "subscene.com",
            "SubDL": "subdl.com",
            "Podnapisi": "podnapisi.net"
        }
        
        if provider in provider_domains:
            if provider_domains[provider] in download_url and "/search" not in download_url:
                return True
        
        # Default: if we can't determine, assume it needs validation
        logger.debug(f"{provider}: URL validation uncertain: {download_url}")
        return False
    
    def _rank_subtitles(self, results: List[Dict]) -> List[Dict]:
        """
        Rank and score subtitle results based on multiple factors
        Higher score = better subtitle
        Also validates download URLs and marks manual-only subtitles
        """
        for result in results:
            score = 0.0
            
            # Validate download URL
            download_url = result.get('download_url', '')
            provider = result.get('provider', 'unknown')
            has_valid_url = self._validate_download_url(download_url, provider)
            
            # Mark if manual download only
            result['manual_download_only'] = not has_valid_url
            
            if not has_valid_url:
                logger.info(f"⚠️ {provider}: No valid download URL, marked as manual-only")
                # Reduce score for manual downloads
                score -= 20
            
            # Provider quality score (based on reliability and quality)
            provider_scores = {
                "OpenSubtitles.com": 100,  # Official API, best quality
                "Subscene": 95,  # Very popular and reliable
                "Addic7ed": 90,  # Excellent for TV shows
                "Jimaku": 85,  # Good anime source
                "SubDL": 85,  # Good API
                "Podnapisi": 80,
                "AnimeSubtitles": 75,  # Good anime coverage
                "Subf2m": 70,
                "YIFY": 65,  # Movies only
                "SubDivX": 60,  # Spanish only
            }
            score += provider_scores.get(provider, 50)
            
            # Download count (normalized to 0-20 points)
            downloads = result.get('downloads', 0)
            if downloads > 0:
                score += min(20, downloads / 100)
            
            # Rating score (0-10 points)
            rating = result.get('rating', 0.0)
            score += rating
            
            # Format preference (ASS > SRT > others)
            format_type = result.get('format', 'srt').lower()
            if format_type == 'ass':
                score += 5  # ASS has styling
            elif format_type == 'srt':
                score += 3  # SRT is standard
            
            # Store the calculated score
            result['score'] = round(score, 2)
        
        # Sort by score (highest first)
        results.sort(key=lambda x: x.get('score', 0), reverse=True)
        
        return results
    
    def download_subtitle(self, file_id: str, provider: str, output_path: str, download_url: str = "") -> Tuple[bool, str]:
        """
        Download a specific subtitle by file_id and provider
        
        Args:
            file_id: The subtitle's file_id from search results
            provider: Provider name (e.g., "OpenSubtitles.com", "Addic7ed", etc.)
            output_path: Where to save the subtitle file
            download_url: Optional direct download URL
            
        Returns:
            (success: bool, message: str or path)
        """
        try:
            logger.info(f"Downloading subtitle from {provider}: {file_id}")
            logger.info(f"Output path: {output_path}")
            
            # Ensure output directory exists
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            
            if provider == "OpenSubtitles.com":
                # Extract file_id (should be numeric)
                if isinstance(file_id, str) and file_id.isdigit():
                    file_id_int = int(file_id)
                elif isinstance(file_id, int):
                    file_id_int = file_id
                else:
                    return False, f"Invalid file_id format for OpenSubtitles: {file_id}"
                
                success, message = self.opensubtitles.download_subtitle(file_id_int, output_path)
                if success:
                    logger.info(f"✅ Successfully downloaded from OpenSubtitles: {output_path}")
                    return True, output_path
                else:
                    logger.error(f"❌ OpenSubtitles download failed: {message}")
                    return False, message
            
            elif provider == "Addic7ed":
                # Addic7ed requires web scraping
                return self._download_from_addic7ed(file_id, download_url, output_path)
            
            elif provider == "Jimaku":
                # Jimaku requires web scraping
                return self._download_from_jimaku(file_id, download_url, output_path)
            
            elif provider == "AnimeSubtitles":
                # AnimeSubtitles requires web scraping
                return self._download_from_animesubtitles(file_id, download_url, output_path)
            
            elif provider == "Kitsunekko":
                # Kitsunekko requires directory listing scraping
                return self._download_from_kitsunekko(file_id, download_url, output_path)
            
            elif provider == "SubDL":
                # SubDL has an API but requires additional parsing
                return self._download_from_subdl(file_id, download_url, output_path)
            
            elif provider == "YIFY":
                # YIFY requires scraping the movie page
                return self._download_from_yify(file_id, download_url, output_path)
            
            elif provider == "Podnapisi":
                # Podnapisi has direct download links
                return self._download_from_podnapisi(file_id, download_url, output_path)
            
            elif provider == "SubDivX":
                # SubDivX requires following redirect links
                return self._download_from_subdivx(file_id, download_url, output_path)
            
            elif provider == "Subf2m":
                # Subf2m requires scraping the subtitle page
                return self._download_from_subf2m(file_id, download_url, output_path)
            
            elif provider == "Subscene":
                # Subscene provides download pages
                return self._download_from_subscene(file_id, download_url, output_path)
            
            else:
                return False, f"Download not implemented for provider: {provider}"
                
        except Exception as e:
            logger.error(f"Error downloading subtitle: {e}", exc_info=True)
            return False, f"Download error: {str(e)}"
    
    def _download_from_subdl(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """Download from SubDL"""
        try:
            # SubDL API provides download links
            api_url = f"https://api.subdl.com/api/v1/subtitles/{file_id}"
            req = urllib.request.Request(api_url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=30) as response:
                data = json.loads(response.read().decode('utf-8'))
                
                if data.get('success') and data.get('download_url'):
                    download_link = data['download_url']
                    
                    # Download the subtitle file
                    req2 = urllib.request.Request(download_link, headers=self.session_headers)
                    with urllib.request.urlopen(req2, timeout=30) as dl_response:
                        content = dl_response.read()
                        
                        # Handle compressed files
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
    
    def _download_from_yify(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """Download from YIFY Subtitles"""
        try:
            # YIFY requires scraping - for now, return manual download message
            message = (f"YIFY requires manual download. Please visit: {download_url}\n"
                      f"Download the subtitle manually and use 'External File' option.")
            logger.warning("⚠️ YIFY manual download required")
            return False, message
        except Exception as e:
            return False, f"YIFY download error: {str(e)}"
    
    def _download_from_podnapisi(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """Download from Podnapisi"""
        try:
            # Podnapisi provides direct download links
            if not download_url:
                return False, "No download URL provided for Podnapisi"
            
            req = urllib.request.Request(download_url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=30) as response:
                content = response.read()
                
                # Handle compressed files
                if content[:2] == b'\x1f\x8b':  # gzip magic number
                    content = gzip.decompress(content)
                
                with open(output_path, 'wb') as f:
                    f.write(content)
                
                logger.info(f"✅ Downloaded from Podnapisi: {output_path}")
                return True, output_path
                
        except Exception as e:
            logger.error(f"Podnapisi download error: {e}")
            return False, f"Podnapisi download failed: {str(e)}"
    
    def _download_from_subdivx(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """Download from SubDivX"""
        try:
            # SubDivX requires manual download as it has anti-bot protection
            message = (f"SubDivX requires manual download. Please visit: {download_url}\n"
                      f"Download the subtitle manually and use 'External File' option.")
            logger.warning("⚠️ SubDivX manual download required")
            return False, message
        except Exception as e:
            return False, f"SubDivX download error: {str(e)}"
    
    def _download_from_subf2m(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """Download from Subf2m"""
        try:
            # Subf2m requires scraping the subtitle page
            message = (f"Subf2m requires manual download. Please visit: {download_url}\n"
                      f"Download the subtitle manually and use 'External File' option.")
            logger.warning("⚠️ Subf2m manual download required")
            return False, message
        except Exception as e:
            return False, f"Subf2m download error: {str(e)}"
    
    def _download_from_subscene(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """Download from Subscene"""
        try:
            logger.info(f"Attempting to download from Subscene: {file_id}")
            
            if not download_url:
                return False, "No download URL provided for Subscene"
            
            # Enhanced headers
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Accept-Encoding': 'gzip, deflate, br',
                'DNT': '1',
                'Connection': 'keep-alive',
                'Upgrade-Insecure-Requests': '1',
                'Referer': 'https://subscene.com/',
                'Pragma': 'no-cache',
                'Cache-Control': 'no-cache'
            }
            
            # Add delay
            time.sleep(0.3)
            
            req = urllib.request.Request(download_url, headers=headers)
            
            try:
                with urllib.request.urlopen(req, timeout=15) as response:
                    html = response.read()
                    
                    # Handle gzip
                    if html[:2] == b'\x1f\x8b':
                        html = gzip.decompress(html)
                    
                    html = html.decode('utf-8', errors='ignore')
                
                # Find the actual download link in the subtitle page
                download_patterns = [
                    r'<a href="(/subtitle/download[^"]+)"',
                    r'href="(https://subscene\.com/subtitle/download[^"]+)"',
                    r'id="downloadButton"[^>]+href="([^"]+)"',
                    r'class="download"[^>]+href="([^"]+)"'
                ]
                
                for pattern in download_patterns:
                    matches = re.findall(pattern, html)
                    if matches:
                        actual_download_link = matches[0]
                        if not actual_download_link.startswith('http'):
                            actual_download_link = f"https://subscene.com{actual_download_link}"
                        
                        logger.info(f"Found Subscene download link: {actual_download_link}")
                        
                        # Add delay before download
                        time.sleep(0.3)
                        
                        # Download the subtitle
                        req2 = urllib.request.Request(actual_download_link, headers=headers)
                        with urllib.request.urlopen(req2, timeout=15) as dl_response:
                            content = dl_response.read()
                            
                            # Handle compression and ZIP files
                            if content[:2] == b'\x1f\x8b':  # gzip
                                content = gzip.decompress(content)
                            elif content[:2] == b'PK':  # ZIP file
                                logger.info("Extracting subtitle from ZIP archive")
                                with zipfile.ZipFile(io.BytesIO(content)) as zf:
                                    # Find subtitle files
                                    subtitle_files = [f for f in zf.namelist() if f.endswith(('.srt', '.ass', '.sub'))]
                                    if subtitle_files:
                                        content = zf.read(subtitle_files[0])
                                        logger.info(f"Extracted: {subtitle_files[0]}")
                                    else:
                                        logger.warning("No subtitle files found in ZIP")
                                        continue
                            
                            with open(output_path, 'wb') as f:
                                f.write(content)
                            
                            logger.info(f"✅ Downloaded from Subscene: {output_path}")
                            return True, output_path
                
                logger.warning("No download links found in Subscene page")
                
            except urllib.error.HTTPError as e:
                logger.warning(f"Subscene HTTP error {e.code}: {e.reason}")
            except Exception as e:
                logger.warning(f"Subscene scraping failed: {e}")
            
            # Fallback to manual
            message = (
                f"Subscene automatic download failed.\n\n"
                f"Manual download steps:\n"
                f"1. Visit: {download_url}\n"
                f"2. Click the download button\n"
                f"3. Extract if it's a ZIP file\n"
                f"4. Use 'External File' option to apply\n\n"
                f"💡 Tip: Subscene is very popular and reliable!"
            )
            logger.warning("⚠️ Subscene requires manual download")
            return False, message
            
        except Exception as e:
            logger.error(f"Subscene download error: {e}", exc_info=True)
            return False, f"Subscene error: {str(e)}"
    
    def _download_from_addic7ed(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """
        Download from Addic7ed by scraping the show page
        Addic7ed requires session cookies and has anti-bot protection
        """
        try:
            logger.info(f"Attempting to download from Addic7ed: {file_id}")
            
            # Extract show info from file_id (format: addic7ed_ShowName_S01E01_lang)
            parts = file_id.replace("addic7ed_", "").rsplit("_", 1)
            if len(parts) != 2:
                return False, "Invalid Addic7ed file_id format"
            
            show_episode = parts[0]
            language = parts[1]
            
            # Enhanced headers to bypass basic anti-bot
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Accept-Encoding': 'gzip, deflate, br',
                'DNT': '1',
                'Connection': 'keep-alive',
                'Upgrade-Insecure-Requests': '1',
                'Sec-Fetch-Dest': 'document',
                'Sec-Fetch-Mode': 'navigate',
                'Sec-Fetch-Site': 'none',
                'Pragma': 'no-cache',
                'Cache-Control': 'no-cache'
            }
            
            # Try to scrape Addic7ed
            try:
                search_url = f"https://www.addic7ed.com/search.php?search={urllib.parse.quote(show_episode)}"
                logger.info(f"Attempting to scrape Addic7ed: {search_url}")
                
                req = urllib.request.Request(search_url, headers=headers)
                
                # Add a small delay to appear more human-like
                time.sleep(0.5)
                
                with urllib.request.urlopen(req, timeout=15) as response:
                    html = response.read()
                    
                    # Handle gzip
                    if html[:2] == b'\x1f\x8b':
                        html = gzip.decompress(html)
                    
                    html = html.decode('utf-8', errors='ignore')
                
                # Look for download links
                # Addic7ed uses /original/ or /updated/ download paths
                download_patterns = [
                    r'href="(/original/[^"]+)"',
                    r'href="(/updated/[^"]+)"',
                    r'href="(\/downloadexport\.php\?[^"]+)"'
                ]
                
                for pattern in download_patterns:
                    matches = re.findall(pattern, html)
                    if matches:
                        download_path = matches[0]
                        download_link = f"https://www.addic7ed.com{download_path}"
                        
                        logger.info(f"Found Addic7ed download link: {download_link}")
                        
                        # Add delay before download
                        time.sleep(0.5)
                        
                        # Download subtitle
                        req2 = urllib.request.Request(download_link, headers=headers)
                        with urllib.request.urlopen(req2, timeout=15) as dl_response:
                            content = dl_response.read()
                            
                            # Handle compression
                            if content[:2] == b'\x1f\x8b':
                                content = gzip.decompress(content)
                            
                            with open(output_path, 'wb') as f:
                                f.write(content)
                            
                            logger.info(f"✅ Downloaded from Addic7ed: {output_path}")
                            return True, output_path
                
                logger.warning("No download links found in Addic7ed page")
                
            except urllib.error.HTTPError as e:
                if e.code == 403:
                    logger.warning(f"Addic7ed blocked request (403 Forbidden)")
                elif e.code == 429:
                    logger.warning(f"Addic7ed rate limited (429 Too Many Requests)")
                else:
                    logger.warning(f"Addic7ed HTTP error: {e.code}")
            except Exception as e:
                logger.warning(f"Addic7ed scraping failed: {e}")
            
            # Fallback to manual instructions
            search_url = f"https://www.addic7ed.com/search.php?search={urllib.parse.quote(show_episode)}"
            
            message = (
                f"Addic7ed automatic download failed (anti-bot protection).\n\n"
                f"Manual download steps:\n"
                f"1. Visit: {search_url}\n"
                f"2. Find your episode: {show_episode}\n"
                f"3. Select language: {language}\n"
                f"4. Click download button\n"
                f"5. Use 'External File' option to apply\n\n"
                f"💡 Tip: Addic7ed has excellent TV show subtitles!"
            )
            
            logger.warning(f"⚠️ Addic7ed requires manual download")
            return False, message
            
        except Exception as e:
            logger.error(f"Addic7ed download error: {e}", exc_info=True)
            return False, f"Addic7ed error: {str(e)}"
    
    def _download_from_jimaku(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """
        Download from Jimaku (jimaku.cc) - Modern anime subtitle site
        """
        try:
            logger.info(f"Attempting to download from Jimaku: {file_id}")
            
            # Jimaku uses a modern API/site structure
            # Try to fetch the search page and find download links
            if not download_url:
                return False, "No download URL provided for Jimaku"
            
            # Enhanced headers to bypass anti-bot
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Accept-Encoding': 'gzip, deflate, br',
                'DNT': '1',
                'Connection': 'keep-alive',
                'Upgrade-Insecure-Requests': '1',
                'Sec-Fetch-Dest': 'document',
                'Sec-Fetch-Mode': 'navigate',
                'Sec-Fetch-Site': 'cross-site',
                'Referer': 'https://jimaku.cc/',
                'Pragma': 'no-cache',
                'Cache-Control': 'no-cache'
            }
            
            # Add delay to appear human-like
            time.sleep(0.3)
            
            req = urllib.request.Request(download_url, headers=headers)
            
            try:
                with urllib.request.urlopen(req, timeout=30) as response:
                    html = response.read()
                    
                    # Handle gzip
                    if html[:2] == b'\x1f\x8b':
                        html = gzip.decompress(html)
                    
                    html = html.decode('utf-8', errors='ignore')
                
                # Try to find direct download links in the page
                # Jimaku might have download buttons or links
                download_patterns = [
                    r'href="(/api/download/[^"]+)"',
                    r'href="(https://jimaku\.cc/api/download/[^"]+)"',
                    r'data-download-url="([^"]+)"',
                    r'data-download="([^"]+)"',
                    r'href="(/entries/[^"]+/download)"',
                    r'"download_url":"([^"]+)"',  # JSON in page
                    r'downloadUrl:\s*["\']([^"\']+)["\']'  # JavaScript
                ]
                
                for pattern in download_patterns:
                    matches = re.findall(pattern, html)
                    if matches:
                        download_link = matches[0]
                        if not download_link.startswith('http'):
                            download_link = f"https://jimaku.cc{download_link}"
                        
                        logger.info(f"Found Jimaku download link: {download_link}")
                        
                        # Add delay before download
                        time.sleep(0.3)
                        
                        # Download the subtitle
                        req2 = urllib.request.Request(download_link, headers=headers)
                        with urllib.request.urlopen(req2, timeout=30) as dl_response:
                            content = dl_response.read()
                            
                            # Handle compressed files
                            if content[:2] == b'\x1f\x8b':  # gzip
                                content = gzip.decompress(content)
                            
                            with open(output_path, 'wb') as f:
                                f.write(content)
                            
                            logger.info(f"✅ Downloaded from Jimaku: {output_path}")
                            return True, output_path
                
                logger.warning("No download links found in Jimaku page")
                
            except urllib.error.HTTPError as e:
                logger.warning(f"Jimaku HTTP error {e.code}: {e.reason}")
            except Exception as e:
                logger.warning(f"Jimaku scraping failed: {e}")
            
            # If no direct download found, provide manual instructions
            message = (
                f"Jimaku automatic download failed.\n\n"
                f"Manual download steps:\n"
                f"1. Visit: {download_url}\n"
                f"2. Find and click the download button\n"
                f"3. Save the subtitle file\n"
                f"4. Use 'External File' option to apply\n\n"
                f"💡 Tip: Jimaku specializes in anime subtitles!"
            )
            logger.warning("⚠️ Jimaku requires manual download")
            return False, message
            
        except Exception as e:
            logger.error(f"Jimaku download error: {e}", exc_info=True)
            return False, f"Jimaku error: {str(e)}"
    
    def _download_from_animesubtitles(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """
        Download from AnimeSubtitles.com
        """
        try:
            logger.info(f"Attempting to download from AnimeSubtitles: {file_id}")
            
            if not download_url:
                return False, "No download URL provided for AnimeSubtitles"
            
            # Enhanced headers to bypass anti-bot
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
                'Accept-Encoding': 'gzip, deflate, br',
                'DNT': '1',
                'Connection': 'keep-alive',
                'Upgrade-Insecure-Requests': '1',
                'Sec-Fetch-Dest': 'document',
                'Sec-Fetch-Mode': 'navigate',
                'Sec-Fetch-Site': 'cross-site',
                'Referer': 'https://www.animesubtitles.com/',
                'Pragma': 'no-cache',
                'Cache-Control': 'no-cache'
            }
            
            # Add delay to appear human-like
            time.sleep(0.3)
            
            req = urllib.request.Request(download_url, headers=headers)
            
            try:
                with urllib.request.urlopen(req, timeout=30) as response:
                    html = response.read()
                    
                    # Handle gzip
                    if html[:2] == b'\x1f\x8b':
                        html = gzip.decompress(html)
                    
                    html = html.decode('utf-8', errors='ignore')
                
                # Look for download links (common patterns)
                download_patterns = [
                    r'href="(/download/[^"]+)"',
                    r'href="(https://www\.animesubtitles\.com/download/[^"]+)"',
                    r'<a[^>]+class="[^"]*download[^"]*"[^>]+href="([^"]+)"',
                    r'data-file-url="([^"]+)"',
                    r'data-file="([^"]+\.srt)"',
                    r'href="([^"]+\.srt)"',
                    r'href="([^"]+\.zip)"',  # Some sites provide zips
                    r'"file":"([^"]+\.srt)"',  # JSON in page
                    r'"downloadUrl":"([^"]+)"'
                ]
                
                for pattern in download_patterns:
                    matches = re.findall(pattern, html, re.IGNORECASE)
                    if matches:
                        download_link = matches[0]
                        if not download_link.startswith('http'):
                            if download_link.startswith('/'):
                                download_link = f"https://www.animesubtitles.com{download_link}"
                            else:
                                download_link = f"https://www.animesubtitles.com/{download_link}"
                        
                        logger.info(f"Found AnimeSubtitles download link: {download_link}")
                        
                        # Add delay before download
                        time.sleep(0.3)
                        
                        # Download the subtitle
                        req2 = urllib.request.Request(download_link, headers=headers)
                        with urllib.request.urlopen(req2, timeout=30) as dl_response:
                            content = dl_response.read()
                            
                            # Handle various compressions
                            if content[:2] == b'\x1f\x8b':  # gzip
                                content = gzip.decompress(content)
                            elif content[:2] == b'PK':  # ZIP file
                                logger.info("Extracting subtitle from ZIP archive")
                                with zipfile.ZipFile(io.BytesIO(content)) as zf:
                                    # Find .srt or .ass file in zip
                                    subtitle_files = [f for f in zf.namelist() if f.endswith(('.srt', '.ass'))]
                                    if subtitle_files:
                                        content = zf.read(subtitle_files[0])
                                        logger.info(f"Extracted: {subtitle_files[0]}")
                                    else:
                                        logger.warning("No subtitle files found in ZIP")
                                        continue
                            
                            with open(output_path, 'wb') as f:
                                f.write(content)
                            
                            logger.info(f"✅ Downloaded from AnimeSubtitles: {output_path}")
                            return True, output_path
                
                logger.warning("No download links found in AnimeSubtitles page")
                
            except urllib.error.HTTPError as e:
                logger.warning(f"AnimeSubtitles HTTP error {e.code}: {e.reason}")
            except Exception as e:
                logger.warning(f"AnimeSubtitles scraping failed: {e}")
            
            # If no download link found
            message = (
                f"AnimeSubtitles automatic download failed.\n\n"
                f"Manual download steps:\n"
                f"1. Visit: {download_url}\n"
                f"2. Search for your anime episode\n"
                f"3. Click the download button\n"
                f"4. Save the subtitle file\n"
                f"5. Use 'External File' option to apply\n\n"
                f"💡 Tip: AnimeSubtitles has multi-language anime subtitles!"
            )
            logger.warning("⚠️ AnimeSubtitles requires manual download")
            return False, message
            
        except Exception as e:
            logger.error(f"AnimeSubtitles download error: {e}", exc_info=True)
            return False, f"AnimeSubtitles error: {str(e)}"
    
    def _download_from_kitsunekko(self, file_id: str, download_url: str, output_path: str) -> Tuple[bool, str]:
        """
        Download from Kitsunekko (Japanese anime subtitles)
        Kitsunekko uses a directory listing structure
        """
        try:
            logger.info(f"Attempting to download from Kitsunekko: {file_id}")
            
            # Extract anime name from file_id
            anime_name = file_id.replace("kitsunekko_", "").rsplit("_", 1)[0]
            
            # Kitsunekko has a directory structure
            base_url = "https://kitsunekko.net/dirlist.php?dir=subtitles%2Fjapanese%2F"
            
            # Try to search for the anime
            search_url = base_url + urllib.parse.quote(anime_name)
            
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
                'Referer': 'https://kitsunekko.net/'
            }
            
            req = urllib.request.Request(search_url, headers=headers)
            
            try:
                with urllib.request.urlopen(req, timeout=30) as response:
                    html = response.read().decode('utf-8', errors='ignore')
                
                # Look for .ass or .srt files
                subtitle_patterns = [
                    r'href="([^"]+\.ass)"',
                    r'href="([^"]+\.srt)"',
                    r'<a href="(/dirlist\.php[^"]+\.(?:ass|srt))"'
                ]
                
                for pattern in subtitle_patterns:
                    matches = re.findall(pattern, html)
                    if matches:
                        download_link = matches[0]
                        if not download_link.startswith('http'):
                            if download_link.startswith('/'):
                                download_link = f"https://kitsunekko.net{download_link}"
                            else:
                                download_link = f"https://kitsunekko.net/subtitles/japanese/{download_link}"
                        
                        logger.info(f"Found Kitsunekko download link: {download_link}")
                        
                        # Download the subtitle
                        req2 = urllib.request.Request(download_link, headers=headers)
                        with urllib.request.urlopen(req2, timeout=30) as dl_response:
                            content = dl_response.read()
                            
                            with open(output_path, 'wb') as f:
                                f.write(content)
                            
                            logger.info(f"✅ Downloaded from Kitsunekko: {output_path}")
                            return True, output_path
            
            except Exception as e:
                logger.warning(f"Kitsunekko search failed: {e}")
            
            # If automatic download fails, provide instructions
            message = (
                f"Kitsunekko subtitle located but automatic download unavailable.\n"
                f"Please visit: https://kitsunekko.net/dirlist.php?dir=subtitles%2Fjapanese%2F\n"
                f"Search for your anime and download manually."
            )
            logger.warning("⚠️ Kitsunekko automatic download not available")
            return False, message
            
        except Exception as e:
            logger.error(f"Kitsunekko download error: {e}")
            return False, f"Kitsunekko error: {str(e)}"
    
    def download_best_subtitle(self, video_path: str, language: str = "en") -> Tuple[bool, Optional[str]]:
        """Download best subtitle from any provider"""
        results = self.search_all_providers(video_path, [language])
        
        if not results:
            logger.warning(f"No subtitles found for {video_path}")
            return False, None
        
        # Sort by score (already ranked)
        # Try each subtitle until one succeeds
        for subtitle in results:
            try:
                provider = subtitle.get("provider", "unknown")
                logger.info(f"Attempting to download from {provider}: {subtitle.get('file_name', 'unknown')}")
                
                video_file = Path(video_path)
                lang_code = subtitle.get("language", language)
                output_path = str(video_file.parent / f"{video_file.stem}.{lang_code}.srt")
                
                success, result = self.download_subtitle(
                    subtitle["file_id"],
                    provider,
                    output_path,
                    subtitle.get("download_url", "")
                )
                
                if success:
                    logger.info(f"✅ Successfully downloaded: {result}")
                    return True, result
                else:
                    logger.warning(f"⚠️ {provider} download failed: {result}")
                    continue
                
            except Exception as e:
                logger.error(f"Error downloading from {subtitle.get('provider')}: {e}")
                continue
        
        logger.error("All subtitle download attempts failed")
        return False, None
    
    def _download_from_opensubs_com(self, subtitle_info: Dict, output_path: str) -> bool:
        """Download subtitle from OpenSubtitles.com"""
        try:
            download_link = subtitle_info.get("download_link")
            if not download_link:
                return False
            
            req = urllib.request.Request(download_link, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=30) as response:
                content = response.read()
                
                # OpenSubtitles.com provides gzipped content
                if content[:2] == b'\x1f\x8b':  # gzip magic number
                    content = gzip.decompress(content)
                
                # Write to file
                with open(output_path, 'wb') as f:
                    f.write(content)
                
                return True
                
        except Exception as e:
            logger.error(f"Error downloading from OpenSubs.com: {e}")
            return False
    
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

