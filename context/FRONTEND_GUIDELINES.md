# Frontend Guidelines

This document is the authoritative reference for how the ttscore frontend is written. It covers technical patterns, styling conventions, and code quality rules. Follow it for all new work and treat deviations as technical debt. When the code and this document disagree, fix one of them — never let them drift silently.

---

## 1. Stack

| Layer | Tool | Notes |
|---|---|---|
| Framework | SvelteKit 2 + Svelte 5 (runes mode) | No legacy Svelte 4 syntax anywhere |
| Language | TypeScript, strict mode | `"strict": true` in tsconfig |
| Styling | Tailwind CSS v4 | Inline `@theme` in `app.css`, no config file |
| UI primitives | shadcn-svelte v1 + bits-ui v2 | Vendored under `$lib/components/ui/` |
| Variant system | `tailwind-variants` | For multi-variant wrapper components |
| Icons | `phosphor-svelte` | Named imports, numeric `size={n}` |
| Fonts | DM Sans Variable (UI), DM Mono (numerics) | Via `@fontsource` |
| Color space | OKLCH throughout | Perceptually uniform, dark-mode friendly |
| Charts | `layerchart` + `d3-scale`/`d3-shape` | Wrapped by shadcn `Chart.Container` |
| i18n | `svelte-i18n` | Four locales: de (default), fr, it, en |
| Toasts | `svelte-sonner` | All transient user feedback |
| Auth | Supabase (`@supabase/ssr`) | Session validated server-side |
| Package manager | pnpm | Never `npm install` |

---

## 2. Svelte 5 Runes

This project uses the runes API exclusively. Never write `$:`, `export let`, `<slot>`, or `createEventDispatcher`.

### Props

Always declare a typed `interface Props` — even for a single prop — and destructure with `$props()`.

```svelte
<script lang="ts">
  interface Props {
    player: Player;
    classification?: string | null;
    /** Fired when the user picks a result. */
    onSelect?: (id: string) => void;
  }

  let { player, classification = null, onSelect }: Props = $props();
</script>
```

- Optional props get their default in the destructuring, not a separate assignment.
- Event callbacks are typed function props with a camelCase `on` prefix (`onSelect`, `onUnfollow`, `onRemove`) — never `createEventDispatcher`, never lowercase DOM-style names for component-level callbacks. (Native passthroughs like `onclick` on an element keep their DOM name.)
- Two-way binding uses `$bindable()`: `let { value = $bindable() }: Props = $props()`.
- Document non-obvious props with a `/** … */` line — especially bindables and callbacks.

### State and derived values

```svelte
let open = $state(false);
const winPct = $derived(total > 0 ? Math.round((wins / total) * 100) : 0);
const groups = $derived.by(() => { /* multi-statement derivation */ });
```

- `$state()` for all local reactive state; never `writable` for component-local state.
- Anything computable from other state is `$derived` — reach for `$effect` only when you genuinely need a side effect (DOM sync, network call, subscription).
- Values computed from props at the top level of the script must be `$derived`, not `const` — SvelteKit reuses page components across client-side navigations, so a plain `const` silently goes stale.

### Initial-value captures

Capturing a prop's initial value into `$state` is occasionally correct (seeding a selection, an optimistic local copy). When it is, say so — justification comment first, then a *bare* ignore line:

```ts
// Deliberate local copy: optimistic removal on unfollow without waiting for the server.
// svelte-ignore state_referenced_locally
let favoritePlayers = $state([...data.favoritePlayers]);
```

The `svelte-ignore` line must contain only the warning code — trailing prose is parsed as more codes and fails lint.

When state must *track* load data instead (e.g. follow state that changes when navigating between two player pages), sync it in a pre-effect:

```ts
// Synced in an effect (not just initialised) so client-side navigation between
// players — which reuses this component — picks up the new player's follow state.
let following = $state(false);

$effect.pre(() => {
  following = data.following;
});
```

### Effects

- `$effect` for side effects that re-run with state; `onMount` for one-time browser-only init (localStorage reads, matchMedia).
- An `$effect` that just assigns to state is usually a `$derived` in disguise — rewrite it.
- Effects with guards, keys, or async bodies get a comment explaining the lifecycle (see the sync effect in `players/[id]/+page.svelte`).

