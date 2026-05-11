# Frontend Coding Guidelines

This document is the authoritative reference for how the ttfeed frontend is written. It covers technical patterns, styling conventions, and code quality rules. Follow it for all new work and treat deviations from it as technical debt.

---

## 1. Stack

| Layer | Tool | Notes |
|---|---|---|
| Framework | SvelteKit 2 + Svelte 5 (runes mode) | No legacy Svelte 4 syntax |
| Language | TypeScript — strict mode | `"strict": true` in tsconfig |
| Styling | Tailwind CSS v4 | Inline `@theme` in `app.css`, no config file |
| UI primitives | shadcn-svelte v1 + bits-ui v2 | Headless, accessible |
| Variant system | `tailwind-variants` | Used for multi-variant components |
| Icons | `phosphor-svelte` | One import per icon used |
| Font | DM Sans Variable | Loaded via `@fontsource-variable/dm-sans` |
| Color space | OKLCH throughout | Perceptually uniform, works in dark mode |
| Toasts | `svelte-sonner` | Use for all user feedback |

---

## 2. Svelte 5 Runes

This project uses Svelte 5's runes API exclusively. Never write Svelte 4-style reactive declarations (`$:`, `export let`, `<slot>`).

### Props

Always declare a typed `interface Props` and destructure with `$props()`.

```svelte
<script lang="ts">
  interface Props {
    id: string;
    name: string;
    klass?: string | null;
    onSelect?: (id: string) => void;
  }

  let { id, name, klass = null, onSelect }: Props = $props();
</script>
```

- Optional props get a default in destructuring, not a separate assignment.
- Event callbacks are typed function props (`onSelect?: ...`), not `createEventDispatcher`.
- Two-way binding uses `$bindable()`: `let { value = $bindable() }: Props = $props()`.

### State

```svelte
let count = $state(0);
let open = $state(false);
```

- Use `$state()` for all local reactive state.
- Never import `writable` from `svelte/store` for component-local state.

### Derived values

```svelte
const fullLabel = $derived(`${name} (${klass ?? '?'})`);
```

- Compute derived values with `$derived()`, not inline expressions in templates.
- For expensive derivations, prefer `$derived.by(() => { ... })`.

### Effects

```svelte
$effect(() => {
  document.title = name;
});
```

- Use `$effect()` for side effects that run when state changes (DOM sync, subscriptions).
- Use `onMount` for one-time initialization, not `$effect`.
- Avoid `$effect` for logic that can be expressed as `$derived`.

### Children / snippets

Use `{@render children?.()}` — never `<slot>`.

```svelte
<script lang="ts">
  import type { Snippet } from 'svelte';
  interface Props { children?: Snippet }
  let { children }: Props = $props();
</script>

<div>{@render children?.()}</div>
```

---

## 3. TypeScript

- **Strict mode** is always on. No implicit `any`, no unchecked indexing.
- Use `interface` for object shapes, `type` for unions, aliases, and utility types.
- No `any`. Use `unknown` at system boundaries and narrow before use.
- API response types are defined in `$lib/api.ts` — never redeclare them locally.
- SvelteKit type augmentations live in `src/app.d.ts` (`App.Locals`, `App.PageData`).

```ts
// Good
type Result = { ok: true; data: Player } | { ok: false; error: string };

// Bad
const data: any = await res.json();
```

---

## 4. Component Architecture

### File organization

```
src/lib/components/
  ├── ui/                  shadcn-svelte primitives (don't edit directly)
  ├── home/                home page specific components
  ├── PlayerCard.svelte    shared domain components at root
  ├── GameCard.svelte
  └── ...
```

- Domain components live in `$lib/components/` (root or a subfolder if page-specific).
- shadcn primitives live in `$lib/components/ui/` — treat them as a vendor folder.
- One component per file. File name matches the exported component in PascalCase.
- Split when a `.svelte` file exceeds ~150 lines of template+script combined.

### Composition

Use the shadcn compound pattern for structured UI:

```svelte
<Card.Root>
  <Card.Header>
    <Card.Title>Player</Card.Title>
  </Card.Header>
  <Card.Content>...</Card.Content>
</Card.Root>
```

### Class merging

Always use `cn()` from `$lib/utils` when conditionally applying classes. Never concatenate strings.

```svelte
<!-- Good -->
<div class={cn('base-class', isActive && 'active-class', extraClass)}>

<!-- Bad -->
<div class="base-class {isActive ? 'active-class' : ''} {extraClass}">
```

---

## 5. Routing & Data Fetching

### When to use which load file

| File | Use for |
|---|---|
| `+page.server.ts` | Auth-protected data, server secrets, SEO-critical content |
| `+page.ts` | Public client-renderable data, no secrets |
| `+layout.server.ts` | Session validation, data needed by all child routes |

### Always fetch in parallel

```ts
const [player, groups] = await Promise.all([
  api.players.get(params.id),
  api.groups.list()
]);
```

