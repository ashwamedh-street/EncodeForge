#!/usr/bin/env python3
"""
FFmpeg Core - Backward compatibility module
This module provides backward compatibility by importing from encodeforge_core
"""

from encodeforge_core import EncodeForgeCore
from encodeforge_modules import ConversionSettings, FileInfo

# Backward compatibility alias
FFmpegCore = EncodeForgeCore

# Re-export for backward compatibility
__all__ = ['FFmpegCore', 'EncodeForgeCore', 'ConversionSettings', 'FileInfo']
