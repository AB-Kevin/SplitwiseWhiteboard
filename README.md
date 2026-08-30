# Splitwise Whiteboard

An Android home-screen widget that shows and lets you edit a Splitwise
group's "whiteboard" note — no need to open the Splitwise app.

Log in with your Splitwise account, pick a group, and add the widget to
your home screen. It refreshes periodically in the background and lets
you tap to edit the whiteboard text directly.

## ⚠️ Not built on the official Splitwise API

Splitwise's public API doesn't expose the whiteboard feature at all, so
this app talks to the same internal endpoints the Splitwise **website**
uses (session-cookie + CSRF authenticated), discovered by inspecting its
network traffic. These endpoints are undocumented and unsupported by
Splitwise — **the app could break at any time** if Splitwise changes them.

## Installing

Not on the Play Store. Grab the APK from [GitHub Releases](../../releases) and
sideload it — the app also checks for and installs new releases itself.

## Building

Standard Gradle/Android Studio project.

```
./gradlew assembleDebug
```
