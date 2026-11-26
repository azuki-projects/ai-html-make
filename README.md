# AI HTML Make

A mobile-friendly, browser-based HTML/CSS/JavaScript project editor with a continuous Groq AI builder.

## Features

- Home page with local projects
- Project file browser and built-in text editor
- Continuous AI chat that can create and update multiple files
- Live preview using the project's `index.html`
- Groq API key saved locally in the browser
- Project data stored in browser local storage
- Responsive layout for mobile screens

## Run

This is a static web app. Open `index.html` in a browser or deploy the repository using GitHub Pages or another static host. No build step or package installation is required.

## Connect Groq

1. Open AI HTML Make.
2. Tap the settings icon in the top-right corner.
3. Enter your own Groq API key.
4. Open a project and ask AI to create or edit your website.

The app uses the Groq OpenAI-compatible chat completions endpoint and the `openai/gpt-oss-120b` model.

**Security:** The API key is saved in browser local storage and sent directly from your browser to Groq. Do not use this setup to distribute an app with a shared secret. For a public multi-user service, put the API request behind a server-side endpoint and keep the key in a server environment variable.

## Project data

Projects and chat history are stored in this browser's local storage. Clearing browser site data can erase them. Export or copy important files before clearing storage.

## Preview note

The preview runs inside a sandboxed iframe. External assets referenced by URLs still need an internet connection. The current preview automatically inlines the common root files `style.css`, `styles.css`, `app.js`, and `script.js`.
