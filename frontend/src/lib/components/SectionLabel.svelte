<script lang="ts">
	import type { Component } from 'svelte';

	interface Props {
		label: string;
		icon?: Component;
		class?: string;
	}

	let { label, icon: Icon, class: className = '' }: Props = $props();
</script>

<h2
	class="flex items-center gap-1.5 text-xs font-bold tracking-widest text-muted-foreground uppercase {className}"
>
	{label}
	{#if Icon}
		<Icon class="h-4 w-4 shrink-0" />
	{/if}
</h2>
