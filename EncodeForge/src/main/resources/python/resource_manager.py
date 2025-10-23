"""
Resource Manager - Intelligent System Resource Detection and Worker Allocation
Detects CPU cores, RAM, and determines optimal worker counts for different tasks
"""

import os
import platform
import multiprocessing
import psutil
import logging
from typing import Dict, Tuple

logger = logging.getLogger(__name__)


class ResourceManager:
    """Manages system resource detection and intelligent worker allocation"""
    
    def __init__(self):
        self.cpu_count = multiprocessing.cpu_count()
        self.physical_cores = psutil.cpu_count(logical=False) or self.cpu_count
        self.total_ram_gb = psutil.virtual_memory().total / (1024 ** 3)
        self.available_ram_gb = psutil.virtual_memory().available / (1024 ** 3)
        
        logger.info("=== System Resources Detected ===")
        logger.info(f"CPU Cores (Logical): {self.cpu_count}")
        logger.info(f"CPU Cores (Physical): {self.physical_cores}")
        logger.info(f"Total RAM: {self.total_ram_gb:.2f} GB")
        logger.info(f"Available RAM: {self.available_ram_gb:.2f} GB")
    
    def get_optimal_worker_count(self, task_type: str) -> int:
        """
        Calculate optimal worker count based on task type and system resources
        
        Args:
            task_type: Type of task ("whisper", "encoding", "subtitle_search", "download", "metadata")
            
        Returns:
            Optimal number of workers for this task type
        """
        if task_type == "whisper":
            # Whisper AI: Memory-intensive (4-8GB per instance)
            # Base model: ~4GB, Small: ~6GB, Medium/Large: 8GB+
            return self._calculate_whisper_workers()
        
        elif task_type == "encoding":
            # Video encoding: CPU-intensive, use physical cores
            # Leave 1-2 cores free for system
            return max(1, self.physical_cores - 1)
        
        elif task_type == "subtitle_search":
            # Subtitle search: Network I/O bound, can parallelize heavily
            # Use more workers than CPU cores (network waiting)
            return min(8, self.cpu_count * 2)
        
        elif task_type == "download":
            # Downloads: Network I/O bound, moderate parallelization
            return min(6, self.cpu_count)
        
        elif task_type == "metadata":
            # Metadata operations: Light CPU, moderate I/O
            return min(4, self.cpu_count)
        
        else:
            # Default: use half of logical cores
            return max(2, self.cpu_count // 2)
    
    def _calculate_whisper_workers(self) -> int:
        """
        Calculate optimal Whisper workers based on available RAM
        
        Memory requirements per Whisper model:
        - Tiny: ~1GB
        - Base: ~2GB
        - Small: ~5GB
        - Medium: ~10GB
        - Large: ~15GB
        
        We assume Base model (2GB) as default, with 2GB buffer for system
        """
        # Conservative estimate: 4GB per Whisper instance (2GB model + 2GB working memory)
        gb_per_instance = 4.0
        
        # Reserve 2GB for system
        available_for_whisper = max(0, self.available_ram_gb - 2)
        
        # Calculate how many instances we can run
        max_instances = int(available_for_whisper / gb_per_instance)
        
        # Limit to physical cores (Whisper is CPU-intensive too)
        max_instances = min(max_instances, self.physical_cores)
        
        # At least 1, at most 4 (diminishing returns beyond 4 parallel transcriptions)
        optimal = max(1, min(4, max_instances))
        
        logger.info(f"Whisper workers: {optimal} (based on {self.available_ram_gb:.2f}GB available RAM)")
        return optimal
    
    def get_system_info(self) -> Dict:
        """Get detailed system information for diagnostics"""
        return {
            "cpu_count_logical": self.cpu_count,
            "cpu_count_physical": self.physical_cores,
            "total_ram_gb": round(self.total_ram_gb, 2),
            "available_ram_gb": round(self.available_ram_gb, 2),
            "platform": platform.system(),
            "platform_version": platform.version(),
            "python_version": platform.python_version()
        }
    
    def can_run_parallel_whisper(self, num_instances: int = 2) -> Tuple[bool, str]:
        """
        Check if system can handle multiple parallel Whisper instances
        
        Args:
            num_instances: Number of parallel instances to check
            
        Returns:
            Tuple of (can_run: bool, reason: str)
        """
        gb_per_instance = 4.0
        required_ram = num_instances * gb_per_instance + 2  # +2GB for system
        
        if self.available_ram_gb < required_ram:
            return False, f"Insufficient RAM: need {required_ram:.1f}GB, have {self.available_ram_gb:.1f}GB"
        
        if self.physical_cores < num_instances:
            return False, f"Insufficient CPU cores: need {num_instances}, have {self.physical_cores}"
        
        return True, f"Can run {num_instances} parallel Whisper instances"
    
    def should_use_gpu(self) -> bool:
        """Check if GPU acceleration should be used (if available)"""
        try:
            import torch
            if torch.cuda.is_available():
                gpu_count = torch.cuda.device_count()
                logger.info(f"CUDA available: {gpu_count} GPU(s) detected")
                return True
        except ImportError:
            pass
        
        logger.info("No GPU acceleration available, using CPU")
        return False
    
    def get_encoding_workers(self) -> int:
        """Get optimal encoding worker count"""
        # For encoding, use physical cores minus 1 (leave one core free)
        # But at least 1 worker
        workers = max(1, self.physical_cores - 1)
        logger.info(f"Encoding workers: {workers} (based on {self.physical_cores} physical cores)")
        return workers
    
    def refresh_available_ram(self):
        """Refresh available RAM measurement (useful for long-running processes)"""
        old_available = self.available_ram_gb
        self.available_ram_gb = psutil.virtual_memory().available / (1024 ** 3)
        
        if abs(old_available - self.available_ram_gb) > 1.0:  # More than 1GB change
            logger.info(f"Available RAM updated: {old_available:.2f}GB → {self.available_ram_gb:.2f}GB")


# Global instance for easy access
_resource_manager = None


def get_resource_manager() -> ResourceManager:
    """Get or create the global ResourceManager instance"""
    global _resource_manager
    if _resource_manager is None:
        _resource_manager = ResourceManager()
    return _resource_manager
