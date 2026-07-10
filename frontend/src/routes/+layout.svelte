<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { invalidate } from '$app/navigation';
	import { page, navigating } from '$app/state';
	import { theme } from '$lib/theme.svelte';
	import { h2h, closeH2H } from '$lib/h2h.svelte';
	import H2HDrawer from '$lib/components/player/H2HDrawer.svelte';
	import { Toaster } from '$lib/components/ui/sonner/index.js';
	import { HouseIcon, TrophyIcon, MagnifyingGlassIcon, UserCircleIcon, SignInIcon } from 'phosphor-svelte';
	import type { Component } from 'svelte';
	import { _ } from 'svelte-i18n';
	import { classColorVar } from '$lib/utils';

	let { children, data } = $props();

	// Drawer open state: set by the h2h store, cleared when the drawer is swiped away.
	let drawerOpen = $state(false);
	$effect(() => { if (h2h.rightId) drawerOpen = true; });
	$effect(() => { if (!drawerOpen) closeH2H(); });

	type NavItem = { href: string; label: string; icon: Component };

	// One flat list of destinations. Account/sign-in is just another link whose target
	// depends on auth — no separate button needed.
	const navItems = $derived<NavItem[]>([
		...(data.hasHomePlayer ? [{ href: '/', label: $_('nav.home'), icon: HouseIcon }] : []),
		{ href: '/divisions', label: $_('nav.leagues'), icon: TrophyIcon },
		{ href: '/players', label: $_('nav.search'), icon: MagnifyingGlassIcon },
		data.user
			? { href: '/account', label: $_('nav.account'), icon: UserCircleIcon }
			: {
					href: `/signin?redirectTo=${encodeURIComponent(page.url.pathname)}`,
					label: $_('nav.sign_in'),
					icon: SignInIcon
				}
	]);

	// Track the pending navigation target so a tapped item lights up with its class
	// color immediately, instead of waiting for the load to finish.
	const currentPath = $derived(navigating.to?.url.pathname ?? page.url.pathname);

	// Compare against the pathname only (the sign-in link carries a redirect query).
	const isActive = (href: string) => currentPath === href.split('?')[0];

	// Active items are tinted with the set player's class color, falling back to the
	// default foreground color (white in dark mode) when no player is set.
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
</script>

<main
	class="mx-auto max-w-2xl px-4 pt-[calc(1rem+env(safe-area-inset-top))] pb-[calc(6rem+env(safe-area-inset-bottom))]"
>
	{@render children()}
</main>

<H2HDrawer
	bind:open={drawerOpen}
	leftPlayerId={h2h.leftId ?? data.homePlayerId}
	rightPlayerId={h2h.rightId}
	isPro={data.isPro}
	supabase={data.supabase}
/>

<nav
	class="fixed inset-x-0 bottom-0 z-50 border-t border-primary/20 bg-card pb-[env(safe-area-inset-bottom)]"
>
	<div class="mx-auto flex h-16 max-w-2xl items-center justify-center gap-4 px-4">
		{#each navItems as item (item.href.split('?')[0])}
			{@const active = isActive(item.href)}
			<a
				href={item.href}
				aria-label={item.label}
				class="flex w-16 flex-col items-center justify-center gap-1 text-muted-foreground [@media(hover:hover)]:hover:text-foreground"
				style={active ? `color: ${activeColor}` : ''}
			>
				<item.icon size="22" weight={active ? 'fill' : 'regular'} />
				{#if active}
					<span class="text-xs font-semibold tracking-wide">{item.label}</span>
				{/if}
			</a>
		{/each}
	</div>
</nav>

<Toaster position="top-center" />
