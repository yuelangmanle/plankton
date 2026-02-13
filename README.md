# Plankton Project

This repository contains multiple modules for plankton data work, reporting, and Android tools.

Important:

- `android/app/src/main/assets/templates/table1.xlsx`
- `android/app/src/main/assets/templates/table2.xlsx`

are sanitized empty templates for export (no real data).

## Repository Structure

- `app/`: Web app (Vite + React + TypeScript)
- `android/`: Native Android app (`plankton-native`)
- `voice_assistant/`: Native Android voice assistant app (`voice_assistant`)
- `docs/`: Project docs
- `tools/`: Utility scripts and helper tools

## Quick Start (Web App)

```bash
cd app
npm install
npm run dev
```

Common scripts:

- `npm run build`: Production build
- `npm run lint`: Lint
- `npm run preview`: Preview build
- `npm run dev:export`: Export debug demo files

## First-Time Resource Initialization

See `docs/首次初始化资源.md` and follow it once after clone.

## Android Modules

- `android/`: Main native app module
- `voice_assistant/`: Voice assistant app + bridge module

Build from Android Studio, or use Gradle wrappers in each module directory.

## What Is Intentionally Not Tracked In Git

To keep repository size manageable and avoid pushing personal/local artifacts, these are ignored:

- Build artifacts and temp directories (`build/`, `.gradle/`, `.tmp/`, `.toolchain/`, etc.)
- APK history packages (`apk_history/`, `voice_assistant/apk_history/`, etc.)
- Local model bundles (`voice_assistant/app/src/main/assets/models/`, `voice_assistant/app/src/main/assets/sherpa/`, `voice_assistant/third_party/sherpa/`)
- Spreadsheet files (`*.xlsx`, `*.xls`, `*.csv`, `*.tsv`, `*.xlsm`, `*.ods`)

Note: Spreadsheet files are kept local but excluded from version control/upload.

## Git Workflow

```bash
git add .
git commit -m "your message"
git push
```
