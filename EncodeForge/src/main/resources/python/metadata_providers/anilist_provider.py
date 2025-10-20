#!/usr/bin/env python3
"""
AniList Provider
No API key required - Best for anime
"""

import json
import logging
import urllib.request
from typing import Dict, Optional

from .base_provider import BaseMetadataProvider

logger = logging.getLogger(__name__)


class AniListProvider(BaseMetadataProvider):
    """AniList (anime database) provider"""

    API_URL = "https://graphql.anilist.co"

    def __init__(self):
        super().__init__()
        logger.info("AniListProvider initialized")

    def search_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """Search anime movie using AniList GraphQL API"""
        try:
            query = """
            query ($search: String) {
                Media (search: $search, type: ANIME, format: MOVIE) {
                    id
                    title {
                        romaji
                        english
                        native
                    }
                    startDate {
                        year
                    }
                    description
                    averageScore
                }
            }
            """
            
            variables = {"search": title}
            data = json.dumps({"query": query, "variables": variables}).encode('utf-8')
            
            request = urllib.request.Request(
                self.API_URL,
                data=data,
                headers={"Content-Type": "application/json"}
            )
            
            with urllib.request.urlopen(request, timeout=10) as response:
                result = json.loads(response.read().decode())
            
            if result.get("data", {}).get("Media"):
                anime = result["data"]["Media"]
                title_data = anime.get("title", {})
                
                return {
                    "title": title_data.get("english") or title_data.get("romaji", ""),
                    "year": str(anime.get("startDate", {}).get("year", "")),
                    "overview": anime.get("description", "")[:200] + "..." if anime.get("description") else "",
                    "rating": anime.get("averageScore", 0) / 10.0 if anime.get("averageScore") else 0,
                    "source": "anilist"
                }
        except Exception as e:
            logger.error(f"AniList movie search error: {e}")
        
        return None

    def search_tv(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search anime using AniList GraphQL API"""
        try:
            query = """
            query ($search: String) {
                Media (search: $search, type: ANIME) {
                    id
                    title {
                        romaji
                        english
                        native
                    }
                    startDate {
                        year
                    }
                    episodes
                    format
                    status
                    description
                }
            }
            """
            
            variables = {"search": title}
            data = json.dumps({"query": query, "variables": variables}).encode('utf-8')
            
            request = urllib.request.Request(
                self.API_URL,
                data=data,
                headers={"Content-Type": "application/json"}
            )
            
            with urllib.request.urlopen(request, timeout=10) as response:
                result = json.loads(response.read().decode())
            
            if result.get("data", {}).get("Media"):
                anime = result["data"]["Media"]
                title_data = anime.get("title", {})
                
                return {
                    "show_title": title_data.get("english") or title_data.get("romaji", ""),
                    "show_year": str(anime.get("startDate", {}).get("year", "")),
                    "season": season,
                    "episode": episode,
                    "episode_title": f"Episode {episode}",
                    "overview": anime.get("description", "")[:200] + "..." if anime.get("description") else "",
                    "source": "anilist"
                }
        except Exception as e:
            logger.error(f"AniList search error: {e}")
        
        return None

