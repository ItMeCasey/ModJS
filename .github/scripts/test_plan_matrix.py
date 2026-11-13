#!/usr/bin/env python3
"""Tests for plan_matrix.py. Run from the repo root: python -I -m unittest discover -s .github/scripts  (or run this file)."""
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import plan_matrix as pm  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
EVERY = sorted(pm.NAMES)


def names(picked):
    return sorted(m for m, _ in picked)


def git_diff(a, b):
    """Changed files between two commits, or skip the test when a commit is missing from this clone."""
    out = subprocess.run(["git", "diff", "--name-only", a, b], cwd=ROOT, capture_output=True, text=True)
    if out.returncode != 0:
        raise unittest.SkipTest(f"commit {a} or {b} is not in this clone")
    return [line for line in out.stdout.splitlines() if line]


class SyntheticChanges(unittest.TestCase):
    def test_docs_and_status_only_build_nothing(self):
        files = ["PROJECT_STATUS.md", "README.md", "CHANGELOG.md", "docs/PARITY.md", "docs/frames/a.png", "docs/testing/x.log",
                 "branding/banners/banner.png", "curseforge/DESCRIPTION.md", "scripts/outline_tour.py", ".gitignore"]
        self.assertEqual(pm.plan_build(files), [])

    def test_workflow_and_ci_script_only_build_nothing(self):
        files = [".github/workflows/build.yml", ".github/workflows/release.yml", ".github/scripts/plan_matrix.py",
                 ".github/scripts/publish_curseforge.py", ".github/ISSUE_TEMPLATE/config.yml"]
        self.assertEqual(pm.plan_build(files), [])

    def test_common_selects_every_module(self):
        self.assertEqual(names(pm.plan_build(["common/src/main/java/com/itmecasey/modjs/X.java"])), EVERY)
        self.assertEqual(names(pm.plan_build(["common/src/main/resources/modjs/modjs.d.ts"])), EVERY)

    def test_smoke_test_and_examples_select_every_module(self):
        self.assertEqual(names(pm.plan_build(["smoke-test/server_scripts/selftest.js"])), EVERY)
        self.assertEqual(names(pm.plan_build(["examples/registry/startup_scripts/registry_example.js"])), EVERY)

    def test_single_module(self):
        self.assertEqual(names(pm.plan_build(["forge-1.21.1/src/main/java/A.java"])), ["forge-1.21.1"])
        self.assertEqual(names(pm.plan_build(["neoforge-26.2/gradle.properties", "docs/a.md"])), ["neoforge-26.2"])
        self.assertEqual(names(pm.plan_build(["forge-26.3/build.gradle"])), ["forge-26.3"])

    def test_forge_1_20_1_sources_also_select_neoforge_1_20_1(self):
        self.assertEqual(names(pm.plan_build(["forge-1.20.1/src/main/java/A.java"])), ["forge-1.20.1", "neoforge-1.20.1"])
        self.assertEqual(names(pm.plan_build(["forge-1.20.1/build.gradle"])), ["forge-1.20.1"])
        self.assertEqual(names(pm.plan_build(["neoforge-1.20.1/src/main/java/A.java"])), ["neoforge-1.20.1"])

    def test_libs_select_the_modules_of_that_minecraft_version(self):
        self.assertEqual(names(pm.plan_build(["libs/smwti-api-26.2.jar"])), ["forge-26.2", "neoforge-26.2"])
        self.assertEqual(names(pm.plan_build(["libs/smwti-api-26.1.2.jar"])), ["forge-26.1.2", "neoforge-26.1.2"])
        self.assertEqual(names(pm.plan_build(["libs/other.jar"])), EVERY)

    def test_mixed_files_union(self):
        picked = pm.plan_build(["docs/a.md", "forge-26.2/src/main/java/A.java", "neoforge-26.3/src/main/java/B.java"])
        self.assertEqual(names(picked), ["forge-26.2", "neoforge-26.3"])

    def test_unknown_path_selects_everything(self):
        self.assertEqual(names(pm.plan_build(["something/new.txt"])), EVERY)
        self.assertEqual(names(pm.plan_build(["build.gradle"])), EVERY)
        self.assertEqual(names(pm.plan_build(["forge-27.0/src/A.java"])), EVERY)
        self.assertEqual(names(pm.plan_build([".github/dependabot.yml"])), EVERY)

    def test_diff_failure_selects_everything(self):
        self.assertEqual(names(pm.plan_build(None)), EVERY)
        self.assertIsNone(pm.changed_files("0123456789abcdef0123456789abcdef01234567", "HEAD"))

    def test_matrix_entries_keep_module_and_java(self):
        by_name = dict(pm.ALL)
        self.assertEqual(by_name["forge-1.20.1"], 17)
        self.assertEqual(by_name["neoforge-1.21.1"], 21)
        self.assertEqual(by_name["forge-26.3"], 25)
        self.assertEqual(len(pm.ALL), 10)

    def test_files_option_and_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "out.txt")
            env = dict(os.environ, GITHUB_OUTPUT=out)
            script = os.path.join(ROOT, ".github", "scripts", "plan_matrix.py")
            run = subprocess.run([sys.executable, "-I", script, "build", "--files", "PROJECT_STATUS.md"],
                                 cwd=ROOT, env=env, capture_output=True, text=True)
            self.assertEqual(run.returncode, 0, run.stderr)
            with open(out, encoding="utf-8") as fh:
                text = fh.read()
            self.assertIn('matrix={"include": []}', text)
            self.assertIn("any=false", text)


