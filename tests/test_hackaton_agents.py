"""Construct real CrewAI agents and tasks without model or network calls."""

import unittest

from crewai import Agent, BaseLLM, Task

from agents import (
    ai_engineer,
    architect,
    presenter_timekeeper,
    product_owner,
    qa_security,
)


class OfflineLLM(BaseLLM):
    def call(self, messages, **kwargs):
        raise AssertionError("These tests must not call a model.")

    def supports_function_calling(self):
        return False


MODULES = (
    product_owner,
    architect,
    ai_engineer,
    qa_security,
    presenter_timekeeper,
)


class HackatonAgentTests(unittest.TestCase):
    def setUp(self):
        self.llm = OfflineLLM(model="offline-test")

    def test_exactly_five_distinct_roles(self):
        self.assertEqual(
            [module.ROLE for module in MODULES],
            [
                "Product Owner",
                "Arquitecto/a",
                "AI Engineer",
                "QA & Security",
                "Presenter / Timekeeper",
            ],
        )

    def test_real_agents_have_safe_defaults_and_explicit_model(self):
        for module in MODULES:
            with self.subTest(role=module.ROLE):
                self.assertTrue(module.INSTRUCTIONS.strip())
                agent = module.create_agent(self.llm)
                self.assertIsInstance(agent, Agent)
                self.assertIs(agent.llm, self.llm)
                self.assertEqual(agent.role, module.ROLE)
                self.assertFalse(agent.allow_delegation)
                self.assertFalse(agent.allow_code_execution)
                self.assertEqual(agent.tools, [])
                self.assertEqual(agent.max_iter, 5)
                self.assertIn("/groups/3", agent.backstory)
                self.assertIn("No inventes", agent.backstory)
                self.assertIn(module.INSTRUCTIONS.strip(), agent.backstory)

    def test_tasks_keep_real_context_and_only_po_requests_human_input(self):
        context = "Criterios W1-01 y W2-01; alcance pendiente de aprobacion."
        for module in MODULES:
            with self.subTest(role=module.ROLE):
                agent = module.create_agent(self.llm)
                task = module.create_task(agent, context)
                self.assertIsInstance(task, Task)
                self.assertIs(task.agent, agent)
                self.assertIn(context, task.description)
                self.assertEqual(task.expected_output, module.EXPECTED_OUTPUT)
                self.assertEqual(task.human_input, module is product_owner)

    def test_empty_context_and_blank_model_fail_explicitly(self):
        for module in MODULES:
            with self.subTest(role=module.ROLE):
                with self.assertRaises(ValueError):
                    module.create_agent(" ")
                with self.assertRaises(ValueError):
                    module.create_task(module.create_agent(self.llm), " \n")


if __name__ == "__main__":
    unittest.main()
