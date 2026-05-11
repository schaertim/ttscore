<script lang="ts">
	import type { Component } from 'svelte';

	interface Props {
		href: string;
		icon: Component<{ size?: number; class?: string }>;
		label: string;
		sublabel: string;
	}

	let { href, icon: Icon, label, sublabel }: Props = $props();
</script>

<a
	{href}
	class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:bg-accent"
>
	<div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted">
		<Icon size={16} class="text-muted-foreground" />
	</div>
	<div class="min-w-0">
		<p class="text-sm leading-snug font-semibold">{label}</p>
		<p class="truncate text-xs text-muted-foreground">{sublabel}</p>
	</div>
</a>
