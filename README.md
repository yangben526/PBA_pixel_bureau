# Pixel Bureau

A small Android company you run from your phone. You decide how many agents work for you, give each one a personality and standing duties, and talk to them the way you would in a chat app. While they answer, they walk to a desk in a pixel office.

The phone calls the model itself. In **Settings**, add as many models as you want. Each one is an API URL, your key, and a model id. The chips fill in xAI, OpenAI, OpenRouter, or Groq, and you can type a different URL. Chats use the model you mark **In use**. No key is bundled.

## Run

```bash
./build.sh
./install.sh
```

`install.sh` builds the debug APK if it is missing, installs it on the connected phone, and opens it. Several devices: `./install.sh --all`, or set `ANDROID_SERIAL`.

## What you can do

- **Office** — the floor, and a chat with whoever you have selected. Tap a person or their chip.
- **Company** — ★ reputation opens hiring desks (1★ each). Hire in PixelHire, edit or fire on HR, assign tasks.
- **Settings** — add, edit, and remove models. Mark one **In use**.

A finished task adds 1 reputation. Every 4 points starts the next day. Opening a new hiring slot costs 1 reputation star. PixelHire hires from a candidate pool into a free desk; each person keeps role instructions and an output personality. Changing either of those costs 1 star, and the star stays spent. Idle people wander the floor. Status bubbles: `···` thinking, `</>` writing, `★` finished, `X` the call failed.

Sprites live in `media/sprites/` and are copied into the app at build time.