### Snippets

Use `{@render children?.()}` — never `<slot>`. Use local snippets aggressively to kill repetition *within* a file before reaching for a new component:

```svelte
{#snippet playerName(id: string | null, name: string | null, won: boolean)}
  …
{/snippet}

{@render playerName(game.homePlayerId, game.homePlayerName, homeWon)}
{@render playerName(game.awayPlayerId, game.awayPlayerName, awayWon)}
```

Snippet parameters are typed inline. Named snippet props (`{#snippet footer()}…{/snippet}`) are the idiom for optional component regions — see `StatTile`, `PlayerTile`, `InfoItem`.

---

## 3. TypeScript

- No `any`. Use `unknown` at system boundaries and narrow before use. Chart-library contexts that would be `any` get a structural type instead (see `GradientContext` in `$lib/utils`).
- `interface` for object shapes, `type` for unions, aliases, and utility types.
- API response types live in `$lib/api.ts` and nowhere else — never redeclare them locally. Document non-obvious fields on the type (`/** Last five decided matches, newest first. */`).
- `import type { … }` for type-only imports.
- SvelteKit augmentations (`App.Locals`, `App.PageData`) live in `src/app.d.ts`.

---

## 4. Component Architecture

### File organization

```
src/lib/components/
  ├── ui/              shadcn-svelte primitives — vendor folder, never edit
  ├── icons/           brand/custom SVG icons (GoogleIcon)
  ├── account/         account page sections
  ├── group/           group page pieces (StandingsTable, ScheduledMatchCard)
  ├── home/            home page pieces (HomeHero, FollowFeed, FeedItemSkeleton, …)
  ├── match/           match preview pieces
  ├── player/          player page pieces (StatsTab, H2HDrawer, MonthAccordion, …)
  ├── PlayerCard.svelte    cross-page domain components at the root
  ├── IconButton.svelte
  └── …
```

- Shared domain components live at the root of `components/`; page-specific ones live in a subfolder named after the page.
- Pure logic shared by components ships as a plain `.ts` module next to them (`player/monthGroups.ts`) or in `$lib` when app-wide (`$lib/date.ts`, `$lib/debounce.ts`).
- One component per file, PascalCase filename matching the component.
- **Aim for ≤150 lines per `.svelte` file.** When a page grows past that, extract its sections into the page's component subfolder — the account page is the template: a ~70-line page composing `MyPlayerSection`, `FavoritesSection`, `PushSection`, `ProSection`.

### The reuse ladder

Before writing markup for an interactive element, check the ladder — hand-rolled `<button>`/`<a>` styling is a code smell:

1. **`Button`** (shadcn) — CTAs, form submits, links styled as buttons (`href` prop renders an `<a>`).
2. **`IconButton`** — icon-only round buttons (follow star, bell, trash, compare). Handles focus ring, disabled state, and tones.
3. **`InfoItem`** — full-width tappable rows with icon/title/description/trailing (settings rows, callouts). Renders `<a>` or `<button>` depending on `href`.
4. Only below that, a bespoke element — with focus-visible styles and a translated label.

Same idea for display primitives: `Overline` (micro label), `PageTitle` (h1), `SectionLabel` (section heading), `StatTile` (labeled stat with skeleton + footer), `ScoreLine` (colored dash-separated numbers), `ClassBadge`, `FormPills`, `PlayerAvatar`, `PlayerTile`.

### When to extract

Extract a shared component or helper when the same pattern appears **3+ times**. Two similar blocks are fine — don't pre-abstract. Within one file, prefer a local snippet over a new file.

### Class merging

Always `cn()` from `$lib/utils` when combining or conditionally applying classes. Never string-interpolate class fragments.

```svelte
<!-- Good -->
<span class={cn('rounded-full px-2', sizeClasses[size], className)}>

<!-- Bad -->
<span class="rounded-full px-2 {sizeClasses[size]} {className}">
```

---

## 5. Routing & Data Fetching

### Which load file

