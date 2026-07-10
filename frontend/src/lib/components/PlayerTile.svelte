<script lang="ts">
	import type { Snippet } from 'svelte';
	import PlayerAvatar from './PlayerAvatar.svelte';
	import { formatName, formatShortName } from '$lib/utils';

	interface Props {
		fullName: string;
		classification?: string | null;
		/** Renders the tile as a link when set; otherwise it's a button. */
		href?: string;
		onclick?: (e: MouseEvent) => void;
		/** Content row under the name — club, H2H score, etc. */
		content?: Snippet;
		/** Icon or action pinned to a corner (follow, remove, compare…). */
		corner?: Snippet;
		cornerPosition?: 'top-right' | 'bottom-right';
		avatarSize?: 'sm' | 'md' | 'lg';
	}

	let {
		fullName,
		classification,
		href,
		onclick,
		content,
		corner,
		cornerPosition = 'top-right',
		avatarSize = 'md'
	}: Props = $props();

	const cardClass =
		'flex h-full w-full flex-col items-center gap-0.5 rounded-2xl border border-border bg-card p-4 text-center transition-colors hover:bg-accent';
</script>

{#snippet body()}
	<PlayerAvatar {fullName} {classification} size={avatarSize} />
	<div class="flex w-full min-w-0 flex-col items-center gap-0.5">
		<span class="w-full truncate text-sm font-semibold leading-tight" title={formatName(fullName)}>
			{formatShortName(fullName)}
		</span>
		{#if content}
			<div class="w-full min-w-0">{@render content()}</div>
		{/if}
	</div>
{/snippet}

<div class="relative h-full w-full">
	{#if href}
		<a {href} {onclick} class={cardClass}>{@render body()}</a>
	{:else}
		<button type="button" {onclick} class={cardClass}>{@render body()}</button>
	{/if}

	{#if corner}
		<!-- Wrapper ignores pointer events so a decorative corner icon doesn't block the
		     card; interactive corners (buttons/forms) opt back in with pointer-events-auto. -->
		<div
			class="pointer-events-none absolute {cornerPosition === 'top-right'
				? 'top-2 right-2'
				: 'right-2 bottom-2'}"
		>
			{@render corner()}
		</div>
	{/if}
</div>
