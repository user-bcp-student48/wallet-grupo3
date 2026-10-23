"""Verify dependency diagnostics and test-runner exit status offline."""

import contextlib
import io
import subprocess
import sys
import unittest
from unittest.mock import patch

from tools import verify


class VerifyTests(unittest.TestCase):
    def test_missing_crewai_reports_current_interpreter_without_running_tests(self):
        error = io.StringIO()
        with (
            patch.object(verify.importlib.util, "find_spec", return_value=None),
            patch.object(verify.subprocess, "run") as run,
            contextlib.redirect_stdout(io.StringIO()),
            contextlib.redirect_stderr(error),
        ):
            self.assertEqual(verify.main(), 1)
        run.assert_not_called()
        self.assertIn(sys.executable, error.getvalue())
        self.assertIn("requirements-hackaton.txt", error.getvalue())

    def test_only_current_tests_run_with_same_python_and_disabled_telemetry(self):
        with (
            patch.object(verify.importlib.util, "find_spec", return_value=object()),
            patch.object(
                verify.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 0),
            ) as run,
            contextlib.redirect_stdout(io.StringIO()),
        ):
            self.assertEqual(verify.main(), 0)
        command = run.call_args.args[0]
        self.assertEqual(command[0], sys.executable)
        self.assertIn("test_hackaton*.py", command)
        self.assertNotIn("test_graph_hitl.py", command)
        self.assertEqual(run.call_args.kwargs["cwd"], verify.ROOT)
        self.assertEqual(
            run.call_args.kwargs["env"]["OTEL_SDK_DISABLED"], "true"
        )

    def test_failure_is_not_reported_as_success(self):
        output = io.StringIO()
        with (
            patch.object(verify.importlib.util, "find_spec", return_value=object()),
            patch.object(
                verify.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 2),
            ),
            contextlib.redirect_stdout(output),
            contextlib.redirect_stderr(io.StringIO()),
        ):
            self.assertEqual(verify.main(), 2)
        self.assertNotIn("aprobados", output.getvalue())


if __name__ == "__main__":
    unittest.main()
