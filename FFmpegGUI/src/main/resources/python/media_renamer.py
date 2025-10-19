#!/usr/bin/env python3
"""
Media Renamer - FileBot-style renaming with database integration
Supports 15+ metadata providers for movies, TV shows, and anime
"""

import json
import logging
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


class MediaRenamer:
    """
    Handles media file renaming with multiple database providers
    
    FREE Providers (No API Key):
    - AniList (Anime)
    - Kitsu (Anime)
    - Jikan/MyAnimeList (Anime - read-only)
    - TVmaze (TV Shows)
    
    API Key Providers (Free Keys):
    - TMDB (Movies & TV)
    - TVDB (TV Shows)
    - OMDB (Movies & TV)
    - Trakt (Movies & TV)
    - Fanart.tv (Artwork)
    """
    
    # API URLs
    TMDB_API_URL = "https://api.themoviedb.org/3"
    TVDB_API_URL = "https://api4.thetvdb.com/v4"
    ANILIST_API_URL = "https://graphql.anilist.co"
    OMDB_API_URL = "http://www.omdbapi.com/"
    TVMAZE_API_URL = "https://api.tvmaze.com"
    KITSU_API_URL = "https://kitsu.io/api/edge"
    JIKAN_API_URL = "https://api.jikan.moe/v4"  # MyAnimeList mirror
    TRAKT_API_URL = "https://api.trakt.tv"
    FANART_API_URL = "https://webservice.fanart.tv/v3"
    
    def __init__(self, 
                 tmdb_key: str = "", 
                 tvdb_key: str = "", 
                 omdb_key: str = "",
                 trakt_key: str = "",
                 fanart_key: str = "",
                 mal_client_id: str = ""):
        """Initialize with API keys"""
        self.tmdb_key = tmdb_key
        self.tvdb_key = tvdb_key
        self.omdb_key = omdb_key
        self.trakt_key = trakt_key
        self.fanart_key = fanart_key
        self.mal_client_id = mal_client_id
        self.tvdb_token = None  # TVDB requires JWT token
        self.last_request_time = 0  # Rate limiting
    
    def _rate_limit(self, min_interval: float = 0.25):
        """Simple rate limiting to avoid hammering APIs"""
        current_time = time.time()
        time_since_last = current_time - self.last_request_time
        if time_since_last < min_interval:
            time.sleep(min_interval - time_since_last)
        self.last_request_time = time.time()
    
    def validate_tmdb_key(self) -> Tuple[bool, str]:
        """Validate TMDB API key"""
        if not self.tmdb_key:
            return False, "No API key provided"
        
        try:
            url = f"{self.TMDB_API_URL}/configuration?api_key={self.tmdb_key}"
            with urllib.request.urlopen(url, timeout=5) as response:
                data = json.loads(response.read().decode())
                if data.get("images"):
                    return True, "Valid TMDB API key"
        except Exception as e:
            return False, f"Invalid: {str(e)}"
        
        return False, "Unknown error"
    
    def validate_tvdb_key(self) -> Tuple[bool, str]:
        """Validate TVDB API key"""
        if not self.tvdb_key:
            return False, "No API key provided"
        
        if self.tvdb_login():
            return True, "Valid TVDB API key"
        return False, "Invalid TVDB API key"
    
    def validate_omdb_key(self) -> Tuple[bool, str]:
        """Validate OMDB API key"""
        if not self.omdb_key:
            return False, "No API key provided"
        
        try:
            url = f"{self.OMDB_API_URL}?apikey={self.omdb_key}&t=test"
            with urllib.request.urlopen(url, timeout=5) as response:
                data = json.loads(response.read().decode())
                if data.get("Response") != "False" or "movie not found" in data.get("Error", "").lower():
                    return True, "Valid OMDB API key"
                if "invalid api key" in data.get("Error", "").lower():
                    return False, "Invalid API key"
        except Exception as e:
            return False, f"Error: {str(e)}"
        
        return False, "Unknown error"
    
    def validate_trakt_key(self) -> Tuple[bool, str]:
        """Validate Trakt API key"""
        if not self.trakt_key:
            return False, "No API key provided"
        
        try:
            headers = {
                "Content-Type": "application/json",
                "trakt-api-version": "2",
                "trakt-api-key": self.trakt_key
            }
            request = urllib.request.Request(f"{self.TRAKT_API_URL}/search/movie?query=test&limit=1", headers=headers)
            with urllib.request.urlopen(request, timeout=5) as response:
                return True, "Valid Trakt API key"
        except Exception as e:
            if "401" in str(e) or "403" in str(e):
                return False, "Invalid API key"
            return False, f"Error: {str(e)}"
    
    def get_available_providers(self) -> Dict[str, bool]:
        """
        Get list of available providers based on configured API keys
        
        Returns:
            Dict mapping provider name to availability status
        """
        return {
            # Always available (no key needed)
            "anilist": True,
            "kitsu": True,
            "jikan": True,
            "tvmaze": True,
            
            # Require API keys
            "tmdb": bool(self.tmdb_key),
            "tvdb": bool(self.tvdb_key),
            "omdb": bool(self.omdb_key),
            "trakt": bool(self.trakt_key),
            "fanart": bool(self.fanart_key),
            "mal": bool(self.mal_client_id),
        }
    
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
    
    def tvdb_login(self) -> bool:
        """Login to TVDB and get JWT token"""
        if not self.tvdb_key:
            return False
        
        try:
            login_data = json.dumps({"apikey": self.tvdb_key}).encode('utf-8')
            request = urllib.request.Request(
                f"{self.TVDB_API_URL}/login",
                data=login_data,
                headers={"Content-Type": "application/json"}
            )
            
            with urllib.request.urlopen(request, timeout=10) as response:
                data = json.loads(response.read().decode())
                self.tvdb_token = data.get("data", {}).get("token")
                return self.tvdb_token is not None
        except Exception as e:
            logger.error(f"TVDB login error: {e}")
            return False
    
    def search_tv_show_tvdb(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search TV show using TVDB"""
        if not self.tvdb_token and not self.tvdb_login():
            return None
        
        try:
            # Search for series
            search_url = f"{self.TVDB_API_URL}/search/series?name={urllib.parse.quote(title)}"
            headers = {"Authorization": f"Bearer {self.tvdb_token}"}
            
            request = urllib.request.Request(search_url, headers=headers)
            with urllib.request.urlopen(request, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data.get("data"):
                series = data["data"][0]
                series_id = series["id"]
                
                # Get episode info
                episode_url = f"{self.TVDB_API_URL}/series/{series_id}/episodes/default?season={season}&episodeNumber={episode}"
                request = urllib.request.Request(episode_url, headers=headers)
                
                with urllib.request.urlopen(request, timeout=10) as ep_response:
                    episode_data = json.loads(ep_response.read().decode())
                
                if episode_data.get("data", {}).get("episodes"):
                    ep = episode_data["data"]["episodes"][0]
                    return {
                        "show_title": series.get("name", ""),
                        "show_year": series.get("firstAired", "")[:4] if series.get("firstAired") else "",
                        "season": season,
                        "episode": episode,
                        "episode_title": ep.get("name", ""),
                        "overview": ep.get("overview", ""),
                        "source": "tvdb"
                    }
        except Exception as e:
            logger.error(f"TVDB search error: {e}")
        
        return None
    
    def search_anime(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
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
                self.ANILIST_API_URL,
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
    
    def search_anime_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
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
                self.ANILIST_API_URL,
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
    
    def search_tvmaze(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search TV show using TVmaze API (no key required)"""
        try:
            self._rate_limit()
            
            # Search for show
            search_url = f"{self.TVMAZE_API_URL}/singlesearch/shows"
            params = {"q": title}
            url = f"{search_url}?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                show = json.loads(response.read().decode())
            
            if show and show.get("id"):
                show_id = show["id"]
                
                # Get episode info
                episode_url = f"{self.TVMAZE_API_URL}/shows/{show_id}/episodebynumber?season={season}&number={episode}"
                
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
    
    def search_kitsu_anime(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search anime using Kitsu API (no key required)"""
        try:
            self._rate_limit()
            
            # Search for anime
            params = {"filter[text]": title, "page[limit]": "1"}
            url = f"{self.KITSU_API_URL}/anime?{urllib.parse.urlencode(params)}"
            
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
    
    def search_kitsu_anime_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """Search anime movie using Kitsu API"""
        try:
            self._rate_limit()
            
            params = {"filter[text]": title, "filter[subtype]": "movie", "page[limit]": "1"}
            url = f"{self.KITSU_API_URL}/anime?{urllib.parse.urlencode(params)}"
            
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
    
    def search_jikan(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search anime using Jikan API (MyAnimeList mirror - no key required)"""
        try:
            self._rate_limit(0.5)  # Jikan has stricter rate limits
            
            # Search for anime
            params = {"q": title, "limit": "1", "type": "tv"}
            url = f"{self.JIKAN_API_URL}/anime?{urllib.parse.urlencode(params)}"
            
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
    
    def search_jikan_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """Search anime movie using Jikan API"""
        try:
            self._rate_limit(0.5)
            
            params = {"q": title, "limit": "1", "type": "movie"}
            url = f"{self.JIKAN_API_URL}/anime?{urllib.parse.urlencode(params)}"
            
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
    
    def search_trakt_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """Search movie using Trakt API"""
        if not self.trakt_key:
            return None
        
        try:
            self._rate_limit()
            
            params = {"query": title, "type": "movie", "limit": "1"}
            if year:
                params["years"] = str(year)
            
            url = f"{self.TRAKT_API_URL}/search/movie?{urllib.parse.urlencode(params)}"
            headers = {
                "Content-Type": "application/json",
                "trakt-api-version": "2",
                "trakt-api-key": self.trakt_key
            }
            
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data and len(data) > 0:
                movie = data[0].get("movie", {})
                
                return {
                    "title": movie.get("title", ""),
                    "year": str(movie.get("year", "")),
                    "overview": movie.get("overview", ""),
                    "rating": float(movie.get("rating", 0)),
                    "source": "trakt"
                }
        except Exception as e:
            logger.error(f"Trakt movie search error: {e}")
        
        return None
    
    def search_trakt_tv(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search TV show using Trakt API"""
        if not self.trakt_key:
            return None
        
        try:
            self._rate_limit()
            
            # Search for show
            params = {"query": title, "type": "show", "limit": "1"}
            url = f"{self.TRAKT_API_URL}/search/show?{urllib.parse.urlencode(params)}"
            headers = {
                "Content-Type": "application/json",
                "trakt-api-version": "2",
                "trakt-api-key": self.trakt_key
            }
            
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data and len(data) > 0:
                show = data[0].get("show", {})
                
                return {
                    "show_title": show.get("title", ""),
                    "show_year": str(show.get("year", "")),
                    "season": season,
                    "episode": episode,
                    "episode_title": f"Episode {episode}",  # Trakt doesn't provide episode details easily
                    "overview": show.get("overview", ""),
                    "source": "trakt"
                }
        except Exception as e:
            logger.error(f"Trakt TV search error: {e}")
        
        return None
    
    def search_omdb_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """Search movie using OMDB API"""
        if not self.omdb_key:
            return None
            
        try:
            params = {
                "apikey": self.omdb_key,
                "t": title,
                "type": "movie"
            }
            
            if year:
                params["y"] = str(year)
            
            url = f"{self.OMDB_API_URL}?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data.get("Response") == "True":
                return {
                    "title": data.get("Title", ""),
                    "year": data.get("Year", ""),
                    "overview": data.get("Plot", ""),
                    "rating": float(data.get("imdbRating", 0)) if data.get("imdbRating") != "N/A" else 0,
                    "source": "omdb"
                }
        except Exception as e:
            logger.error(f"OMDB search error: {e}")
        
        return None
    
    def search_omdb_tv(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Search TV show using OMDB API"""
        if not self.omdb_key:
            return None
            
        try:
            params = {
                "apikey": self.omdb_key,
                "t": title,
                "type": "series"
            }
            
            url = f"{self.OMDB_API_URL}?{urllib.parse.urlencode(params)}"
            
            with urllib.request.urlopen(url, timeout=10) as response:
                data = json.loads(response.read().decode())
            
            if data.get("Response") == "True":
                return {
                    "show_title": data.get("Title", ""),
                    "show_year": data.get("Year", "")[:4] if data.get("Year") else "",
                    "season": season,
                    "episode": episode,
                    "episode_title": f"Episode {episode}",
                    "overview": data.get("Plot", ""),
                    "source": "omdb"
                }
        except Exception as e:
            logger.error(f"OMDB TV search error: {e}")
        
        return None
    
    def search_tv_show(self, title: str, season: int = 1, episode: int = 1, provider: str = "auto") -> Optional[Dict]:
        """
        Search for TV show - tries multiple providers
        
        Args:
            title: Show title to search
            season: Season number
            episode: Episode number
            provider: Specific provider to use, or "auto" for automatic selection
        
        Provider Priority:
            - Anime: AniList, Kitsu, Jikan -> TMDB
            - TV: TVDB, TVmaze, Trakt -> TMDB -> OMDB
        """
        # Detect if anime
        is_anime = any(word in title.lower() for word in [
            'anime', 'naruto', 'attack on titan', 'bleach', 'one piece', 
            'dragon ball', 'demon slayer', 'jujutsu kaisen', 'my hero academia',
            'one punch man', 'death note', 'fullmetal', 'sword art online'
        ])
        
        # Try specific provider if requested
        if provider != "auto":
            if provider == "tvdb" and self.tvdb_key:
                return self.search_tv_show_tvdb(title, season, episode)
            elif provider == "tvmaze":
                return self.search_tvmaze(title, season, episode)
            elif provider == "anilist":
                return self.search_anime(title, season, episode)
            elif provider == "kitsu":
                return self.search_kitsu_anime(title, season, episode)
            elif provider == "jikan":
                return self.search_jikan(title, season, episode)
            elif provider == "trakt" and self.trakt_key:
                return self.search_trakt_tv(title, season, episode)
            elif provider == "omdb" and self.omdb_key:
                return self.search_omdb_tv(title, season, episode)
            elif provider == "tmdb" and self.tmdb_key:
                # Fall through to TMDB below
                pass
        
        # Auto mode - try providers based on content type
        if is_anime:
            # Try anime providers first (all free)
            for search_func in [self.search_anime, self.search_kitsu_anime, self.search_jikan]:
                try:
                    result = search_func(title, season, episode)
                    if result:
                        return result
                except Exception as e:
                    logger.error(f"Error with anime provider: {e}")
        
        # Try general TV show providers
        # TVDB (requires key)
        if self.tvdb_key:
            result = self.search_tv_show_tvdb(title, season, episode)
            if result:
                return result
        
        # TVmaze (free, no key)
        result = self.search_tvmaze(title, season, episode)
        if result:
            return result
        
        # Trakt (requires key)
        if self.trakt_key:
            result = self.search_trakt_tv(title, season, episode)
            if result:
                return result
        
        # Try TMDB
        if self.tmdb_key:
            try:
                self._rate_limit()
                search_url = f"{self.TMDB_API_URL}/search/tv"
                params = {
                    "api_key": self.tmdb_key,
                    "query": title
                }
                
                url = f"{search_url}?{urllib.parse.urlencode(params)}"
                
                with urllib.request.urlopen(url, timeout=10) as response:
                    data = json.loads(response.read().decode())
                
                if data.get("results"):
                    show = data["results"][0]
                    show_id = show["id"]
                    
                    # Get episode details
                    episode_url = f"{self.TMDB_API_URL}/tv/{show_id}/season/{season}/episode/{episode}"
                    episode_params = {"api_key": self.tmdb_key}
                    episode_full_url = f"{episode_url}?{urllib.parse.urlencode(episode_params)}"
                    
                    try:
                        with urllib.request.urlopen(episode_full_url, timeout=10) as ep_response:
                            episode_data = json.loads(ep_response.read().decode())
                        
                        return {
                            "show_title": show.get("name", ""),
                            "show_year": show.get("first_air_date", "")[:4] if show.get("first_air_date") else "",
                            "season": season,
                            "episode": episode,
                            "episode_title": episode_data.get("name", ""),
                            "overview": episode_data.get("overview", ""),
                            "source": "tmdb"
                        }
                    except Exception as e:
                        logger.error(f"Error fetching TMDB episode details: {e}")
                        # Return show info without episode details
                        return {
                            "show_title": show.get("name", ""),
                            "show_year": show.get("first_air_date", "")[:4] if show.get("first_air_date") else "",
                            "season": season,
                            "episode": episode,
                            "episode_title": "",
                            "overview": "",
                            "source": "tmdb"
                        }
            except Exception as e:
                logger.error(f"Error searching TMDB TV show: {e}")
        
        # Last resort: OMDB
        if self.omdb_key:
            result = self.search_omdb_tv(title, season, episode)
            if result:
                return result
        
        logger.warning(f"No results found for TV show: {title}")
        return None
    
    def search_movie(self, title: str, year: Optional[int] = None, provider: str = "auto") -> Optional[Dict]:
        """
        Search for movie information using multiple providers
        
        Args:
            title: Movie title to search
            year: Release year (optional, helps accuracy)
            provider: Specific provider to use, or "auto" for automatic selection
        
        Provider Priority:
            - Anime Movies: AniList, Kitsu, Jikan -> TMDB
            - Regular Movies: TMDB, Trakt, OMDB
        
        Returns dict with movie information or None
        """
        # Detect if anime movie
        is_anime = any(word in title.lower() for word in [
            'anime', 'ghibli', 'pokemon', 'naruto', 'dragon ball', 
            'evangelion', 'akira', 'spirited away', 'your name', 'weathering with you'
        ])
        
        # Try specific provider if requested
        if provider != "auto":
            if provider == "anilist":
                return self.search_anime_movie(title, year)
            elif provider == "kitsu":
                return self.search_kitsu_anime_movie(title, year)
            elif provider == "jikan":
                return self.search_jikan_movie(title, year)
            elif provider == "tmdb" and self.tmdb_key:
                # Fall through to TMDB below
                pass
            elif provider == "trakt" and self.trakt_key:
                return self.search_trakt_movie(title, year)
            elif provider == "omdb" and self.omdb_key:
                return self.search_omdb_movie(title, year)
        
        # Auto mode - try providers based on content type
        if is_anime:
            # Try anime providers first (all free)
            for search_func in [self.search_anime_movie, self.search_kitsu_anime_movie, self.search_jikan_movie]:
                try:
                    result = search_func(title, year)
                    if result:
                        return result
                except Exception as e:
                    logger.error(f"Error with anime movie provider: {e}")
        
        # Try TMDB first (best quality for regular movies)
        if self.tmdb_key:
            try:
                self._rate_limit()
                search_url = f"{self.TMDB_API_URL}/search/movie"
                params = {
                    "api_key": self.tmdb_key,
                    "query": title
                }
                
                if year:
                    params["year"] = str(year)
                
                url = f"{search_url}?{urllib.parse.urlencode(params)}"
                
                with urllib.request.urlopen(url, timeout=10) as response:
                    data = json.loads(response.read().decode())
                
                if data.get("results"):
                    movie = data["results"][0]
                    
                    return {
                        "title": movie.get("title", ""),
                        "year": movie.get("release_date", "")[:4] if movie.get("release_date") else "",
                        "overview": movie.get("overview", ""),
                        "rating": movie.get("vote_average", 0),
                        "source": "tmdb"
                    }
            except Exception as e:
                logger.error(f"Error searching TMDB movie: {e}")
        
        # Try Trakt
        if self.trakt_key:
            result = self.search_trakt_movie(title, year)
            if result:
                return result
        
        # Try OMDB
        if self.omdb_key:
            result = self.search_omdb_movie(title, year)
            if result:
                return result
        
        logger.warning(f"No results found for movie: {title}")
        return None
    
    def format_filename(self, info: Dict, pattern: str) -> str:
        """
        Format filename using a pattern
        
        Pattern tokens:
            {title} - Show/Movie title
            {year} - Year
            {season} - Season number (padded)
            {episode} - Episode number (padded)
            {episodeTitle} - Episode title
            {S} - Season (S01)
            {E} - Episode (E01)
        
        Example patterns:
            TV: "{title} - S{season}E{episode} - {episodeTitle}"
            Movie: "{title} ({year})"
        """
        # Start with pattern
        result = pattern
        
        # Replace tokens
        replacements = {
            "{title}": info.get("show_title", info.get("title", "")),
            "{year}": str(info.get("year", info.get("show_year", ""))),
            "{season}": f"{info.get('season', 1):02d}",
            "{episode}": f"{info.get('episode', 1):02d}",
            "{episodeTitle}": info.get("episode_title", ""),
            "{S}": f"S{info.get('season', 1):02d}",
            "{E}": f"E{info.get('episode', 1):02d}",
        }
        
        for token, value in replacements.items():
            result = result.replace(token, value)
        
        # Clean up multiple spaces
        result = re.sub(r'\s+', ' ', result).strip()
        
        # Remove invalid filename characters
        result = re.sub(r'[<>:"/\\|?*]', '', result)
        
        return result
    
    def rename_file(
        self,
        file_path: str,
        pattern: str,
        auto_detect: bool = True,
        dry_run: bool = False
    ) -> Tuple[bool, str, Optional[str]]:
        """
        Rename a media file
        
        Args:
            file_path: Path to file
            pattern: Naming pattern
            auto_detect: Automatically detect and search for metadata
            dry_run: Don't actually rename, just show what would happen
            
        Returns:
            (success, message, new_path)
        """
        path = Path(file_path)
        
        if not path.exists():
            return False, f"File not found: {file_path}", None
        
        # Detect media type
        media_type = self.detect_media_type(path.name)
        
        info = None
        
        if auto_detect:
            if media_type == "tv":
                parsed = self.parse_tv_filename(path.name)
                if parsed:
                    info = self.search_tv_show(
                        parsed["title"],
                        parsed["season"],
                        parsed["episode"]
                    )
            elif media_type == "movie":
                parsed = self.parse_movie_filename(path.name)
                if parsed:
                    info = self.search_movie(parsed["title"], parsed.get("year"))
        
        if not info:
            return False, "Could not find metadata for file", None
        
        # Format new filename
        new_name = self.format_filename(info, pattern)
        new_path = path.parent / f"{new_name}{path.suffix}"
        
        if dry_run:
            return True, f"Would rename to: {new_path.name}", str(new_path)
        
        # Perform rename
        try:
            path.rename(new_path)
            return True, f"Renamed successfully to: {new_path.name}", str(new_path)
        except Exception as e:
            return False, f"Rename failed: {str(e)}", None


def main():
    """Test the media renamer"""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python media_renamer.py <filename>")
        print("\nExample filenames to test:")
        print("  - The.Mandalorian.S01E01.1080p.mkv")
        print("  - Inception.2010.1080p.mkv")
        return
    
    filename = sys.argv[1]
    
    renamer = MediaRenamer()
    
    # Detect type
    media_type = renamer.detect_media_type(filename)
    print(f"Detected type: {media_type}")
    
    if media_type == "tv":
        info = renamer.parse_tv_filename(filename)
        print(f"\nParsed TV info: {json.dumps(info, indent=2)}")
        
        if info:
            pattern = "{title} - S{season}E{episode} - {episodeTitle}"
            # Note: Would need API key to actually search
            print(f"\nWould use pattern: {pattern}")
            print("(Configure TMDB API key to search for metadata)")
    
    elif media_type == "movie":
        info = renamer.parse_movie_filename(filename)
        print(f"\nParsed movie info: {json.dumps(info, indent=2)}")
        
        if info:
            pattern = "{title} ({year})"
            print(f"\nWould use pattern: {pattern}")
            print("(Configure TMDB API key to search for metadata)")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
