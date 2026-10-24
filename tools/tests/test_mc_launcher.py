"""ArchWeaver 启动工具的离线回归测试：CLI 解析、分支/worktree、PCL 实例选择、JAR 部署与多版本流水线。"""
import importlib.util
import io
import os
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1]
LOGIC_PATH = TOOLS / "scripts" / "mc_launcher_logic.py"
ENTRY_PATH = TOOLS / "1.一键启动mc脚本.py"


def load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


logic = load_module(LOGIC_PATH, "mc_launcher_logic")
entry = load_module(ENTRY_PATH, "archweaver_launcher_entry")


def completed(returncode=0, stdout="", stderr=""):
    return subprocess.CompletedProcess([], returncode, stdout, stderr)


def make_jar(path, loader="neoforge", with_class=True):
    """生成带加载器元数据的测试 JAR；with_class=False 用于模拟只有资源的无效产物。"""
    metadata = "META-INF/neoforge.mods.toml" if loader == "neoforge" else "fabric.mod.json"
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(metadata, "[[mods]]")
        if with_class:
            archive.writestr("com/sakurakugu/archweaver/Entry.class", b"class")
    return path


class ParseTests(unittest.TestCase):
    def test_defaults(self):
        args = entry.parse_args([])
        self.assertEqual(args.task, "all")
        self.assertEqual(args.mode, "debug")
        self.assertFalse(args.build)
        self.assertIsNone(args.release_path)
        self.assertIsNone(args.java_path)
        self.assertIsNone(args.mc_version)
        self.assertIsNone(args.branch)

    def test_targets_options_and_mode(self):
        args = entry.parse_args(["--fabric", "--release", "--path", r"C:\PCL", "--java-path", r"C:\Java",
                                 "--mc_version", "26.1.2-26.3", "--branch", "main"])
        self.assertEqual(args.task, "fabric")
        self.assertEqual(args.mode, "release")
        self.assertEqual(args.release_path, r"C:\PCL")
        self.assertEqual(args.java_path, r"C:\Java")
        self.assertEqual(args.mc_version, "26.1.2-26.3")
        self.assertEqual(args.branch, "main")

    def test_build_flag_and_all_branch_target(self):
        args = entry.parse_args(["--all_branch", "--build"])
        self.assertEqual(args.task, "all_branch")
        self.assertTrue(args.build)

    def test_unknown_argument_reports_chinese_error(self):
        with redirect_stderr(io.StringIO()) as err, self.assertRaises(SystemExit) as ctx:
            entry.parse_args(["--both"])
        self.assertEqual(ctx.exception.code, 2)
        self.assertIn("不支持的参数 '--both'", err.getvalue())

    def test_build_and_release_conflict(self):
        with redirect_stderr(io.StringIO()) as err, self.assertRaises(SystemExit) as ctx:
            entry.parse_args(["--build", "--release"])
        self.assertEqual(ctx.exception.code, 2)
        self.assertIn("不能同时使用", err.getvalue())

    def test_invalid_mc_version(self):
        for value in ("26.x", "mc/26.3", "26.3-", "26.1.2.3.4"):
            with self.subTest(value=value), redirect_stderr(io.StringIO()) as err, self.assertRaises(SystemExit) as ctx:
                entry.parse_args(["--mc_version", value])
            self.assertEqual(ctx.exception.code, 2)
            self.assertIn("Minecraft 版本号无效", err.getvalue())

    def test_invalid_branch(self):
        with redirect_stderr(io.StringIO()) as err, self.assertRaises(SystemExit) as ctx:
            entry.parse_args(["--branch", " "])
        self.assertEqual(ctx.exception.code, 2)
        self.assertIn("Git 分支名无效", err.getvalue())

    def test_help_returns_zero(self):
        with redirect_stdout(io.StringIO()) as out:
            self.assertEqual(entry.main(["--help"]), 0)
        text = out.getvalue()
        self.assertIn("目标：--all（默认）、--neoforge、--fabric", text)
        self.assertIn("--mc_version", text)
        self.assertIn("--all_branch", text)
        self.assertIn("PCL2_Release.exe", text)

    def test_no_arguments_returns_zero_and_prints_help(self):
        with redirect_stdout(io.StringIO()) as out:
            self.assertEqual(entry.main([]), 0)
        self.assertIn("用法：", out.getvalue())


