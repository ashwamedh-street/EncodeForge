"""
Metadata Providers Package
Modular metadata provider implementations for movies, TV shows, and anime
"""

from .base_provider import BaseMetadataProvider
from .tmdb_provider import TMDBProvider
from .tvdb_provider import TVDBProvider
from .anilist_provider import AniListProvider
from .tvmaze_provider import TVmazeProvider
from .kitsu_provider import KitsuProvider
from .jikan_provider import JikanProvider
from .trakt_provider import TraktProvider
from .omdb_provider import OMDBProvider

__all__ = [
    'BaseMetadataProvider',
    'TMDBProvider',
    'TVDBProvider',
    'AniListProvider',
    'TVmazeProvider',
    'KitsuProvider',
    'JikanProvider',
    'TraktProvider',
    'OMDBProvider'
]