| File | Use for |
|---|---|
| `+page.server.ts` | Auth-protected data, anything needing the session token, SEO-critical content |
| `+page.ts` | Public client-renderable data, no secrets |
| `+layout.server.ts` | Session validation, data needed by all child routes |

### Fetch in parallel, stream the heavy parts

```ts
const [group, standings, matches] = await Promise.all([
  api.groups.get(id).catch(() => null),
  api.groups.standings(id).catch(() => null),
  api.groups.matches(id).catch(() => null)
]);
```

Secondary data that shouldn't block first paint goes under a `streamed` key and is awaited in the template:

```ts
return {
  player,
  streamed: {
    elo: api.players.elo(params.id),
    matches: api.players.matches(params.id)
  }
};
```

With `Promise.allSettled`, always handle the `rejected` branch explicitly.

### 404s and errors

```ts
const player = await api.players.get(params.id).catch(() => null);
if (!player) error(404, 'Player not found');
```

- `error()` throws by itself in SvelteKit 2 — **no `throw` keyword**.
- Never return `null` page data for the template to deal with; throw so `+error.svelte` renders.
- Never wrap a whole load in one try/catch — it flattens every failure (including incidental ones) into a single error. Catch per call.

### Auth

Check the session at the top of every protected load or action:

```ts
const { session } = await locals.safeGetSession();
if (!session) redirect(303, '/signin?redirectTo=/account');
```

Server-side calls to Ktor go through `authedKtor(session.access_token)` from `$lib/server/ktor`.

### Pro-gated client fetches

Pro endpoints are fetched from the browser so the Supabase access token can be forwarded. Never hand-roll the loading/error bookkeeping — use `ProResource`:

```ts
const preview = new ProResource<MatchPreview>();

$effect(() => {
  if (!data.isPro) return;
  const id = data.match.id;
  preview.load(data.supabase, (token) => api.matches.preview(id, token));
});
```

Template branches on `preview.loading` / `preview.error` / `preview.data`.

---

## 6. Forms & Actions

- Always `use:enhance` so forms work without JavaScript.
- Optimistic updates: flip `$state` before the server responds, roll back on failure (see `FollowButton`).
- Return shape: `{ success: true, … }` or `fail(status, { error | reason })`. **Always check `res.ok`** on the backend call — a fire-and-forget delete that "succeeds" while failing server-side is a bug.
- Follow/unfollow/notify actions are shared: import `followAction` / `unfollowAction` / `setNotifyAction` from `$lib/server/followActions` instead of reimplementing them per page. They translate backend 403 reasons (`follow_limit`, `notify_pro`) into typed `fail(403, { reason })` results that the shared buttons turn into Pro-upsell toasts.
- Pass entity IDs via hidden inputs; never encode sensitive data in form fields.

---

## 7. Error Handling

| Context | Pattern |
|---|---|
| Load functions | `.catch(() => null)` per call → `if (!data) error(404, …)` |
| Server actions | Explicit `fail(status, …)`; check `res.ok` on every backend response |
| API client | `if (!res.ok) throw new Error(…)` before `res.json()` |
| `allSettled` | Handle the `rejected` branch — no silent drops |
| Error page | `src/routes/+error.svelte` renders status + localized message; keep it on-brand |

**No error silencing**: `.catch(() => {})` is acceptable only for fire-and-forget side loads whose failure the UI already tolerates (e.g. optional ELO overlays that hide when empty), and must carry a comment saying why the failure is safe to ignore.

---

## 8. Internationalization

The app ships in German (default, *du* form), French (*vous* form), Italian (*tu* form), and English.

- **Every user-facing string goes through `$_()`** — including `title` attributes, `aria-label`s, placeholders, empty states, and error pages. If a reviewer can read English in the rendered DOM of a non-English locale, it's a bug.
- A new key is added to **all four** locale files in the same commit, matching each file's tone.
- Keys are `section.key_name`; sections mirror features (`player`, `feed`, `account`, `common`, …). Cross-cutting strings (`common.back`, `common.tbd`, `common.unfollow`) live in `common`.
- Keys must appear as **literal strings** in code — ternaries between two literals are fine, string concatenation or template-literal keys are not. This keeps unused-key detection a plain grep.
- Delete keys when their last usage goes; the locale files are code, not an archive.
- Locale resolution: server reads the `ttscore_locale` cookie (falling back to `Accept-Language`), browser refines from localStorage — both via `STORAGE_KEYS.locale` and `resolveLocale()` from `$lib/i18n`.

