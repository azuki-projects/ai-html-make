# AI HTML Make

A **native Android application** built with Java. AI HTML Make is a Groq-powered website project builder: create projects, manage HTML/CSS/JavaScript files, chat continuously with AI to create or update files, preview `index.html`, and export the project as a ZIP.

## Android project

- Android application ID: `com.azuki.htmlmake`
- Language: Java
- Minimum Android: API 23 (Android 6.0)
- Compile SDK: 35
- No third-party Android libraries required

Open this repository as an Android Gradle project in Android Studio or CodeAssist and build the APK.

## App sections

- **Home:** create and switch between local projects.
- **Files:** browse, create, edit, save, and export project files.
- **AI Chat:** continuous Groq conversation that can create and update complete files. Progress appears in chat, for example `index.html created...` and `style.css updated...`.
- **Preview:** render the project's `index.html` in an Android WebView, with the common root `style.css` and `app.js` files injected.

## Groq setup

Open the settings button in the app and enter your own Groq API key. The key is stored in app-private SharedPreferences on the device. The app calls the Groq chat completions API using `openai/gpt-oss-120b`.

Do not commit API keys or distribute an APK containing a shared private key. A public multi-user release should use a backend that keeps the Groq key server-side.

## Project storage

Projects, files, and AI chat history are stored locally in the app's private preferences. Export project ZIPs regularly if you need a backup.

## Build status

The native Android source has been committed. A successful Gradle build and device runtime test have not yet been verified.
