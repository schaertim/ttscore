<script lang="ts">
	import type { Snippet } from 'svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import Overline from '$lib/components/Overline.svelte';
	import { cn } from '$lib/utils';

	interface Props {
		label: string;
		/** Whether the label sits above or below the value. */
		labelPosition?: 'top' | 'bottom';
		/** Horizontal alignment of label and value. */
		align?: 'start' | 'center';
		/** Simple value; wrapped in the default value style. Pass null to show a loading
		 *  skeleton (async stat). Ignored when children are given. */
		value?: string | number | null;
		/** Custom value content (colored numbers, icons, badges…); overrides `value`. */
		children?: Snippet;
		/** Small muted meta row under the value (e.g. an icon + caption). */
		footer?: Snippet;
	}

	let { label, labelPosition = 'top', align = 'start', value, children, footer }: Props = $props();
</script>

<Card.Root
	class={cn('gap-1 p-4', align === 'center' ? 'items-center text-center' : 'items-start text-left')}
>
	{#if labelPosition === 'bottom'}
		{@render valueBlock()}
		<Overline>{label}</Overline>
	{:else}
		<Overline>{label}</Overline>
		{@render valueBlock()}
	{/if}
	{#if footer}
		<div class="mt-1 flex items-center gap-2 text-muted-foreground">
			{@render footer()}
		</div>
	{/if}
</Card.Root>

{#snippet valueBlock()}
	{#if children}
		{@render children()}
	{:else if value === null}
		<Skeleton class="h-6 w-16 rounded" />
	{:else if value != null}
		<p class="font-mono text-xl leading-none font-black tabular-nums">{value}</p>
	{/if}
{/snippet}
