#!/usr/bin/env python3
"""
Base Provider for Metadata
Common interface and utilities for all metadata providers
"""

import logging
import re
import time
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, Optional, Tuple

logger = logging.getLogger(__name__)


class BaseMetadataProvider(ABC):
    """Abstract base class for all metadata providers."""

    def __init__(self, api_key: str = ""):
        self.api_key = api_key
        self.last_request_time = 0

    @abstractmethod
    def search_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """
        Search for movie metadata.
        Each concrete provider must implement this.
        
        Returns dict with: title, year, overview, rating, source
        """
        pass

    @abstractmethod
    def search_tv(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """
        Search for TV show metadata.
        Each concrete provider must implement this.
        
        Returns dict with: show_title, show_year, season, episode, episode_title, overview, source
        """
        pass

    def validate_api_key(self) -> Tuple[bool, str]:
        """
        Validate API key.
        Override in subclasses if API key validation is needed.
        """
        if not self.api_key:
            return False, "No API key provided"
        return True, "API key present (validation not implemented)"

    def _rate_limit(self, min_interval: float = 0.25):
        """Simple rate limiting to avoid hammering APIs"""
        current_time = time.time()
        time_since_last = current_time - self.last_request_time
        if time_since_last < min_interval:
            time.sleep(min_interval - time_since_last)
        self.last_request_time = time.time()

    def detect_media_type(self, filename: str) -> str:
        """
        Detect if file is a movie or TV show
        
        Returns: "movie", "tv", or "unknown"
        """
        # TV show patterns
        tv_patterns = [
            r'[Ss](\d+)[Ee](\d+)',  # S01E01
            r'(\d+)x(\d+)',  # 1x01
            r'[Ee]pisode\s*(\d+)',  # Episode 01
            r'\[(\d+)\]',  # [01]
        ]
        
        for pattern in tv_patterns:
            if re.search(pattern, filename):
                return "tv"
        
        # Movie year pattern
        if re.search(r'\(?\d{4}\)?', filename):
            return "movie"
        
        return "unknown"

    def parse_tv_filename(self, filename: str) -> Optional[Dict]:
        """
        Parse TV show filename to extract information
        
        Returns dict with: title, season, episode, or None
        """
        # Remove extension
        name = Path(filename).stem
        
        # Common TV patterns
        patterns = [
            r'(?P<title>.+?)[.\s_-]+[Ss](?P<season>\d+)[Ee](?P<episode>\d+)',
            r'(?P<title>.+?)[.\s_-]+(?P<season>\d+)x(?P<episode>\d+)',
            r'(?P<title>.+?)[.\s_-]+[Ee]pisode\s*(?P<episode>\d+)',
        ]
        
        for pattern in patterns:
            match = re.search(pattern, name, re.IGNORECASE)
            if match:
                result = match.groupdict()
                
                # Clean title
                title = result['title'].replace('.', ' ').replace('_', ' ').strip()
                title = re.sub(r'\s+', ' ', title)
                
                return {
                    "type": "tv",
                    "title": title,
                    "season": int(result.get('season', 1)),
                    "episode": int(result['episode']),
                    "original": filename
                }
        
        return None

    def parse_movie_filename(self, filename: str) -> Optional[Dict]:
        """
        Parse movie filename to extract information
        
        Returns dict with: title, year, or None
        """
        # Remove extension
        name = Path(filename).stem
        
        # Movie pattern with year
        pattern = r'(?P<title>.+?)[.\s_-]+\(?(?P<year>\d{4})\)?'
        match = re.search(pattern, name)
        
        if match:
            result = match.groupdict()
            
            # Clean title
            title = result['title'].replace('.', ' ').replace('_', ' ').strip()
            title = re.sub(r'\s+', ' ', title)
            
            return {
                "type": "movie",
                "title": title,
                "year": int(result['year']),
                "original": filename
            }
        
        # Try without year
        title = name.replace('.', ' ').replace('_', ' ').strip()
        title = re.sub(r'\s+', ' ', title)
        
        return {
            "type": "movie",
            "title": title,
            "year": None,
            "original": filename
        }

    def is_anime(self, title: str) -> bool:
        """Check if title is likely anime"""
        anime_keywords = [
            'anime', 'naruto', 'attack on titan', 'bleach', 'one piece', 
            'dragon ball', 'demon slayer', 'jujutsu kaisen', 'my hero academia',
            'one punch man', 'death note', 'fullmetal', 'sword art online',
            'ghibli', 'pokemon', 'evangelion', 'akira', 'spirited away', 
            'your name', 'weathering with you'
        ]
        return any(word in title.lower() for word in anime_keywords)

