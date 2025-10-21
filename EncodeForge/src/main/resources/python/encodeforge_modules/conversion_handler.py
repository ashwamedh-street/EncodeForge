#!/usr/bin/env python3
"""
Conversion Handler - Video conversion and encoding operations
"""

import json
import logging
import os
import signal
import subprocess
import time
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

from .models import ConversionSettings

logger = logging.getLogger(__name__)


class ConversionHandler:
    """Handles video file conversion and encoding with hardware acceleration"""
    
    def __init__(self, settings: ConversionSettings, ffmpeg_mgr):
        self.settings = settings
        self.ffmpeg_mgr = ffmpeg_mgr
        self.current_process: Optional[subprocess.Popen] = None
        self.cancel_requested: bool = False
        
        # Queue state tracking for recovery
        self._file_queue: List[Dict] = []  # Full queue with status
        self._current_index: int = 0
        self._completed_files: List[str] = []
        self._failed_files: List[Dict] = []
    
    def _select_best_encoder(self, is_10bit: bool = False) -> Dict[str, Any]:
        """
        Determine the best available encoder (hardware or software fallback)
        
        Args:
            is_10bit: Whether the source video is 10-bit
        
        Returns:
            Dict with 'type', 'codec', 'platform', and 'needs_conversion' keys
        """
        hwaccel_options = self.ffmpeg_mgr.get_hwaccel_options()
        logger.info(f"Hardware acceleration options: {hwaccel_options}")
        logger.info(f"Settings - use_nvenc: {self.settings.use_nvenc}, use_amf: {self.settings.use_amf}, use_qsv: {self.settings.use_qsv}")
        logger.info(f"Source is 10-bit: {is_10bit}")
        
        # For 10-bit sources, prefer HEVC hardware encoders or software with conversion
        if is_10bit:
            logger.info("10-bit source detected - selecting appropriate encoder")
            
            # Try HEVC hardware encoders first (they support 10-bit)
            if self.settings.use_nvenc and "nvenc" in hwaccel_options.get("encode", []):
                logger.info("Selected NVENC HEVC hardware encoder for 10-bit source")
                return {"type": "hardware", "codec": "hevc_nvenc", "platform": "nvidia", "needs_conversion": False}
            
            if self.settings.use_amf and "amf" in hwaccel_options.get("encode", []):
                logger.info("Selected AMF HEVC hardware encoder for 10-bit source")
                return {"type": "hardware", "codec": "hevc_amf", "platform": "amd", "needs_conversion": False}
            
            if self.settings.use_qsv and "qsv" in hwaccel_options.get("encode", []):
                logger.info("Selected QSV HEVC hardware encoder for 10-bit source")
                return {"type": "hardware", "codec": "hevc_qsv", "platform": "intel", "needs_conversion": False}
            
            if self.settings.use_videotoolbox and "videotoolbox" in hwaccel_options.get("encode", []):
                logger.info("Selected VideoToolbox HEVC hardware encoder for 10-bit source")
                return {"type": "hardware", "codec": "hevc_videotoolbox", "platform": "apple", "needs_conversion": False}
            
            # If HEVC not available, use H.264 hardware with 8-bit conversion
            if self.settings.use_nvenc and "nvenc" in hwaccel_options.get("encode", []):
                logger.info("Using NVENC H.264 with 10-bit to 8-bit conversion")
                return {"type": "hardware", "codec": "h264_nvenc", "platform": "nvidia", "needs_conversion": True}
            
            if self.settings.use_amf and "amf" in hwaccel_options.get("encode", []):
                logger.info("Using AMF H.264 with 10-bit to 8-bit conversion")
                return {"type": "hardware", "codec": "h264_amf", "platform": "amd", "needs_conversion": True}
            
            if self.settings.use_qsv and "qsv" in hwaccel_options.get("encode", []):
                logger.info("Using QSV H.264 with 10-bit to 8-bit conversion")
                return {"type": "hardware", "codec": "h264_qsv", "platform": "intel", "needs_conversion": True}
            
            if self.settings.use_videotoolbox and "videotoolbox" in hwaccel_options.get("encode", []):
                logger.info("Using VideoToolbox H.264 with 10-bit to 8-bit conversion")
                return {"type": "hardware", "codec": "h264_videotoolbox", "platform": "apple", "needs_conversion": True}
            
            # Fallback to software (supports 10-bit natively)
            logger.info("Using software encoder (supports 10-bit natively)")
            return {"type": "software", "codec": self.settings.video_codec_fallback, "platform": "cpu", "needs_conversion": False}
        
        # For 8-bit sources, use H.264 hardware encoders
        if self.settings.use_nvenc and "nvenc" in hwaccel_options.get("encode", []):
            logger.info("Selected NVENC H.264 hardware encoder")
            return {"type": "hardware", "codec": "h264_nvenc", "platform": "nvidia", "needs_conversion": False}
        
        if self.settings.use_amf and "amf" in hwaccel_options.get("encode", []):
            logger.info("Selected AMF H.264 hardware encoder")
            return {"type": "hardware", "codec": "h264_amf", "platform": "amd", "needs_conversion": False}
        
        if self.settings.use_qsv and "qsv" in hwaccel_options.get("encode", []):
            logger.info("Selected QSV H.264 hardware encoder")
            return {"type": "hardware", "codec": "h264_qsv", "platform": "intel", "needs_conversion": False}
        
        if self.settings.use_videotoolbox and "videotoolbox" in hwaccel_options.get("encode", []):
            logger.info("Selected VideoToolbox H.264 hardware encoder")
            return {"type": "hardware", "codec": "h264_videotoolbox", "platform": "apple", "needs_conversion": False}
        
        # Fallback to software encoding
        logger.info("No hardware encoders available or enabled, falling back to software")
        return {"type": "software", "codec": self.settings.video_codec_fallback, "platform": "cpu", "needs_conversion": False}
    
    def _is_hardware_encoder_error(self, error_output: str) -> bool:
        """Check if error is related to hardware encoder failure"""
        hw_error_indicators = [
            "cannot load",
            "not available",
            "not found",
            "nvenc",
            "amf",
            "qsv",
            "videotoolbox",
            "cuda",
            "driver",
            "gpu"
        ]
        error_lower = error_output.lower()
        return any(indicator in error_lower for indicator in hw_error_indicators)
    
    def _parse_ffmpeg_progress(self, line: str) -> Optional[Dict]:
        """Parse FFmpeg progress information from output line"""
        import re
        
        # FFmpeg progress lines typically look like:
        # frame=  123 fps= 45 q=28.0 size=    1024kB time=00:00:05.12 bitrate=1638.4kbits/s speed=1.8x
        # Also handle progress pipe format: out_time_us=5120000
        
        # Handle progress pipe format first (more reliable)
        if 'out_time_us=' in line:
            try:
                time_match = re.search(r'out_time_us=(\d+)', line)
                if time_match:
                    microseconds = int(time_match.group(1))
                    seconds = microseconds / 1_000_000
                    
                    progress_data = {
                        'time': seconds,
                        'status': 'processing',
                        'file': getattr(self, '_current_file', 'Unknown')
                    }
                    
                    # Calculate progress percentage if we have duration
                    if hasattr(self, '_total_duration') and self._total_duration > 0:
                        progress_data['progress'] = min(100, int((seconds / self._total_duration) * 100))
                        
                        # Calculate ETA
                        remaining_time = self._total_duration - seconds
                        if remaining_time > 0:
                            if remaining_time < 60:
                                progress_data['eta'] = f"{int(remaining_time)}s"
                            elif remaining_time < 3600:
                                minutes = int(remaining_time // 60)
                                secs = int(remaining_time % 60)
                                progress_data['eta'] = f"{minutes}m {secs}s"
                            else:
                                hours = int(remaining_time // 3600)
                                minutes = int((remaining_time % 3600) // 60)
                                progress_data['eta'] = f"{hours}h {minutes}m"
                        else:
                            progress_data['eta'] = "Almost done"
                    else:
                        progress_data['eta'] = "Calculating..."
                    
                    logger.debug(f"Parsed progress: {progress_data}")
                    return progress_data
            except Exception as e:
                logger.debug(f"Error parsing progress pipe format: {e}")
        
        # Fallback to traditional format (more reliable - what -stats outputs)
        if 'frame=' in line and 'time=' in line:
            try:
                progress_data = {}
                
                # Extract frame number
                frame_match = re.search(r'frame=\s*(\d+)', line)
                if frame_match:
                    progress_data['frame'] = int(frame_match.group(1))
                
                # Extract FPS
                fps_match = re.search(r'fps=\s*([\d.]+)', line)
                if fps_match:
                    progress_data['fps'] = float(fps_match.group(1))
                
                # Extract time (format: HH:MM:SS.ss)
                time_match = re.search(r'time=(\d{2}):(\d{2}):(\d{2})\.(\d{2})', line)
                if time_match:
                    hours = int(time_match.group(1))
                    minutes = int(time_match.group(2))
                    seconds = int(time_match.group(3))
                    centiseconds = int(time_match.group(4))
                    total_seconds = hours * 3600 + minutes * 60 + seconds + centiseconds / 100
                    progress_data['time'] = total_seconds
                
                # Extract bitrate
                bitrate_match = re.search(r'bitrate=\s*([\d.]+)kbits/s', line)
                if bitrate_match:
                    progress_data['bitrate'] = f"{bitrate_match.group(1)}kbits/s"
                
                # Extract speed
                speed_match = re.search(r'speed=\s*([\d.]+)x', line)
                if speed_match:
                    progress_data['speed'] = f"{speed_match.group(1)}x"
                
                # Extract size
                size_match = re.search(r'size=\s*(\d+)kB', line)
                if size_match:
                    progress_data['size'] = f"{size_match.group(1)}kB"
                
                if progress_data:
                    # Add status and file info
                    progress_data['status'] = 'processing'
                    progress_data['file'] = getattr(self, '_current_file', 'Unknown')
                    
                    # Calculate progress percentage
                    # Prefer frame-based progress (more accurate) over time-based
                    if 'frame' in progress_data and hasattr(self, '_total_frames') and self._total_frames > 0:
                        progress_data['progress'] = min(100, int((progress_data['frame'] / self._total_frames) * 100))
                    elif 'time' in progress_data and hasattr(self, '_total_duration') and self._total_duration > 0:
                        progress_data['progress'] = min(100, int((progress_data['time'] / self._total_duration) * 100))
                    
                    # Calculate ETA if we have enough data
                    if 'fps' in progress_data and progress_data['fps'] > 0:
                        if 'frame' in progress_data and hasattr(self, '_total_frames') and self._total_frames > 0:
                            # Frame-based ETA calculation (most accurate)
                            remaining_frames = self._total_frames - progress_data['frame']
                            estimated_seconds = remaining_frames / progress_data['fps']
                        elif 'time' in progress_data and hasattr(self, '_total_duration') and self._total_duration > 0:
                            # Time-based ETA calculation
                            estimated_seconds = self._total_duration - progress_data['time']
                        else:
                            estimated_seconds = 0
                        
                        if estimated_seconds > 0:
                            if estimated_seconds < 60:
                                progress_data['eta'] = f"{int(estimated_seconds)}s"
                            elif estimated_seconds < 3600:
                                minutes = int(estimated_seconds // 60)
                                seconds = int(estimated_seconds % 60)
                                progress_data['eta'] = f"{minutes}m {seconds}s"
                            else:
                                hours = int(estimated_seconds // 3600)
                                minutes = int((estimated_seconds % 3600) // 60)
                                progress_data['eta'] = f"{hours}h {minutes}m"
                        else:
                            progress_data['eta'] = "Almost done"
                    else:
                        progress_data['eta'] = "Calculating..."
                    
                    return progress_data
                    
            except Exception as e:
                logger.debug(f"Error parsing FFmpeg progress: {e}")
        
        return None
    
    def _get_video_duration(self, input_path: str) -> float:
        """Get video duration in seconds using ffprobe"""
        try:
            cmd = [
                self.settings.ffprobe_path,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                input_path
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                import json
                data = json.loads(result.stdout)
                duration = float(data.get("format", {}).get("duration", 0))
                logger.debug(f"Video duration: {duration:.2f} seconds")
                return duration
                
        except Exception as e:
            logger.debug(f"Error getting video duration: {e}")
        
        return 0.0  # Return 0 if duration cannot be determined
    
    def _get_video_info(self, input_path: str) -> Dict:
        """Get video duration and frame count using ffprobe for accurate progress calculation"""
        try:
            cmd = [
                self.settings.ffprobe_path,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                "-select_streams", "v:0",
                input_path
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                data = json.loads(result.stdout)
                duration = float(data.get("format", {}).get("duration", 0))
                
                streams = data.get("streams", [])
                total_frames = 0
                fps = 0
                if streams:
                    # Try nb_frames first (most accurate if available)
                    total_frames = int(streams[0].get("nb_frames", 0))
                    
                    # Get FPS for fallback calculation
                    r_frame_rate = streams[0].get("r_frame_rate", "0/1")
                    try:
                        if "/" in r_frame_rate:
                            num, den = r_frame_rate.split("/")
                            fps = float(num) / float(den) if float(den) > 0 else 0
                        else:
                            fps = float(r_frame_rate)
                    except (ValueError, ZeroDivisionError):
                        fps = 0
                    
                    # If nb_frames not available, calculate from duration and fps
                    if not total_frames and duration > 0 and fps > 0:
                        total_frames = int(duration * fps)
                
                logger.debug(f"Video info: duration={duration:.2f}s, frames={total_frames}, fps={fps:.2f}")
                
                return {
                    "duration": duration,
                    "total_frames": total_frames,
                    "fps": fps
                }
                
        except Exception as e:
            logger.debug(f"Error getting video info: {e}")
        
        return {"duration": 0.0, "total_frames": 0, "fps": 0}
    
    def _get_pixel_format(self, input_path: str) -> Optional[str]:
        """Get pixel format of video stream using ffprobe"""
        try:
            cmd = [
                self.settings.ffprobe_path,
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-select_streams", "v:0",
                input_path
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                data = json.loads(result.stdout)
                streams = data.get("streams", [])
                if streams:
                    pix_fmt = streams[0].get("pix_fmt", "")
                    logger.info(f"Source pixel format: {pix_fmt}")
                    return pix_fmt
                
        except Exception as e:
            logger.debug(f"Error getting pixel format: {e}")
        
        return None
    
    def _is_10bit_format(self, pix_fmt: Optional[str]) -> bool:
        """Check if pixel format is 10-bit"""
        if not pix_fmt:
            return False
        
        # 10-bit formats typically end with '10le', '10be', or 'p10'
        ten_bit_indicators = ['10le', '10be', 'p10', '10']
        return any(indicator in pix_fmt.lower() for indicator in ten_bit_indicators)
    
    def _supports_10bit(self, encoder: str) -> bool:
        """Check if encoder supports 10-bit encoding"""
        # HEVC encoders generally support 10-bit
        if 'hevc' in encoder.lower() or 'h265' in encoder.lower():
            return True
        
        # H.264 hardware encoders typically do NOT support 10-bit
        if 'h264' in encoder.lower():
            if any(hw in encoder.lower() for hw in ['nvenc', 'amf', 'qsv', 'videotoolbox']):
                return False
        
        # Software encoders (libx264/libx265) support 10-bit
        if encoder in ['libx264', 'libx265']:
            return True
        
        return False
    
    def _retry_with_software_encoder(self, input_file: Path, output_path_obj: Path, 
                                     progress_callback: Optional[Callable] = None) -> Dict:
        """Retry conversion using software encoder as fallback"""
        logger.warning("⚠️ Retrying with software encoder...")
        
        # Build software encoding command
        cmd = [
            self.settings.ffmpeg_path,
            "-hide_banner",
            "-loglevel", "error",
            "-i", str(input_file),
            "-c:v", self.settings.video_codec_fallback,
            "-preset", self.settings.video_preset,
            "-crf", str(self.settings.video_crf)
        ]
        
        # Add audio codec
        if self.settings.audio_codec == "copy":
            cmd.extend(["-c:a", "copy"])
        else:
            cmd.extend(["-c:a", self.settings.audio_codec])
            if self.settings.audio_bitrate:
                cmd.extend(["-b:a", self.settings.audio_bitrate])
        
        # Add faststart for MP4
        if self.settings.use_faststart and self.settings.output_format in ["mp4", "m4v"]:
            cmd.extend(["-movflags", "+faststart"])
        
        cmd.extend(["-y", str(output_path_obj)])
        
        logger.info(f"Software encoding command: {' '.join(cmd)}")
        
        try:
            self.current_process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True
            )
            
            stdout, stderr = self.current_process.communicate()
            
            if self.current_process.returncode == 0:
                logger.info("✅ Software encoding completed successfully")
                return {
                    "status": "success",
                    "message": "Conversion completed (software encoder)",
                    "output_path": str(output_path_obj),
                    "encoder": "software"
                }
            else:
                logger.error(f"Software encoding also failed: {stderr}")
                return {
                    "status": "error",
                    "message": f"Software encoding failed: {stderr}",
                    "encoder": "software"
                }
        except Exception as e:
            logger.error(f"Error during software encoding: {e}")
            return {
                "status": "error",
                "message": f"Software encoding error: {str(e)}",
                "encoder": "software"
            }
    
    def convert_file(
        self,
        input_path: str,
        output_path: Optional[str] = None,
        progress_callback: Optional[Callable] = None
    ) -> Dict:
        """Convert a single video file with the current settings"""
        try:
            input_file = Path(input_path)
            
            # Validate input
            if not input_file.exists():
                return {"status": "error", "message": f"Input file not found: {input_path}"}
            
            if not input_file.is_file():
                return {"status": "error", "message": f"Input path is not a file: {input_path}"}
            
            # Check file size
            file_size = input_file.stat().st_size
            if file_size == 0:
                return {"status": "error", "message": f"Input file is empty: {input_path}"}
            
            logger.info(f"Input file size: {file_size / (1024**2):.2f} MB")
            
            # Set current file for progress tracking
            self._current_file = input_file.name
            
            # Get video info for progress calculation (duration, frames, fps)
            video_info = self._get_video_info(str(input_file))
            self._total_duration = video_info.get("duration", 0)
            self._total_frames = video_info.get("total_frames", 0)
            logger.info(f"Video info: {self._total_duration:.1f}s, {self._total_frames} frames")
            
            # Detect pixel format for encoder selection
            pixel_format = self._get_pixel_format(str(input_file))
            is_10bit = self._is_10bit_format(pixel_format)
            
            if is_10bit:
                logger.info(f"10-bit source detected: {pixel_format}")
            
            # Generate output path
            if output_path is None:
                output_dir = input_file.parent
                base_name = input_file.stem
                
                if input_file.suffix.lower().lstrip('.') == self.settings.output_format.lower():
                    if self.settings.output_suffix:
                        output_path_obj = output_dir / f"{base_name}{self.settings.output_suffix}.{self.settings.output_format}"
                    else:
                        output_path_obj = output_dir / f"{base_name}_converted.{self.settings.output_format}"
                else:
                    if self.settings.output_suffix:
                        output_path_obj = output_dir / f"{base_name}{self.settings.output_suffix}.{self.settings.output_format}"
                    else:
                        output_path_obj = output_dir / f"{base_name}.{self.settings.output_format}"
                
                # Generate unique name if needed
                if output_path_obj.exists() and not self.settings.overwrite_existing:
                    counter = 1
                    while output_path_obj.exists():
                        if self.settings.output_suffix:
                            output_path_obj = output_dir / f"{base_name}{self.settings.output_suffix}_{counter}.{self.settings.output_format}"
                        else:
                            output_path_obj = output_dir / f"{base_name}_{counter}.{self.settings.output_format}"
                        counter += 1
            else:
                output_path_obj = Path(output_path)
                if output_path_obj.exists() and not self.settings.overwrite_existing:
                    return {"status": "error", "message": f"Output file already exists: {output_path_obj}"}
            
            # Build FFmpeg command with progress output enabled
            # Use -progress pipe:2 to force progress output to stderr even when piped
            # According to FFmpeg docs, pipe:2 explicitly sends to stderr (fd 2)
            cmd = [
                self.settings.ffmpeg_path,
                "-hide_banner",
                "-loglevel", "error",  # Only errors to keep stderr clean
                "-progress", "pipe:2",  # Force progress to stderr in key=value format
                "-i", str(input_file)
            ]
            
            logger.info(f"FFmpeg command: {' '.join(cmd)}")
            
            # Select encoder based on pixel format
            encoder_info = self._select_best_encoder(is_10bit=is_10bit)
            logger.info(f"Using encoder: {encoder_info['codec']} ({encoder_info['type']})")
            
            # Add pixel format conversion if needed
            if encoder_info.get("needs_conversion", False):
                logger.info("Adding pixel format conversion: 10-bit -> 8-bit (yuv420p)")
                cmd.extend(["-pix_fmt", "yuv420p"])
            
            if encoder_info["type"] == "hardware":
                cmd.extend(["-c:v", encoder_info["codec"]])
                
                # Hardware-specific optimizations (works for both H.264 and HEVC variants)
                if encoder_info["codec"].endswith("nvenc"):
                    cmd.extend([
                        "-preset", self.settings.nvenc_preset,
                        "-cq", str(self.settings.nvenc_cq),
                        "-rc", "vbr"
                    ])
                    logger.info(f"NVENC quality: preset={self.settings.nvenc_preset}, cq={self.settings.nvenc_cq}")
                elif encoder_info["codec"].endswith("amf"):
                    # Add QP values for all frame types to control quality (prevents massive file sizes)
                    cmd.extend([
                        "-quality", "balanced",
                        "-rc", "vbr_latency",
                        "-qp_i", "23",  # I-frame quality
                        "-qp_p", "23",  # P-frame quality
                        "-qp_b", "23"   # B-frame quality
                    ])
                    logger.info("AMF quality: balanced, QP=23 for all frame types")
                elif encoder_info["codec"].endswith("qsv"):
                    cmd.extend([
                        "-preset", "medium",
                        "-global_quality", "23"
                    ])
                    logger.info("QSV quality: medium preset, global_quality=23")
                elif encoder_info["codec"].endswith("videotoolbox"):
                    cmd.extend(["-b:v", "5M"])
                    logger.info("VideoToolbox bitrate: 5M")
            else:
                cmd.extend([
                    "-c:v", encoder_info["codec"],
                    "-preset", self.settings.video_preset,
                    "-crf", str(self.settings.video_crf)
                ])
            
            # Audio codec
            if self.settings.audio_codec == "copy":
                cmd.extend(["-c:a", "copy"])
            else:
                cmd.extend(["-c:a", self.settings.audio_codec])
                if self.settings.audio_bitrate:
                    cmd.extend(["-b:a", self.settings.audio_bitrate])
            
            # Subtitle handling
            if self.settings.convert_subtitles:
                # Handle "auto" subtitle format by choosing appropriate codec based on output format
                if self.settings.subtitle_format == "auto":
                    if self.settings.output_format.lower() in ["mp4", "m4v"]:
                        subtitle_codec = "mov_text"  # MP4 compatible
                    elif self.settings.output_format.lower() in ["mkv", "webm"]:
                        subtitle_codec = "srt"  # MKV compatible
                    else:
                        subtitle_codec = "srt"  # Default fallback
                else:
                    subtitle_codec = self.settings.subtitle_format
                
                cmd.extend(["-c:s", subtitle_codec])
            else:
                cmd.extend(["-sn"])  # No subtitles
            
            # MP4 faststart
            if self.settings.use_faststart and self.settings.output_format in ["mp4", "m4v"]:
                cmd.extend(["-movflags", "+faststart"])
            
            # Target resolution
            if self.settings.target_resolution:
                cmd.extend(["-vf", f"scale={self.settings.target_resolution}"])
            
            # Output
            cmd.extend(["-y", str(output_path_obj)])
            
            logger.info(f"Starting conversion: {input_file.name} -> {output_path_obj.name}")
            
            # Execute conversion
            # On Unix, start FFmpeg in its own process group so we can kill the entire tree
            if os.name != 'nt':
                # Unix: use preexec_fn to create new process group
                self.current_process = subprocess.Popen(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,  # Keep stderr separate to capture progress
                    universal_newlines=True,
                    bufsize=1,  # Line buffered
                    preexec_fn=os.setsid  # Create new process group
                )
            else:
                # Windows: CREATE_NEW_PROCESS_GROUP flag is set automatically
                self.current_process = subprocess.Popen(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,  # Keep stderr separate to capture progress
                    universal_newlines=True,
                    bufsize=1,  # Line buffered
                    creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0
                )
            
            # Monitor progress in real-time
            import threading
            
            stdout_lines = []
            stderr_lines = []
            
            if progress_callback and self.current_process:
                # Read stderr for progress (from -stats) since FFmpeg outputs stats to stderr
                logger.info("Starting stderr monitoring thread for progress...")
                
                def read_stderr():
                    """Read stderr for progress data and errors"""
                    logger.info("Stderr reading thread started")
                    progress_count = 0
                    last_progress_time = 0
                    last_progress_data = None
                    line_count = 0
                    
                    while True:
                        if self.cancel_requested:
                            logger.info("Cancel requested, stopping stderr thread")
                            break
                        
                        if self.current_process and self.current_process.stderr:
                            line = self.current_process.stderr.readline()
                            if not line:
                                logger.info("Stderr EOF reached")
                                # Send final progress update
                                if last_progress_data:
                                    try:
                                        progress_callback(last_progress_data)
                                        logger.info("Sent final progress update")
                                    except Exception as cb_error:
                                        logger.error(f"Error in final progress callback: {cb_error}")
                                break
                        else:
                            break
                        
                        line_count += 1
                        stderr_lines.append(line)
                        
                        # DEBUG: Log every line to see what format FFmpeg is using
                        if line.strip():
                            logger.debug(f"STDERR LINE: {line.strip()}")
                        
                        # Parse progress data (both traditional and pipe formats)
                        progress_info = self._parse_ffmpeg_progress(line.strip())
                        if progress_info:
                            last_progress_data = progress_info
                            current_time = time.time()
                            
                            # Throttle to once every 2 seconds
                            if current_time - last_progress_time >= 2.0:
                                last_progress_time = current_time
                                progress_count += 1
                                logger.info(f"✓ Progress: {progress_info.get('progress', 0)}% | FPS: {progress_info.get('fps', 'N/A')} | ETA: {progress_info.get('eta', 'N/A')}")
                                try:
                                    progress_callback(progress_info)
                                except Exception as cb_error:
                                    logger.error(f"Error in progress callback: {cb_error}")
                        else:
                            # Log errors and important messages
                            line_lower = line.lower().strip()
                            if any(keyword in line_lower for keyword in ['error', 'failed', 'cannot', 'invalid']):
                                logger.error(f"FFmpeg ERROR: {line.strip()}")
                            elif 'warning' in line_lower:
                                logger.warning(f"FFmpeg: {line.strip()}")
                            elif 'stream mapping' in line_lower or line_lower.startswith('encoder'):
                                logger.info(f"FFmpeg: {line.strip()}")
                    
                    logger.info(f"Stderr thread finished: {line_count} lines read, {progress_count} progress updates sent")
                
                # Start stderr reading thread
                stderr_thread = threading.Thread(target=read_stderr, daemon=True)
                stderr_thread.start()
                logger.info("Stderr monitoring thread launched")
                
                # Read stdout (should be empty, but capture it anyway)
                stdout_lines = []
                while True:
                    if self.cancel_requested:
                        logger.info("Cancel requested, terminating process")
                        self.current_process.terminate()
                        break
                    
                    if self.current_process and self.current_process.stdout:
                        line = self.current_process.stdout.readline()
                        if not line:
                            break
                        stdout_lines.append(line)
                    else:
                        break
                
                # Wait for process to complete
                logger.info("Waiting for FFmpeg process to complete...")
                self.current_process.wait()
                logger.info(f"FFmpeg process completed with return code: {self.current_process.returncode}")
                
                # Wait for stderr thread to finish
                stderr_thread.join(timeout=2)
                if stderr_thread.is_alive():
                    logger.warning("Stderr thread still running after 2s timeout")
                
                stdout = ''.join(stdout_lines)
                stderr = ''.join(stderr_lines)
            else:
                # No progress monitoring, use communicate
                stdout, stderr = self.current_process.communicate()
            
            # Check if cancelled
            if self.cancel_requested:
                self.cancel_requested = False
                return {"status": "cancelled", "message": "Conversion cancelled by user"}
            
            # Check result
            if self.current_process.returncode == 0:
                # Verify output file
                if output_path_obj.exists() and output_path_obj.stat().st_size > 0:
                    logger.info(f"✅ Conversion completed: {output_path_obj.name}")
                    
                    # Delete original if requested
                    if self.settings.delete_original and input_file != output_path_obj:
                        try:
                            input_file.unlink()
                            logger.info(f"🗑️ Deleted original file: {input_file.name}")
                        except Exception as e:
                            logger.warning(f"Could not delete original file: {e}")
                    
                    return {
                        "status": "success",
                        "message": "Conversion completed successfully",
                        "output_path": str(output_path_obj),
                        "encoder": encoder_info["codec"]
                    }
                else:
                    return {"status": "error", "message": "Output file is empty or missing"}
            else:
                # Check if it's a hardware encoder error
                if encoder_info["type"] == "hardware" and self._is_hardware_encoder_error(stderr):
                    logger.warning("Hardware encoder failed, trying software fallback...")
                    return self._retry_with_software_encoder(input_file, output_path_obj, progress_callback)
                else:
                    logger.error(f"Conversion failed: {stderr}")
                    return {
                        "status": "error",
                        "message": f"FFmpeg error: {stderr}"
                    }
        
        except Exception as e:
            logger.error(f"Error during conversion: {e}", exc_info=True)
            return {
                "status": "error",
                "message": f"Conversion error: {str(e)}"
            }
        finally:
            self.current_process = None
    
    def convert_files(
        self,
        file_paths: List[str],
        progress_callback: Optional[Callable] = None
    ) -> Dict:
        """Convert multiple files with full queue tracking"""
        results = []
        total_files = len(file_paths)
        
        # Initialize queue tracking
        self._file_queue = []
        self._current_index = 0
        self._completed_files = []
        self._failed_files = []
        
        for idx, file_path in enumerate(file_paths, 1):
            logger.info(f"=== Processing {idx}/{total_files}: {Path(file_path).name} ===")
            
            # Update current index
            self._current_index = idx
            
            # Save process state before starting each file
            self._save_process_state(file_paths, idx, total_files)
            
            if progress_callback:
                progress_callback({
                    "file": Path(file_path).name,
                    "progress": int((idx - 1) / total_files * 100),
                    "status": f"Converting {Path(file_path).name}",
                    "current": idx,
                    "total": total_files
                })
            
            result = self.convert_file(file_path, progress_callback=progress_callback)
            results.append({
                "input": file_path,
                "result": result
            })
            
            # Track completion/failure
            if result["status"] == "success":
                self._completed_files.append(file_path)
                logger.info(f"✓ Completed {idx}/{total_files}: {Path(file_path).name}")
            elif result["status"] == "error":
                self._failed_files.append({
                    'path': file_path,
                    'error': result.get('message', 'Unknown error')
                })
                logger.error(f"✗ Failed {idx}/{total_files}: {Path(file_path).name} - {result['message']}")
            elif result["status"] == "cancelled":
                logger.info("Batch conversion cancelled by user")
                break
            
            # Update state after each file completes
            self._save_process_state(file_paths, idx, total_files)
        
        success_count = sum(1 for r in results if r["result"]["status"] == "success")
        
        # Clear process state only when batch fully completes or is cancelled
        self._clear_process_state()
        
        return {
            "status": "success",
            "converted": success_count,
            "failed": len(self._failed_files),
            "total": total_files,
            "results": results
        }
    
    def cancel_current(self) -> Dict:
        """Cancel the current conversion (cross-platform process tree termination)"""
        logger.info(f"cancel_current called, current_process: {self.current_process is not None}")
        if self.current_process:
            try:
                logger.info("Attempting to cancel conversion...")
                self.cancel_requested = True
                pid = self.current_process.pid
                
                # Platform-specific process tree termination
                # We need to kill the entire process tree, not just the parent
                if os.name == 'nt':  # Windows
                    logger.info(f"Windows: Using taskkill to terminate PID {pid} and all children")
                    try:
                        # /F = Force termination, /T = Tree kill (kills children too)
                        result = subprocess.run(
                            ['taskkill', '/F', '/T', '/PID', str(pid)],
                            capture_output=True, text=True, timeout=5
                        )
                        logger.info(f"taskkill output: {result.stdout}")
                        if result.stderr:
                            logger.warning(f"taskkill stderr: {result.stderr}")
                        
                    except subprocess.TimeoutExpired:
                        logger.error("taskkill command timed out")
                    except Exception as e:
                        logger.error(f"taskkill failed: {e}")
                        
                else:  # Linux/macOS
                    logger.info(f"Unix: Using process group kill for PID {pid}")
                    try:
                        # Kill process group (includes all children spawned by FFmpeg)
                        # This is the proper way to kill FFmpeg and its subprocesses
                        pgid = os.getpgid(pid)
                        logger.info(f"Process group ID: {pgid}")
                        os.killpg(pgid, signal.SIGTERM)
                        logger.info("Sent SIGTERM to process group")
                        
                    except ProcessLookupError:
                        logger.warning("Process group not found, trying direct kill")
                        try:
                            os.kill(pid, signal.SIGTERM)
                        except ProcessLookupError:
                            logger.warning("Process no longer exists")
                    except Exception as e:
                        logger.error(f"Process group kill failed: {e}")
                        # Try direct kill as fallback
                        try:
                            os.kill(pid, signal.SIGTERM)
                        except Exception as direct_kill_error:
                            logger.error(f"Direct kill also failed: {direct_kill_error}")
                
                # Wait for termination
                time.sleep(1.0)
                
                # Verify process is actually dead
                if not self._is_process_running(pid):
                    logger.info("✓ Conversion cancelled successfully - process terminated")
                    self._clear_process_state()
                    return {"status": "success", "message": "Conversion cancelled"}
                else:
                    logger.error(f"Process {pid} still running after termination attempt!")
                    
                    # Force kill as last resort
                    logger.warning("Attempting force kill...")
                    if os.name == 'nt':
                        subprocess.run(['taskkill', '/F', '/PID', str(pid)], 
                                     capture_output=True, timeout=3)
                    else:
                        try:
                            os.killpg(os.getpgid(pid), signal.SIGKILL)
                        except Exception:
                            os.kill(pid, signal.SIGKILL)
                    
                    # Final verification
                    time.sleep(0.5)
                    if not self._is_process_running(pid):
                        logger.warning("Process force killed")
                        self._clear_process_state()
                        return {"status": "success", "message": "Conversion force killed"}
                    else:
                        logger.error(f"Failed to kill process {pid} - it may be stuck")
                        return {"status": "error", "message": "Failed to terminate process"}
                        
            except Exception as e:
                logger.error(f"Error cancelling conversion: {e}", exc_info=True)
                return {"status": "error", "message": f"Failed to cancel: {str(e)}"}
            finally:
                # Always reset the process reference and cancel flag
                self.current_process = None
                self.cancel_requested = False
        else:
            logger.info("No active conversion to cancel")
            return {"status": "success", "message": "No active conversion"}
    
    def _verify_file_integrity(self, input_path: str, output_path: str) -> Dict[str, Any]:
        """Verify the integrity of converted file"""
        try:
            # input_file = Path(input_path)  # Not used currently
            output_file = Path(output_path)
            
            if not output_file.exists():
                return {"valid": False, "error": "Output file does not exist"}
            
            output_size = output_file.stat().st_size
            if output_size == 0:
                return {"valid": False, "error": "Output file is empty"}
            
            # Try to get duration of output file
            cmd = [
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                str(output_file)
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                data = json.loads(result.stdout)
                duration = float(data.get("format", {}).get("duration", 0))
                
                if duration > 0:
                    return {
                        "valid": True,
                        "duration": duration,
                        "size": output_size
                    }
                else:
                    return {"valid": False, "error": "Output file has no duration"}
            else:
                return {"valid": False, "error": "Could not probe output file"}
        
        except Exception as e:
            logger.error(f"Error verifying file: {e}")
            return {"valid": False, "error": str(e)}
    
    def _get_process_state_file(self) -> Path:
        """Get the path to the process state file"""
        # Use a temporary directory or user's app data directory
        if os.name == 'nt':  # Windows
            app_data = os.getenv('LOCALAPPDATA', os.path.expanduser('~'))
            state_dir = Path(app_data) / '.encodeforge'
        else:  # Unix-like
            state_dir = Path.home() / '.encodeforge'
        
        state_dir.mkdir(exist_ok=True)
        return state_dir / 'conversion_state.json'
    
    def _save_process_state(self, file_paths: List[str], current_index: int, total_files: int):
        """Save entire conversion queue state to disk for recovery"""
        try:
            # Build queue status for all files
            queue = []
            for i, path in enumerate(file_paths):
                file_entry = {
                    "path": path,
                    "index": i,
                    "filename": Path(path).name
                }
                
                if i < current_index - 1:
                    # Already processed
                    file_entry["status"] = "completed"
                    # Find in completed files
                    for completed in self._completed_files:
                        if completed == path:
                            file_entry["output"] = str(Path(path).with_suffix(f".{self.settings.output_format}"))
                            break
                elif i == current_index - 1:
                    # Currently processing
                    file_entry["status"] = "processing"
                    file_entry["pid"] = self.current_process.pid if self.current_process else None
                else:
                    # Waiting to process
                    file_entry["status"] = "queued"
                
                # Check if failed
                for failed in self._failed_files:
                    if failed.get('path') == path:
                        file_entry["status"] = "failed"
                        file_entry["error"] = failed.get('error', 'Unknown error')
                        break
                
                queue.append(file_entry)
            
            state = {
                'timestamp': time.time(),
                'pid': self.current_process.pid if self.current_process else None,
                'queue': queue,
                'current_index': current_index,
                'total_files': total_files,
                'current_file': getattr(self, '_current_file', ''),
                'completed_count': len(self._completed_files),
                'failed_count': len(self._failed_files),
                'settings': {
                    'ffmpeg_path': self.settings.ffmpeg_path,
                    'ffprobe_path': self.settings.ffprobe_path,
                    'output_format': self.settings.output_format,
                    'video_codec_fallback': self.settings.video_codec_fallback,
                    'audio_codec': self.settings.audio_codec,
                }
            }
            
            state_file = self._get_process_state_file()
            with open(state_file, 'w') as f:
                json.dump(state, f, indent=2)
            
            logger.debug(f"Saved conversion queue state to {state_file} ({len(queue)} files)")
            
        except Exception as e:
            logger.error(f"Error saving process state: {e}")
    
    def _clear_process_state(self):
        """Clear the saved process state"""
        try:
            state_file = self._get_process_state_file()
            if state_file.exists():
                state_file.unlink()
                logger.debug("Cleared conversion state")
        except Exception as e:
            logger.error(f"Error clearing process state: {e}")
    
    def get_saved_process_state(self) -> Optional[Dict]:
        """Get saved process state if it exists and is recent"""
        try:
            state_file = self._get_process_state_file()
            if not state_file.exists():
                return None
            
            with open(state_file, 'r') as f:
                state = json.load(f)
            
            # Check if state is recent (within last hour)
            if time.time() - state.get('timestamp', 0) > 3600:
                logger.debug("Saved state is too old, ignoring")
                self._clear_process_state()
                return None
            
            # Check if the process is still running
            pid = state.get('pid')
            if pid and self._is_process_running(pid):
                logger.info(f"Found ongoing conversion process (PID: {pid})")
                return state
            else:
                logger.debug("Saved process is no longer running")
                self._clear_process_state()
                return None
                
        except Exception as e:
            logger.error(f"Error reading process state: {e}")
            return None
    
    def _is_process_running(self, pid: int) -> bool:
        """Check if a process with given PID is still running"""
        try:
            if os.name == 'nt':  # Windows
                result = subprocess.run(['tasklist', '/FI', f'PID eq {pid}'], 
                                      capture_output=True, text=True)
                return str(pid) in result.stdout
            else:  # Unix-like
                try:
                    os.kill(pid, 0)  # Signal 0 just checks if process exists
                    return True
                except OSError:
                    return False
        except Exception as e:
            logger.debug(f"Error checking if process {pid} is running: {e}")
            return False

