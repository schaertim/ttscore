<script lang="ts">
	import type { Snippet } from 'svelte';
	import { cn } from '$lib/utils';

	let {
		class: className = '',
		children
	}: { class?: string; children: Snippet } = $props();
</script>

<!--
	Overline / eyebrow — the smallest label tier (10px). Always uppercase, widest tracking,
	semibold, muted. Use for stat-card labels, meta lines, and section eyebrows.
-->
<p
	class={cn(
		'text-2xs font-semibold tracking-widest text-muted-foreground uppercase',
		className
	)}
>
	{@render children()}
</p>
