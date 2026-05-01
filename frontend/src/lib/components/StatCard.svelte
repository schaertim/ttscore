<script lang="ts">
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { cn } from '$lib/utils';
	import type { Snippet } from 'svelte';

	interface Props {
		label: string;
		/** Pass null/undefined to show a loading skeleton */
		value?: string | number | null;
		class?: string;
		footer?: Snippet;
	}

	let { label, value = null, class: extraClass = '', footer }: Props = $props();
</script>

<Card.Root class={cn('p-5 shadow-sm', extraClass)}>
	<p class="text-[10px] font-black uppercase tracking-widest text-muted-foreground">{label}</p>
	{#if value == null}
		<Skeleton class="h-8 w-20 mt-1" />
	{:else}
		<p class="text-3xl font-black text-muted-foreground">{value}</p>
	{/if}
	{#if footer}
		<div class="flex items-center gap-2 text-muted-foreground">
			{@render footer()}
		</div>
	{/if}
</Card.Root>
