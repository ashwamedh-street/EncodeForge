#!/usr/bin/env python3
"""
Kitsu Provider
No API key required - Good for anime
"""

import json
import logging
import urllib.parse
import urllib.request
from typing import Dict, Optional

from .base_provider import BaseMetadataProvider

logger = logging.getLogger(__name__)


class KitsuProvider(BaseMetadataProvider):
    """Kitsu (anime database) provider"""

    API_URL = "https://kitsu.io/api/edge"

    def __init__(self):
        super().__init__()
        logger.info("KitsuProvider initialized")

    def search_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """Search anime movie using Kitsu API"""
        try:
            self._rate_limit()
            
            params = {"filter[text]": title, "filter[subtype]": "movie", "page[limit]": "1"}
            url = f"{self.API_URL}/anime?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data.get("data") and len(data["data"]) > 0:
                anime = data["data"][0]["attributes"]
                
                return {
                    "title": anime.get("canonicalTitle", "") or anime.get("titles", {}).get("en", ""),
                    "year": anime.get("startDate", "")[:4] if anime.get("startDate") else "",
                    "overview": anime.get("synopsis", "")[:200] + "..." if anime.get("synopsis") else "",
                    "rating": float(anime.get("averageRating", 0)) / 10.0 if anime.get("averageRating") else 0,
                    "source": "kitsu"
                }
        except Exception as e:
            logger.error(f"Kitsu movie search error: {e}")
        
        return None

    def search_tv(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search anime using Kitsu API (no key required)"""
        try:
            self._rate_limit()
            
            # Search for anime
            params = {"filter[text]": title, "page[limit]": "1"}
            url = f"{self.API_URL}/anime?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data.get("data") and len(data["data"]) > 0:
                anime = data["data"][0]["attributes"]
                
                return {
                    "show_title": anime.get("canonicalTitle", "") or anime.get("titles", {}).get("en", ""),
                    "show_year": anime.get("startDate", "")[:4] if anime.get("startDate") else "",
                    "season": season,
                    "episode": episode,
                    "episode_title": f"Episode {episode}",
                    "overview": anime.get("synopsis", "")[:200] + "..." if anime.get("synopsis") else "",
                    "source": "kitsu"
                }
        except Exception as e:
            logger.error(f"Kitsu search error: {e}")
        
        return None