class RealCommits(unittest.TestCase):
    def test_status_only_commit(self):
        self.assertEqual(pm.plan_build(git_diff("b64fbce^", "b64fbce")), [])

    def test_ci_script_only_commit(self):
        self.assertEqual(pm.plan_build(git_diff("e0015e7^", "e0015e7")), [])

    def test_python_tour_script_and_docs_commit(self):
        self.assertEqual(pm.plan_build(git_diff("776bba0^", "776bba0")), [])

    def test_single_module_commit(self):
        self.assertEqual(names(pm.plan_build(git_diff("ddffa14^", "ddffa14"))), ["forge-1.21.1"])

    def test_three_neoforge_modules_commit(self):
        self.assertEqual(names(pm.plan_build(git_diff("11be48f^", "11be48f"))), ["neoforge-26.1.2", "neoforge-26.2", "neoforge-26.3"])

    def test_legacy_pair_commit(self):
        self.assertEqual(names(pm.plan_build(git_diff("2f77dab^", "2f77dab"))), ["forge-1.20.1", "neoforge-1.20.1"])

    def test_common_commit(self):
        self.assertEqual(names(pm.plan_build(git_diff("ec73827^", "ec73827"))), EVERY)

    def test_examples_only_commit(self):
        self.assertEqual(names(pm.plan_build(git_diff("e12416c^", "e12416c"))), EVERY)


class ReleaseMode(unittest.TestCase):
    def make_repo(self, versions):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        for module, _ in pm.ALL:
            os.makedirs(os.path.join(tmp.name, module))
            with open(os.path.join(tmp.name, module, "gradle.properties"), "w", encoding="utf-8") as fh:
                fh.write(f"mod_id=modjs\nmod_version={versions.get(module, '2026.11.1')}\n")
        return tmp.name

    def test_only_modules_at_the_tag_version(self):
        root = self.make_repo({"forge-26.3": "2026.11.2", "neoforge-26.3": "2026.11.2"})
        version, picked = pm.plan_release("v2026.11.2", root)
        self.assertEqual(version, "2026.11.2")
        self.assertEqual(names(picked), ["forge-26.3", "neoforge-26.3"])

    def test_all_modules_at_the_tag_version_release_everything(self):
        root = self.make_repo({})
        self.assertEqual(names(pm.plan_release("v2026.11.1", root)[1]), EVERY)

    def test_neoforge_1_20_1_pulls_in_the_forge_jar_it_ships(self):
        root = self.make_repo({"neoforge-1.20.1": "2026.11.5"})
        self.assertEqual(names(pm.plan_release("v2026.11.5", root)[1]), ["forge-1.20.1", "neoforge-1.20.1"])

    def test_tag_nobody_carries_selects_nothing(self):
        root = self.make_repo({})
        self.assertEqual(pm.plan_release("v2026.11.9", root)[1], [])

    def test_manual_run_uses_the_newest_version(self):
        root = self.make_repo({"forge-26.2": "2026.11.10", "forge-26.3": "2026.11.9"})
        version, picked = pm.plan_release("main", root)
        self.assertEqual(version, "2026.11.10")
        self.assertEqual(names(picked), ["forge-26.2"])

    def test_real_repo_current_version_matches_every_module(self):
        current = pm.module_version("forge-26.3", ROOT)
        if current is None:
            self.skipTest("forge-26.3 is not in this checkout")
        version, picked = pm.plan_release("v" + current, ROOT)
        self.assertIn("forge-26.3", names(picked))


if __name__ == "__main__":
    unittest.main()