Use `Promise.allSettled` when some requests are optional — but always handle the `rejected` case explicitly, never silently ignore it.

### Streamed data

Use the `streamed` return pattern for heavy secondary data that shouldn't block the initial render:

```ts
return {
  player,
  streamed: {
    history: api.players.matches(params.id)
  }
};
```

### Auth protection

Check the session at the very top of every protected load or action:

```ts
export const load: PageServerLoad = async ({ locals }) => {
  const { session } = await locals.safeGetSession();
  if (!session) redirect(303, '/signin?redirectTo=/account');
  // ...
};
```

### 404 / error pattern

```ts
const player = await api.players.get(params.id).catch(() => null);
if (!player) throw error(404, 'Player not found');
```

Never return `null` page data and let the template handle it — throw the error so SvelteKit renders the error page.

---

## 6. Form Actions

- Always use `use:enhance` so forms work without JavaScript.
- For optimistic updates: update `$state` before the server responds, roll back on failure.
- Return shape: `{ success: true, data: ... }` or `fail(status, { error: 'message' })`.
- Pass entity IDs via hidden inputs. Never encode sensitive data in form fields.

```svelte
<form method="POST" action="?/favorite" use:enhance={() => {
  // Optimistic update
  favorited = true;
  return async ({ result }) => {
    if (result.type !== 'success') favorited = false; // roll back
  };
}}>
  <input type="hidden" name="targetId" value={id} />
  <Button type="submit">Favorite</Button>
</form>
```

---

## 7. Error Handling

| Context | Pattern |
|---|---|
| Load functions | `.catch(() => null)` → `if (!data) throw error(404, ...)` |
| Server actions | `if (!session) return fail(401, ...)` — explicit fail codes |
| API client | Check `res.ok` before `res.json()` |
| AllSettled | Always handle the `rejected` branch — no silent drops |

```ts
// Good — explicit, typed fail
if (!res.ok) return fail(500, { error: 'Failed to save' });

// Bad — silent swallow
await api.doThing().catch(() => {});
```

---

## 8. Design Tokens — Color

**Rule: never use raw color values in components.** Always use design tokens (CSS variables) via Tailwind's token classes.

### Semantic tokens (always prefer these)

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

### Domain-specific tokens

| Purpose | Class |
|---|---|
| Win result | `text-win` / `bg-win/15` |
| Loss result | `text-loss` / `bg-loss/15` |
| Klass tier badge | Use `klassColors(klass)` from `$lib/utils` |

```svelte
<!-- Good -->
<span class={cn('rounded px-2 py-0.5 text-xs font-medium', klassColors(klass))}>
  {klass}
</span>

<!-- Bad — hardcoded color, breaks dark mode -->
<span class="bg-violet-400/15 text-violet-400">A</span>
```

### Dark mode

Dark mode is handled by `.dark` on `<html>` + CSS variable overrides in `app.css`. Components that only use semantic token classes automatically support dark mode — **no `dark:` prefix needed** for those. Only reach for `dark:` when you must style something that doesn't have a token.

---

## 9. Typography Scale

Use this scale consistently. No mixing of weights at the same size level.

| Role | Classes |
|---|---|
| Page title | `text-3xl font-black tracking-tight` |
| Section heading | `text-xl font-bold` |
| Subsection / card title | `text-base font-semibold` |
| Body / default | `text-sm` |
| Secondary / metadata | `text-xs text-muted-foreground` |
| Micro label | `text-[10px] font-medium uppercase tracking-wide text-muted-foreground` |

Rules:
- `font-black` and `font-extrabold` are not interchangeable — use `font-black` for numerics/stats, `font-bold` for headings.
- Don't introduce arbitrary font sizes. `text-[10px]` is the only exception already in use.
- Pair `tracking-tight` with large headings (`text-2xl`+), not body text.
- Use `leading-none` for stat numbers to prevent awkward spacing.

---

## 10. Spacing Scale

### Internal component spacing (gaps between child elements)

| Context | Class |
|---|---|
| Icon + label, tight | `gap-1` or `gap-1.5` |
| Between items in a row | `gap-2` |
| Between items in a column (standard) | `gap-3` |
| Between distinct sections | `gap-4` |

### Card padding

| Context | Class |
|---|---|
| Compact / list item card | `p-4` |
| Standard card | `p-5` |
| Spacious / hero card | `p-6` |

Don't mix asymmetric padding (`px-4 py-3`) unless it's an intentional list-row tap-target pattern. Use `p-*` for uniform cards.

### Page layout rhythm

- `gap-4` between cards in a list/grid
- `gap-6` between logical page sections
- `py-4` or `py-6` for page-level vertical padding

---

## 11. Icon Usage (Phosphor)

| Context | Size | Weight |
|---|---|---|
| Bottom navigation | `size={22}` | `fill` (active) / `regular` (inactive) |
| Inline with body text | `size={16}` | `regular` |
| Card / section indicator | `size={20}` | `regular` |
| Icon-only button | `class="size-4"` | `regular` |

