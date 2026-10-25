# ShotSort v1.1 — screenshot asks "Save to which folder?"
Free & open-source Android app. Instagram-Saved style sorting for screenshots.
Take a screenshot → notification "Sort screenshot?" → pick a folder (or + New folder) → moved to Pictures/<folder>/, shows in Gallery as that album.
Name one folder `Calendar` — inbox for screenshot→Google Calendar automation.
## Install
Push to GitHub → Actions → Build APK → download shotsort-apk → install → grant Photos + Notifications → Start watching.
100% offline. MIT.

## Calendar folder (events from screenshots)
Any new image in `Pictures/Calendar` is read on the phone (ML Kit text recognition + date/time finding). Nothing is added immediately. Each evening (default 9 PM, changeable on the home screen) one notification lists what was found; tap it to approve or skip each event, and approved ones are written to your Google Calendar with a 30-minute reminder.
- First run needs internet once to download the small date-finding model; after that it works offline.
- English text only. Images already in the folder before you installed this version are ignored.
- Grant Calendar access when asked. Use "Review events" on the home screen to look at pending events at any time.

## Smart titles (optional, open source, on your phone)
The home screen can download a small open language model (Qwen2.5 1.5B Instruct, Apache-2.0, about 1 GB, run through the MIT-licensed llama.cpp). After ML Kit has found an event's date and time, the model reads the screenshot text and suggests a short title and place. It never decides the date or time, and its suggestion is only used if it matches words that really appear in the screenshot; otherwise the built-in title stays.
- Runs in a separate process that is shut down after use, so a problem with the model cannot stop screenshot watching.
- Needs the INTERNET permission only for the one-time model download. You can instead pick a GGUF file you downloaded yourself ("Use a model file").
- Takes roughly 10-40 seconds per event in the background; results are ready by the evening review.
- If the model crashes 3 times it is switched off automatically.
