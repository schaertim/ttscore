<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { theme } from '$lib/theme.svelte';

	const navItems = [
		{ href: '/divisions', label: 'Leagues', icon: 'emoji_events' },
		{ href: '/players', label: 'Search', icon: 'search' }
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
	class="fixed top-3 right-4 z-50 p-1 text-on-surface-muted transition-colors hover:text-on-surface"
	aria-label="Toggle theme"
>
	<span class="material-symbols-outlined" style="font-size:22px">
		{theme.dark ? 'light_mode' : 'dark_mode'}
	</span>
</button>

<!-- Page content -->
<main class="mx-auto max-w-2xl px-4 pt-4 pb-20">
	{@render children()}
</main>

<!-- Bottom nav -->
<nav
	class="fixed bottom-0 left-0 z-50 h-16 w-full border-t border-border-base bg-surface"
	style="backdrop-filter: blur(24px);"
>
	<div class="mx-auto flex h-full max-w-2xl items-center justify-around px-4">
		{#each navItems as item}
			<a
				href={item.href}
				class="flex flex-1 flex-col items-center justify-center gap-0.5 pt-1
               text-on-surface-subtle transition-colors hover:text-on-surface"
			>
				<span class="material-symbols-outlined" style="font-size:22px">{item.icon}</span>
				<span class="text-label tracking-widest uppercase">{item.label}</span>
			</a>
		{/each}
	</div>
</nav>
