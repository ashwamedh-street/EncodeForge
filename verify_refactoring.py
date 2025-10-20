#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Refactoring Verification Script
Verifies that the MainController refactoring was completed successfully.
Checks for missing code, syntax issues, and completeness.
"""

import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple

# Configure output encoding for Windows
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8') if hasattr(sys.stdout, 'reconfigure') else None

@dataclass
class FileCheck:
    """Represents verification checks for a file"""
    filename: str
    expected_min_lines: int
    expected_max_lines: int
    should_have_imports: bool
    should_have_methods: bool
    should_have_fields: bool

class RefactoringVerifier:
    def __init__(self, components_dir: str, model_dir: str):
        self.components_dir = Path(components_dir)
        self.model_dir = Path(model_dir)
        self.issues = []
        self.warnings = []
        self.successes = []
        
    def log_issue(self, message: str):
        """Log a critical issue"""
        self.issues.append(message)
        print(f"❌ ISSUE: {message}")
    
    def log_warning(self, message: str):
        """Log a warning"""
        self.warnings.append(message)
        print(f"⚠️  WARNING: {message}")
    
    def log_success(self, message: str):
        """Log a success"""
        self.successes.append(message)
        print(f"✅ {message}")
    
    def get_expected_files(self) -> Dict[str, FileCheck]:
        """Define expected files and their characteristics"""
        return {
            'ISubController.java': FileCheck('ISubController.java', 15, 30, False, True, False),
            'LoggingController.java': FileCheck('LoggingController.java', 100, 200, True, True, True),
            'WindowController.java': FileCheck('WindowController.java', 400, 550, True, True, True),
            'IconController.java': FileCheck('IconController.java', 300, 450, True, True, True),
            'QueueController.java': FileCheck('QueueController.java', 500, 700, True, True, True),
            'EncoderController.java': FileCheck('EncoderController.java', 600, 850, True, True, True),
            'ModeController.java': FileCheck('ModeController.java', 200, 350, True, True, True),
            'FileInfoController.java': FileCheck('FileInfoController.java', 300, 500, True, True, True),
            'SubtitleController.java': FileCheck('SubtitleController.java', 1000, 1400, True, True, True),
            'RenamerController.java': FileCheck('RenamerController.java', 600, 850, True, True, True),
            'SettingsDialogController.java': FileCheck('SettingsDialogController.java', 150, 300, True, True, False),
        }
    
    def check_file_exists(self, filepath: Path) -> bool:
        """Check if file exists"""
        if filepath.exists():
            return True
        self.log_issue(f"File not found: {filepath}")
        return False
    
    def count_lines(self, filepath: Path) -> int:
        """Count non-empty lines in file"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            # Count non-empty, non-comment-only lines
            non_empty = [l for l in lines if l.strip() and not l.strip().startswith('//')]
            return len(non_empty)
        except Exception as e:
            self.log_issue(f"Error reading {filepath.name}: {e}")
            return 0
    
    def check_has_imports(self, filepath: Path) -> bool:
        """Check if file has import statements"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            has_imports = 'import ' in content and ';' in content
            return has_imports
        except Exception as e:
            self.log_issue(f"Error checking imports in {filepath.name}: {e}")
            return False
    
    def check_has_methods(self, filepath: Path) -> bool:
        """Check if file has method definitions"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            # Look for method patterns: public/private void/Type methodName(
            method_pattern = r'(public|private|protected)\s+\w+\s+\w+\s*\('
            has_methods = bool(re.search(method_pattern, content))
            return has_methods
        except Exception as e:
            self.log_issue(f"Error checking methods in {filepath.name}: {e}")
            return False
    
    def check_has_fields(self, filepath: Path) -> bool:
        """Check if file has field declarations"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            # Look for field patterns: private/public Type fieldName
            field_pattern = r'(private|public|protected)\s+(final\s+)?\w+(<.*?>)?\s+\w+\s*[;=]'
            has_fields = bool(re.search(field_pattern, content))
            return has_fields
        except Exception as e:
            self.log_issue(f"Error checking fields in {filepath.name}: {e}")
            return False
    
    def check_brace_matching(self, filepath: Path) -> bool:
        """Check if braces are balanced"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Simple brace counting (not perfect but catches obvious issues)
            open_braces = content.count('{')
            close_braces = content.count('}')
            
            if open_braces != close_braces:
                self.log_warning(f"{filepath.name}: Unbalanced braces ({open_braces} open, {close_braces} close)")
                return False
            return True
        except Exception as e:
            self.log_issue(f"Error checking braces in {filepath.name}: {e}")
            return False
    
    def check_for_todos(self, filepath: Path) -> List[str]:
        """Check for remaining TODO comments"""
        todos = []
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            
            for i, line in enumerate(lines, 1):
                if 'TODO' in line and 'Copy' in line:
                    todos.append(f"Line {i}: {line.strip()}")
            
            return todos
        except Exception as e:
            self.log_issue(f"Error checking TODOs in {filepath.name}: {e}")
            return []
    
    def check_implements_interface(self, filepath: Path) -> bool:
        """Check if controller implements ISubController"""
        if filepath.name == 'ISubController.java':
            return True  # Interface itself
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            implements = 'implements ISubController' in content
            return implements
        except Exception as e:
            self.log_issue(f"Error checking interface in {filepath.name}: {e}")
            return False
    
    def check_has_constructor(self, filepath: Path) -> bool:
        """Check if file has a constructor"""
        if filepath.name == 'ISubController.java':
            return True  # Interface doesn't need constructor
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Extract class name
            class_match = re.search(r'public\s+class\s+(\w+)', content)
            if not class_match:
                return False
            
            class_name = class_match.group(1)
            
            # Look for constructor
            constructor_pattern = rf'public\s+{class_name}\s*\('
            has_constructor = bool(re.search(constructor_pattern, content))
            return has_constructor
        except Exception as e:
            self.log_issue(f"Error checking constructor in {filepath.name}: {e}")
            return False
    
    def check_package_declaration(self, filepath: Path, expected_package: str) -> bool:
        """Check if file has correct package declaration"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                first_line = f.readline().strip()
            
            if not first_line.startswith('package'):
                # Skip empty lines
                with open(filepath, 'r', encoding='utf-8') as f:
                    for line in f:
                        if line.strip():
                            first_line = line.strip()
                            break
            
            if f'package {expected_package}' in first_line:
                return True
            else:
                self.log_warning(f"{filepath.name}: Incorrect package (expected {expected_package})")
                return False
        except Exception as e:
            self.log_issue(f"Error checking package in {filepath.name}: {e}")
            return False
    
    def verify_subtitle_item(self) -> bool:
        """Special verification for SubtitleItem.java"""
        print("\n🔍 Verifying SubtitleItem.java...")
        filepath = self.model_dir / 'SubtitleItem.java'
        
        if not self.check_file_exists(filepath):
            return False
        
        all_good = True
        
        # Check package
        if not self.check_package_declaration(filepath, 'com.encodeforge.model'):
            all_good = False
        
        # Check it's a class (not interface)
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            if 'public class SubtitleItem' not in content:
                self.log_issue("SubtitleItem: Not a valid class definition")
                all_good = False
            
            # Check for expected fields
            required_fields = ['selected', 'language', 'provider', 'score', 'format', 'fileId', 'downloadUrl']
            for field in required_fields:
                if field not in content:
                    self.log_warning(f"SubtitleItem: Missing field '{field}'")
            
            # Check for getters
            if 'getLanguage()' not in content or 'getProvider()' not in content:
                self.log_warning("SubtitleItem: Missing getter methods")
            
        except Exception as e:
            self.log_issue(f"Error verifying SubtitleItem: {e}")
            all_good = False
        
        if all_good:
            self.log_success("SubtitleItem.java is properly structured")
        
        return all_good
    
    def verify_controller_file(self, filename: str, check: FileCheck) -> bool:
        """Verify a single controller file"""
        print(f"\n🔍 Verifying {filename}...")
        filepath = self.components_dir / filename
        
        if not self.check_file_exists(filepath):
            return False
        
        all_good = True
        
        # Check line count
        line_count = self.count_lines(filepath)
        if line_count < check.expected_min_lines:
            self.log_warning(f"{filename}: Only {line_count} lines (expected {check.expected_min_lines}+)")
            all_good = False
        elif line_count > check.expected_max_lines:
            self.log_warning(f"{filename}: {line_count} lines (expected max {check.expected_max_lines})")
        else:
            self.log_success(f"{filename}: {line_count} lines (within expected range)")
        
        # Check package
        if not self.check_package_declaration(filepath, 'com.encodeforge.controller.components'):
            all_good = False
        
        # Check imports
        if check.should_have_imports:
            if self.check_has_imports(filepath):
                self.log_success(f"{filename}: Has import statements")
            else:
                self.log_warning(f"{filename}: Missing import statements")
                all_good = False
        
        # Check methods
        if check.should_have_methods:
            if self.check_has_methods(filepath):
                self.log_success(f"{filename}: Has method definitions")
            else:
                self.log_warning(f"{filename}: No methods found")
                all_good = False
        
        # Check fields
        if check.should_have_fields:
            if self.check_has_fields(filepath):
                self.log_success(f"{filename}: Has field declarations")
            else:
                self.log_warning(f"{filename}: No fields found")
        
        # Check braces
        if not self.check_brace_matching(filepath):
            all_good = False
        
        # Check for remaining TODOs
        todos = self.check_for_todos(filepath)
        if todos:
            self.log_warning(f"{filename}: Found {len(todos)} unresolved TODO(s)")
            for todo in todos[:3]:  # Show first 3
                print(f"    {todo}")
        
        # Check implements ISubController (except for ISubController itself)
        if filename != 'ISubController.java':
            if self.check_implements_interface(filepath):
                self.log_success(f"{filename}: Implements ISubController")
            else:
                self.log_warning(f"{filename}: Doesn't implement ISubController")
        
        # Check constructor
        if self.check_has_constructor(filepath):
            self.log_success(f"{filename}: Has constructor")
        else:
            self.log_warning(f"{filename}: No constructor found")
        
        return all_good
    
    def check_duplicate_code(self) -> List[Tuple[str, str, str]]:
        """Check for duplicate code blocks between files"""
        print("\n🔍 Checking for duplicate code...")
        duplicates = []
        
        files_content = {}
        for file in self.components_dir.glob('*.java'):
            try:
                with open(file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    # Extract method signatures for comparison
                    methods = re.findall(r'(public|private|protected)\s+\w+\s+(\w+)\s*\([^)]*\)', content)
                    files_content[file.name] = set(m[1] for m in methods)  # Just method names
            except Exception as e:
                continue
        
        # Compare files
        file_list = list(files_content.keys())
        for i, file1 in enumerate(file_list):
            for file2 in file_list[i+1:]:
                common_methods = files_content[file1] & files_content[file2]
                if common_methods:
                    for method in common_methods:
                        if method not in ['initialize', 'shutdown', 'toString', 'equals', 'hashCode']:
                            duplicates.append((file1, file2, method))
        
        if duplicates:
            self.log_warning(f"Found {len(duplicates)} potential duplicate method(s)")
            for file1, file2, method in duplicates[:5]:
                print(f"    {method}() appears in both {file1} and {file2}")
        else:
            self.log_success("No obvious duplicate methods found")
        
        return duplicates
    
    def generate_report(self):
        """Generate final verification report"""
        print("\n" + "="*70)
        print("📊 VERIFICATION REPORT")
        print("="*70)
        
        print(f"\n✅ Successes: {len(self.successes)}")
        print(f"⚠️  Warnings: {len(self.warnings)}")
        print(f"❌ Critical Issues: {len(self.issues)}")
        
        if self.issues:
            print("\n🚨 CRITICAL ISSUES TO FIX:")
            for issue in self.issues:
                print(f"  • {issue}")
        
        if self.warnings:
            print("\n⚠️  WARNINGS (Review Recommended):")
            for warning in self.warnings[:10]:  # Show first 10
                print(f"  • {warning}")
            if len(self.warnings) > 10:
                print(f"  ... and {len(self.warnings) - 10} more")
        
        print("\n" + "="*70)
        if not self.issues and len(self.warnings) < 5:
            print("🎉 REFACTORING LOOKS GOOD!")
            print("✅ All files are present and properly structured")
            print("\n📝 Next Steps:")
            print("  1. Fix any warnings listed above")
            print("  2. Compile the project to check for syntax errors")
            print("  3. Update MainController.java to use sub-controllers")
            print("  4. Run the application and test all features")
        elif not self.issues:
            print("✅ REFACTORING COMPLETE with minor warnings")
            print("⚠️  Review warnings above and fix as needed")
        else:
            print("❌ REFACTORING HAS ISSUES")
            print("🔧 Fix critical issues before proceeding")
        print("="*70 + "\n")
    
    def verify(self):
        """Main verification process"""
        print("🚀 Starting Refactoring Verification...\n")
        
        # Check components directory exists
        if not self.components_dir.exists():
            self.log_issue(f"Components directory not found: {self.components_dir}")
            self.generate_report()
            return
        
        # Verify SubtitleItem first
        self.verify_subtitle_item()
        
        # Verify each controller file
        expected_files = self.get_expected_files()
        for filename, check in expected_files.items():
            self.verify_controller_file(filename, check)
        
        # Check for duplicate code
        self.check_duplicate_code()
        
        # Generate final report
        self.generate_report()


def main():
    """Main entry point"""
    COMPONENTS_DIR = "EncodeForge/src/main/java/com/encodeforge/controller/components"
    MODEL_DIR = "EncodeForge/src/main/java/com/encodeforge/model"
    
    # Check if directories exist
    if not os.path.exists(COMPONENTS_DIR):
        print(f"❌ Error: Components directory not found at {COMPONENTS_DIR}")
        return
    
    # Run verification
    verifier = RefactoringVerifier(COMPONENTS_DIR, MODEL_DIR)
    verifier.verify()


if __name__ == "__main__":
    main()