Rules:
- Import only the icons you use: `import { House, Star } from 'phosphor-svelte'`
- Don't mix `size={n}` and `class="size-n"` on the same element — pick one.
- Active/selected state uses `weight="fill"`, default is `weight="regular"`.

---

## 12. shadcn-svelte Usage

### Importing

```ts
// Compound component (Card, Table, etc.)
import * as Card from '$lib/components/ui/card/index.js';

// Single component (Button, Input, etc.)
import { Button } from '$lib/components/ui/button/index.js';
```

### Rules

- **Never edit files in `$lib/components/ui/`** for one-off adjustments — pass `class` prop + `cn()` instead.
- To extend a component with new variants, create a wrapper component in `$lib/components/` that uses `tailwind-variants`. Don't patch the source.
- Use shadcn's `Button` for all interactive elements, not plain `<button>`. This ensures consistent focus styles, disabled states, and accessibility.
- Exhaust `size="sm" | "lg"` props before reaching for custom padding overrides.
- Use `variant="ghost"` for icon-only and low-emphasis actions, `variant="outline"` for secondary CTAs.

### Building variant components

Use `tailwind-variants` when a component needs multiple variants:

```ts
import { tv } from 'tailwind-variants';

const chip = tv({
  base: 'inline-flex items-center rounded-full font-medium ring-1',
  variants: {
    variant: {
      default: 'bg-muted text-muted-foreground ring-border',
      win: 'bg-win/15 text-win ring-win/30',
      loss: 'bg-loss/15 text-loss ring-loss/30',
    },
    size: {
      sm: 'px-2 py-0.5 text-xs',
      md: 'px-2.5 py-1 text-sm',
    }
  },
  defaultVariants: { variant: 'default', size: 'md' }
});
```

---

## 13. Loading States

- Always render `<Skeleton>` for async content — never leave a blank area.
- Size skeletons to match the rendered element dimensions.
- Use consistent sizing — don't invent skeleton heights per page:

| Element being loaded | Skeleton class |
|---|---|
| Body text line | `h-4 w-32 rounded` |
| Small/metadata text | `h-3.5 w-24 rounded` |
| Heading | `h-6 w-48 rounded` |
| Card / list item | `h-16 rounded-lg` |
| Avatar | `size-10 rounded-full` |

- Rely on shadcn Skeleton's built-in pulse animation — don't add custom `animate-` classes.

---

## 14. Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Svelte components | PascalCase | `PlayerCard.svelte` |
| TypeScript utilities/stores | camelCase | `utils.ts`, `theme.svelte.ts` |
| Route segments | kebab-case | `/players/[id]/games` |
| Boolean props | `is` prefix | `isLoading`, `isOpen`, `isActive` |
| Event callback props | `on` prefix | `onSelect`, `onChange`, `onClose` |
| API response types | PascalCase noun | `Player`, `Match`, `Standing` |
| Tailwind class variables | camelCase | `const baseClass = 'flex items-center'` |

---

## 15. Code Quality

- **Comments**: only for *why*, never for *what*. If removing the comment wouldn't confuse a future reader, don't write it.
- **Abstractions**: extract a shared helper only when the same pattern appears 3+ times. Two identical blocks are fine.
- **No dead code**: don't leave `_unused` variables, commented-out blocks, or backwards-compat shims.
- **No `console.log`** in committed code.
- **Line limit**: aim for ≤150 lines in a `.svelte` file. If the template alone is approaching that, extract sub-components.
- **No error silencing**: `.catch(() => {})` with an empty body is a bug waiting to happen.
- **Linting**: `npm run lint` (Prettier + ESLint) and `npm run check` (svelte-check) must pass before committing.

---

## 16. File & Import Hygiene

- Use `$lib/` path aliases everywhere — no relative `../` imports that traverse more than one level.
- Group imports: Svelte/SvelteKit → third-party → `$lib` utilities → `$lib` components.
- Don't import types at runtime — use `import type { ... }` for type-only imports.

```ts
// Good
import { onMount } from 'svelte';
import { cn, klassColors } from '$lib/utils';
import * as Card from '$lib/components/ui/card/index.js';
import type { Player } from '$lib/api';

// Bad
import { cn } from '../../utils';
import type { Player } from '../../api'; // relative traversal
```

---

## 17. Key Files Reference

| File | Purpose |
|---|---|
| `src/app.css` | All design tokens — colors, radius, font — defined here |
| `src/lib/utils.ts` | `cn()`, `klassColors()`, `timeAgo()`, `ordinal()` |
| `src/lib/api.ts` | Public API client + all response types |
| `src/lib/server/ktor.ts` | Authenticated server-side HTTP client |
| `src/lib/theme.svelte.ts` | Dark mode store (`theme.toggle()`, `theme.dark`) |
| `src/lib/components/ui/` | shadcn-svelte primitives — treat as vendor |
| `src/app.d.ts` | SvelteKit type augmentations (`App.Locals`, `App.PageData`) |
