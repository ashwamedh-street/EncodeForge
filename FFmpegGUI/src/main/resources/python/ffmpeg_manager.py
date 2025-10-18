#!/usr/bin/env python3
"""
FFmpeg Manager - Handles FFmpeg detection, download, and version management
"""

import os
import platform
import subprocess
import urllib.request
import zipfile
import tarfile
import json
from pathlib import Path
from typing import Optional, Dict, Tuple, List
import logging

logger = logging.getLogger(__name__)


class FFmpegManager:
    """Manages FFmpeg installation and detection"""
    
    FFMPEG_DOWNLOAD_URLS = {
        "Windows": "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
        "Darwin": "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip",
        "Linux": "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz"
    }
    
    def __init__(self, custom_path: Optional[str] = None):
        self.custom_path = custom_path
        self.ffmpeg_path = None
        self.ffprobe_path = None
        self.version_info = {}
        
    def detect_ffmpeg(self) -> Tuple[bool, Dict[str, str]]:
        """
        Detect FFmpeg installation and get version information
        
        Returns:
            (success, info_dict)
        """
        paths_to_check = []
        
        # Check custom path first
        if self.custom_path:
            paths_to_check.append(self.custom_path)
        
        # Check environment variable
        if os.getenv("FFMPEG_PATH"):
            paths_to_check.append(os.getenv("FFMPEG_PATH"))
        
        # Check common installation paths
        if platform.system() == "Windows":
            # Check PATH using shutil.which first (most reliable)
            try:
                import shutil
                which_result = shutil.which("ffmpeg")
                if which_result:
                    paths_to_check.insert(0, which_result)
            except:
                pass
            
            # Add executable variants
            paths_to_check.extend([
                "ffmpeg.exe",
                "ffmpeg",
            ])
            
            # User directories (including Documents)
            userprofile = os.getenv("USERPROFILE", "")
            if userprofile:
                paths_to_check.extend([
                    os.path.join(userprofile, "ffmpeg\\bin\\ffmpeg.exe"),
                    os.path.join(userprofile, "ffmpeg\\ffmpeg.exe"),
                    os.path.join(userprofile, "Documents\\ffmpeg\\bin\\ffmpeg.exe"),
                    os.path.join(userprofile, "Documents\\ffmpeg\\ffmpeg.exe"),
                    os.path.join(userprofile, "Downloads\\ffmpeg\\bin\\ffmpeg.exe"),
                    os.path.join(userprofile, "Downloads\\ffmpeg\\ffmpeg.exe"),
                ])
            
            # Common installation paths
            paths_to_check.extend([
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\ffmpeg\\ffmpeg.exe",
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe",
            ])
            
            # Check LOCALAPPDATA and PROGRAMFILES
            localappdata = os.getenv("LOCALAPPDATA", "")
            if localappdata:
                paths_to_check.extend([
                    os.path.join(localappdata, "ffmpeg\\bin\\ffmpeg.exe"),
                    os.path.join(localappdata, "ffmpeg\\ffmpeg.exe"),
                ])
            
            programfiles = os.getenv("PROGRAMFILES", "")
            if programfiles:
                paths_to_check.append(os.path.join(programfiles, "ffmpeg\\bin\\ffmpeg.exe"))
            
            # Try recursive search in Documents folder
            if userprofile:
                docs_path = os.path.join(userprofile, "Documents")
                if os.path.exists(docs_path):
                    logger.info(f"Searching recursively in: {docs_path}")
                    found_path = self._recursive_search(docs_path, "ffmpeg.exe", max_depth=3)
                    if found_path:
                        logger.info(f"Recursive search found: {found_path}")
                        paths_to_check.insert(0, found_path)
                    else:
                        logger.debug(f"Recursive search in Documents did not find ffmpeg.exe")
        else:
            paths_to_check.extend([
                "ffmpeg",
                "/usr/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/opt/ffmpeg/ffmpeg",
                "/opt/homebrew/bin/ffmpeg",
                os.path.expanduser("~/bin/ffmpeg"),
                os.path.expanduser("~/ffmpeg/ffmpeg"),
            ])
        
        # Try each path
        logger.info(f"Searching for FFmpeg in {len([p for p in paths_to_check if p])} locations...")
        for path in paths_to_check:
            if path:
                logger.debug(f"Checking: {path}")
                if self._test_ffmpeg(path):
                    self.ffmpeg_path = path
                    self.ffprobe_path = path.replace("ffmpeg", "ffprobe")
                    
                    logger.info(f"✓ Found FFmpeg at: {path}")
                    
                    # Get version info
                    self.version_info = self._get_version_info()
                    
                    return True, {
                        "ffmpeg_path": self.ffmpeg_path,
                        "ffprobe_path": self.ffprobe_path,
                        "version": self.version_info.get("version", "Unknown"),
                        "configuration": self.version_info.get("configuration", ""),
                        "encoders": self.version_info.get("encoders", []),
                        "decoders": self.version_info.get("decoders", []),
                    }
        
        logger.warning("FFmpeg not found in any checked location")
        logger.debug(f"Searched paths: {[p for p in paths_to_check if p]}")
        
        return False, {
            "error": "FFmpeg not found in system PATH or common locations",
            "searched_paths": [p for p in paths_to_check if p]
        }
    
    def _recursive_search(self, directory: str, filename: str, max_depth: int = 3, current_depth: int = 0) -> Optional[str]:
        """Recursively search for a file in directory tree"""
        if current_depth > max_depth:
            return None
        
        try:
            for entry in os.listdir(directory):
                full_path = os.path.join(directory, entry)
                
                # Check if this is the file we're looking for
                if entry.lower() == filename.lower() and os.path.isfile(full_path):
                    return full_path
                
                # Recurse into subdirectories
                if os.path.isdir(full_path) and not entry.startswith('.'):
                    result = self._recursive_search(full_path, filename, max_depth, current_depth + 1)
                    if result:
                        return result
        except (PermissionError, OSError):
            pass
        
        return None
    
    def _test_ffmpeg(self, path: str) -> bool:
        """Test if FFmpeg is available at the given path"""
        if not path:
            return False
        
        try:
            result = subprocess.run(
                [path, "-version"],
                capture_output=True,
                text=True,
                timeout=5
            )
            return result.returncode == 0
        except (subprocess.SubprocessError, FileNotFoundError, OSError):
            return False
    
    def _get_version_info(self) -> Dict:
        """Get detailed version information from FFmpeg"""
        info = {}
        
        try:
            # Get version
            if self.ffmpeg_path:
                result = subprocess.run(
                    [self.ffmpeg_path, "-version"],
                    capture_output=True,
                    text=True,
                    timeout=5
                )
            else:
                return info
            
            if result.returncode == 0:
                lines = result.stdout.split('\n')
                if lines:
                    # Parse version line
                    version_line = lines[0]
                    if "version" in version_line:
                        info["version"] = version_line.split("version")[1].split()[0]
                    
                    # Parse configuration
                    for line in lines:
                        if "configuration:" in line:
                            info["configuration"] = line.split("configuration:")[1].strip()
                            break
            
            # Check for hardware encoders
            encoders = self._check_encoders()
            info["encoders"] = encoders
            
            # Check for hardware decoders
            decoders = self._check_decoders()
            info["decoders"] = decoders
            
        except Exception as e:
            logger.error(f"Error getting FFmpeg version info: {e}")
        
        return info
    
    def _check_encoders(self) -> list:
        """Check available hardware encoders"""
        encoders = []
        
        try:
            if not self.ffmpeg_path:
                return encoders
            
            result = subprocess.run(
                [self.ffmpeg_path, "-encoders"],
                capture_output=True,
                text=True,
                timeout=5
            )
            
            if result.returncode == 0:
                output = result.stdout.lower()
                
                # Check for various hardware encoders
                hw_encoders = [
                    ("h264_nvenc", "NVIDIA H.264"),
                    ("hevc_nvenc", "NVIDIA H.265"),
                    ("h264_amf", "AMD H.264"),
                    ("hevc_amf", "AMD H.265"),
                    ("h264_qsv", "Intel Quick Sync H.264"),
                    ("hevc_qsv", "Intel Quick Sync H.265"),
                    ("h264_videotoolbox", "Apple VideoToolbox H.264"),
                    ("hevc_videotoolbox", "Apple VideoToolbox H.265"),
                ]
                
                for encoder_id, encoder_name in hw_encoders:
                    if encoder_id in output:
                        encoders.append(encoder_name)
        
        except Exception as e:
            logger.error(f"Error checking encoders: {e}")
        
        return encoders
    
    def _check_decoders(self) -> list:
        """Check available hardware decoders"""
        decoders = []
        
        try:
            if not self.ffmpeg_path:
                return decoders
            
            result = subprocess.run(
                [self.ffmpeg_path, "-decoders"],
                capture_output=True,
                text=True,
                timeout=5
            )
            
            if result.returncode == 0:
                output = result.stdout.lower()
                
                # Check for various hardware decoders
                hw_decoders = [
                    ("h264_cuvid", "NVIDIA H.264 (CUVID)"),
                    ("hevc_cuvid", "NVIDIA H.265 (CUVID)"),
                    ("h264_qsv", "Intel Quick Sync H.264"),
                    ("hevc_qsv", "Intel Quick Sync H.265"),
                ]
                
                for decoder_id, decoder_name in hw_decoders:
                    if decoder_id in output:
                        decoders.append(decoder_name)
        
        except Exception as e:
            logger.error(f"Error checking decoders: {e}")
        
        return decoders
    
    def download_ffmpeg(self, install_dir: Optional[Path] = None, progress_callback=None) -> Tuple[bool, str]:
        """
        Download and install FFmpeg
        
        Args:
            install_dir: Directory to install FFmpeg (default: user's home directory)
            progress_callback: Function to call with progress updates
            
        Returns:
            (success, message)
        """
        system = platform.system()
        
        if system not in self.FFMPEG_DOWNLOAD_URLS:
            return False, f"Automatic download not supported for {system}"
        
        download_url = self.FFMPEG_DOWNLOAD_URLS[system]
        
        # Set install directory
        if install_dir is None:
            if system == "Windows":
                local_appdata = os.getenv("LOCALAPPDATA", str(Path.home() / "AppData" / "Local"))
                install_dir = Path(local_appdata) / "ffmpeg"
            else:
                install_dir = Path.home() / ".local" / "bin"
        
        install_dir.mkdir(parents=True, exist_ok=True)
        
        try:
            # Download file
            if progress_callback:
                progress_callback({"status": "downloading", "progress": 0, "message": "Downloading FFmpeg..."})
            
            filename = download_url.split("/")[-1]
            download_path = install_dir / filename
            
            logger.info(f"Downloading FFmpeg from {download_url}")
            
            def download_progress(block_num, block_size, total_size):
                if progress_callback and total_size > 0:
                    progress = min(100, (block_num * block_size * 100) / total_size)
                    progress_callback({
                        "status": "downloading",
                        "progress": progress,
                        "message": f"Downloading: {progress:.1f}%"
                    })
            
            urllib.request.urlretrieve(download_url, download_path, reporthook=download_progress)
            
            # Extract
            if progress_callback:
                progress_callback({"status": "extracting", "progress": 50, "message": "Extracting..."})
            
            logger.info(f"Extracting {download_path}")
            
            if filename.endswith(".zip"):
                with zipfile.ZipFile(download_path, 'r') as zip_ref:
                    zip_ref.extractall(install_dir)
            elif filename.endswith((".tar.xz", ".tar.gz")):
                with tarfile.open(download_path, 'r:*') as tar_ref:
                    tar_ref.extractall(install_dir)
            
            # Find ffmpeg executable
            for root, dirs, files in os.walk(install_dir):
                for file in files:
                    if file.startswith("ffmpeg") and (file.endswith(".exe") or not "." in file):
                        ffmpeg_exe = Path(root) / file
                        self.ffmpeg_path = str(ffmpeg_exe)
                        self.ffprobe_path = str(ffmpeg_exe).replace("ffmpeg", "ffprobe")
                        
                        # Make executable on Unix systems
                        if system != "Windows":
                            os.chmod(ffmpeg_exe, 0o755)
                            if Path(self.ffprobe_path).exists():
                                os.chmod(self.ffprobe_path, 0o755)
                        
                        # Clean up download file
                        download_path.unlink()
                        
                        if progress_callback:
                            progress_callback({
                                "status": "complete",
                                "progress": 100,
                                "message": f"FFmpeg installed successfully at {self.ffmpeg_path}"
                            })
                        
                        return True, f"FFmpeg installed at {self.ffmpeg_path}"
            
            return False, "FFmpeg executable not found in downloaded package"
            
        except Exception as e:
            logger.error(f"Error downloading FFmpeg: {e}")
            return False, f"Download failed: {str(e)}"
    
    def get_hwaccel_options(self) -> Dict[str, List[str]]:
        """Get available hardware acceleration options"""
        options = {
            "decode": [],
            "encode": []
        }
        
        if not self.ffmpeg_path:
            return options
        
        # Check for NVIDIA
        if "NVIDIA" in " ".join(self.version_info.get("encoders", [])):
            options["decode"].append("cuda")
            options["decode"].append("cuvid")
            options["encode"].append("nvenc")
        
        # Check for AMD
        if "AMD" in " ".join(self.version_info.get("encoders", [])):
            options["decode"].append("amf")
            options["encode"].append("amf")
        
        # Check for Intel
        if "Intel" in " ".join(self.version_info.get("encoders", [])):
            options["decode"].append("qsv")
            options["encode"].append("qsv")
        
        # Check for Apple
        if "Apple" in " ".join(self.version_info.get("encoders", [])):
            options["decode"].append("videotoolbox")
            options["encode"].append("videotoolbox")
        
        return options


def main():
    """Test the FFmpeg manager"""
    manager = FFmpegManager()
    
    success, info = manager.detect_ffmpeg()
    
    if success:
        print("✅ FFmpeg detected!")
        print(f"Path: {info['ffmpeg_path']}")
        print(f"Version: {info['version']}")
        print(f"Hardware Encoders: {', '.join(info['encoders']) if info['encoders'] else 'None'}")
        print(f"Hardware Decoders: {', '.join(info['decoders']) if info['decoders'] else 'None'}")
        
        hwaccel = manager.get_hwaccel_options()
        print(f"\nHardware Acceleration Options:")
        print(f"  Decode: {', '.join(hwaccel['decode']) if hwaccel['decode'] else 'None'}")
        print(f"  Encode: {', '.join(hwaccel['encode']) if hwaccel['encode'] else 'None'}")
    else:
        print("❌ FFmpeg not found")
        print(info.get("error", "Unknown error"))
        print("\nAttempting automatic download...")
        
        def progress_handler(update):
            print(f"  {update['message']}")
        
        success, message = manager.download_ffmpeg(progress_callback=progress_handler)
        
        if success:
            print(f"✅ {message}")
        else:
            print(f"❌ {message}")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()

