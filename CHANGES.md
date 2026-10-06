# Focus Lamp 2.0: what changed and why

## The build compiled again

The previous commit had 8 compile errors:

| File | Error |
|---|---|
| `SettingsFragment` | `LinearLayout` used without import |
| `SettingsFragment` | `androidx.lifecycle.lifecycleScope.launchWhenResumed` isn't valid |
| `MainActivity` | `ParentModeActivity` used without import |
| `ChatAdapter` | `companion me` instead of `companion object` |
| `ChatToolHandler`, `AiRepository`, `ParentDashboardFragment` | `getDistractingApps()` and `usageTimeMinutes` don't exist |
| `InsightsFragment` | adapter constructor and ViewModel members that don't exist |
| `HomeFragment`, `SessionFragment` | navigation actions missing from `nav_graph.xml` |
| `ParentPinManager` | `PrefKeyEncryptionScheme.AES256_SKEY_TEXT` doesn't exist (it's `AES256_SIV`) |

## Screen time accuracy: root causes

Rewritten as `UsageEngine` (pure Kotlin, unit tested in `UsageEngineTest`).

1. **Time lost on in-app screen changes.** Android reports per *activity*. When an app moves from
   screen A to B the events are `PAUSED(A), RESUMED(B), STOPPED(A)`. The old code ended the
   session on `STOPPED(A)` and forgot screen B. Same events, old vs new:
   25 real minutes of YouTube → old **5.0 min**, new **25.0 min**.
2. **Screen-off never handled.** Screen-off, lock and shutdown events have package `android`,
   which the old code filtered out first. 10 minutes of video then screen off for 50 →
   old **120.0 min**, new **10.0 min**.
3. **Launcher, keyboard and Focus Lamp counted.** In the same test the launcher added 95 minutes
   to the old total. Now excluded (detected per phone), matching Digital Wellbeing.
4. **Home compared the wrong number.** `distractionMinutes` actually held *total* screen time and
   Home compared it with the distraction limit, while the service used distracting-only time.
   Every screen now reads one `UsageSnapshot`.
5. **School Hours and Bedtime were on by default**, forcing the lamp red regardless of use.
   Now off by default.
6. Sessions open at midnight were dropped; now clipped at midnight.

## App names and icons

- Names showed `packageName.substringAfterLast(".")`: Facebook → "katana", TikTok → "musically",
  Instagram/Twitter → "android". Now the launcher label.
- Android 11+ package visibility hid other apps, so name/icon lookups failed and apps silently
  disappeared from the list. Fixed with a `<queries>` launcher declaration in the manifest.
- The list is a RecyclerView with DiffUtil: ticking a checkbox updates one row instead of
  rebuilding the list and jumping to the top. Checkboxes stay beside each app.

## Navigation

- **Stuck on Home**: `setupWithNavController` keeps a saved back stack per tab. Screens that
  weren't tabs were saved inside Home's stack; re-tapping Home was ignored as a "reselect", and
  switching tabs restored the screen you were leaving. `TabNavigator` now always shows a tab's
  first screen, including when you tap the highlighted tab.
- New structure: four tabs (Today, Usage, Focus, Family), a live lamp chip and Settings in the
  top bar, and an **Ask coach** button on every screen. Every section is one tap away.
- The parent's phone has its own tabs: Children, Requests, Rules, Settings.

## Blocking and parental controls

- `BlockPolicy` (unit tested in `BlockPolicyTest`) decides what's blocked:
  parent limits and schedules first, then focus sessions, the student's own per-app limits,
  and the daily budget.
- **Self-set limits** block until midnight. **Parent limits** block until the parent lifts them,
  even across days.
- An accessibility service covers a blocked app the moment it opens (the old check ran every 10 s
  and lost track of the app after 5 minutes). A 1-second fallback poll is used if it's off.
- Requests go to the parent's phone; the parent approves from a notification. See `FAMILY_SETUP.md`.
- The old Parent Mode used hard-coded fake data ("Alex", "Sam", `REQ_101`).
- Security fixes: "forgot PIN" reset the parent PIN with the phone's fingerprint (the student's);
  a 24-hour automatic reset was a bypass; the recovery code could never verify. All removed.
  Switching a student's phone to parent mode now needs the parent PIN.

## Lamp

- Protocol unchanged (`/focus`, `/warning`, `/distraction`, `/idle`, `/status`).
- The app used to call `/blue`, `/purple`, `/amber` and `/pulse_*`, which the firmware doesn't
  serve. Removed.
- Failed commands are retried, and the state is re-sent every minute so a rebooted lamp catches up.
- "Find it for me" discovers the lamp via the mDNS service the firmware already advertises.
- Timeouts cut from 15 s to 4 s.

## Other

- Theme was `Material3.Light` with dark colors (invisible status-bar icons, light dialogs);
  the color named `text_navy` was near-white. New dark theme with a real token set.
- The coach had two separate implementations and sent each question twice. One version now,
  in a sheet, using the same numbers as the rest of the app.
- Focus sessions survive the app closing, and history is visible (it was unreachable before).
- Monitoring restarts after a reboot.
- Room schema unchanged, so existing session history and chats are kept.
