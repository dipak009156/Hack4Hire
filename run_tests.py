import subprocess
import sys
import os

def run():
    print("=" * 80)
    print("           QUANTUM-LOCK AUTOMATED COMPREHENSIVE VERIFICATION SUITE")
    print("=" * 80)
    
    python_exe = sys.executable
    tests_dir = os.path.join(os.path.dirname(__file__), "tests")
    
    # 1. Platform Mail & Onboarding Multi-Tenant Isolation
    mail_test = os.path.join(tests_dir, "test_platform_mail_workflow.py")
    print("\n>>> Running Suite 1: Credential Onboarding & Tenant Isolation (13 checkpoints)...")
    res1 = subprocess.run([python_exe, mail_test])
    if res1.returncode != 0:
        print("\n[ERROR] Suite 1 Failed!")
        sys.exit(res1.returncode)
        
    # 2. Comprehensive 25-Point System Test
    suite_test = os.path.join(tests_dir, "test_suite.py")
    print("\n>>> Running Suite 2: Core Zero-Trust Architecture (25 checkpoints)...")
    res2 = subprocess.run([python_exe, suite_test])
    if res2.returncode != 0:
        print("\n[ERROR] Suite 2 Failed!")
        sys.exit(res2.returncode)
        
    print("\n" + "=" * 80)
    print("ALL SUITES PASSED! 38/38 VERIFICATION CHECKPOINTS 100% SUCCESSFUL!")
    print("=" * 80)

if __name__ == "__main__":
    run()

