---
tags:
  - project-hub
  - slim-launcher
  - documentation
created: 2026-06-02
updated: 2026-06-29
status: In Development
---

# Slim Launcher: Obsidian Knowledge Base & Agent Docs

Welcome to the central documentation hub for **Slim Launcher** (a minimalist, gesture-based Android launcher inspired by Niagara Launcher). This vault is structured for both human developers and AI coding agents to collaborate and build the project from scratch.

---

## 🗺️ Navigation Map

### 🏛️ Architecture & Requirements
- **[[architecture/system_requirements|System Requirements & API Architecture]]**: Deep-dive into Android system services, permissions, background lifecycle, and **window focus & ANR stability rules**.
- **[[architecture/ui_ux_layout|UI/UX Layout & Wave Gesture Mechanics]]**: Detailed specifications for the minimalist scroll and wave touch mechanics.
- **[[architecture/settings_and_features|Settings, Feature Toggles & Search Behavior]]**: The Settings screen, preference storage, smart search ranking, weather modes, adaptive theming, and widget hosting.
- **[[distribution/fdroid_publishing|F-Droid Publishing & Compliance Guide]]**: Security, FOSS principles, and build setups for F-Droid submission.

> [!NOTE]
> The repo root also contains `CLAUDE.md` — an agent-facing quick-reference for the most critical invariants (ANR rules, Back handling, widget binding). Claude Code loads it automatically on every session.

### 🤖 Agent Development Protocols
- **[[agent_protocols/agent_development_plan|AI-Led Development Plan]]**: The step-by-step roadmap for how I (the AI) will build and test the codebase.
- **[[agent_protocols/subagent_delegation_tasks|Subagent Roles & Prompts]]**: Specialized system prompts and tasks for AI subagents to tackle specific modules.

### ⏱️ AI Effort Estimation
- **[[estimation_ai_led|AI-Led Development Estimation]]**: Detailed timeline, token/cycle estimations, and verification plans if the AI develops the project end-to-end.

---

> [!NOTE]
> All files in this vault use standard Obsidian formatting, including `[[WikiLinks]]` and YAML frontmatter. Coding agents should parse these documents to maintain absolute alignment with project requirements.
