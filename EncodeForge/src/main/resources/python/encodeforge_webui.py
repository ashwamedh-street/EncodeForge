#!/usr/bin/env python3
"""
FFmpeg WebUI Module - Streamlit-based web interface for FFmpeg operations
Supports three modes: Encoder, Subtitle, and Renamer
"""

import logging
import os
import tempfile
from pathlib import Path
from typing import Any, Dict, List

import streamlit as st

from ffmpeg_core import ConversionSettings, FFmpegCore

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class FFmpegWebUI:
    """Web interface for FFmpeg operations"""
    
    def __init__(self):
        # Initialize session state
        if 'core' not in st.session_state:
            st.session_state.core = None
        if 'settings' not in st.session_state:
            st.session_state.settings = ConversionSettings()
        if 'mode' not in st.session_state:
            st.session_state.mode = "encoder"
        if 'files' not in st.session_state:
            st.session_state.files = []
        if 'rename_previews' not in st.session_state:
            st.session_state.rename_previews = []
        if 'conversion_results' not in st.session_state:
            st.session_state.conversion_results = []
        if 'temp_files' not in st.session_state:
            st.session_state.temp_files = []
    
    def render_sidebar(self):
        """Render sidebar with settings"""
        st.sidebar.title("⚙️ Settings")
        
        # Mode selection
        st.sidebar.subheader("Operation Mode")
        mode = st.sidebar.radio(
            "Select Mode:",
            ["Encoder", "Subtitle", "Renamer"],
            index=["encoder", "subtitle", "renamer"].index(st.session_state.mode)
        )
        st.session_state.mode = mode.lower()
        
        st.sidebar.divider()
        
        # General settings
        st.sidebar.subheader("General Settings")
        
        ffmpeg_path = st.sidebar.text_input(
            "FFmpeg Path",
            value=st.session_state.settings.ffmpeg_path,
            help="Leave empty for auto-detection"
        )
        st.session_state.settings.ffmpeg_path = ffmpeg_path or "ffmpeg"
        
        if st.sidebar.button("🔍 Check FFmpeg"):
            if st.session_state.core is None:
                st.session_state.core = FFmpegCore(st.session_state.settings)
            
            result = st.session_state.core.check_ffmpeg()
            
            if result["ffmpeg_available"]:
                st.sidebar.success(f"✅ FFmpeg {result['ffmpeg_version']} detected")
                
                if result["hardware_encoders"]:
                    st.sidebar.info("🎮 Hardware Encoders: " + ", ".join(result["hardware_encoders"]))
            else:
                st.sidebar.error(f"❌ {result['message']}")
        
        st.sidebar.divider()
        
        # Mode-specific settings
        if st.session_state.mode == "encoder":
            self.render_encoder_settings()
        elif st.session_state.mode == "subtitle":
            self.render_subtitle_settings()
        elif st.session_state.mode == "renamer":
            self.render_renamer_settings()
    
    def render_encoder_settings(self):
        """Render encoder settings"""
        st.sidebar.subheader("🎬 Encoder Settings")
        
        # Output format
        output_format = st.sidebar.selectbox(
            "Output Format",
            ["mp4", "mkv", "avi", "mov"],
            index=["mp4", "mkv", "avi", "mov"].index(st.session_state.settings.output_format)
        )
        st.session_state.settings.output_format = output_format
        
        # Hardware acceleration
        use_nvenc = st.sidebar.checkbox(
            "Use NVIDIA NVENC",
            value=st.session_state.settings.use_nvenc
        )
        st.session_state.settings.use_nvenc = use_nvenc
        
        if use_nvenc:
            nvenc_preset = st.sidebar.select_slider(
                "NVENC Preset",
                options=["p1", "p2", "p3", "p4", "p5", "p6", "p7"],
                value=st.session_state.settings.nvenc_preset
            )
            st.session_state.settings.nvenc_preset = nvenc_preset
            
            nvenc_cq = st.sidebar.slider(
                "NVENC Quality (CQ)",
                min_value=0,
                max_value=51,
                value=st.session_state.settings.nvenc_cq,
                help="Lower = better quality (18-23 recommended)"
            )
            st.session_state.settings.nvenc_cq = nvenc_cq
        
        # Other hardware acceleration
        use_amf = st.sidebar.checkbox("Use AMD AMF", value=st.session_state.settings.use_amf)
        st.session_state.settings.use_amf = use_amf
        
        use_qsv = st.sidebar.checkbox("Use Intel Quick Sync", value=st.session_state.settings.use_qsv)
        st.session_state.settings.use_qsv = use_qsv
        
        # General options
        delete_original = st.sidebar.checkbox(
            "Delete Original Files",
            value=st.session_state.settings.delete_original,
            help="Delete original files after successful conversion"
        )
        st.session_state.settings.delete_original = delete_original
        
        overwrite_existing = st.sidebar.checkbox(
            "Overwrite Existing",
            value=st.session_state.settings.overwrite_existing
        )
        st.session_state.settings.overwrite_existing = overwrite_existing
        
        # Optional: subtitle generation during encoding
        st.sidebar.divider()
        st.sidebar.subheader("Optional: Add Subtitles")
        
        enable_subtitle_gen = st.sidebar.checkbox(
            "Generate Subtitles (Whisper)",
            value=st.session_state.settings.enable_subtitle_generation
        )
        st.session_state.settings.enable_subtitle_generation = enable_subtitle_gen
        
        # Optional: renaming after encoding
        enable_renaming = st.sidebar.checkbox(
            "Rename Files After Encoding",
            value=st.session_state.settings.enable_renaming
        )
        st.session_state.settings.enable_renaming = enable_renaming
        
        # Audio settings
        st.sidebar.divider()
        st.sidebar.subheader("Audio Settings")
        
        audio_codec = st.sidebar.selectbox(
            "Audio Codec",
            ["copy", "aac", "ac3", "mp3", "flac"],
            index=["copy", "aac", "ac3", "mp3", "flac"].index(
                getattr(st.session_state.settings, 'audio_codec', 'copy')
            )
        )
        st.session_state.settings.audio_codec = audio_codec
        
        if audio_codec != "copy":
            audio_bitrate = st.sidebar.text_input(
                "Audio Bitrate (e.g., 192k)",
                value=getattr(st.session_state.settings, 'audio_bitrate', '') or ""
            )
            st.session_state.settings.audio_bitrate = audio_bitrate if audio_bitrate else None
            
            normalize_audio = st.sidebar.checkbox(
                "Normalize Audio",
                value=getattr(st.session_state.settings, 'normalize_audio', False)
            )
            st.session_state.settings.normalize_audio = normalize_audio
    
    def render_subtitle_settings(self):
        """Render subtitle settings"""
        st.sidebar.subheader("💬 Subtitle Settings")
        
        # Generation
        enable_generation = st.sidebar.checkbox(
            "Generate with Whisper AI",
            value=st.session_state.settings.enable_subtitle_generation,
            help="Generate subtitles using Whisper AI"
        )
        st.session_state.settings.enable_subtitle_generation = enable_generation
        
        if enable_generation:
            whisper_model = st.sidebar.selectbox(
                "Whisper Model",
                ["tiny", "base", "small", "medium", "large"],
                index=["tiny", "base", "small", "medium", "large"].index(
                    st.session_state.settings.whisper_model
                ),
                help="Larger models are more accurate but slower"
            )
            st.session_state.settings.whisper_model = whisper_model
            
            if st.sidebar.button("🔍 Check Whisper"):
                if st.session_state.core is None:
                    st.session_state.core = FFmpegCore(st.session_state.settings)
                
                result = st.session_state.core.check_whisper()
                
                if result["whisper_available"]:
                    st.sidebar.success("✅ Whisper installed")
                    st.sidebar.info("Models: " + ", ".join(result["installed_models"]))
                else:
                    st.sidebar.error("❌ Whisper not installed")
                    if st.sidebar.button("Install Whisper"):
                        with st.spinner("Installing Whisper..."):
                            install_result = st.session_state.core.install_whisper()
                            if install_result["status"] == "success":
                                st.sidebar.success("✅ Whisper installed successfully")
                            else:
                                st.sidebar.error(f"❌ {install_result['message']}")
        
        # Download
        enable_download = st.sidebar.checkbox(
            "Download from OpenSubtitles",
            value=st.session_state.settings.enable_subtitle_download,
            help="Download subtitles from OpenSubtitles.org"
        )
        st.session_state.settings.enable_subtitle_download = enable_download
        
        if enable_download:
            api_key = st.sidebar.text_input(
                "OpenSubtitles API Key",
                value=st.session_state.settings.opensubtitles_api_key,
                type="password",
                help="Get your free API key from https://www.opensubtitles.com/en/consumers"
            )
            st.session_state.settings.opensubtitles_api_key = api_key
        
        # Languages
        languages = st.sidebar.text_input(
            "Languages (comma-separated)",
            value=",".join(st.session_state.settings.subtitle_languages),
            help="Language codes: eng, spa, fra, etc."
        )
        st.session_state.settings.subtitle_languages = [
            lang.strip() for lang in languages.split(",")
        ]
        
        # Replace existing
        replace_existing = st.sidebar.checkbox(
            "Replace Existing Subtitles",
            value=st.session_state.settings.replace_existing_subtitles
        )
        st.session_state.settings.replace_existing_subtitles = replace_existing
    
    def render_renamer_settings(self):
        """Render renamer settings"""
        st.sidebar.subheader("📝 Renamer Settings")
        
        # TMDB API Key
        tmdb_key = st.sidebar.text_input(
            "TMDB API Key",
            value=st.session_state.settings.tmdb_api_key,
            type="password",
            help="Required for metadata lookup. Get from https://www.themoviedb.org/"
        )
        st.session_state.settings.tmdb_api_key = tmdb_key
        
        # Naming patterns
        pattern_tv = st.sidebar.text_input(
            "TV Show Pattern",
            value=st.session_state.settings.renaming_pattern_tv,
            help="Tokens: {title}, {season}, {episode}, {episodeTitle}"
        )
        st.session_state.settings.renaming_pattern_tv = pattern_tv
        
        pattern_movie = st.sidebar.text_input(
            "Movie Pattern",
            value=st.session_state.settings.renaming_pattern_movie,
            help="Tokens: {title}, {year}"
        )
        st.session_state.settings.renaming_pattern_movie = pattern_movie
    
    def render_encoder_tab(self):
        """Render encoder tab"""
        st.header("🎬 Video Encoder")
        
        # File selection
        col1, col2 = st.columns([3, 1])
        
        with col1:
            uploaded_files = st.file_uploader(
                "Upload video files",
                accept_multiple_files=True,
                type=["mkv", "mp4", "avi", "mov", "wmv", "flv", "webm"]
            )
        
        with col2:
            if st.button("📁 Add Files"):
                if uploaded_files:
                    for file in uploaded_files:
                        if file.name not in [f.name for f in st.session_state.files]:
                            st.session_state.files.append(file)
        
        # Display files
        if st.session_state.files or uploaded_files:
            st.subheader("Queue")
            
            files_to_show = uploaded_files if uploaded_files else st.session_state.files
            
            for i, file in enumerate(files_to_show):
                col1, col2, col3 = st.columns([3, 1, 1])
                
                with col1:
                    st.write(f"📄 {file.name}")
                
                with col2:
                    st.write(f"{file.size / 1024 / 1024:.1f} MB")
                
                with col3:
                    if st.button("Remove", key=f"remove_{i}"):
                        st.session_state.files.pop(i)
                        st.rerun()
            
            # Start button
            if st.button("▶️ Start Conversion", type="primary"):
                self._run_conversion(files_to_show)
        else:
            st.info("👆 Upload files to get started")
    
    def render_subtitle_tab(self):
        """Render subtitle tab"""
        st.header("💬 Subtitle Generator/Downloader")
        
        # File selection
        uploaded_files = st.file_uploader(
            "Upload video files",
            accept_multiple_files=True,
            type=["mkv", "mp4", "avi", "mov", "wmv", "flv", "webm"],
            key="subtitle_uploader"
        )
        
        if uploaded_files:
            st.subheader("Files")
            
            for file in uploaded_files:
                st.write(f"📄 {file.name}")
            
            # Generate button
            if st.button("🎙️ Generate/Download Subtitles", type="primary"):
                if not st.session_state.settings.enable_subtitle_generation and \
                   not st.session_state.settings.enable_subtitle_download:
                    st.error("Please enable subtitle generation or download in settings")
                else:
                    self._run_subtitle_processing(uploaded_files)
        else:
            st.info("👆 Upload files to get started")
    
    def render_renamer_tab(self):
        """Render renamer tab"""
        st.header("📝 Media File Renamer")
        
        if not st.session_state.settings.tmdb_api_key:
            st.warning("⚠️ TMDB API key required. Add it in the settings sidebar.")
            st.info("Get a free API key at: https://www.themoviedb.org/settings/api")
            return
        
        # File selection
        uploaded_files = st.file_uploader(
            "Upload media files",
            accept_multiple_files=True,
            type=["mkv", "mp4", "avi", "mov", "wmv", "flv", "webm"],
            key="renamer_uploader"
        )
        
        if uploaded_files:
            # Preview button
            if st.button("👀 Preview Renames"):
                if st.session_state.core is None:
                    st.session_state.core = FFmpegCore(st.session_state.settings)
                
                with st.spinner("Fetching metadata..."):
                    # Save files temporarily for preview
                    file_paths = []
                    for file in uploaded_files:
                        temp_path = Path("/tmp") / file.name
                        temp_path.write_bytes(file.read())
                        file_paths.append(str(temp_path))
                    
                    result = st.session_state.core.preview_rename(file_paths)
                    
                    if result["status"] == "success":
                        st.session_state.rename_previews = result["previews"]
            
            # Display previews
            if st.session_state.rename_previews:
                st.subheader("📋 Rename Preview")
                
                for preview in st.session_state.rename_previews:
                    with st.expander(preview["original_name"]):
                        col1, col2 = st.columns(2)
                        
                        with col1:
                            st.write("**Original:**")
                            st.code(preview["original_name"])
                        
                        with col2:
                            st.write("**New Name:**")
                            st.code(preview["new_name"])
                        
                        st.write(f"**Type:** {preview['media_type']}")
                        
                        if not preview["can_rename"]:
                            st.warning("⚠️ Cannot rename (no metadata found)")
                        
                        if preview.get("metadata"):
                            st.json(preview["metadata"])
                
                # Rename button
                if st.button("✅ Apply Renames", type="primary"):
                    self._run_renaming(uploaded_files)
        else:
            st.info("👆 Upload files to get started")
    
    def run(self):
        """Main entry point"""
        st.set_page_config(
            page_title="FFmpeg Batch Transcoder",
            page_icon="🎬",
            layout="wide"
        )
        
        st.title("🎬 FFmpeg Batch Transcoder")
        
        # Render sidebar
        self.render_sidebar()
        
        # Render main content based on mode
        if st.session_state.mode == "encoder":
            self.render_encoder_tab()
        elif st.session_state.mode == "subtitle":
            self.render_subtitle_tab()
        elif st.session_state.mode == "renamer":
            self.render_renamer_tab()
    
    def _save_uploaded_files(self, uploaded_files) -> List[str]:
        """Save uploaded files to temporary directory and return paths"""
        temp_paths = []
        
        for file in uploaded_files:
            # Create temporary file
            temp_dir = tempfile.mkdtemp()
            temp_path = Path(temp_dir) / file.name
            
            # Write file content
            temp_path.write_bytes(file.read())
            temp_paths.append(str(temp_path))
            
            # Track for cleanup
            st.session_state.temp_files.append(str(temp_path))
        
        return temp_paths
    
    def _run_conversion(self, uploaded_files):
        """Run video conversion process"""
        if st.session_state.core is None:
            st.session_state.core = FFmpegCore(st.session_state.settings)
        
        # Save uploaded files to temporary paths
        with st.spinner("Saving files..."):
            temp_paths = self._save_uploaded_files(uploaded_files)
        
        # Progress tracking
        progress_bar = st.progress(0)
        status_text = st.empty()
        results_container = st.container()
        
        def progress_callback(data):
            if data.get("status") == "progress":
                progress = data.get("progress", 0)
                progress_bar.progress(progress / 100)
                status_text.text(f"Converting: {data.get('current_file', '')} - {progress:.1f}%")
            elif data.get("status") == "complete":
                progress_bar.progress(1.0)
                status_text.text("Conversion complete!")
        
        # Run conversion
        with st.spinner("Converting files..."):
            result = st.session_state.core.convert_files(temp_paths, progress_callback)
        
        # Display results
        with results_container:
            st.subheader("Conversion Results")
            
            if result["status"] == "success":
                st.success(f"✅ Successfully converted {len(result['successful'])} files")
                
                for success in result["successful"]:
                    st.write(f"✅ {Path(success['input_file']).name} → {Path(success['output_file']).name}")
                    
                    # Offer download
                    if Path(success['output_file']).exists():
                        with open(success['output_file'], 'rb') as f:
                            st.download_button(
                                f"Download {Path(success['output_file']).name}",
                                f.read(),
                                file_name=Path(success['output_file']).name,
                                key=f"download_{success['output_file']}"
                            )
            
            elif result["status"] == "error":
                st.error(f"❌ Conversion failed: {result.get('message', 'Unknown error')}")
            
            elif result["status"] == "partial":
                st.warning(f"⚠️ Partial success: {len(result['successful'])} succeeded, {len(result['failed'])} failed")
                
                for success in result["successful"]:
                    st.write(f"✅ {Path(success['input_file']).name}")
                
                for failure in result["failed"]:
                    st.write(f"❌ {Path(failure['input_file']).name}: {failure['error']}")
    
    def _run_subtitle_processing(self, uploaded_files):
        """Run subtitle generation/download process"""
        if st.session_state.core is None:
            st.session_state.core = FFmpegCore(st.session_state.settings)
        
        # Save uploaded files to temporary paths
        with st.spinner("Saving files..."):
            temp_paths = self._save_uploaded_files(uploaded_files)
        
        results_container = st.container()
        
        with st.spinner("Processing subtitles..."):
            for temp_path in temp_paths:
                file_name = Path(temp_path).name
                st.write(f"Processing: {file_name}")
                
                # Generate subtitles if enabled
                if st.session_state.settings.enable_subtitle_generation:
                    for language in st.session_state.settings.subtitle_languages:
                        with st.spinner(f"Generating {language} subtitles for {file_name}..."):
                            result = st.session_state.core.generate_subtitles(temp_path, language)
                            
                            if result["status"] == "success":
                                st.success(f"✅ Generated {language} subtitles for {file_name}")
                                
                                # Offer download if subtitle file exists
                                if "subtitle_file" in result and Path(result["subtitle_file"]).exists():
                                    with open(result["subtitle_file"], 'r', encoding='utf-8') as f:
                                        st.download_button(
                                            f"Download {language} subtitles for {file_name}",
                                            f.read(),
                                            file_name=f"{Path(file_name).stem}_{language}.srt",
                                            key=f"sub_{language}_{file_name}"
                                        )
                            else:
                                st.error(f"❌ Failed to generate {language} subtitles for {file_name}: {result.get('message', 'Unknown error')}")
                
                # Download subtitles if enabled
                if st.session_state.settings.enable_subtitle_download:
                    with st.spinner(f"Downloading subtitles for {file_name}..."):
                        result = st.session_state.core.download_subtitles(temp_path, st.session_state.settings.subtitle_languages)
                        
                        if result["status"] == "success":
                            st.success(f"✅ Downloaded subtitles for {file_name}")
                            
                            for sub_file in result.get("downloaded_files", []):
                                if Path(sub_file).exists():
                                    with open(sub_file, 'r', encoding='utf-8') as f:
                                        st.download_button(
                                            f"Download {Path(sub_file).name}",
                                            f.read(),
                                            file_name=Path(sub_file).name,
                                            key=f"download_sub_{sub_file}"
                                        )
                        else:
                            st.error(f"❌ Failed to download subtitles for {file_name}: {result.get('message', 'Unknown error')}")
    
    def _run_renaming(self, uploaded_files):
        """Run file renaming process"""
        if st.session_state.core is None:
            st.session_state.core = FFmpegCore(st.session_state.settings)
        
        # Save uploaded files to temporary paths
        with st.spinner("Saving files..."):
            temp_paths = self._save_uploaded_files(uploaded_files)
        
        with st.spinner("Renaming files..."):
            result = st.session_state.core.rename_files(temp_paths, dry_run=False)
            
            if result["status"] == "success":
                st.success("✅ Files renamed successfully!")
                
                for item in result["results"]:
                    if item["success"]:
                        st.write(f"✅ {item['message']}")
                        
                        # Offer download of renamed file
                        if "new_path" in item and Path(item["new_path"]).exists():
                            with open(item["new_path"], 'rb') as f:
                                st.download_button(
                                    f"Download {Path(item['new_path']).name}",
                                    f.read(),
                                    file_name=Path(item["new_path"]).name,
                                    key=f"renamed_{item['new_path']}"
                                )
                    else:
                        st.write(f"❌ {item['message']}")
            else:
                st.error(f"❌ Renaming failed: {result.get('message', 'Unknown error')}")


def main():
    """Entry point"""
    webui = FFmpegWebUI()
    webui.run()


if __name__ == "__main__":
    main()