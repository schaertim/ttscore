<script lang="ts">
	import type { Component } from 'svelte';
	import { cn } from '$lib/utils';

	interface Props {
		label: string;
		icon?: Component;
		class?: string;
	}

	let { label, icon: Icon, class: className = '' }: Props = $props();
</script>

<h2
	class={cn('flex items-center gap-1.5 px-1 text-xs font-medium tracking-widest text-muted-foreground uppercase', className)}
>
	{label}
	{#if Icon}
		<Icon class="h-4 w-4 shrink-0" />
	{/if}
</h2>
