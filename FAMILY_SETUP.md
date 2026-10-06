# Setting up parent approval (Firebase)

Everything in Focus Lamp works without this, including limits, blocking, the lamp, the coach,
and parent controls on the student's phone behind a PIN.
This setup adds the part that needs two phones: a parent sets rules and approves extra time
**from their own phone**.

It takes about 15 minutes, once.

## 1. Create the Firebase project

1. Go to <https://console.firebase.google.com> and create a project (Analytics not needed).
2. **Add an Android app** with package name `com.focuslamp.app`.
3. Download `google-services.json` and put it in the `app/` folder (next to `build.gradle.kts`).
   It's in `.gitignore`, so it won't be committed.
4. Rebuild. The build switches family features on automatically when that file exists.

## 2. Turn on the services

In the Firebase console:

1. **Authentication → Sign-in method → Anonymous → Enable.**
   Each phone signs in anonymously. Nobody types a password; access comes from the family code.
2. **Firestore Database → Create database** (production mode, a region near you).
3. **Cloud Messaging** is on by default.

## 3. Deploy the security rules and the push function

Install the Firebase CLI once: `npm install -g firebase-tools`, then from the project folder:

```bash
firebase login
firebase use --add            # pick the project you created
firebase deploy --only firestore:rules
cd functions && npm install && cd ..
firebase deploy --only functions
```

The function (`functions/index.js`) sends the push notifications that let a parent approve
from the notification shade and make the block lift on the student's phone within seconds,
even when the app is closed.

> Cloud Functions require the **Blaze (pay-as-you-go)** plan, which needs a card on file.
> A family's usage stays far inside the free tier, so in practice it costs nothing.
> Without the function, everything still works while the apps are open, but parents won't
> get request notifications when their app is closed.

## 4. Pair the phones

1. On the parent's phone: open Focus Lamp, choose **I'm a parent**, then **Create family** → **Show a code**.
2. On the student's phone: **Family → Link to a parent's phone**, enter the 6-digit code (valid 15 minutes).
3. On the parent's phone: **Rules** → add app limits, schedules, and an unlock PIN → **Save rules**.

## How a request flows

1. A parent limit runs out → the app is covered by the block screen and stays closed, even the next day.
2. The student taps **Ask** (15/30/60 minutes, optional reason).
3. The parent gets a notification with **Allow** / **Not now** buttons (or uses the Requests tab).
4. On approval, the student's phone gets the extra time and the block lifts.
5. If both phones are offline, a parent who is physically there can enter the PIN on the block screen.

## What it can and can't prevent

Focus Lamp blocks apps and reports to the parent, but it isn't full device management.
A determined student could still revoke permissions or, with uninstall protection off, remove the app.
When that happens the parent's dashboard shows it ("Protection is off" or "No update for a while"),
and a push alert is sent. Locking that down completely needs Android's device-owner mode
(Family Link / enterprise management), which is out of scope here.
