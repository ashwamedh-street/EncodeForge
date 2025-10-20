#!/usr/bin/env python3
"""
Jikan Provider (MyAnimeList Mirror)
No API key required - Good for anime
"""

import json
import logging
import urllib.parse
import urllib.request
from typing import Dict, Optional

from .base_provider import BaseMetadataProvider

logger = logging.getLogger(__name__)


class JikanProvider(BaseMetadataProvider):
    """Jikan API (MyAnimeList mirror) provider"""

    API_URL = "https://api.jikan.moe/v4"

    def __init__(self):
        super().__init__()
        logger.info("JikanProvider initialized")

    def search_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """Search anime movie using Jikan API"""
        try:
            self._rate_limit(0.5)  # Jikan has stricter rate limits
            
            params = {"q": title, "limit": "1", "type": "movie"}
            url = f"{self.API_URL}/anime?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data.get("data") and len(data["data"]) > 0:
                anime = data["data"][0]
                
                return {
                    "title": anime.get("title_english") or anime.get("title", ""),
                    "year": str(anime.get("year", "")) if anime.get("year") else "",
                    "overview": anime.get("synopsis", "")[:200] + "..." if anime.get("synopsis") else "",
                    "rating": float(anime.get("score", 0)) if anime.get("score") else 0,
                    "source": "jikan"
                }
        except Exception as e:
            logger.error(f"Jikan movie search error: {e}")
        
        return None

    def search_tv(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search anime using Jikan API (MyAnimeList mirror - no key required)"""
        try:
            self._rate_limit(0.5)  # Jikan has stricter rate limits
            
            # Search for anime
            params = {"q": title, "limit": "1", "type": "tv"}
            url = f"{self.API_URL}/anime?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data.get("data") and len(data["data"]) > 0:
                anime = data["data"][0]
                
                return {
                    "show_title": anime.get("title_english") or anime.get("title", ""),
                    "show_year": str(anime.get("year", "")) if anime.get("year") else "",
                    "season": season,
                    "episode": episode,
                    "episode_title": f"Episode {episode}",
                    "overview": anime.get("synopsis", "")[:200] + "..." if anime.get("synopsis") else "",
                    "source": "jikan"
                }
        except Exception as e:
            logger.error(f"Jikan search error: {e}")
        
        return None