---

## 9. Dates & Numbers

All user-facing date formatting goes through `$lib/date` — never call `toLocaleDateString` in a component. Pass the `$locale` store value straight through.

| Helper | Output (de) | Use for |
|---|---|---|
| `relativeTime()` | „vor 3 Tagen" / „in 5 Std." | Feed stamps, countdowns (past and future) |
| `dateNumeric()` | `05.07.26` | Dense list rows (match cards) |
| `dayMonth()` | `05.07.` | Rows already sitting under a month header |
| `dateLong()` | `5. Juli 2026` | Prominent single dates, section headers |
| `dateWeekday()` | `Sa., 5. Juli` | Imminent fixtures where the weekday matters |
| `monthYear()` | `Juli 2026` | Month group headers |

Calendar helpers return `null` for a missing date — the caller supplies the context-appropriate fallback: `$_('common.tbd')` for fixtures, `'—'` (em dash, never a hyphen) for everything else.

Numbers: stat values use `font-mono` + `tabular-nums`; use `toLocaleString()` for large counts.

---

## 10. Design Tokens — Color

**Never use raw color values or Tailwind palette colors in components.** Always the token classes. The only exceptions are third-party brand marks (the Google logo) and chart code that composes token *variables* with `color-mix()`.

### Semantic tokens

| Purpose | Class |
|---|---|
| Page background | `bg-background` |
| Card background | `bg-card` |
| Primary text | `text-foreground` |
| Secondary text | `text-muted-foreground` |
| Borders | `border-border` |
| Focus ring | `ring-ring` |
| Destructive | `text-destructive` / `bg-destructive` |
| Primary accent | `bg-primary` / `text-primary-foreground` |

### Domain tokens

| Purpose | Class / helper |
|---|---|
| Win | `text-win` / `bg-win/15` / `border-win/30` |
| Loss | `text-loss` / `bg-loss/15` / `border-loss/30` |
| Classification badge | `ClassBadge` component, or `classificationColors(classification)` from `$lib/utils` |
| Classification as CSS value | `classColorVar(classification)` → `var(--class-a)` etc., for inline styles and charts |

### Dark mode

`.dark` on `<html>` + variable overrides in `app.css`. Components that only use token classes support dark mode automatically — **no `dark:` prefix needed**. Only reach for `dark:` when styling something that has no token (currently: nothing does).

---

## 11. Typography Scale

| Role | Classes / component |
|---|---|
| Page title | `PageTitle` (`text-3xl font-black tracking-tight leading-none`) |
| Section heading | `SectionLabel` (uppercase overline style with optional icon) |
| Subsection / card title | `text-base font-semibold` |
| Body / default | `text-sm` |
| Secondary / metadata | `text-xs text-muted-foreground` |
| Micro label / overline | `Overline` (`text-2xs font-semibold uppercase tracking-widest text-muted-foreground`) |
| Stat value | `font-mono text-xl font-black tabular-nums leading-none` (default `StatTile` style) |

Rules:
- `font-black` is for numerics/stats and display headings; `font-bold` for regular headings. Don't mix weights at the same size level.
- No arbitrary font sizes. `text-2xs` (10px, defined in `app.css`) is the only size below `text-xs`.
- `tracking-tight` pairs with large headings (`text-2xl`+), not body text.
- `leading-none` on stat numbers.

---

## 12. Spacing Scale

### Inside components

| Context | Class |
|---|---|
| Icon + label, tight | `gap-1` / `gap-1.5` |
| Items in a row | `gap-2` |
| Items in a column (standard) | `gap-3` |
| Distinct sections | `gap-4` |

### Card padding

| Context | Class |
|---|---|
| Compact / list-item card | `p-4` |
| Standard card | `p-5` |
| Spacious / hero card | `p-6` |

`px-4 py-3` is reserved for the tappable list-row pattern; uniform cards use `p-*`.

### Page rhythm

