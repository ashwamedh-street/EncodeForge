#!/usr/bin/env python3
"""
Conversion Handler - Video conversion and encoding operations
"""

import json
import logging
import os
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
                cmd.extend(["-c:s", self.settings.subtitle_format])
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
                stderr=subprocess.PIPE,
                universal_newlines=True
            )
            
            # Monitor progress
            if progress_callback:
                # TODO: Parse ffmpeg output for progress
                pass
            
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
            
            if progress_callback:
                progress_callback(idx, total_files, f"Converting {Path(file_path).name}")
            
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
        
        return {
            "status": "success",
            "converted": success_count,
            "total": total_files,
            "results": results
        }
    
    def cancel_current(self) -> Dict:
        """Cancel the current conversion"""
        if self.current_process:
            try:
                self.cancel_requested = True
                self.current_process.terminate()
                self.current_process.wait(timeout=5)
                logger.info("Conversion cancelled successfully")
                return {"status": "success", "message": "Conversion cancelled"}
            except subprocess.TimeoutExpired:
                self.current_process.kill()
                logger.warning("Conversion forcefully killed")
                return {"status": "success", "message": "Conversion forcefully stopped"}
            except Exception as e:
                logger.error(f"Error cancelling conversion: {e}")
                return {"status": "error", "message": f"Failed to cancel: {str(e)}"}
        else:
            return {"status": "error", "message": "No conversion in progress"}
    
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

