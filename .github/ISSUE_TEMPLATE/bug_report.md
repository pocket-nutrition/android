---
name: Bug report
about: Report something that is broken in the Android app
title: "[Bug] "
labels: bug
assignees: ""
---

## Describe the bug

A clear, concise description of what is wrong.

## Steps to reproduce

1. Go to '...'
2. Tap on '...'
3. Enter '...'
4. See error

## Expected behavior

What you expected to happen instead.

## Screenshots / screen recording

If applicable, attach screenshots or a screen recording to help explain the problem.

## Environment

- **Device:** (e.g. Pixel 8, Samsung Galaxy S24)
- **Android version:** (e.g. Android 15)
- **App version:** (Settings/About screen, e.g. 2026.08.4, or `versionCode` if you have it)
- **Build variant:** (debug / release, and where you got it — GitHub release, sideloaded, F-Droid)
- **Network:** (Wi-Fi / mobile data / offline) — relevant if the bug involves a server call

## Logs

If you can reproduce with `adb logcat` attached, paste the relevant lines here (redact anything
personal). Fenced code block, please:

```
paste logcat output here
```

## Additional context

Anything else that seems relevant — does it happen every time, only with a specific ingredient or
cooking method, only after a fresh install, etc.

## Is this an app bug, or upstream?

If the wrong behavior looks like it could be a missing/incorrect ingredient, mapping, or nutrition
value rather than something the app itself is doing wrong, it likely belongs in
`pocket-nutrition/knowledge` instead — mention that here if you're not sure, and it'll get redirected.
