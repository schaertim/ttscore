<script lang="ts">
	import type { Snippet } from 'svelte';
	import { cn } from '$lib/utils';

	interface Props {
		class?: string;
		children: Snippet;
	}

	let { class: className = '', children }: Props = $props();
</script>

<!-- Page title (h1). The single display-heading style: 30px, black, tight, leading-none. -->
<h1 class={cn('text-3xl leading-none font-black tracking-tight', className)}>
	{@render children()}
</h1>
