<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { goto, invalidate } from '$app/navigation';
	import { page } from '$app/state';
	import { theme } from '$lib/theme.svelte';
	import { h2h } from '$lib/h2h.svelte';
	import H2HDrawer from '$lib/components/player/H2HDrawer.svelte';
	import { Toaster } from '$lib/components/ui/sonner/index.js';
	import { HouseIcon, TrophyIcon, MagnifyingGlassIcon, UserCircleIcon, SignInIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
	import { classColorVar } from '$lib/utils';

	let { children, data } = $props();

	// Drawer open state: set by the h2h store, cleared when the drawer is swiped away.
	let drawerOpen = $state(false);
	$effect(() => { if (h2h.opponentId) drawerOpen = true; });
	$effect(() => { if (!drawerOpen) h2h.opponentId = null; });

	const navItems = $derived(
		data.hasHomePlayer
			? [
					{ href: '/', label: $_('nav.home'), icon: HouseIcon },
					{ href: '/divisions', label: $_('nav.leagues'), icon: TrophyIcon },
					{ href: '/players', label: $_('nav.search'), icon: MagnifyingGlassIcon }
				]
			: [
					{ href: '/divisions', label: $_('nav.leagues'), icon: TrophyIcon },
					{ href: '/players', label: $_('nav.search'), icon: MagnifyingGlassIcon }
				]
	);

	function isActive(href: string): boolean {
		return page.url.pathname === href;
	}

	const accountActive = $derived(page.url.pathname === '/account');

	// Active nav items are tinted with the set player's class color, falling back to
	// the default foreground color (white in dark mode) when no player is set.
	const activeColor = $derived(
		data.homePlayerClassification
			? classColorVar(data.homePlayerClassification)
			: 'var(--color-foreground)'
	);

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

<main
	class="mx-auto max-w-2xl px-4 pt-[calc(1rem+env(safe-area-inset-top))] pb-[calc(6rem+env(safe-area-inset-bottom))]"
>
	{@render children()}
</main>

{#if data.homePlayerId}
	<H2HDrawer
		bind:open={drawerOpen}
		homePlayerId={data.homePlayerId}
		opponentId={h2h.opponentId}
		isPro={data.isPro}
		supabase={data.supabase}
	/>
{/if}

<nav
	class="fixed inset-x-0 bottom-0 z-50 border-t-1 border-primary/20 bg-card pb-[env(safe-area-inset-bottom)]"
>
	<div class="mx-auto flex h-16 max-w-2xl items-center justify-center gap-4 px-4">
		{#each navItems as item}
			{@const active = isActive(item.href)}
			<a
				href={item.href}
				class="flex w-16 flex-col items-center justify-center gap-1 transition-colors
				       {active ? '' : 'text-muted-foreground hover:text-foreground'}"
				style={active ? `color: ${activeColor}` : ''}
			>
				<item.icon size="22" weight={active ? 'fill' : 'regular'} />
				{#if active}
					<span class="text-xs font-semibold tracking-wide">{item.label}</span>
				{/if}
			</a>
		{/each}

		<button
			onclick={handleAccountClick}
			class="flex w-16 flex-col items-center justify-center gap-1 transition-colors
			       {accountActive ? '' : 'text-muted-foreground hover:text-foreground'}"
			style={accountActive ? `color: ${activeColor}` : ''}
			aria-label={data.user ? $_('nav.account') : $_('nav.sign_in')}
		>
			{#if data.user}
				<UserCircleIcon size="22" weight={accountActive ? 'fill' : 'regular'} />
			{:else}
				<SignInIcon size="22" weight="regular" />
			{/if}
			{#if accountActive}
				<span class="text-xs font-semibold tracking-wide">
					{data.user ? $_('nav.account') : $_('nav.sign_in')}
				</span>
			{/if}
		</button>
	</div>
</nav>

<Toaster position="top-center" />
