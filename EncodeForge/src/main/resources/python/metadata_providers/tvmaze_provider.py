#!/usr/bin/env python3
"""
TVmaze Provider
No API key required - Good for TV shows
"""

import json
import logging
import urllib.parse
import urllib.request
from typing import Dict, Optional

from .base_provider import BaseMetadataProvider

logger = logging.getLogger(__name__)


class TVmazeProvider(BaseMetadataProvider):
    """TVmaze provider (no key required)"""

    API_URL = "https://api.tvmaze.com"

    def __init__(self):
        super().__init__()
        logger.info("TVmazeProvider initialized")

    def search_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """TVmaze doesn't support movies, return None"""
        return None

    def search_tv(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search TV show using TVmaze API (no key required)"""
        try:
            self._rate_limit()
            
            # Search for show
            search_url = f"{self.API_URL}/singlesearch/shows"
            params = {"q": title}
            url = f"{search_url}?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                show = json.loads(response.read().decode())
            
            if show and show.get("id"):
                show_id = show["id"]
                
                # Get episode info
                episode_url = f"{self.API_URL}/shows/{show_id}/episodebynumber?season={season}&number={episode}"
                
                try:
                    with urllib.request.urlopen(episode_url, timeout=10) as ep_response:
                        episode_data = json.loads(ep_response.read().decode())
                    
                    return {
                        "show_title": show.get("name", ""),
                        "show_year": show.get("premiered", "")[:4] if show.get("premiered") else "",
                        "season": season,
                        "episode": episode,
                        "episode_title": episode_data.get("name", ""),
                        "overview": episode_data.get("summary", "").replace("<p>", "").replace("</p>", "") if episode_data.get("summary") else "",
                        "source": "tvmaze"
                    }
                except Exception as e:
                    logger.error(f"TVmaze episode error: {e}")
                    # Return show info without episode details
                    return {
                        "show_title": show.get("name", ""),
                        "show_year": show.get("premiered", "")[:4] if show.get("premiered") else "",
                        "season": season,
                        "episode": episode,
                        "episode_title": "",
                        "overview": "",
                        "source": "tvmaze"
                    }
        except Exception as e:
            logger.error(f"TVmaze search error: {e}")
        
        return None

