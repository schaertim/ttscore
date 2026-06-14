<script lang="ts">
	import type { Component, Snippet } from 'svelte';
	import { cn } from '$lib/utils';

	interface Props {
		label: string;
		icon?: Component;
		class?: string;
		children?: Snippet;
	}

	let { label, icon: Icon, class: className = '', children }: Props = $props();
</script>

<h2
	class={cn('flex items-center gap-2 px-1 text-xs font-semibold tracking-widest text-muted-foreground uppercase', className)}
>
	{label}
	{#if Icon}
		<Icon size="16" class="shrink-0" />
	{/if}
	{#if children}
		{@render children()}
	{/if}
</h2>
