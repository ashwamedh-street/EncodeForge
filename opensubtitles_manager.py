#!/usr/bin/env python3
"""
OpenSubtitles Manager - Handles subtitle download from OpenSubtitles.com API
"""

import hashlib
import json
import logging
import struct
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


class OpenSubtitlesManager:
    """
    Manages subtitle downloads from OpenSubtitles.com
    Uses the OpenSubtitles REST API v1
    """
    
    API_URL = "https://api.opensubtitles.com/api/v1"
    USER_AGENT = "FFmpegBatchTranscoder v2.0"
    
    def __init__(self, api_key: str = "", username: str = "", password: str = ""):
        self.api_key = api_key
        self.username = username
        self.password = password
        self.auth_token = None
    
    def login(self) -> Tuple[bool, str]:
        """
        Login to OpenSubtitles API
        
        Returns:
            (success, message/token)
        """
        if not self.username or not self.password:
            return False, "Username and password required"
        
        try:
            # Prepare login data
            login_data = {
                "username": self.username,
                "password": self.password
            }
            
            # Make request
            headers = {
                "Content-Type": "application/json",
                "User-Agent": self.USER_AGENT
            }
            
            if self.api_key:
                headers["Api-Key"] = self.api_key
            
            request = urllib.request.Request(
                f"{self.API_URL}/login",
                data=json.dumps(login_data).encode('utf-8'),
                headers=headers,
                method="POST"
            )
            
            with urllib.request.urlopen(request, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if "token" in data:
                self.auth_token = data["token"]
                logger.info("Successfully logged in to OpenSubtitles")
                return True, self.auth_token
            else:
                return False, "Login failed: No token received"
        
        except urllib.error.HTTPError as e:
            error_msg = f"HTTP {e.code}: {e.reason}"
            try:
                error_data = json.loads(e.read().decode())
                error_msg = error_data.get("message", error_msg)
            except:
                pass
            logger.error(f"Login error: {error_msg}")
            return False, error_msg
        
        except Exception as e:
            logger.error(f"Login error: {e}")
            return False, str(e)
    
    def calculate_file_hash(self, file_path: str) -> Optional[str]:
        """
        Calculate OpenSubtitles hash for a video file
        
        The hash is based on file size and first/last 64KB of the file
        """
        try:
            longlongformat = '<q'  # little-endian long long
            bytesize = struct.calcsize(longlongformat)
            
            with open(file_path, "rb") as f:
                filesize = Path(file_path).stat().st_size
                hash_value = filesize
                
                if filesize < 65536 * 2:
                    return None
                
                # Read first 64KB
                for _ in range(65536 // bytesize):
                    buffer = f.read(bytesize)
                    (l_value,) = struct.unpack(longlongformat, buffer)
                    hash_value += l_value
                    hash_value &= 0xFFFFFFFFFFFFFFFF  # 64-bit
                
                # Read last 64KB
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
    
    def search_subtitles(
        self,
        file_path: str,
        languages: Optional[List[str]] = None,
        limit: int = 5
    ) -> Tuple[bool, List[Dict]]:
        """
        Search for subtitles for a video file
        
        Args:
            file_path: Path to video file
            languages: List of language codes (e.g., ['en', 'es'])
            limit: Maximum number of results
            
        Returns:
            (success, list of subtitle dicts)
        """
        if languages is None:
            languages = ['en']
        
        try:
            # Auto-login if we have credentials but no token
            if self.username and self.password and not self.auth_token:
                logger.info("Auto-logging in to OpenSubtitles.com...")
                success, msg = self.login()
                if not success:
                    logger.warning(f"Auto-login failed: {msg}")
            
            # Calculate file hash
            file_hash = self.calculate_file_hash(file_path)
            file_size = Path(file_path).stat().st_size
            
            if not file_hash:
                logger.error("Failed to calculate file hash")
                return False, []
            
            logger.info(f"File hash: {file_hash}, size: {file_size}")
            
            # Prepare search parameters
            params = {
                "moviehash": file_hash,
                "languages": ",".join(languages)
            }
            
            # Build URL
            url = f"{self.API_URL}/subtitles?{urllib.parse.urlencode(params)}"
            logger.info(f"Searching OpenSubtitles.com API: {url}")
            
            # Prepare headers
            headers = {
                "User-Agent": self.USER_AGENT,
                "Accept": "application/json"
            }
            
            if self.api_key:
                headers["Api-Key"] = self.api_key
                logger.info("Using API key for authentication")
            
            if self.auth_token:
                headers["Authorization"] = f"Bearer {self.auth_token}"
                logger.info("Using Bearer token for authentication")
            
            # Make request
            request = urllib.request.Request(url, headers=headers)
            
            with urllib.request.urlopen(request, timeout=15) as response:
                data = json.loads(response.read().decode())
            
            logger.info(f"OpenSubtitles.com API response received: {len(str(data))} chars")
            
            # Parse results
            results = []
            
            if "data" in data:
                for item in data["data"][:limit]:
                    attributes = item.get("attributes", {})
                    files = attributes.get("files", [])
                    
                    if files:
                        file_info = files[0]
                        results.append({
                            "id": item.get("id"),
                            "language": attributes.get("language"),
                            "file_name": file_info.get("file_name"),
                            "file_id": file_info.get("file_id"),
                            "download_url": attributes.get("url"),
                            "downloads": attributes.get("download_count", 0),
                            "rating": attributes.get("ratings", 0),
                            "uploader": attributes.get("uploader", {}).get("name", "Unknown"),
                            "format": "srt"
                        })
            
            logger.info(f"Found {len(results)} subtitle(s) for {Path(file_path).name}")
            return True, results
        
        except urllib.error.HTTPError as e:
            logger.error(f"HTTP Error searching subtitles: {e.code} {e.reason}")
            try:
                error_body = e.read().decode()
                logger.error(f"Error body: {error_body}")
                
                # Check for API key permission issues
                if "cannot consume" in error_body.lower() or e.code == 403:
                    logger.warning("OpenSubtitles API key may not have sufficient permissions")
                    logger.warning("Consider upgrading your OpenSubtitles account or using free providers")
            except:
                pass
            return False, []
        except Exception as e:
            logger.error(f"Error searching subtitles: {e}", exc_info=True)
            return False, []
    
    def download_subtitle(
        self,
        file_id: int,
        output_path: str
    ) -> Tuple[bool, str]:
        """
        Download a subtitle file
        
        Args:
            file_id: OpenSubtitles file ID
            output_path: Where to save the subtitle
            
        Returns:
            (success, message)
        """
        try:
            # Get download link
            url = f"{self.API_URL}/download"
            
            download_data = {
                "file_id": file_id
            }
            
            headers = {
                "Content-Type": "application/json",
                "User-Agent": self.USER_AGENT,
                "Accept": "application/json"
            }
            
            if self.api_key:
                headers["Api-Key"] = self.api_key
            
            if self.auth_token:
                headers["Authorization"] = f"Bearer {self.auth_token}"
            
            request = urllib.request.Request(
                url,
                data=json.dumps(download_data).encode('utf-8'),
                headers=headers,
                method="POST"
            )
            
            with urllib.request.urlopen(request, timeout=15) as response:
                data = json.loads(response.read().decode())
            
            download_url = data.get("link")
            
            if not download_url:
                return False, "No download link received"
            
            # Download the file
            urllib.request.urlretrieve(download_url, output_path)
            
            logger.info(f"Downloaded subtitle to {output_path}")
            return True, f"Subtitle downloaded successfully"
        
        except Exception as e:
            logger.error(f"Error downloading subtitle: {e}")
            return False, str(e)
    
    def download_subtitles_for_file(
        self,
        video_path: str,
        languages: Optional[List[str]] = None,
        auto_select: bool = True
    ) -> Tuple[bool, List[str]]:
        """
        Search and download subtitles for a video file
        
        Args:
            video_path: Path to video file
            languages: List of language codes
            auto_select: Automatically select best subtitle
            
        Returns:
            (success, list of downloaded subtitle paths)
        """
        if languages is None:
            languages = ['en']
        
        # Search for subtitles
        success, results = self.search_subtitles(video_path, languages)
        
        if not success or not results:
            return False, []
        
        downloaded_files = []
        
        # Download subtitles for each language
        for language in languages:
            # Find best subtitle for this language
            lang_results = [r for r in results if r["language"] == language]
            
            if not lang_results:
                continue
            
            # Sort by rating and downloads
            lang_results.sort(
                key=lambda x: (x["rating"], x["downloads"]),
                reverse=True
            )
            
            best_subtitle = lang_results[0]
            
            # Determine output path
            video_file = Path(video_path)
            output_path = video_file.parent / f"{video_file.stem}.{language}.srt"
            
            # Download
            success, message = self.download_subtitle(
                best_subtitle["file_id"],
                str(output_path)
            )
            
            if success:
                downloaded_files.append(str(output_path))
        
        if downloaded_files:
            return True, downloaded_files
        else:
            return False, []


def main():
    """Test the OpenSubtitles manager"""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python opensubtitles_manager.py <video_file>")
        print("\nOptional environment variables:")
        print("  OPENSUBTITLES_API_KEY - API key for better rate limits")
        print("  OPENSUBTITLES_USERNAME - Username for authentication")
        print("  OPENSUBTITLES_PASSWORD - Password for authentication")
        return
    
    import os
    
    video_file = sys.argv[1]
    
    api_key = os.getenv("OPENSUBTITLES_API_KEY", "")
    username = os.getenv("OPENSUBTITLES_USERNAME", "")
    password = os.getenv("OPENSUBTITLES_PASSWORD", "")
    
    manager = OpenSubtitlesManager(api_key, username, password)
    
    # Login if credentials provided
    if username and password:
        print("Logging in to OpenSubtitles...")
        success, message = manager.login()
        
        if success:
            print(f"✅ Logged in successfully")
        else:
            print(f"❌ Login failed: {message}")
            print("Continuing without authentication (limited features)")
    
    # Search for subtitles
    print(f"\nSearching subtitles for: {Path(video_file).name}")
    success, results = manager.search_subtitles(video_file, ["en", "es"])
    
    if success and results:
        print(f"\n✅ Found {len(results)} subtitle(s):")
        
        for i, result in enumerate(results, 1):
            print(f"\n{i}. {result['file_name']}")
            print(f"   Language: {result['language']}")
            print(f"   Downloads: {result['downloads']}")
            print(f"   Rating: {result['rating']}")
            print(f"   Uploader: {result['uploader']}")
    else:
        print("❌ No subtitles found")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()

