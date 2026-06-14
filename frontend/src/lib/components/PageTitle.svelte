<script lang="ts">
	import type { Snippet } from 'svelte';
	import { cn } from '$lib/utils';

	let {
		class: className = '',
		children
	}: { class?: string; children: Snippet } = $props();
</script>

<!-- Page title (h1). The single display-heading style: 30px, black, tight, leading-none. -->
<h1 class={cn('text-3xl font-black tracking-tight leading-none', className)}>
	{@render children()}
</h1>
