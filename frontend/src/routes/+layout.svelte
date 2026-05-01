<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { theme } from '$lib/theme.svelte';

	const navItems = [
		{ href: '/divisions', label: 'Leagues', icon: 'emoji_events' },
		{ href: '/players',   label: 'Search',  icon: 'search'       },
	];

	let { children } = $props();

	onMount(() => theme.init());
</script>

<svelte:head>
	<link
		href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap"
		rel="stylesheet"
	/>
</svelte:head>

<!-- Theme toggle -->
<button
	onclick={() => theme.toggle()}
	class="fixed top-3 right-4 z-50 text-on-surface-muted hover:text-on-surface transition-colors p-1"
	aria-label="Toggle theme"
>
	<span class="material-symbols-outlined" style="font-size:22px">
		{theme.dark ? 'light_mode' : 'dark_mode'}
	</span>
</button>

<!-- Page content -->
<main class="pt-4 pb-20 px-4 max-w-2xl mx-auto">
	{@render children()}
</main>

<!-- Bottom nav -->
<nav class="fixed bottom-0 left-0 w-full z-50 h-16 bg-surface border-t border-border-base"
     style="backdrop-filter: blur(24px);">
	<div class="flex justify-around items-center h-full px-4 max-w-2xl mx-auto">
		{#each navItems as item}
			<a
				href={item.href}
				class="flex flex-col items-center justify-center gap-0.5 flex-1 pt-1
               text-on-surface-subtle hover:text-on-surface transition-colors"
			>
				<span class="material-symbols-outlined" style="font-size:22px">{item.icon}</span>
				<span class="text-label uppercase tracking-widest">{item.label}</span>
			</a>
		{/each}
	</div>
</nav>