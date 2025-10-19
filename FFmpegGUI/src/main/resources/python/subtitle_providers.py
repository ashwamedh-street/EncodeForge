#!/usr/bin/env python3
"""
Multi-provider subtitle search and download
Supports: OpenSubtitles, OpenSubtitles.com, Podnapisi, SubDivX, YIFY Subtitles
"""

import gzip
import hashlib
import json
import logging
import re
import struct
import time
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
        """Convert 3-letter language code to full name"""
        lang_map = {
            "eng": "English",
            "spa": "Spanish",
            "fre": "French",
            "ger": "German",
            "ita": "Italian",
            "por": "Portuguese",
            "rus": "Russian",
            "ara": "Arabic",
            "chi": "Chinese",
            "jpn": "Japanese",
            "kor": "Korean"
        }
        return lang_map.get(lang_code, lang_code)
    
    def search_addic7ed(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search Addic7ed (addic7ed.com) - Excellent for TV shows
        Requires user agent and may have rate limits
        """
        results = []
        
        try:
            file_name = Path(video_path).stem
            
            # Check if it's a TV show
            tv_match = re.search(r'S(\d+)E(\d+)', file_name, re.IGNORECASE)
            if not tv_match:
                logger.info("Addic7ed skipped - not a TV show (requires S##E## pattern)")
                return results
            
            season = tv_match.group(1)
            episode = tv_match.group(2)
            
            # Extract show name
            show_name = file_name.split(tv_match.group(0))[0]
            show_name = re.sub(r'[._-]+', ' ', show_name).strip()
            
            logger.info(f"Searching Addic7ed for: {show_name} S{season}E{episode}")
            
            # Addic7ed requires session cookies and specific scraping
            # For now, return placeholder structure
            for lang in languages:
                results.append({
                    "provider": "Addic7ed",
                    "file_name": f"{show_name}.S{season}E{episode}.{lang}.srt",
                    "language": lang,
                    "downloads": 0,
                    "rating": 0.0,
                    "file_id": f"addic7ed_{show_name}_S{season}E{episode}_{lang}",
                    "download_url": "https://www.addic7ed.com/",
                    "movie_name": f"{show_name} S{season}E{episode}",
                    "format": "srt"
                })
            
            logger.info(f"Addic7ed prepared {len(results)} placeholder result(s)")
            
        except Exception as e:
            logger.error(f"Error searching Addic7ed: {e}")
            
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
    
    def _lang_name_to_code(self, lang_name: str) -> str:
        """Convert full language name to 3-letter code"""
        lang_map = {
            "english": "eng",
            "spanish": "spa",
            "french": "fre",
            "german": "ger",
            "italian": "ita",
            "portuguese": "por",
            "russian": "rus",
            "arabic": "ara",
            "chinese": "chi",
            "japanese": "jpn",
            "korean": "kor"
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
        
        logger.info(f"=== SEARCH COMPLETE: Total {len(all_results)} subtitle(s) from {len(set(r.get('provider', 'unknown') for r in all_results))} provider(s) ===")
        
        if all_results:
            # Log provider breakdown
            provider_counts = {}
            for r in all_results:
                provider = r.get('provider', 'unknown')
                provider_counts[provider] = provider_counts.get(provider, 0) + 1
            logger.info("Provider breakdown:")
            for provider, count in provider_counts.items():
                logger.info(f"  - {provider}: {count}")
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
    
    def download_best_subtitle(self, video_path: str, language: str = "en") -> Tuple[bool, Optional[str]]:
        """Download best subtitle from any provider"""
        results = self.search_all_providers(video_path, [language])
        
        if not results:
            logger.warning(f"No subtitles found for {video_path}")
            return False, None
        
        # Sort by rating and downloads
        results.sort(key=lambda x: (x.get("rating", 0), x.get("downloads", 0)), reverse=True)
        
        # Try each subtitle until one succeeds
        for subtitle in results:
            try:
                provider = subtitle.get("provider", "unknown")
                logger.info(f"Attempting to download from {provider}: {subtitle.get('file_name', 'unknown')}")
                
                video_file = Path(video_path)
                output_path = str(video_file.parent / f"{video_file.stem}.{language}.srt")
                
                if provider == "opensubtitles":
                    success, message = self.opensubtitles.download_subtitle(
                        subtitle["file_id"],
                        output_path
                    )
                    if success:
                        logger.info(f"Successfully downloaded from OpenSubtitles: {output_path}")
                        return True, output_path
                        
                elif provider == "opensubs_com":
                    # Download from OpenSubtitles.com REST API
                    success = self._download_from_opensubs_com(subtitle, output_path)
                    if success:
                        logger.info(f"Successfully downloaded from OpenSubs.com: {output_path}")
                        return True, output_path
                
                # Add more provider downloads here as implemented
                
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

