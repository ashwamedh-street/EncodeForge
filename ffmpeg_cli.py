#!/usr/bin/env python3
"""
FFmpeg CLI Module - Command-line interface for FFmpeg operations
Supports three modes: Encoder, Subtitle, and Renamer
"""

import argparse
import sys
import json
import logging
from pathlib import Path
from typing import Optional

from ffmpeg_core import FFmpegCore, ConversionSettings

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class FFmpegCLI:
    """Command-line interface for FFmpeg operations"""
    
    def __init__(self):
        self.core = None
        self.settings = ConversionSettings()
    
    def create_parser(self) -> argparse.ArgumentParser:
        """Create argument parser"""
        parser = argparse.ArgumentParser(
            description="FFmpeg Batch Transcoder - CLI Mode",
            epilog="Supports three modes: encoder (default), subtitle, renamer"
        )
        
        # Mode selection
        parser.add_argument(
            "mode",
            nargs="?",
            default="encoder",
            choices=["encoder", "subtitle", "renamer"],
            help="Operation mode (default: encoder)"
        )
        
        # Input files/directories
        parser.add_argument(
            "input",
            nargs="*",
            help="Input files or directories to process"
        )
        
        # General options
        parser.add_argument("--ffmpeg-path", help="Path to ffmpeg executable")
        parser.add_argument("--ffprobe-path", help="Path to ffprobe executable")
        parser.add_argument("--output-dir", help="Output directory")
        parser.add_argument("--recursive", action="store_true", default=True, help="Scan directories recursively")
        parser.add_argument("--no-recursive", action="store_false", dest="recursive", help="Don't scan subdirectories")
        parser.add_argument("--dry-run", action="store_true", help="Show what would be done without doing it")
        parser.add_argument("--output-format", default="mp4", choices=["mp4", "mkv", "avi", "mov"], help="Output format")
        
        # Encoder mode options
        encoder_group = parser.add_argument_group("Encoder Options")
        encoder_group.add_argument("--use-nvenc", action="store_true", default=True, help="Use NVIDIA NVENC")
        encoder_group.add_argument("--use-amf", action="store_true", help="Use AMD AMF")
        encoder_group.add_argument("--use-qsv", action="store_true", help="Use Intel Quick Sync")
        encoder_group.add_argument("--nvenc-preset", default="p4", help="NVENC preset (p1-p7)")
        encoder_group.add_argument("--nvenc-cq", type=int, default=23, help="NVENC quality (0-51)")
        encoder_group.add_argument("--delete-original", action="store_true", help="Delete original files after conversion")
        encoder_group.add_argument("--no-delete", action="store_false", dest="delete_original", help="Keep original files")
        encoder_group.add_argument("--overwrite", action="store_true", help="Overwrite existing output files")
        
        # Subtitle mode options
        subtitle_group = parser.add_argument_group("Subtitle Options")
        subtitle_group.add_argument("--enable-subtitle-generation", action="store_true", help="Generate subtitles with Whisper AI")
        subtitle_group.add_argument("--enable-subtitle-download", action="store_true", help="Download subtitles from OpenSubtitles")
        subtitle_group.add_argument("--subtitle-languages", default="eng", help="Comma-separated language codes")
        subtitle_group.add_argument("--whisper-model", default="base", choices=["tiny", "base", "small", "medium", "large"], help="Whisper model size")
        subtitle_group.add_argument("--replace-subtitles", action="store_true", help="Replace existing subtitles")
        subtitle_group.add_argument("--opensubtitles-username", help="OpenSubtitles username")
        subtitle_group.add_argument("--opensubtitles-password", help="OpenSubtitles password")
        subtitle_group.add_argument("--opensubtitles-api-key", help="OpenSubtitles API key")
        
        # Renamer mode options
        renamer_group = parser.add_argument_group("Renamer Options")
        renamer_group.add_argument("--enable-renaming", action="store_true", help="Enable file renaming")
        renamer_group.add_argument("--pattern-tv", default="{title} - S{season}E{episode} - {episodeTitle}", help="TV show naming pattern")
        renamer_group.add_argument("--pattern-movie", default="{title} ({year})", help="Movie naming pattern")
        renamer_group.add_argument("--tmdb-api-key", help="TMDB API key for metadata lookup")
        renamer_group.add_argument("--preview-only", action="store_true", help="Show rename preview without renaming")
        
        # Audio options
        audio_group = parser.add_argument_group("Audio Options")
        audio_group.add_argument("--audio-codec", default="copy", help="Audio codec (copy, aac, ac3, etc.)")
        audio_group.add_argument("--audio-bitrate", help="Audio bitrate (e.g., 192k)")
        audio_group.add_argument("--normalize-audio", action="store_true", help="Normalize audio levels")
        
        return parser
    
    def run_encoder_mode(self, args):
        """Run encoder mode"""
        logger.info("Running in ENCODER mode")
        
        # Apply settings from args
        if args.ffmpeg_path:
            self.settings.ffmpeg_path = args.ffmpeg_path
        if args.ffprobe_path:
            self.settings.ffprobe_path = args.ffprobe_path
        
        self.settings.use_nvenc = args.use_nvenc
        self.settings.use_amf = args.use_amf
        self.settings.use_qsv = args.use_qsv
        self.settings.nvenc_preset = args.nvenc_preset
        self.settings.nvenc_cq = args.nvenc_cq
        self.settings.delete_original = args.delete_original
        self.settings.overwrite_existing = args.overwrite
        self.settings.dry_run = args.dry_run
        self.settings.output_format = args.output_format
        self.settings.audio_codec = args.audio_codec
        self.settings.audio_bitrate = args.audio_bitrate
        self.settings.normalize_audio = args.normalize_audio
        
        # Optional subtitle generation/download during encoding
        if args.enable_subtitle_generation:
            self.settings.enable_subtitle_generation = True
            self.settings.whisper_model = args.whisper_model
            self.settings.subtitle_languages = args.subtitle_languages.split(",")
        
        if args.enable_subtitle_download:
            self.settings.enable_subtitle_download = True
            self.settings.opensubtitles_username = args.opensubtitles_username or ""
            self.settings.opensubtitles_password = args.opensubtitles_password or ""
            self.settings.opensubtitles_api_key = args.opensubtitles_api_key or ""
        
        # Optional renaming after encoding
        if args.enable_renaming:
            self.settings.enable_renaming = True
            self.settings.renaming_pattern_tv = args.pattern_tv
            self.settings.renaming_pattern_movie = args.pattern_movie
            self.settings.tmdb_api_key = args.tmdb_api_key or ""
        
        # Initialize core
        self.core = FFmpegCore(self.settings)
        
        # Check FFmpeg
        ffmpeg_status = self.core.check_ffmpeg()
        if not ffmpeg_status["ffmpeg_available"]:
            logger.error("FFmpeg not found!")
            print("ERROR: FFmpeg not found. Please install FFmpeg or specify path with --ffmpeg-path")
            sys.exit(1)
        
        logger.info(f"FFmpeg detected: {ffmpeg_status['ffmpeg_version']}")
        
        # Collect files
        files = self._collect_files(args.input, args.recursive)
        
        if not files:
            logger.error("No files found to process")
            sys.exit(1)
        
        logger.info(f"Found {len(files)} file(s) to process")
        
        # TODO: Implement actual conversion logic
        print("\n✅ Encoder mode configured. Conversion logic coming soon!")
        print(f"Files to process: {len(files)}")
        print(f"Settings: NVENC={self.settings.use_nvenc}, Quality={self.settings.nvenc_cq}")
    
    def run_subtitle_mode(self, args):
        """Run subtitle mode"""
        logger.info("Running in SUBTITLE mode")
        
        # Apply settings
        self.settings.enable_subtitle_generation = args.enable_subtitle_generation
        self.settings.enable_subtitle_download = args.enable_subtitle_download
        self.settings.whisper_model = args.whisper_model
        self.settings.subtitle_languages = args.subtitle_languages.split(",")
        self.settings.replace_existing_subtitles = args.replace_subtitles
        self.settings.opensubtitles_username = args.opensubtitles_username or ""
        self.settings.opensubtitles_password = args.opensubtitles_password or ""
        self.settings.opensubtitles_api_key = args.opensubtitles_api_key or ""
        
        # Initialize core
        self.core = FFmpegCore(self.settings)
        
        # Check Whisper if needed
        if args.enable_subtitle_generation:
            whisper_status = self.core.check_whisper()
            if not whisper_status["whisper_available"]:
                print("WARNING: Whisper not installed. Install with: pip install openai-whisper")
                response = input("Would you like to install Whisper now? (y/n): ")
                if response.lower() == "y":
                    result = self.core.install_whisper()
                    if result["status"] == "error":
                        logger.error(f"Failed to install Whisper: {result['message']}")
                        sys.exit(1)
                else:
                    sys.exit(1)
        
        # Collect files
        files = self._collect_files(args.input, args.recursive)
        
        if not files:
            logger.error("No files found to process")
            sys.exit(1)
        
        logger.info(f"Found {len(files)} file(s) to process")
        
        # Process each file
        for file_path in files:
            print(f"\nProcessing: {Path(file_path).name}")
            
            for language in self.settings.subtitle_languages:
                print(f"  Generating {language} subtitles...")
                result = self.core.generate_subtitles(file_path, language)
                
                if result["status"] == "success":
                    print(f"  ✅ {result['message']}")
                else:
                    print(f"  ❌ {result['message']}")
        
        print("\n✅ Subtitle generation complete!")
    
    def run_renamer_mode(self, args):
        """Run renamer mode"""
        logger.info("Running in RENAMER mode")
        
        # Apply settings
        self.settings.renaming_pattern_tv = args.pattern_tv
        self.settings.renaming_pattern_movie = args.pattern_movie
        self.settings.tmdb_api_key = args.tmdb_api_key or ""
        
        if not self.settings.tmdb_api_key:
            logger.error("TMDB API key required for renaming")
            print("ERROR: TMDB API key required. Provide with --tmdb-api-key")
            print("Get a free API key at: https://www.themoviedb.org/settings/api")
            sys.exit(1)
        
        # Initialize core
        self.core = FFmpegCore(self.settings)
        
        # Collect files
        files = self._collect_files(args.input, args.recursive)
        
        if not files:
            logger.error("No files found to process")
            sys.exit(1)
        
        logger.info(f"Found {len(files)} file(s) to process")
        
        # Preview renames
        print("\n📋 Rename Preview:")
        print("=" * 80)
        
        preview_result = self.core.preview_rename(files)
        
        if preview_result["status"] == "success":
            for preview in preview_result["previews"]:
                print(f"\nOLD: {preview['original_name']}")
                print(f"NEW: {preview['new_name']}")
                print(f"Type: {preview['media_type']}")
                
                if not preview['can_rename']:
                    print("⚠️  Cannot rename (no metadata found)")
        
        # Ask for confirmation if not preview-only
        if not args.preview_only:
            print("\n" + "=" * 80)
            response = input("Proceed with renaming? (y/n): ")
            
            if response.lower() == "y":
                result = self.core.rename_files(files, dry_run=args.dry_run)
                
                if result["status"] == "success":
                    for item in result["results"]:
                        if item["success"]:
                            print(f"✅ {item['message']}")
                        else:
                            print(f"❌ {item['message']}")
                
                print("\n✅ Renaming complete!")
            else:
                print("Renaming cancelled.")
        else:
            print("\n(Preview only - use without --preview-only to rename)")
    
    def _collect_files(self, inputs, recursive: bool) -> list:
        """Collect files from input paths"""
        files = []
        
        if not inputs:
            logger.error("No input files or directories specified")
            return files
        
        # Ensure core is initialized
        if self.core is None:
            self.core = FFmpegCore(self.settings)
        
        for input_path in inputs:
            path = Path(input_path)
            
            if path.is_file():
                files.append(str(path.absolute()))
            elif path.is_dir():
                # Scan directory
                result = self.core.scan_directory(str(path), recursive=recursive)
                if result["status"] == "success":
                    files.extend(result["files"])
        
        return files
    
    def run(self, args=None):
        """Main entry point"""
        parser = self.create_parser()
        args = parser.parse_args(args)
        
        # Route to appropriate mode
        if args.mode == "encoder":
            self.run_encoder_mode(args)
        elif args.mode == "subtitle":
            self.run_subtitle_mode(args)
        elif args.mode == "renamer":
            self.run_renamer_mode(args)
        else:
            parser.print_help()
            sys.exit(1)


def main():
    """Entry point"""
    cli = FFmpegCLI()
    cli.run()


if __name__ == "__main__":
    main()

