<script lang="ts">
	import type { Snippet } from 'svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import Overline from '$lib/components/Overline.svelte';
	import { cn } from '$lib/utils';

	interface Props {
		label: string;
		/** Whether the label sits above or below the value. */
		labelPosition?: 'top' | 'bottom';
		/** Horizontal alignment of label and value. */
		align?: 'start' | 'center';
		/** Simple value; wrapped in the default value style. Ignored when children are given. */
		value?: string | number;
		/** Custom value content (colored numbers, icons, badges…); overrides `value`. */
		children?: Snippet;
	}

	let { label, labelPosition = 'top', align = 'start', value, children }: Props = $props();
</script>

<Card.Root class={cn('gap-1 p-4', align === 'center' ? 'items-center text-center' : 'items-start text-left')}>
	{#if labelPosition === 'bottom'}
		{@render valueBlock()}
		<Overline>{label}</Overline>
	{:else}
		<Overline>{label}</Overline>
		{@render valueBlock()}
	{/if}
</Card.Root>

{#snippet valueBlock()}
	{#if children}
		{@render children()}
	{:else if value != null}
		<p class="text-xl leading-none font-black tabular-nums">{value}</p>
	{/if}
{/snippet}
