#!/usr/bin/env python3
"""
Media Renamer - FileBot-style renaming with database integration
"""

import json
import logging
import re
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


class MediaRenamer:
    """
    Handles media file renaming with multiple database providers
    Supports: TMDB, TVDB, AniDB, AniList
    """
    
    # TMDB API (The Movie Database)
    TMDB_API_URL = "https://api.themoviedb.org/3"
    
    # TVDB API (TheTVDB)
    TVDB_API_URL = "https://api4.thetvdb.com/v4"
    
    # AniDB API
    ANIDB_API_URL = "http://api.anidb.net:9001/httpapi"
    
    # AniList GraphQL API
    ANILIST_API_URL = "https://graphql.anilist.co"
    
    # OMDB API (Open Movie Database)
    OMDB_API_URL = "http://www.omdbapi.com/"
    
    # MyAnimeList API
    MAL_API_URL = "https://api.myanimelist.net/v2"
    
    def __init__(self, tmdb_key: str = "", tvdb_key: str = "", anidb_key: str = "", omdb_key: str = ""):
        self.tmdb_key = tmdb_key
        self.tvdb_key = tvdb_key
        self.anidb_key = anidb_key
        self.omdb_key = omdb_key
        self.tvdb_token = None  # TVDB requires JWT token
    
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
    
    def search_tv_show(self, title: str, season: int = 1, episode: int = 1) -> Optional[Dict]:
        """
        Search for TV show - tries multiple providers
        Priority: TVDB -> TMDB -> AniList (for anime)
        """
        # Try TVDB first if available
        if self.tvdb_key:
            result = self.search_tv_show_tvdb(title, season, episode)
            if result:
                return result
        
        # Try AniList for anime detection (always available)
        if any(word in title.lower() for word in ['anime', 'naruto', 'attack on titan', 'bleach', 'one piece', 'dragon ball', 'demon slayer', 'jujutsu kaisen']):
            result = self.search_anime(title, season, episode)
            if result:
                return result
        
        # Try OMDB as another fallback
        if self.omdb_key:
            result = self.search_omdb_tv(title, season, episode)
            if result:
                return result
        
        # Fall back to TMDB
        if not self.tmdb_key:
            logger.warning("No API keys configured")
            return None
        
        try:
            # Search for TV show
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
                        "show_year": show.get("first_air_date", "")[:4],
                        "season": season,
                        "episode": episode,
                        "episode_title": episode_data.get("name", ""),
                        "overview": episode_data.get("overview", ""),
                    }
                except Exception as e:
                    logger.error(f"Error fetching episode details: {e}")
                    # Return show info without episode details
                    return {
                        "show_title": show.get("name", ""),
                        "show_year": show.get("first_air_date", "")[:4],
                        "season": season,
                        "episode": episode,
                        "episode_title": "",
                        "overview": "",
                    }
        
        except Exception as e:
            logger.error(f"Error searching TV show: {e}")
        
        return None
    
    def search_movie(self, title: str, year: Optional[int] = None) -> Optional[Dict]:
        """
        Search for movie information using multiple providers
        
        Returns dict with movie information or None
        """
        # Try AniList for anime movies first
        result = self.search_anime_movie(title, year)
        if result:
            return result
        
        # Try OMDB if available
        if self.omdb_key:
            result = self.search_omdb_movie(title, year)
            if result:
                return result
        
        # Fall back to TMDB
        if not self.tmdb_key:
            logger.warning("No movie API keys configured")
            return None
        
        try:
            # Search for movie
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
                    "year": movie.get("release_date", "")[:4],
                    "overview": movie.get("overview", ""),
                    "rating": movie.get("vote_average", 0),
                }
        
        except Exception as e:
            logger.error(f"Error searching movie: {e}")
        
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

