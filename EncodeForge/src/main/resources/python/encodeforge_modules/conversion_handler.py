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
    
    def _select_best_encoder(self) -> Dict[str, str]:
        """Determine the best available encoder (hardware or software fallback)"""
        hwaccel_options = self.ffmpeg_mgr.get_hwaccel_options()
        
        # Try hardware encoders in order of preference
        if self.settings.use_nvenc and hwaccel_options.get("nvenc"):
            return {"type": "hardware", "codec": self.settings.nvenc_codec, "platform": "nvidia"}
        
        if self.settings.use_amf and hwaccel_options.get("amf"):
            return {"type": "hardware", "codec": "h264_amf", "platform": "amd"}
        
        if self.settings.use_qsv and hwaccel_options.get("qsv"):
            return {"type": "hardware", "codec": "h264_qsv", "platform": "intel"}
        
        if self.settings.use_videotoolbox and hwaccel_options.get("videotoolbox"):
            return {"type": "hardware", "codec": "h264_videotoolbox", "platform": "apple"}
        
        # Fallback to software encoding
        return {"type": "software", "codec": self.settings.video_codec_fallback, "platform": "cpu"}
    
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
                    
                    # Calculate progress percentage if we have time info
                    if 'time' in progress_data and hasattr(self, '_total_duration') and self._total_duration > 0:
                        progress_data['progress'] = min(100, int((progress_data['time'] / self._total_duration) * 100))
                        
                        # Calculate ETA if we have enough data
                        if 'fps' in progress_data and progress_data['fps'] > 0:
                            current_time = progress_data['time']
                            remaining_time = self._total_duration - current_time
                            if remaining_time > 0:
                                # Estimate remaining time based on current fps
                                estimated_seconds = remaining_time
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
                logger.info(f"✅ Software encoding completed successfully")
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
            
            # Get video duration for progress calculation
            self._total_duration = self._get_video_duration(str(input_file))
            
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
            
            # Build FFmpeg command
            cmd = [
                self.settings.ffmpeg_path,
                "-hide_banner",
                "-loglevel", "error",
                "-stats",
                "-i", str(input_file)
            ]
            
            # Select encoder
            encoder_info = self._select_best_encoder()
            logger.info(f"Using encoder: {encoder_info['codec']} ({encoder_info['type']})")
            
            if encoder_info["type"] == "hardware":
                cmd.extend(["-c:v", encoder_info["codec"]])
                
                # Hardware-specific optimizations
                if encoder_info["codec"].endswith("nvenc"):
                    cmd.extend([
                        "-preset", self.settings.nvenc_preset,
                        "-cq", str(self.settings.nvenc_cq),
                        "-rc", "vbr"
                    ])
                elif encoder_info["codec"].endswith("amf"):
                    cmd.extend([
                        "-quality", "balanced",
                        "-rc", "vbr_latency"
                    ])
                elif encoder_info["codec"].endswith("qsv"):
                    cmd.extend([
                        "-preset", "medium",
                        "-global_quality", "23"
                    ])
                elif encoder_info["codec"].endswith("videotoolbox"):
                    cmd.extend([
                        "-b:v", "5M"
                    ])
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
            self.current_process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,  # Redirect stderr to stdout for unified output
                universal_newlines=True,
                bufsize=1  # Line buffered
            )
            
            # Monitor progress in real-time
            stdout_lines = []
            
            if progress_callback and self.current_process.stdout:
                # Read output line by line for progress monitoring
                while True:
                    if self.cancel_requested:
                        self.current_process.terminate()
                        break
                    
                    line = self.current_process.stdout.readline()
                    if not line:
                        break
                    
                    stdout_lines.append(line)
                    
                    # Parse FFmpeg progress from output
                    progress_info = self._parse_ffmpeg_progress(line.strip())
                    if progress_info:
                        progress_callback(progress_info)
                
                # Wait for process to complete
                self.current_process.wait()
                stdout = ''.join(stdout_lines)
                stderr = ''  # All output is in stdout now
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
                    logger.warning(f"Hardware encoder failed, trying software fallback...")
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
        """Convert multiple files"""
        results = []
        total_files = len(file_paths)
        
        for idx, file_path in enumerate(file_paths, 1):
            logger.info(f"=== Processing {idx}/{total_files}: {Path(file_path).name} ===")
            
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
            
            if result["status"] == "error":
                logger.error(f"Failed to convert {file_path}: {result['message']}")
            elif result["status"] == "cancelled":
                logger.info("Batch conversion cancelled")
                break
        
        success_count = sum(1 for r in results if r["result"]["status"] == "success")
        
        # Clear process state when batch conversion completes
        self._clear_process_state()
        
        return {
            "status": "success",
            "converted": success_count,
            "total": total_files,
            "results": results
        }
    
    def cancel_current(self) -> Dict:
        """Cancel the current conversion"""
        logger.info(f"cancel_current called, current_process: {self.current_process is not None}")
        if self.current_process:
            try:
                logger.info("Attempting to cancel conversion...")
                self.cancel_requested = True
                
                # First try graceful termination
                self.current_process.terminate()
                
                try:
                    # Wait up to 3 seconds for graceful shutdown
                    self.current_process.wait(timeout=3)
                    logger.info("Conversion cancelled gracefully")
                    return {"status": "success", "message": "Conversion cancelled"}
                except subprocess.TimeoutExpired:
                    # If graceful shutdown fails, force kill
                    logger.warning("Graceful shutdown failed, force killing process...")
                    self.current_process.kill()
                    
                    try:
                        # Wait up to 2 more seconds for force kill
                        self.current_process.wait(timeout=2)
                        logger.warning("Conversion forcefully killed")
                        return {"status": "success", "message": "Conversion forcefully stopped"}
                    except subprocess.TimeoutExpired:
                        # If even force kill fails, try system-level termination
                        logger.error("Force kill failed, attempting system-level termination...")
                        try:
                            import os
                            if hasattr(os, 'kill'):
                                # Use SIGTERM (15) on Unix-like systems, or just try os.kill
                                if os.name != 'nt':  # Unix-like
                                    os.kill(self.current_process.pid, signal.SIGTERM)
                                else:  # Windows
                                    # On Windows, use taskkill for more reliable termination
                                    try:
                                        subprocess.run(['taskkill', '/F', '/T', '/PID', str(self.current_process.pid)], 
                                                     check=False, capture_output=True)
                                        logger.warning("Process terminated using taskkill")
                                    except Exception as taskkill_error:
                                        logger.error(f"taskkill failed: {taskkill_error}")
                                        # Fallback to os.kill
                                        os.kill(self.current_process.pid, 1)  # SIGTERM equivalent
                                logger.warning("Process terminated using system kill")
                                return {"status": "success", "message": "Process terminated forcefully"}
                        except Exception as kill_error:
                            logger.error(f"System kill failed: {kill_error}")
                        
                        return {"status": "error", "message": "Failed to terminate process"}
                        
            except Exception as e:
                logger.error(f"Error cancelling conversion: {e}")
                return {"status": "error", "message": f"Failed to cancel: {str(e)}"}
            finally:
                # Always reset the process reference and cancel flag
                self.current_process = None
                self.cancel_requested = False
                # Clear process state when cancelled
                self._clear_process_state()
        else:
            logger.info("No active conversion to cancel")
            return {"status": "success", "message": "No active conversion"}
    
    def _verify_file_integrity(self, input_path: str, output_path: str) -> Dict[str, Any]:
        """Verify the integrity of converted file"""
        try:
            input_file = Path(input_path)
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
        """Save current conversion state to disk"""
        try:
            state = {
                'timestamp': time.time(),
                'pid': self.current_process.pid if self.current_process else None,
                'file_paths': file_paths,
                'current_index': current_index,
                'total_files': total_files,
                'current_file': getattr(self, '_current_file', ''),
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
            
            logger.debug(f"Saved conversion state to {state_file}")
            
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
                import subprocess
                result = subprocess.run(['tasklist', '/FI', f'PID eq {pid}'], 
                                      capture_output=True, text=True)
                return str(pid) in result.stdout
            else:  # Unix-like
                import os
                import signal
                try:
                    os.kill(pid, 0)  # Signal 0 just checks if process exists
                    return True
                except OSError:
                    return False
        except Exception as e:
            logger.debug(f"Error checking if process {pid} is running: {e}")
            return False

