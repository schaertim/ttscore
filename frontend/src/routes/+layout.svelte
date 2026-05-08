<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { goto, invalidate } from '$app/navigation';
	import { page } from '$app/state';
	import { theme } from '$lib/theme.svelte';
	import { House, Trophy, MagnifyingGlass, UserCircle, SignIn } from 'phosphor-svelte';

	let { children, data } = $props();

	const navItems = [
		{ href: '/', label: 'Home', icon: House },
		{ href: '/divisions', label: 'Leagues', icon: Trophy },
		{ href: '/players', label: 'Search', icon: MagnifyingGlass }
	];

	onMount(() => {
		theme.init();

		const {
			data: { subscription }
		} = data.supabase.auth.onAuthStateChange((event, session) => {
			if (session?.expires_at !== data.session?.expires_at) {
				invalidate('supabase:auth');
			}
		});

		return () => subscription.unsubscribe();
	});

	function handleAccountClick() {
		if (data.user) {
			goto('/account');
		} else {
			goto(`/signin?redirectTo=${encodeURIComponent(page.url.pathname)}`);
		}
	}
</script>

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
				<item.icon class="h-5 w-5" />
				<span class="text-label tracking-widest uppercase">{item.label}</span>
			</a>
		{/each}

		<!-- Account button -->
		<button
			onclick={handleAccountClick}
			class="flex flex-1 flex-col items-center justify-center gap-0.5 pt-1
             text-on-surface-subtle transition-colors hover:text-on-surface"
			aria-label={data.user ? 'Account' : 'Sign in'}
		>
			{#if data.user}
				<UserCircle class="h-5 w-5" />
			{:else}
				<SignIn class="h-5 w-5" />
			{/if}
			<span class="text-label tracking-widest uppercase">
				{data.user ? 'Account' : 'Sign in'}
			</span>
		</button>
	</div>
</nav>
