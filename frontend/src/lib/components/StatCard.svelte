<script lang="ts">
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import Overline from '$lib/components/Overline.svelte';
	import type { Snippet } from 'svelte';

	interface Props {
		label: string;
		/** Pass null/undefined to show a loading skeleton */
		value?: string | number | null;
		footer?: Snippet;
	}

	let { label, value = null, footer }: Props = $props();
</script>

<Card.Root class="p-4 gap-2">
	<Overline>{label}</Overline>
	{#if value == null}
		<Skeleton class="mt-1 h-8 w-20" />
	{:else}
		<p class="text-3xl font-black text-muted-foreground">{value}</p>
	{/if}
	{#if footer}
		<div class="flex items-center gap-2 text-muted-foreground">
			{@render footer()}
		</div>
	{/if}
</Card.Root>
