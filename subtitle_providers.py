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
        Search OpenSubtitles.com (the new free API, no key required)
        This is different from OpenSubtitles.org
        """
        results = []
        
        try:
            # Calculate file hash
            file_hash = self.calculate_hash(video_path)
            file_size = Path(video_path).stat().st_size
            
            if not file_hash:
                logger.warning("Could not calculate file hash for OpenSubs.com search")
                return results
            
            # OpenSubtitles.com REST API (no auth required for search)
            url = f"https://rest.opensubtitles.org/search/moviehash-{file_hash}/sublanguageid-{','.join(languages)}"
            
            req = urllib.request.Request(url, headers=self.session_headers)
            
            with urllib.request.urlopen(req, timeout=10) as response:
                data = json.loads(response.read().decode('utf-8'))
                
                if isinstance(data, list):
                    for item in data[:10]:  # Limit to top 10 results
                        results.append({
                            "provider": "opensubs_com",
                            "file_name": item.get("SubFileName", "Unknown"),
                            "language": item.get("SubLanguageID", "unknown"),
                            "downloads": int(item.get("SubDownloadsCnt", 0)),
                            "rating": float(item.get("SubRating", 0)),
                            "file_id": item.get("IDSubtitleFile", ""),
                            "download_link": item.get("SubDownloadLink", ""),
                            "movie_name": item.get("MovieName", ""),
                            "format": item.get("SubFormat", "srt")
                        })
                        
        except Exception as e:
            logger.error(f"Error searching OpenSubs.com: {e}")
            
        return results
    
    def search_yifysubtitles(self, video_path: str, languages: List[str]) -> List[Dict]:
        """
        Search YIFY Subtitles (yifysubtitles.org) - Free, no API key needed
        Good for movies
        """
        results = []
        
        try:
            # Extract movie name from filename
            file_name = Path(video_path).stem
            # Clean up the name - remove year, quality tags, etc.
            movie_name = re.sub(r'\b(19|20)\d{2}\b', '', file_name)  # Remove year
            movie_name = re.sub(r'\b(720p|1080p|2160p|4K|BluRay|WEB-DL|WEBRip|HDTV|x264|x265|HEVC)\b', '', movie_name, flags=re.IGNORECASE)
            movie_name = re.sub(r'[._-]+', ' ', movie_name).strip()
            
            # Search YIFY (note: this is a simplified example, actual implementation would need web scraping)
            # For now, we'll return empty results but the structure is ready
            logger.info(f"YIFY search prepared for: {movie_name}")
            
            # TODO: Implement actual YIFY scraping when needed
            # YIFY requires web scraping as they don't have a public API
            
        except Exception as e:
            logger.error(f"Error searching YIFY Subtitles: {e}")
            
        return results
    
    def search_podnapisi(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search Podnapisi.NET (Slovenian subtitle site with good English content)"""
        results = []
        
        try:
            # Podnapisi has an unofficial API
            file_name = Path(video_path).stem
            # Clean filename
            search_term = re.sub(r'[._-]+', ' ', file_name).strip()
            
            # Podnapisi.NET search endpoint (unofficial)
            # For now, return empty - would require implementation of their search protocol
            logger.info(f"Podnapisi search prepared for: {search_term}")
            
            # TODO: Implement Podnapisi search when needed
            
        except Exception as e:
            logger.error(f"Error searching Podnapisi: {e}")
            
        return results
    
    def search_subdivx(self, video_path: str, languages: List[str]) -> List[Dict]:
        """Search SubDivX (Spanish subtitles - largest Spanish subtitle database)"""
        results = []
        
        try:
            # SubDivX is primarily for Spanish language content
            if "spa" not in languages and "es" not in languages:
                return results
            
            file_name = Path(video_path).stem
            search_term = re.sub(r'[._-]+', ' ', file_name).strip()
            
            logger.info(f"SubDivX search prepared for: {search_term}")
            
            # TODO: Implement SubDivX scraping when needed
            # SubDivX requires web scraping
            
        except Exception as e:
            logger.error(f"Error searching SubDivX: {e}")
            
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
        
        # OpenSubtitles.org REST API (free, no key required)
        logger.info("→ Searching OpenSubtitles.org (free API)...")
        try:
            opensubs_results = self.search_opensubs_com(video_path, lang_codes)
            logger.info(f"  OpenSubtitles.org returned {len(opensubs_results)} results")
            all_results.extend(opensubs_results)
            if opensubs_results:
                logger.info(f"  ✅ OpenSubtitles.org: Found {len(opensubs_results)} subtitles")
            else:
                logger.info(f"  ⚠️ OpenSubtitles.org: No results")
        except Exception as e:
            logger.error(f"  ❌ OpenSubtitles.org search failed: {e}", exc_info=True)
        
        # YIFY Subtitles (good for movies)
        logger.info("→ Searching YIFY Subtitles...")
        try:
            yify_results = self.search_yifysubtitles(video_path, lang_codes)
            logger.info(f"  YIFY returned {len(yify_results)} results")
            all_results.extend(yify_results)
            if yify_results:
                logger.info(f"  ✅ YIFY: Found {len(yify_results)} subtitles")
            else:
                logger.info(f"  ⚠️ YIFY: No results")
        except Exception as e:
            logger.error(f"  ❌ YIFY search failed: {e}", exc_info=True)
        
        # Podnapisi
        logger.info("→ Searching Podnapisi...")
        try:
            podnapisi_results = self.search_podnapisi(video_path, lang_codes)
            logger.info(f"  Podnapisi returned {len(podnapisi_results)} results")
            all_results.extend(podnapisi_results)
            if podnapisi_results:
                logger.info(f"  ✅ Podnapisi: Found {len(podnapisi_results)} subtitles")
            else:
                logger.info(f"  ⚠️ Podnapisi: No results")
        except Exception as e:
            logger.error(f"  ❌ Podnapisi search failed: {e}", exc_info=True)
        
        # SubDivX (Spanish)
        logger.info("→ Searching SubDivX...")
        try:
            subdivx_results = self.search_subdivx(video_path, lang_codes)
            logger.info(f"  SubDivX returned {len(subdivx_results)} results")
            all_results.extend(subdivx_results)
            if subdivx_results:
                logger.info(f"  ✅ SubDivX: Found {len(subdivx_results)} subtitles")
            else:
                logger.info(f"  ⚠️ SubDivX: No results")
        except Exception as e:
            logger.error(f"  ❌ SubDivX search failed: {e}", exc_info=True)
        
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

