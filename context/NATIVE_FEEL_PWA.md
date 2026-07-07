# Making the PWA Feel Native

Checklist of changes with real user-facing impact, ordered by how much they'll actually be felt. Not implemented yet.

## 1. Highest impact

- **Disable pinch/double-tap zoom on UI chrome** — add `touch-action: manipulation` to buttons and links so taps register instantly (no ~300ms delay) and double-tapping a button doesn't zoom the page. This is the single biggest reason a PWA feels "laggy" compared to a native app.
- **Contain overscroll bounce** — `overscroll-behavior-y: contain` on the scrollable root stops the page from rubber-banding into the browser's pull-to-refresh/back-navigation gesture, which is the #1 "this is just a website" tell on mobile.
- **Custom tap highlight** — set `-webkit-tap-highlight-color: transparent` and add your own `:active` press states (slight scale/opacity change) on buttons and cards. Removes the gray flash browsers show on tap and replaces it with deliberate, native-feeling feedback.

## 2. High impact

- **Safe-area padding** — add `viewport-fit=cover` to the viewport meta tag and use `env(safe-area-inset-*)` for padding on headers/bottom nav so content isn't obscured by the iPhone notch/home indicator. Matters most in standalone mode where there's no browser chrome to naturally provide that spacing.
- **Proper app icons** — the manifest only has one SVG icon; add PNG icons (180×180 `apple-touch-icon` at minimum) since iOS ignores manifest icons for "Add to Home Screen" and needs its own `<link>` tag. Right now the home-screen icon likely looks wrong or missing on iOS.
- **Fix the broken description meta tag** — `app.html` uses smart/curly quotes (`”`) instead of straight quotes on the `description` meta tag, so it likely fails to parse. Not a "feel" issue, but worth fixing while touching this file.

## 3. Medium impact

- **Disable long-press callout on UI elements** — `-webkit-touch-callout: none` on icons/buttons/images stops the iOS "Copy/Share" popup from appearing on long-press of things that aren't meant to be selectable text.
- **Prevent text selection on chrome** — `user-select: none` on nav bars, buttons, and headers stops accidental blue text-selection highlighting when a user drags a finger across the UI, while leaving real content selectable.
- **Status bar blending** — switching `apple-mobile-web-app-status-bar-style` to `black-translucent` lets your header color draw underneath the iOS status bar instead of leaving a plain white/gray bar above your app, making standalone mode look more like one continuous native screen (requires safe-area padding to be in place first, or content will sit under the clock/battery icons).

## 4. Lower impact / polish

- **Momentum scrolling** — `-webkit-overflow-scrolling: touch` on custom scroll containers for smoother inertial scrolling (mostly already default in modern iOS Safari, but cheap insurance for older WebKit).
- **Scroll snapping** — `scroll-snap-type` on swipeable lists/carousels if any exist, so they settle into place like a native carousel instead of stopping wherever the finger lifts.
- **Font smoothing** — `-webkit-font-smoothing: antialiased` for slightly crisper text rendering closer to native typography rendering.
- **Standalone-mode detection** — check `display-mode: standalone` to hide/adjust UI that only makes sense in a browser tab (e.g. an "install app" prompt), and ensure any external links use `target="_blank"` so they break out to Safari/Chrome instead of navigating away inside the installed app.