- `space-y-3` between cards in a list, `gap-3` in grids
- `space-y-6` between logical page sections
- Components ship **without outer margins** — the parent owns spacing (usually via `space-y-*`). `BackButton` deliberately has no default margin for this reason.

---

## 13. Icons (Phosphor)

```ts
import { StarIcon, TrendUpIcon } from 'phosphor-svelte';
```

- Named imports from `'phosphor-svelte'`, `*Icon`-suffixed names, only what you use.
- **Numeric size syntax**: `size={16}`, never `size="16"`.

| Context | Size | Weight |
|---|---|---|
| Bottom navigation | `size={22}` | `fill` active / `regular` inactive |
| Card / section indicator | `size={20}` | `regular` |
| Row icon / icon button | `size={18}` | `regular` |
| Inline with body text | `size={16}` | `regular` |
| Tiny badges | `size={10–13}` | `bold` |

Active/selected state uses `weight="fill"`. Icons passed as props are typed `Component` from `'svelte'`.

---

## 14. shadcn-svelte Usage

### Importing

```ts
// Compound component — namespace import
import * as Card from '$lib/components/ui/card/index.js';
import * as Select from '$lib/components/ui/select/index.js';

// Single component — named import
import { Button } from '$lib/components/ui/button/index.js';
```

Use the namespace form for anything compound (Card, Select, Tabs, Drawer, Accordion, Table, Dialog, Carousel, Chart) — don't cherry-pick `SelectTrigger`-style named exports.

### Rules

- **`$lib/components/ui/` is a vendor folder.** Never edit it for app needs — pass `class` + `cn()`, or build a wrapper.
- Wrappers with variants use `tailwind-variants` in a `<script lang="ts" module>` block and export their variant types — `IconButton` and `InfoItem` are the reference implementations:

```svelte
<script lang="ts" module>
  import { tv, type VariantProps } from 'tailwind-variants';

  export const iconButtonVariants = tv({
    base: 'flex items-center justify-center rounded-full p-2 …',
    variants: { tone: { muted: '…', foreground: '…' } },
    defaultVariants: { tone: 'muted' }
  });
</script>
```

- `Button` for every CTA — including link-shaped ones (`<Button href="/pro">`). Exhaust `variant`/`size` props before custom padding. `variant="ghost"` for low-emphasis, `variant="outline"` for secondary CTAs.
- Toasts via `svelte-sonner`'s `toast.error(title, { description, action })` for actionable failures (see the Pro-upsell toasts in `FollowButton`).

---

## 15. Loading States

- Every `{#await}` / streamed block renders `<Skeleton>` placeholders — never a blank area, never a spinner-only page.
- **Skeletons mirror the component they stand in for** — same footprint, same border radius, same internal layout. When a card is skeletonned in more than one place, give it a dedicated skeleton component next to it (`FeedItemSkeleton` mirrors `FeedItemCard`).
- `StatTile` accepts `value={null}` to render its own skeleton — use that for async stats instead of wrapping the tile.

