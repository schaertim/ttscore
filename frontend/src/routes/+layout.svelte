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

	function isActive(href: string): boolean {
		if (href === '/') return page.url.pathname === '/';
		return page.url.pathname.startsWith(href);
	}

	const accountActive = $derived(page.url.pathname.startsWith('/account'));

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
<main class="mx-auto max-w-2xl px-4 pt-4 pb-24">
	{@render children()}
</main>

<!-- Bottom nav -->
<nav
	class="fixed bottom-0 inset-x-0 z-50 border-t border-border bg-card"
	style="backdrop-filter: blur(24px);"
>
	<div class="mx-auto flex h-16 max-w-2xl items-center justify-center gap-2 px-4">
		{#each navItems as item}
			{@const active = isActive(item.href)}
			<a
				href={item.href}
				class="flex w-16 flex-col items-center justify-center gap-0.5 transition-colors
				       {active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}"
			>
				<item.icon size={22} weight={active ? 'fill' : 'regular'} />
				{#if active}
					<span class="text-[10px] font-bold tracking-wide">{item.label}</span>
				{/if}
			</a>
		{/each}

		<!-- Account / Sign in -->
		<button
			onclick={handleAccountClick}
			class="flex w-16 flex-col items-center justify-center gap-0.5 transition-colors
			       {accountActive ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}"
			aria-label={data.user ? 'Account' : 'Sign in'}
		>
			{#if data.user}
				<UserCircle size={22} weight={accountActive ? 'fill' : 'regular'} />
			{:else}
				<SignIn size={22} weight="regular" />
			{/if}
			{#if accountActive}
				<span class="text-[10px] font-bold tracking-wide">
					{data.user ? 'Account' : 'Sign in'}
				</span>
			{/if}
		</button>
	</div>
</nav>
