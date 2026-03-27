# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RealChat is an Android chat application that communicates with OpenAI-compatible AI backends. It supports multiple AI providers (DeepSeek, OpenAI, OpenAI-compatible endpoints), character cards (SillyTavern/TavernAI format), conversation history, and user persona customization.

## Build & Test Commands

```bash
# Lint (always run before committing code changes)
./gradlew lintDebug

# Unit tests (run when touching business logic, state, repositories, or networking)
./gradlew testDebugUnitTest

# Compile instrumented tests without running (to verify androidTest compilation)
./gradlew compileDebugAndroidTestKotlin

# Run instrumented tests on connected device/emulator
./gradlew connectedDebugAndroidTest
```

## Android Emulator Setup (Windows)

The SDK path is in `local.properties`. Derive `adb` and `emulator` paths from it:

```powershell
$raw = (Get-Content local.properties | Where-Object { $_ -match '^sdk\.dir=' } | ForEach-Object { $_ -replace '^sdk\.dir=', '' })
$sdkDir = $raw.Replace('\\\\', '\').Replace('\:', ':')
$adb = Join-Path $sdkDir 'platform-tools\adb.exe'
$emulator = Join-Path $sdkDir 'emulator\emulator.exe'
```

Verified AVD name: `Pixel_8`. Launch with:
```powershell
Start-Process -FilePath $emulator -ArgumentList '-avd', 'Pixel_8'
```

Verify the device is truly ready before running instrumented tests (not just that the window appeared):
```powershell
& $adb shell getprop sys.boot_completed   # expect: 1
& $adb shell getprop init.svc.bootanim    # expect: stopped
& $adb shell settings get secure user_setup_complete  # expect: 1
```

Unlock the screen if needed: `& $adb shell input keyevent 82`

Launch the app: `& $adb shell am start -n io.github.c1921.realchat/.MainActivity`

If the device is `offline`: `& $adb kill-server && & $adb start-server`

## Architecture

The app follows MVVM with a clean separation between data, domain, and UI layers. All UI state flows from a single `ChatViewModel` via `StateFlow<MainUiState>`.

### State Management

`MainUiState` (in `ChatViewModel.kt`) is a single nested state tree:
- `ConversationUiState` — active conversation, message list, draft, send status
- `CharacterCardsUiState` — character card list and editor state
- `SettingsUiState` — provider config form and persona

`ChatViewModel` also holds private in-memory state: `activeConversationBundle` (the loaded conversation+messages), `providerDrafts` (per-provider config edits before saving), and `availableCards`.

### Data Layer

| Component | Responsibility |
|-----------|---------------|
| `AppDatabase` | Room database; exposes DAOs |
| `RoomConversationRepository` | CRUD + streaming for conversations and messages |
| `RoomCharacterCardRepository` | CRUD + import/export for character cards |
| `DataStoreAppPreferencesRepository` | Persists provider configs, selected provider, user persona, last conversation ID |
| `OpenAiCompatibleChatProvider` | HTTP calls via OkHttp to OpenAI-compatible `/chat/completions` |
| `PromptComposer` | Assembles `system` + `user`/`assistant` message list from character snapshot, user persona, and conversation history |

### Key Domain Models (`model/ChatModels.kt`)

- `ProviderConfig` — holds `ProviderType`, `apiKey`, `model`, `baseUrl` with `hasRequiredFields()` validation
- `CharacterCard` / `CharacterCardSnapshot` — full character card data; snapshot is stored on conversations so changes to the card don't retroactively alter history
- `Conversation` — links to a `characterCardId` and stores a `CharacterCardSnapshot` at creation time
- `ChatMessage` — `role` (`System`/`User`/`Assistant`) + `content`

### Navigation

`RealChatApp.kt` is the root composable. Navigation state lives in `MainUiState`:
- `currentScreen: AppScreen` — bottom-nav tabs (`Conversations`, `Characters`, `Settings`)
- `secondaryScreen: SecondaryScreen?` — currently only `ChatDetail(conversationId)`, shown as an overlay/back-stack screen

### Character Card Format

Import/export uses the SillyTavern `CharacterCardV2` JSON format. `CharacterCardJson.kt` handles serialization; the `data/character/` package owns all import/export logic.

## Commit Convention

Follows Conventional Commits. Summaries must be in Chinese, start with a verb, be ≤ 50 chars, and have no trailing period.

```
<type>: <中文摘要>
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`. Breaking changes: append `!` to type or include `BREAKING CHANGE:` in the body.
