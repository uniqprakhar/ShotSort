# ShotSort

**Sort every screenshot into the right folder the moment you take it, and turn event screenshots into calendar entries.**

ShotSort is a free, open-source Android app. Android and Samsung phones always drop new screenshots into one `Screenshots` folder, and there is no setting to change that. ShotSort works around it: right after a screenshot lands, you get a notification asking *where should this go?* Tap a folder and the screenshot is moved there. It then shows up in your Gallery as its own album.

It also has an optional second job: screenshots you put in a folder called **Calendar** are read on your phone, and the events found in them are offered to you for approval each evening.

---

## Contents

1. [What it does](#what-it-does)
2. [How it works](#how-it-works)
3. [Install](#install)
4. [First-time setup](#first-time-setup)
5. [Using it day to day](#using-it-day-to-day)
6. [Smart titles (optional)](#smart-titles-optional)
7. [Privacy: what stays on your phone](#privacy-what-stays-on-your-phone)
8. [Limitations](#limitations)
9. [Troubleshooting](#troubleshooting)
10. [Build from source](#build-from-source)
11. [Project layout](#project-layout)
12. [Licenses and credits](#licenses-and-credits)

---

## What it does

| Feature | What you see |
|---|---|
| **Folder prompt** | Take a screenshot, get a notification "Sort screenshot?", pick a folder or create a new one. |
| **Albums in your Gallery** | Sorted screenshots live in `Pictures/<folder>/`, so each folder appears as an album. |
| **Events from screenshots** | Anything you sort into the `Calendar` folder is read for dates and times. |
| **Evening review** | One notification a day lists the events found. You approve or skip each one, and approved events are added to your calendar. |
| **Smart titles** (optional) | A small open AI model, running on your phone, writes cleaner event titles. |

The default folders are Calendar, Work, Receipts, Memes and Personal. You can add or remove folders at any time from the home screen.

---

## How it works

```
 You take a screenshot
          |
          v
 Android saves it in Screenshots  ---->  ShotSort notices it (background service)
                                                  |
                                                  v
                                   Notification: "Sort screenshot?"
                                                  |
                                    You tap a folder (or make a new one)
                                                  |
                                                  v
                       Copied to Pictures/<folder>/, original removed
                       (Android asks you to confirm the removal once)
                                                  |
                                   Was the folder "Calendar"?
                                         |               |
                                        no              yes
                                         |               |
                                       done              v
                                              Text is read from the image
                                              (ML Kit, on the phone)
                                                         |
                                              Dates, times and addresses are found
                                              (ML Kit, on the phone)
                                                         |
                                              Saved as "pending events"
                                              (nothing goes to your calendar yet)
                                                         |
                                              Optional: a small open AI model
                                              improves the title
                                                         |
                                       Every evening: one notification
                                                         |
                                       You approve or skip each event
                                                         |
                                                         v
                                       Approved events are written to the
                                       calendar on your phone, and Google
                                       Calendar syncs them as usual
```

### Why the screenshot is copied instead of redirected
Android does not let an app change where the system saves screenshots. So ShotSort waits for the file to appear, copies it into your chosen folder, then asks Android to delete the original. Because the screenshot belongs to the system, Android shows a one-tap confirmation for that deletion.

### Where the calendar data goes
ShotSort has no Google login and no API key. When you approve an event, it writes it into the calendar database that is already on your phone. Google Calendar's own sync then uploads it to the Google account that calendar belongs to. The app prefers the primary calendar of a Google account on the phone; if you have several, it usually picks the one that was added to the phone first. See [Limitations](#limitations).

### How an event is worked out
1. **Reading the text.** Google ML Kit text recognition turns the image into text, on the phone.
2. **Finding dates, times and places.** ML Kit entity extraction turns phrases such as "Sat 14 Nov, 4 PM" or "tomorrow at 5" into exact timestamps. It is told when the screenshot was taken so that "tomorrow" is resolved correctly.
3. **Building the event.** ShotSort's own rules take over:
   - the title comes from the text on the same line as the date, minus the date itself;
   - "Date: 14 Nov" on one line and "Time: 4 PM" on another are joined into one event;
   - two times on a line become a start and an end; otherwise an event lasts one hour;
   - a date with no time becomes an all-day event;
   - dates that have already passed are ignored.
4. **Review.** The event waits until the evening review. You can edit its title there before adding it.

No AI language model is involved in steps 1 to 3. The optional model only suggests a nicer title and place afterwards.

---

## Install

You need an Android phone running **Android 10 or newer** with a 64-bit processor (almost every phone since 2018).

1. In the GitHub repository open the **Actions** tab.
2. Open the most recent successful **Build APK** run on the branch you want.
3. Download the **shotsort-apk** artifact and unzip it.
4. Open the `.apk` on your phone and allow installs from your browser or file manager when asked.

If installing over an older ShotSort fails, uninstall the old one first. Your folder list will reset.

---

## First-time setup

Open ShotSort once and grant what it asks for:

| Permission | Why |
|---|---|
| **Photos and videos** (or Storage on Android 12 and below) | To notice new screenshots and move them. |
| **Notifications** | To show "Sort screenshot?" and the evening review. |
| **Calendar** | Asked on the review screen, to add the events you approve. |

Then:

1. Tap **Start watching**. A quiet "ShotSort watching" notification appears. It must stay for the app to work.
2. **Samsung and other brands:** go to Settings > Apps > ShotSort > Battery and choose **Unrestricted**. Without this, the phone may put the app to sleep and you will stop getting prompts.
3. Optionally choose the time of your **daily review** on the home screen (default 9:00 PM).

---

## Using it day to day

### Sorting a screenshot
1. Take a screenshot.
2. Tap the **Sort screenshot?** notification.
3. Tap a folder, or type a new name under "Or create new folder" and tap **Save**.
4. If Android asks whether ShotSort may delete the original, tap **Allow**.

Choose **Leave in Screenshots** to skip a screenshot.

### Turning screenshots into calendar events
1. Sort an event screenshot (an invite, a ticket, a message with a date) into the **Calendar** folder. Images you drop into `Pictures/Calendar` by hand are read as well.
2. Do nothing else. Events found are stored as pending and nothing touches your calendar yet.
3. In the evening you get one notification listing them. Tap it.
4. On the review screen, fix the title if needed, then tap **Add** or **Skip** for each event. **Add all** is also there.

You can open the review screen at any time with **Review events** on the home screen. The line under "Calendar folder" on the home screen tells you the result of the last scan, for example "Found 2 event(s)" or "No upcoming date or time found".

Added events get a 30-minute reminder, except all-day events.

---

## Smart titles (optional)

The rule-based title is a guess from the layout of the text, so it can pick the wrong line. Smart titles fix that with a small AI model that runs **entirely on your phone**.

- **Model:** Qwen2.5 1.5B Instruct, a 4-bit GGUF file of about 1 GB (Apache-2.0 licence), run by the MIT-licensed llama.cpp library.
- **Set up:** on the home screen, under *Smart titles*, tap **Download model** (use Wi-Fi), or **Use a model file** if you downloaded a GGUF file yourself. **Remove model** frees the space again.
- **What it decides:** only the title and the place. Dates and times always come from ML Kit.
- **How it is kept honest:** a suggested title is used only if at least one of its words really appears in the screenshot, and a place is used only if it appears there word for word. Otherwise the built-in title stays.
- **Cost:** roughly 10 to 40 seconds of background processing per event and some battery. Results are ready by the evening review.
- **Safety:** the model runs in its own process that shuts down after use, so a problem with it cannot stop screenshot watching. If it crashes three times it switches itself off.

---

## Privacy: what stays on your phone

- Your screenshots are **never uploaded** by ShotSort. Reading the text and finding dates happen on the device.
- The app asks for the **Internet permission only to download the optional model** once. It does not use it for anything else.
- Two one-time downloads happen if you use the Calendar feature: ML Kit fetches its small English date-finding model through Google Play services, and the optional title model comes from Hugging Face.
- Approved events are stored in your phone's calendar, and Google Calendar syncs them like any other event. That sync is Google's, not ShotSort's.

---

## Limitations

- **English only** for dates, times and the title model.
- **Titles are guesses.** That is why nothing is added without your approval, and why you can edit the title on the review screen.
- **No choice of calendar yet.** If several Google accounts are on the phone, it usually uses the one added first. Check where the first event lands, using the account and calendar name shown on the event in Google Calendar. If there is no Google account, a local calendar that does not sync may be used.
- **Images already in the Calendar folder** before you installed the calendar feature are ignored. Only new ones are read.
- **Deleting the original screenshot needs your confirmation** each time, because Android treats it as a file belonging to another app.
- **Unusual layouts** (many dates, tables, images of handwriting) may produce no event or a wrong one.
- **Android 15 and some phone brands** can stop background apps. Set battery use to Unrestricted as described above.

---

## Troubleshooting

| Problem | What to try |
|---|---|
| No "Sort screenshot?" notification | Check that "ShotSort watching" is showing. Tap **Start watching**. Set battery to **Unrestricted**. Check notifications are allowed. |
| The same screenshot appears twice | You probably dismissed the delete confirmation. The copy is in your folder; delete the original from Screenshots. |
| "No upcoming date or time found" | The date may be in the past, written in a way the reader cannot parse, or in a language other than English. |
| "Date model not downloaded yet" | Connect to the internet once so the small model can download, then take another screenshot. |
| Events added to the wrong account | See "No choice of calendar yet" above. |
| Smart titles do nothing | Look at the status line under *Smart titles* on the home screen. It explains the last problem in plain words. |
| Nothing at the evening time | Open the app once so the daily alarm is set again, and check notifications are allowed. |

---

## Build from source

The easy way is GitHub Actions: every push builds an APK and runs the unit tests.

To build yourself you need JDK 17, the Android SDK (platform 36), and Gradle 8.13:

```
gradle :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

The title prompt and its checks are plain Kotlin with unit tests in `app/src/test`, so they run without a phone.

If you prefer no code at all, `MACRODROID_SETUP.md` describes a rough equivalent of the folder prompt built with the MacroDroid app.

---

## Project layout

```
app/src/main/java/com/shotsort/
  MainActivity.kt      Home screen: folders, status, model, review time
  ScreenshotService.kt Background watcher; spots new screenshots and Calendar-folder images
  SortActivity.kt      The "Move screenshot to?" sheet and the copy-then-delete move
  Prefs.kt             Small stored settings (folders, times, remembered ids)
  Analyzer.kt          Reads text, finds dates, builds pending events
  Proposals.kt         Storage for pending events
  Digest.kt            Daily alarm and the evening notification
  ReviewActivity.kt    The approve / skip screen
  CalendarWriter.kt    Writes an approved event into the phone's calendar
  TitlePrompt.kt       Builds the model prompt and checks the model's answer
  TitleService.kt      Runs the small model in a separate process
  TitleClient.kt       Sends work to that process, with timeouts and crash counting
  ModelStore.kt        Downloads, imports and removes the model file
  BootReceiver.kt      Restarts watching after the phone reboots
```

---

## Licenses and credits

- ShotSort itself: **MIT** (see `LICENSE`).
- Text recognition and date finding: **Google ML Kit**, free to use but not open source.
- Model runtime: [Kotlin-LlamaCpp](https://github.com/ljcamargo/kotlinllamacpp) (MIT) around [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT).
- Optional model: **Qwen2.5 1.5B Instruct** (Apache-2.0), distributed as a GGUF file by bartowski on Hugging Face.
