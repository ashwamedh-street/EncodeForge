
#!/usr/bin/env python3
"""
Whisper Manager - Handles Whisper AI model management and subtitle generation
"""

import logging
import subprocess
from pathlib import Path
from typing import Dict, List, Optional

logger = logging.getLogger(__name__)


class WhisperManager:
    """Manages Whisper AI for subtitle generation"""
    
    MODELS = ["tiny", "base", "small", "medium", "large", "large-v2", "large-v3"]
    
    MODEL_SIZES = {
        "tiny": "75 MB",
        "base": "142 MB",
        "small": "466 MB",
        "medium": "1.5 GB",
        "large": "2.9 GB",
        "large-v2": "2.9 GB",
        "large-v3": "2.9 GB",
    }
    
    def __init__(self):
        self.whisper_available = False
        self.installed_models = []
        self._check_installation()
    
    def _check_installation(self) -> bool:
        """Check if Whisper is installed"""
        try:
            import whisper  # noqa: F401
            self.whisper_available = True
            
            # Check which models are downloaded
            model_dir = Path.home() / ".cache" / "whisper"
            if model_dir.exists():
                for model_file in model_dir.glob("*.pt"):
                    model_name = model_file.stem
                    if model_name in self.MODELS:
                        self.installed_models.append(model_name)
            
            return True
        except ImportError:
            self.whisper_available = False
            return False
    
    def is_available(self) -> bool:
        """Check if Whisper is available"""
        return self.whisper_available
    
    def get_status(self) -> Dict:
        """Get Whisper installation status"""
        return {
            "installed": self.whisper_available,
            "models": self.installed_models,
            "available_models": self.MODELS,
            "model_sizes": self.MODEL_SIZES,
        }
    
    def install_whisper(self, progress_callback=None) -> tuple[bool, str]:
        """
        Install Whisper library
        
        Args:
            progress_callback: Function to call with progress updates
            
        Returns:
            (success, message)
        """
        try:
            if progress_callback:
                progress_callback({
                    "status": "installing",
                    "progress": 0,
                    "message": "Installing Whisper..."
                })
            
            logger.info("Installing openai-whisper...")
            
            # Install using pip
            import sys
            result = subprocess.run(
                [sys.executable, "-m", "pip", "install", "-U", "openai-whisper"],
                capture_output=True,
                text=True
            )
            
            if result.returncode == 0:
                self.whisper_available = True
                
                if progress_callback:
                    progress_callback({
                        "status": "complete",
                        "progress": 100,
                        "message": "Whisper installed successfully"
                    })
                
                return True, "Whisper installed successfully"
            else:
                error = result.stderr or "Installation failed"
                return False, f"Installation failed: {error}"
                
        except Exception as e:
            logger.error(f"Error installing Whisper: {e}")
            return False, f"Installation error: {str(e)}"
    
    def download_model(self, model_name: str, progress_callback=None) -> tuple[bool, str]:
        """
        Download a Whisper model
        
        Args:
            model_name: Model to download (tiny, base, small, medium, large)
            progress_callback: Function to call with progress updates
            
        Returns:
            (success, message)
        """
        if not self.whisper_available:
            return False, "Whisper is not installed. Install it first."
        
        if model_name not in self.MODELS:
            return False, f"Invalid model: {model_name}. Available models: {', '.join(self.MODELS)}"
        
        try:
            if progress_callback:
                progress_callback({
                    "status": "downloading",
                    "progress": 0,
                    "message": f"Downloading Whisper {model_name} model ({self.MODEL_SIZES[model_name]})..."
                })
            
            import whisper
            
            logger.info(f"Downloading Whisper model: {model_name}")
            
            # This will download the model if not already present
            whisper.load_model(model_name)
            
            if model_name not in self.installed_models:
                self.installed_models.append(model_name)
            
            if progress_callback:
                progress_callback({
                    "status": "complete",
                    "progress": 100,
                    "message": f"Model {model_name} downloaded successfully"
                })
            
            return True, f"Model {model_name} is ready"
            
        except Exception as e:
            logger.error(f"Error downloading model: {e}")
            return False, f"Download failed: {str(e)}"
    
    def generate_subtitles(
        self,
        video_path: str,
        output_path: str,
        model_name: str = "base",
        language: Optional[str] = None,
        progress_callback=None
    ) -> tuple[bool, str]:
        """
        Generate subtitles for a video file
        
        Args:
            video_path: Path to video file
            output_path: Path for output SRT file
            model_name: Whisper model to use
            language: Target language (None for auto-detect)
            progress_callback: Function to call with progress updates
            
        Returns:
            (success, message)
        """
        if not self.whisper_available:
            return False, "Whisper is not installed"
        
        if not Path(video_path).exists():
            return False, f"Video file not found: {video_path}"
        
        try:
            if progress_callback:
                progress_callback({
                    "status": "transcribing",
                    "progress": 0,
                    "message": f"Transcribing audio with Whisper {model_name}..."
                })
            
            import whisper
            
            logger.info(f"Loading Whisper model: {model_name}")
            model = whisper.load_model(model_name)
            
            logger.info(f"Transcribing: {video_path}")
            
            # Transcribe
            options = {}
            if language:
                options["language"] = language
            
            result = model.transcribe(video_path, **options)
            
            # Convert to SRT format
            segments = result.get("segments", [])
            if isinstance(segments, list):
                srt_content = self._segments_to_srt(segments)
            else:
                return False, "Invalid segments data from Whisper"
            
            # Write to file
            with open(output_path, "w", encoding="utf-8") as f:
                f.write(srt_content)
            
            if progress_callback:
                progress_callback({
                    "status": "complete",
                    "progress": 100,
                    "message": f"Subtitles generated: {output_path}"
                })
            
            detected_lang = result.get("language", "unknown")
            return True, f"Subtitles generated successfully (detected language: {detected_lang})"
            
        except Exception as e:
            logger.error(f"Error generating subtitles: {e}")
            return False, f"Transcription failed: {str(e)}"
    
    def _segments_to_srt(self, segments: List[Dict]) -> str:
        """Convert Whisper segments to SRT format"""
        srt_lines = []
        
        for i, segment in enumerate(segments, start=1):
            start_time = self._format_timestamp(segment["start"])
            end_time = self._format_timestamp(segment["end"])
            text = segment["text"].strip()
            
            srt_lines.append(f"{i}")
            srt_lines.append(f"{start_time} --> {end_time}")
            srt_lines.append(text)
            srt_lines.append("")  # Blank line between subtitles
        
        return "\n".join(srt_lines)
    
    def _format_timestamp(self, seconds: float) -> str:
        """Format seconds to SRT timestamp format (HH:MM:SS,mmm)"""
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        secs = int(seconds % 60)
        millis = int((seconds % 1) * 1000)
        
        return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"
    
    def get_model_info(self, model_name: str) -> Dict:
        """Get information about a specific model"""
        return {
            "name": model_name,
            "size": self.MODEL_SIZES.get(model_name, "Unknown"),
            "installed": model_name in self.installed_models,
            "available": model_name in self.MODELS,
        }


def main():
    """Test the Whisper manager"""
    manager = WhisperManager()
    
    status = manager.get_status()
    
    print("Whisper Status:")
    print(f"  Installed: {status['installed']}")
    
    if status['installed']:
        print("  Downloaded Models: " + (', '.join(status['models']) if status['models'] else 'None'))
        print("\nAvailable Models:")
        for model in status['available_models']:
            info = manager.get_model_info(model)
            status_icon = "✅" if info['installed'] else "❌"
            print(f"    {status_icon} {model} ({info['size']})")
    else:
        print("\n❌ Whisper is not installed")
        print("Install with: pip install openai-whisper")
        print("\nOr use the application's auto-install feature")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()