| Element being loaded | Skeleton |
|---|---|
| Body text line | `h-4 w-32 rounded` |
| Small/metadata text | `h-3 w-24 rounded` / `h-3.5 w-32 rounded` |
| Section heading | `h-4 w-24 rounded` |
| Card / list row | `h-16 w-full rounded-xl` (match the card's real radius) |
| Avatar | `h-9 w-9 rounded-full` |
| Chart | match the chart container height (`h-52 rounded-none` inside a card) |

Rely on the shadcn Skeleton's pulse — no custom `animate-*`.

---

## 16. Accessibility & Native Feel

- **No nested interactive elements.** A link never contains a button (and vice versa) — make them flex-row siblings, like the account "my player" row and the favorites rows.
- Every icon-only control gets a **translated** `aria-label` or `title` — `$_('common.unfollow')`, not English literals.
- Focus-visible rings come free with `Button` / `IconButton` / `InfoItem` — a third reason not to hand-roll interactive elements.
- Decorative corner icons on tappable tiles use the `pointer-events-none` wrapper + `pointer-events-auto` opt-in pattern from `PlayerTile`.
- `app.css` centrally handles tap-highlight removal, overscroll containment, `touch-action: manipulation`, and text-selection rules — don't re-add these per component.
- Dialogs get an accessible name/description even when their visible content is decorative (see `OnboardingModal`'s `sr-only` header).

---

## 17. Browser Storage

All localStorage/cookie key names live in `$lib/storageKeys.ts` — never inline a key string:

```ts
import { STORAGE_KEYS } from '$lib/storageKeys';
localStorage.setItem(STORAGE_KEYS.theme, 'dark');
```

- New keys follow the `ttscore:*` scheme; existing keys keep their historical spelling (renaming silently drops users' settings).
- Wrap storage access in try/catch when it runs outside `onMount` guards (private browsing throws) — see `$lib/recentPlayers.ts`.

---

## 18. Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Svelte components | PascalCase | `PlayerGameCard.svelte` |
| TS utilities/stores | camelCase | `date.ts`, `theme.svelte.ts` |
| Rune-using modules | `.svelte.ts` suffix | `proResource.svelte.ts`, `h2h.svelte.ts` |
| Route segments | kebab-case | `/players/[id]/games` |
| Boolean props | bare state adjective; `is` prefix only when needed for clarity | `following`, `disabled`, `loading`; `isPro`, `isHome` |
| Event callback props | camelCase `on` prefix | `onSelect`, `onUnfollow`, `onRemove` |
| API response types | PascalCase noun | `Player`, `MatchPreview`, `CareerRival` |
| i18n keys | `section.snake_case` | `player.no_club`, `common.tbd` |
| Class-string variables | camelCase | `const nameClass = cn(…)` |

---

## 19. Code Quality

- **Comments explain *why*, never *what*.** If deleting the comment wouldn't confuse a future reader, delete it. Non-obvious effects, sync patterns, and intentional rule-bendings (`svelte-ignore`, empty catches) always get one.
- **No dead code** — that includes unused icon imports, unused i18n keys, unused `$state`, and commented-out blocks. Lint enforces most of it.
- **No `console.log`** in committed code.
- **≤150 lines** per `.svelte` file as a target; extract sections when a page outgrows it.
- **Extract at 3+ occurrences**; snippets before components; two similar blocks are fine.
- **Gates**: `pnpm format` before committing; `pnpm lint` (Prettier + ESLint) and `pnpm check` (svelte-check) must pass with zero errors *and zero warnings*. A warning you intend to keep gets an explicit, justified `svelte-ignore` — an unexplained warning is a regression.

---

## 20. File & Import Hygiene

- `$lib/` aliases everywhere; relative imports only within the same folder (`./PlayerTile.svelte`).
- Group imports: Svelte/SvelteKit → third-party → `$lib` modules → `$lib` components → types.
- `import type` for type-only imports.

---

## 21. Key Files Reference

| File | Purpose |
|---|---|
| `src/app.css` | All design tokens (colors, radius, fonts, `text-2xs`) + native-feel base styles |
| `src/lib/utils.ts` | `cn()`, name formatting, classification helpers, ELO ladder, `bandGradientStops()` |
| `src/lib/date.ts` | All user-facing date formatting |
| `src/lib/api.ts` | Public API client + every response type |
| `src/lib/debounce.ts` | Cancellable debounce helper |
| `src/lib/storageKeys.ts` | Registry of localStorage/cookie key names |
| `src/lib/proResource.svelte.ts` | `ProResource` — state for Pro-gated client fetches |
| `src/lib/feed.ts` | Follow-feed event resolution |
| `src/lib/h2h.svelte.ts` | Global H2H drawer state (`compareWithMe`, `comparePlayers`) |
| `src/lib/theme.svelte.ts` | Dark mode store (`theme.toggle()`, `theme.dark`) |
| `src/lib/i18n/` | Locale registration, `resolveLocale()`, the four locale JSONs |
| `src/lib/server/ktor.ts` | Authenticated server-side HTTP client (`authedKtor`) |
| `src/lib/server/followActions.ts` | Shared follow/unfollow/notify form actions |
| `src/lib/components/ui/` | shadcn-svelte primitives — vendor, never edit |
| `src/routes/+error.svelte` | Branded, localized error page |
| `src/app.d.ts` | `App.Locals`, `App.PageData` augmentations |