class EntryFlowTests(unittest.TestCase):
    def test_debug_all_launches_each_loader(self):
        calls = []
        with patch.object(entry.logic, "prop", return_value="26.1.2"), \
             patch.object(entry.logic, "java_env") as java_env, \
             patch.object(entry.logic, "loader_client",
                          side_effect=lambda root, loader, version: calls.append((root.name, loader, version))):
            self.assertEqual(entry.main(["--debug"]), 0)
        self.assertEqual([(name, loader, version) for name, loader, version in calls],
                         [("java", "neoforge", "26.1.2"), ("java", "fabric", "26.1.2")])
        java_env.assert_called_once_with(None)

    def test_debug_explicit_loader(self):
        with patch.object(entry.logic, "prop", return_value="26.3"), \
             patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "loader_present", return_value=True), \
             patch.object(entry.logic, "loader_client") as client:
            self.assertEqual(entry.main(["--fabric"]), 0)
        client.assert_called_once_with(entry.logic.PROJECT_ROOT, "fabric", "26.3")

    def test_debug_explicit_loader_missing_module(self):
        with patch.object(entry.logic, "prop", return_value="26.3"), \
             patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "loader_present", return_value=False):
            with self.assertRaisesRegex(RuntimeError, "未找到 fabric 内容版模块"):
                entry.main(["--fabric"])

    def test_debug_all_branch_launches_each_version(self):
        branches = [("mc/26.3", "26.3"), ("mc/26.2", "26.2")]
        calls = []
        with patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "branches", return_value=branches), \
             patch.object(entry.logic, "worktree", side_effect=lambda branch, version: Path(branch.replace("/", "_"))), \
             patch.object(entry.logic, "loader_client",
                          side_effect=lambda root, loader, version: calls.append((root, loader, version))):
            self.assertEqual(entry.main(["--all_branch"]), 0)
        self.assertEqual(calls, [
            (Path("mc_26.3"), "neoforge", "26.3"), (Path("mc_26.3"), "fabric", "26.3"),
            (Path("mc_26.2"), "neoforge", "26.2"), (Path("mc_26.2"), "fabric", "26.2"),
        ])

    def test_mc_version_range_builds_matching_worktrees(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            branch_list = [("mc/26.4", "26.4"), ("mc/26.3", "26.3"), ("mc/26.2", "26.2"), ("mc/26.1.2", "26.1.2")]
            calls = []

            def fake_worktree(branch, version):
                path = base / version
                path.mkdir(parents=True, exist_ok=True)
                (path / "gradle.properties").write_text(f"minecraft_version={version}\n", encoding="utf-8")
                return path

            def fake_gradle(root, *tasks):
                calls.append((root.name, tasks))
                return 0

            with patch.object(entry.logic, "java_env"), \
                 patch.object(entry.logic, "branches", return_value=branch_list), \
                 patch.object(entry.logic, "worktree", side_effect=fake_worktree), \
                 patch.object(entry.logic, "gradle", side_effect=fake_gradle):
                self.assertEqual(entry.main(["--mc_version", "26.1.2-26.3", "--build"]), 0)
        self.assertEqual([name for name, _ in calls], ["26.3", "26.2", "26.1.2"])
        self.assertEqual(calls[0][1], ("clean", "build"))

    def test_mc_version_without_matching_branch_reports_error(self):
        with patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "branches", return_value=[("mc/26.0", "26.0")]):
            with self.assertRaisesRegex(RuntimeError, "未找到 Minecraft 版本分支"):
                entry.main(["--mc_version", "26.1.2-26.3", "--build"])

    def test_conflicting_options_are_rejected(self):
        with patch.object(entry.logic, "java_env"):
            with self.assertRaisesRegex(RuntimeError, "--mc_version 不能与 --branch"):
                entry.main(["--mc_version", "26.3", "--branch", "main"])
            with self.assertRaisesRegex(RuntimeError, "--mc_version 不能与 --all_branch"):
                entry.main(["--mc_version", "26.3", "--all_branch"])
            with self.assertRaisesRegex(RuntimeError, "--branch 不能与 --all_branch"):
                entry.main(["--branch", "main", "--all_branch"])

    def test_branch_main_builds_project_root(self):
        with patch.object(entry.logic, "run", return_value=completed()), \
             patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "prop", return_value="26.1.2"), \
             patch.object(entry.logic, "gradle", return_value=0) as gradle:
            self.assertEqual(entry.main(["--branch", "main", "--build"]), 0)
        gradle.assert_called_once_with(entry.logic.PROJECT_ROOT, "clean", "build")

    def test_branch_worktree_builds_created_worktree(self):
        with tempfile.TemporaryDirectory() as directory:
            worktree = Path(directory) / "wt"
            worktree.mkdir()
            (worktree / "gradle.properties").write_text("minecraft_version=26.3\n", encoding="utf-8")
            with patch.object(entry.logic, "run", return_value=completed()), \
                 patch.object(entry.logic, "java_env"), \
                 patch.object(entry.logic, "worktree", return_value=worktree) as make_worktree, \
                 patch.object(entry.logic, "gradle", return_value=0) as gradle:
                self.assertEqual(entry.main(["--branch", "mc/26.3", "--build"]), 0)
            make_worktree.assert_called_once_with("mc/26.3", "26.3")
            gradle.assert_called_once_with(worktree, "clean", "build")

    def test_missing_git_branch_reports_error(self):
        with patch.object(entry.logic, "run", return_value=completed(returncode=1)), \
             patch.object(entry.logic, "java_env"):
            with self.assertRaisesRegex(RuntimeError, "未找到 Git 分支"):
                entry.main(["--branch", "mc/26.3", "--build"])

    def test_unsupported_branch_name_reports_error(self):
        with patch.object(entry.logic, "run", return_value=completed()), \
             patch.object(entry.logic, "java_env"):
            with self.assertRaisesRegex(RuntimeError, "暂不支持此分支"):
                entry.main(["--branch", "feature/x", "--build"])

    def test_build_single_loader_includes_content_module(self):
        with patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "prop", return_value="26.3"), \
             patch.object(entry.logic, "loader_present", return_value=True), \
             patch.object(entry.logic, "gradle", return_value=0) as gradle:
            self.assertEqual(entry.main(["--neoforge", "--build"]), 0)
        gradle.assert_called_once_with(entry.logic.PROJECT_ROOT, "clean", ":neoforge:build", ":artifice:neoforge:build")

    def test_build_single_loader_missing_module_reports_error(self):
        with patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "prop", return_value="26.3"), \
             patch.object(entry.logic, "loader_present", return_value=False):
            with self.assertRaisesRegex(RuntimeError, "未找到 fabric 内容版模块"):
                entry.main(["--fabric", "--build"])

    def test_build_all_branch_uses_aggregate_build(self):
        with patch.object(entry.logic, "java_env"), \
             patch.object(entry.logic, "build_all_branches") as build:
            self.assertEqual(entry.main(["--all_branch", "--build"]), 0)
        build.assert_called_once_with()

    def test_release_uses_pcl_root_and_starts_pipeline(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            (release / "PCL2_Release.exe").write_bytes(b"")
            with patch.object(entry.logic, "java_env"), \
                 patch.object(entry.logic, "skip_profile_auth") as skip, \
                 patch.object(entry.logic, "release_clients") as clients:
                self.assertEqual(entry.main(["--release", "--path", str(release)]), 0)
            resolved = release.resolve()
            skip.assert_called_once_with(resolved)
            clients.assert_called_once_with([entry.logic.PROJECT_ROOT], "all", resolved, None)

    def test_release_single_loader_filters_deployment(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            (release / "PCL2_Release.exe").write_bytes(b"")
            with patch.object(entry.logic, "java_env"), \
                 patch.object(entry.logic, "skip_profile_auth"), \
                 patch.object(entry.logic, "release_clients") as clients:
                self.assertEqual(entry.main(["--neoforge", "--release", "--path", str(release)]), 0)
            clients.assert_called_once_with([entry.logic.PROJECT_ROOT], "neoforge", release.resolve(), "neoforge")

    def test_release_with_invalid_pcl_root_reports_error(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(entry.logic, "java_env"):
            with self.assertRaisesRegex(RuntimeError, "生产测试端路径无效"):
                entry.main(["--release", "--path", directory])


class WorktreeTests(unittest.TestCase):
    def test_existing_worktree_is_reused(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(logic, "WORKTREES_ROOT", Path(directory)):
            root = Path(directory) / "26.3"
            root.mkdir()
            (root / ".git").write_text("gitdir: ../.git/worktrees/26.3", encoding="utf-8")
            with patch.object(logic, "run") as run:
                self.assertEqual(logic.worktree("mc/26.3", "26.3"), root)
            run.assert_not_called()

    def test_existing_non_git_directory_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(logic, "WORKTREES_ROOT", Path(directory)):
            root = Path(directory) / "26.3"
            root.mkdir()
            (root / "file.txt").write_text("x", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "不是 Git 工作树"):
                logic.worktree("mc/26.3", "26.3")

    def test_worktree_is_pruned_and_added(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(logic, "WORKTREES_ROOT", Path(directory)):
            results = [completed(), completed()]
            with patch.object(logic, "run", side_effect=results) as run, redirect_stdout(io.StringIO()):
                root = logic.worktree("mc/26.3", "26.3")
            self.assertEqual(root, Path(directory) / "26.3")
            self.assertEqual(run.call_count, 2)
            self.assertEqual(run.call_args_list[0].args[0],
                             ["git", "-C", str(logic.PROJECT_ROOT), "worktree", "prune"])
            self.assertEqual(run.call_args_list[1].args[0],
                             ["git", "-C", str(logic.PROJECT_ROOT), "worktree", "add", str(root), "mc/26.3"])

    def test_failed_worktree_creation_reports_error(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(logic, "WORKTREES_ROOT", Path(directory)):
            results = [completed(), completed(returncode=1)]
            with patch.object(logic, "run", side_effect=results), redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, "无法创建 26.3 工作树"):
                    logic.worktree("mc/26.3", "26.3")


class PropertyAndArtifactTests(unittest.TestCase):
    def test_version_key(self):
        self.assertEqual(logic.version_key("26.3"), (26, 3, 0, 0))
        self.assertEqual(logic.version_key("26.1.2"), (26, 1, 2, 0))
        self.assertLess(logic.version_key("26.1.2"), logic.version_key("26.3"))

    def test_prop_reads_and_reports_missing_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(RuntimeError, "中找到属性 minecraft_version"):
                logic.prop(root, "minecraft_version")
            (root / "gradle.properties").write_text(
                "# 注释\nminecraft_version=26.1.2\nmod_version=1.1.0\n", encoding="utf-8")
            self.assertEqual(logic.prop(root, "minecraft_version"), "26.1.2")
            self.assertEqual(logic.prop(root, "mod_version"), "1.1.0")

    def root_with_properties(self, directory):
        root = Path(directory)
        (root / "gradle.properties").write_text(
            "minecraft_version=26.1.2\nmod_id=archweaver\nmod_version=1.1.0\n"
            "artifice_mod_id=archweaver_artifice\nartifice_mod_version=1.0.0\n", encoding="utf-8")
        return root

    def test_built_artifacts_lists_core_and_artifice_jars(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.root_with_properties(directory)
            output = root / "build" / "v1.1.0"
            output.mkdir(parents=True)
            expected = []
            for prefix, version in (("archweaver", "1.1.0"), ("archweaver_artifice", "1.0.0")):
                for loader in logic.LOADERS:
                    name = f"{prefix}-v{version}-mc26.1.2-{loader}.jar"
                    expected.append((output / name))
                    (output / name).write_bytes(b"jar")
            (output / "archweaver-v1.1.0-mc26.0-neoforge.jar").write_bytes(b"stale")
            self.assertEqual(logic.built_artifacts(root, "26.1.2"), expected)

    def test_built_artifacts_missing_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.root_with_properties(directory)
            with self.assertRaisesRegex(RuntimeError, "未在 .* 中找到构建产物"):
                logic.built_artifacts(root, "26.1.2")
            self.assertEqual(logic.built_artifacts(root, "26.1.2", required=False), [])

    def test_loader_present_uses_content_module_build_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertFalse(logic.loader_present(root, "neoforge"))
            module = root / "artifice" / "neoforge"
            module.mkdir(parents=True)
            (module / "build.gradle").write_text("", encoding="utf-8")
            self.assertTrue(logic.loader_present(root, "neoforge"))
            self.assertFalse(logic.loader_present(root, "fabric"))

    def test_loader_client_skips_missing_module(self):
        with tempfile.TemporaryDirectory() as directory, \
             redirect_stdout(io.StringIO()) as out, patch.object(logic, "gradle") as gradle:
            self.assertFalse(logic.loader_client(Path(directory), "fabric", "26.3"))
        gradle.assert_not_called()
        self.assertIn("跳过", out.getvalue())

    def test_loader_client_runs_content_module_client(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "artifice" / "neoforge"
            module.mkdir(parents=True)
            (module / "build.gradle").write_text("", encoding="utf-8")
            with patch.object(logic, "gradle", return_value=0) as gradle, redirect_stdout(io.StringIO()):
                self.assertTrue(logic.loader_client(root, "neoforge", "26.3"))
            gradle.assert_called_once_with(root, ":artifice:neoforge:runClient")
            self.assertTrue((module / "run" / "options.txt").is_file())


class InstanceTests(unittest.TestCase):
    def test_numeric_version_and_exact_minecraft_loader(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            versions = release / ".minecraft" / "versions"
            names = ["26.3-NeoForge_26.3.0.6-beta", "26.3-NeoForge_26.3.0.66-beta",
                     "26.30-NeoForge_26.30.0.100", "26.3-Forge_66.0.9"]
            for name in names:
                (versions / name).mkdir(parents=True)
            files = [Path("archweaver-v1.1.0-mc26.3-neoforge.jar"),
                     Path("archweaver_artifice-v1.0.0-mc26.3-neoforge.jar")]
            selected = logic.targets(files, release, None)
            self.assertEqual(selected[0][3].name, names[1])
            self.assertEqual(logic.targets(files, release, "fabric"), [])

    def test_fabric_space_named_instance_prefers_newer_loader(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            versions = release / ".minecraft" / "versions"
            for name in ("26.1.2-Fabric 0.19.3", "26.1.2-Fabric 0.19.5"):
                (versions / name).mkdir(parents=True)
            files = [Path("archweaver-v1.1.0-mc26.1.2-fabric.jar"),
                     Path("archweaver_artifice-v1.0.0-mc26.1.2-fabric.jar")]
            selected = logic.targets(files, release, None)
            self.assertEqual(selected[0][3].name, "26.1.2-Fabric 0.19.5")

    def test_stable_release_wins_over_same_version_beta(self):
        paths = [Path("26.3-NeoForge_26.3.0.66-beta"), Path("26.3-NeoForge_26.3.0.66")]
        self.assertEqual(logic.newest_instance(paths, "26.3", "neoforge"), paths[1])

    def test_unknown_or_duplicate_versions_remain_ambiguous(self):
        for names in [("26.3-NeoForge_custom", "26.3-NeoForge_26.3.0.66-beta"),
                      ("26.3-NeoForge_26.3.0.66-beta", "26.3-NeoForge 26.3.0.66-beta")]:
            with self.subTest(names=names), self.assertRaisesRegex(RuntimeError, "无法确定最新版本"):
                logic.newest_instance(list(map(Path, names)), "26.3", "neoforge")

    def test_missing_instance_reports_error(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            (release / ".minecraft" / "versions").mkdir(parents=True)
            files = [Path("archweaver-v1.1.0-mc26.3-neoforge.jar"),
                     Path("archweaver_artifice-v1.0.0-mc26.3-neoforge.jar")]
            with self.assertRaisesRegex(RuntimeError, "未安装 Minecraft 26.3 neoforge"):
                logic.targets(files, release, None)

    def test_incomplete_artifact_pair_reports_error(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            (release / ".minecraft" / "versions" / "26.3-NeoForge_26.3.0.66").mkdir(parents=True)
            with self.assertRaisesRegex(RuntimeError, "构建产物不完整"):
                logic.targets([Path("archweaver-v1.1.0-mc26.3-neoforge.jar")], release, None)

    def test_targets_sorted_by_version_then_loader(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            versions = release / ".minecraft" / "versions"
            for name in ("26.3-NeoForge_26.3.0.66-beta", "26.3-Fabric 0.19.5", "26.1.2-NeoForge_26.1.2.116"):
                (versions / name).mkdir(parents=True)
            files = []
            for version, loader in (("26.3", "neoforge"), ("26.3", "fabric"), ("26.1.2", "neoforge")):
                files.append(Path(f"archweaver-v1.1.0-mc{version}-{loader}.jar"))
                files.append(Path(f"archweaver_artifice-v1.0.0-mc{version}-{loader}.jar"))
            selected = logic.targets(files, release, None)
            self.assertEqual([(item[0], item[1]) for item in selected],
                             [("26.3", "neoforge"), ("26.3", "fabric"), ("26.1.2", "neoforge")])

    def test_unknown_artifact_names_are_skipped(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            (release / ".minecraft" / "versions").mkdir(parents=True)
            with redirect_stderr(io.StringIO()) as err:
                self.assertEqual(logic.targets([Path("README.md")], release, None), [])
            self.assertIn("无法识别产物名称", err.getvalue())


class InstallTests(unittest.TestCase):
    def make_pair(self, root, loader="neoforge", core_class=True):
        core = make_jar(root / f"archweaver-v1.1.0-mc26.3-{loader}.jar", loader, core_class)
        artifice = make_jar(root / f"archweaver_artifice-v1.0.0-mc26.3-{loader}.jar", loader)
        return core, artifice

    def test_install_deploys_both_jars_and_removes_old_versions(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            instance = base / "26.3-NeoForge_26.3.0.66-beta"
            mods = instance / "mods"
            mods.mkdir(parents=True)
            old_core = mods / "archweaver-v1.0.0-mc26.1.2-neoforge.jar"
            old_core.write_bytes(b"old")
            old_artifice = mods / "archweaver_artifice-v0.9.0-mc26.1.2-neoforge.jar"
            old_artifice.write_bytes(b"old")
            unrelated = mods / "jei.jar"
            unrelated.write_bytes(b"keep")
            core, artifice = self.make_pair(base)
            with redirect_stdout(io.StringIO()):
                logic.install(("26.3", "neoforge", [core, artifice], instance))
            self.assertTrue((mods / core.name).is_file())
            self.assertTrue((mods / artifice.name).is_file())
            self.assertFalse(old_core.exists())
            self.assertFalse(old_artifice.exists())
            self.assertTrue(unrelated.is_file())

    def test_install_accepts_fabric_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            instance = base / "26.1.2-Fabric 0.19.5"
            core, artifice = self.make_pair(base, "fabric")
            with redirect_stdout(io.StringIO()):
                logic.install(("26.1.2", "fabric", [core, artifice], instance))
            self.assertTrue((instance / "mods" / core.name).is_file())
            self.assertTrue((instance / "mods" / artifice.name).is_file())

    def test_install_rejects_resource_only_jar_without_touching_existing_mods(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            instance = base / "26.3-NeoForge_26.3.0.66-beta"
            mods = instance / "mods"
            mods.mkdir(parents=True)
            existing = mods / "archweaver-v1.0.0-mc26.1.2-neoforge.jar"
            existing.write_bytes(b"existing")
            core, artifice = self.make_pair(base, core_class=False)
            with self.assertRaisesRegex(RuntimeError, "缺少模组元数据或 class 文件"):
                logic.install(("26.3", "neoforge", [core, artifice], instance))
            self.assertTrue(existing.is_file())
            self.assertFalse((mods / core.name).exists())
            self.assertFalse((mods / artifice.name).exists())

    def test_install_rejects_broken_zip(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            instance = base / "26.3-NeoForge_26.3.0.66-beta"
            broken = base / "archweaver-v1.1.0-mc26.3-neoforge.jar"
            broken.write_bytes(b"not a zip")
            artifice = make_jar(base / "archweaver_artifice-v1.0.0-mc26.3-neoforge.jar")
            with self.assertRaisesRegex(RuntimeError, "无效的模组 JAR"):
                logic.install(("26.3", "neoforge", [broken, artifice], instance))


class ReleaseBuildTests(unittest.TestCase):
    def test_release_build_sets_hooks_and_returns_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "archweaver-v1.1.0-mc26.3-neoforge.jar"
            artifact.write_bytes(b"jar")
            with patch.object(logic, "run") as run, \
                 patch.object(logic, "gradle", return_value=0) as gradle, \
                 patch.object(logic, "built_artifacts", return_value=[artifact]) as built:
                self.assertEqual(logic.release_build(root, "26.3"), [artifact])
            run.assert_called_once_with(["git", "-C", str(root), "config", "core.hooksPath", ".githooks"])
            gradle.assert_called_once_with(root, "clean", "build", cancel_event=None)
            built.assert_called_once_with(root, "26.3")

    def test_release_build_reports_failure(self):
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(logic, "run"), patch.object(logic, "gradle", return_value=1):
            with self.assertRaisesRegex(RuntimeError, "打包失败"):
                logic.release_build(Path(directory), "26.3")

    def test_artifact_batches_single_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle.properties").write_text("minecraft_version=26.3\n", encoding="utf-8")
            with patch.object(logic, "release_build", return_value=[Path("x.jar")]) as build:
                batches = logic.artifact_batches(root, "all")
                self.assertEqual(next(batches), [Path("x.jar")])
                with self.assertRaises(StopIteration):
                    next(batches)
            build.assert_called_once_with(root, "26.3", None)

    def test_all_branch_delivers_each_version_and_cleans_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle.properties").write_text("mod_version=1.1.0\n", encoding="utf-8")
            archive = root / "build" / "v1.1.0"
            archive.mkdir(parents=True)
            (archive / "archweaver-v0.9.0-mc26.0-neoforge.jar").write_bytes(b"old")
            (archive / "archweaver_artifice-v0.9.0-mc26.0-neoforge.jar").write_bytes(b"old")
            built = []

            def release_build(build_root, version, cancel_event):
                built.append(version)
                return [make_jar(root / f"archweaver-v1.1.0-mc{version}-neoforge.jar"),
                        make_jar(root / f"archweaver_artifice-v1.0.0-mc{version}-neoforge.jar")]

            refs = "mc/26.3\nmc/26.2\nmc/netease\n"
            with patch.object(logic, "run") as run, \
                 patch.object(logic, "worktree", return_value=root), \
                 patch.object(logic, "release_build", side_effect=release_build), \
                 redirect_stdout(io.StringIO()):
                run.return_value = completed(stdout=refs)
                batches = logic.artifact_batches(root, "all_branch")
                first = next(batches)
                self.assertEqual(built, ["26.3"])
                self.assertEqual([path.name for path in first],
                                 ["archweaver-v1.1.0-mc26.3-neoforge.jar",
                                  "archweaver_artifice-v1.0.0-mc26.3-neoforge.jar"])
                self.assertTrue(first[0].is_file())
                self.assertEqual(first[0].parent, archive)
                self.assertFalse((archive / "archweaver-v0.9.0-mc26.0-neoforge.jar").exists())
                self.assertFalse((archive / "archweaver_artifice-v0.9.0-mc26.0-neoforge.jar").exists())
                next(batches)
                self.assertEqual(built, ["26.3", "26.2"])
                with self.assertRaises(StopIteration):
                    next(batches)


class PipelineTests(unittest.TestCase):
    def test_launch_first_version_while_remaining_version_builds(self):
        first_launched = threading.Event()
        second_built = threading.Event()
        launches = []
        main_thread = threading.get_ident()

        def batches(root, task, cancel_event):
            self.assertNotEqual(threading.get_ident(), main_thread)
            if root == "second":
                if not first_launched.wait(5):
                    raise RuntimeError("首个版本未在剩余构建完成前启动")
                second_built.set()
            yield [root]

        def launch(target, release):
            launches.append(target)
            if target == "first":
                first_launched.set()
                self.assertTrue(second_built.wait(5), "游戏运行期间没有继续构建")

        with patch.object(logic, "artifact_batches", side_effect=batches), \
             patch.object(logic, "targets", side_effect=lambda files, *args: files), \
             patch.object(logic, "install") as install, \
             patch.object(logic, "start_client", side_effect=launch), \
             redirect_stdout(io.StringIO()):
            logic.release_clients(["first", "second"], "all", Path("release"))
        self.assertEqual(launches, ["first", "second"])
        self.assertEqual([call.args[0] for call in install.call_args_list], launches)

    def test_launch_failure_cancels_background_work(self):
        stopped = threading.Event()

        def batches(root, task, cancel_event):
            yield [root]
            if cancel_event.wait(5):
                stopped.set()

        with patch.object(logic, "artifact_batches", side_effect=batches), \
             patch.object(logic, "targets", side_effect=lambda files, *args: files), \
             patch.object(logic, "install"), \
             patch.object(logic, "start_client", side_effect=RuntimeError("启动失败")), \
             redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "启动失败"):
                logic.release_clients(["first"], "all", Path("release"))
        self.assertTrue(stopped.is_set())

    def test_background_build_failure_is_reported(self):
        with patch.object(logic, "artifact_batches", side_effect=RuntimeError("打包失败")), \
             redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "打包失败"):
                logic.release_clients(["first"], "all", Path("release"))

    def test_keyboard_interrupt_cancels_background_work(self):
        stopped = threading.Event()

        def batches(root, task, cancel_event):
            yield [root]
            if cancel_event.wait(5):
                stopped.set()

        with patch.object(logic, "artifact_batches", side_effect=batches), \
             patch.object(logic, "targets", side_effect=lambda files, *args: files), \
             patch.object(logic, "install"), \
             patch.object(logic, "start_client", side_effect=KeyboardInterrupt), \
             redirect_stdout(io.StringIO()):
            with self.assertRaises(KeyboardInterrupt):
                logic.release_clients(["first"], "all", Path("release"))
        self.assertTrue(stopped.is_set())


class MiscTests(unittest.TestCase):
    def test_set_language_normalizes_existing_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sub" / "options.txt"
            path.parent.mkdir(parents=True)
            path.write_text("lang:en_us\nfoo:1\nlang:ja_jp\n", encoding="utf-8")
            logic.set_language(path)
            self.assertEqual(path.read_text(encoding="utf-8"), "lang:zh_cn\nfoo:1\n")

    def test_set_language_creates_missing_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "run" / "options.txt"
            logic.set_language(path)
            self.assertEqual(path.read_text(encoding="utf-8"), "lang:zh_cn\n")

    def test_java_env_without_path_prints_toolchain_message(self):
        with patch.dict(logic.os.environ, {"JAVA_TOOL_OPTIONS": ""}, clear=False), \
             redirect_stdout(io.StringIO()) as out:
            logic.java_env(None)
            self.assertIn("Toolchain", out.getvalue())
            self.assertEqual(logic.os.environ["JAVA_TOOL_OPTIONS"], "-Dfile.encoding=UTF-8")

    def test_java_env_rejects_invalid_path(self):
        with tempfile.TemporaryDirectory() as directory, \
             patch.dict(logic.os.environ, {"JAVA_TOOL_OPTIONS": ""}, clear=False):
            with self.assertRaisesRegex(RuntimeError, "指定的 Java 路径无效"):
                logic.java_env(directory)

    def test_java_env_sets_java_home_and_path(self):
        with tempfile.TemporaryDirectory() as directory, \
             patch.dict(logic.os.environ, {"JAVA_TOOL_OPTIONS": ""}, clear=False), redirect_stdout(io.StringIO()):
            home = Path(directory) / "jdk"
            (home / "bin").mkdir(parents=True)
            (home / "bin" / "java.exe").write_bytes(b"")
            logic.java_env(str(home))
            self.assertEqual(logic.os.environ["JAVA_HOME"], str(home.resolve()))
            self.assertTrue(logic.os.environ["PATH"].startswith(str(home.resolve() / "bin")))

    def test_skip_profile_auth_sets_default_value(self):
        release = Path("release")
        results = [({"status": "ok", "data": {"is_default": True}}, ""), ({"status": "ok"}, "")]
        with patch.object(logic, "pcl_cli", side_effect=results) as cli, redirect_stdout(io.StringIO()) as out:
            logic.skip_profile_auth(release)
        self.assertEqual(cli.call_count, 2)
        self.assertEqual(cli.call_args_list[1].args,
                         (release, "config", "--set", "LaunchSkipProfileAuthRequirement=true"))
        self.assertIn("LaunchSkipProfileAuthRequirement", out.getvalue())

    def test_skip_profile_auth_keeps_user_setting(self):
        with patch.object(logic, "pcl_cli", return_value=({"status": "ok", "data": {"is_default": False}}, "")) as cli:
            logic.skip_profile_auth(Path("release"))
        self.assertEqual(cli.call_count, 1)

    def test_skip_profile_auth_reports_cli_failure(self):
        with patch.object(logic, "pcl_cli", return_value=({"status": "error", "message": "boom"}, "")):
            with self.assertRaisesRegex(RuntimeError, "无法读取 PCL-CE 配置项"):
                logic.skip_profile_auth(Path("release"))

    def test_pcl_cli_parses_indented_result_block(self):
        with patch.object(logic, "run") as run:
            run.return_value = completed(stdout='日志\nPCL_CLI_RESULT={\n  "status": "ok",\n  "data": {"is_default": true}\n}\n')
            result, output = logic.pcl_cli(Path("release"), "config", "--get", "x")
        self.assertEqual(result, {"status": "ok", "data": {"is_default": True}})
        self.assertIn("PCL_CLI_RESULT=", output)

    def test_process_running_detects_current_process(self):
        self.assertTrue(logic.process_running(os.getpid()))

    def test_process_running_is_false_for_invalid_pid(self):
        self.assertFalse(logic.process_running(2 ** 31 - 1))


class ProcessTests(unittest.TestCase):
    def test_cancellation_stops_real_background_process(self):
        cancel_event = threading.Event()
        spawned = []
        real_popen = logic.subprocess.Popen
        timer = None

        def popen(*args, **kwargs):
            nonlocal timer
            process = real_popen(*args, **kwargs)
            spawned.append(process)
            if len(spawned) == 1:
                timer = threading.Timer(0.2, cancel_event.set)
                timer.start()
            return process

        try:
            with patch.object(logic.subprocess, "Popen", side_effect=popen):
                with self.assertRaisesRegex(RuntimeError, "已取消后台构建"):
                    logic.run([sys.executable, "-c", "import time; time.sleep(30)"],
                              capture=True, cancel_event=cancel_event)
            self.assertIsNotNone(spawned[0].poll(), "取消后后台子进程仍在运行")
        finally:
            if timer is not None:
                timer.cancel()
            for process in spawned:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=5)
        self.assertTrue(cancel_event.is_set())
        self.assertIsNotNone(spawned[0].returncode)


if __name__ == "__main__":
    unittest.main()
