<script lang="ts">
	import type { Component } from 'svelte';
	import { CaretRightIcon } from 'phosphor-svelte';

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
	class="group flex items-center gap-3 px-4 py-3 hover:bg-accent"
>
	<div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted ring-1 ring-transparent transition-all group-hover:ring-border">
		<Icon size={18} class="text-muted-foreground" />
	</div>
	<div class="min-w-0 flex-1">
		<p class="text-sm leading-snug font-semibold">{label}</p>
		<p class="truncate text-xs text-muted-foreground">{sublabel}</p>
	</div>
	<CaretRightIcon size="16" class="shrink-0 text-muted-foreground" />
</a>
