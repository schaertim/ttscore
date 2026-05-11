<script lang="ts">
	import type { PageData } from './$types';
	import { goto, invalidate } from '$app/navigation';
	import { enhance } from '$app/forms';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import { api, type Player } from '$lib/api';
	import { Star, BellRinging, Sun, Moon, Trash, User, UsersThree, Trophy, PaintBrushHousehold } from 'phosphor-svelte';
	import { theme } from '$lib/theme.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { page } from '$app/state';

	let { data }: { data: PageData } = $props();

	let query = $state('');
	let results = $state<Player[]>([]);
	let searching = $state(false);
	let searchTimeout: ReturnType<typeof setTimeout>;

	const sortOrder: Record<string, number> = { division_group: 0, team: 1, player: 2 };
	const favoriteGroups = $derived([
		{ label: 'Leagues', icon: Trophy, items: data.favorites.filter(f => f.targetType === 'division_group') },
		{ label: 'Teams', icon: UsersThree, items: data.favorites.filter(f => f.targetType === 'team') },
		{ label: 'Players', icon: User, items: data.favorites.filter(f => f.targetType === 'player') },
	].filter(g => g.items.length > 0));

	const notificationGroups = $derived([
		{ label: 'Leagues', icon: Trophy, items: data.notifications.filter(n => n.targetType === 'division_group') },
		{ label: 'Teams', icon: UsersThree, items: data.notifications.filter(n => n.targetType === 'team') },
		{ label: 'Players', icon: User, items: data.notifications.filter(n => n.targetType === 'player') },
	].filter(g => g.items.length > 0));

	function onInput() {
		clearTimeout(searchTimeout);
		results = [];
		if (!query.trim()) return;
		searchTimeout = setTimeout(async () => {
			searching = true;
			try {
				const res = await api.players.search(query, 0, 6);
				results = res.items;
			} finally {
				searching = false;
			}
		}, 300);
	}

	async function signOut() {
		await data.supabase.auth.signOut();
		invalidate('supabase:auth');
		goto('/');
	}

	function entityIcon(targetType: string) {
		return targetType === 'player' ? User : targetType === 'team' ? UsersThree : Trophy;
	}

	function entityHref(targetType: string, targetId: string) {
		const segment = targetType === 'division_group' ? 'groups' : targetType === 'team' ? 'teams' : 'players';
		return `/${segment}/${targetId}`;
	}
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton class="" />
		<div>
			<h1 class="mb-1.5 text-3xl leading-none font-black tracking-tighter">Account</h1>
			<p class="text-sm text-muted-foreground">{data.user?.email}</p>
		</div>
	</header>

	<section class="space-y-3">
		<SectionLabel label="My Player" icon={User} />

		{#if data.profile.homePlayerId}
			<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
				<a
					href="/players/{data.profile.homePlayerId}"
					class="flex items-center justify-between px-4 py-3 transition-colors hover:bg-accent"
				>
					<span class="font-semibold">{data.profile.homePlayerName ?? 'Unknown player'}</span>
					<form method="POST" action="?/removeHomePlayer" use:enhance onclick={(e) => e.stopPropagation()}>
						<button
							type="submit"
							class="flex items-center justify-center rounded-full p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
							aria-label="Remove home player"
						>
							<Trash size={18} />
						</button>
					</form>
				</a>
			</div>
		{:else}
			{#if page.form?.error}
				<p class="text-sm text-destructive">{page.form.error}</p>
			{/if}

			<div class="space-y-2">
				<Input
					type="search"
					placeholder="Search for your player…"
					bind:value={query}
					oninput={onInput}
				/>

				{#if searching}
					<p class="px-1 text-xs text-muted-foreground">Searching…</p>
				{/if}

				{#if results.length > 0}
					<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
						{#each results as player (player.id)}
							<form
								method="POST"
								action="?/setHomePlayer"
								use:enhance={() => {
									return async ({ update }) => {
										query = '';
										results = [];
										await update();
									};
								}}
							>
								<input type="hidden" name="playerId" value={player.id} />
								<button
									type="submit"
									class="flex w-full items-center justify-between px-4 py-3 text-left transition-colors hover:bg-accent"
								>
									<span class="font-medium">{player.fullName}</span>
									<span class="text-xs text-muted-foreground">{player.currentClubName ?? ''}</span>
								</button>
							</form>
						{/each}
					</div>
				{/if}
			</div>
		{/if}
	</section>

	{#if favoriteGroups.length > 0}
		<section class="space-y-3">
			<SectionLabel label="Favorites" icon={Star} />
			{#each favoriteGroups as group (group.label)}
				<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
					{#each group.items as item (item.id)}
						<a
							href={entityHref(item.targetType, item.targetId)}
							class="flex items-center justify-between px-4 py-3 transition-colors hover:bg-accent"
						>
							<div class="flex min-w-0 items-center gap-3">
								<group.icon size={18} class="shrink-0 text-muted-foreground" />
								<span class="truncate font-semibold">{item.targetName}</span>
							</div>
							<form method="POST" action="?/removeFavorite" use:enhance onclick={(e) => e.stopPropagation()}>
								<input type="hidden" name="favoriteId" value={item.id} />
								<button
									type="submit"
									class="flex items-center justify-center rounded-full p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
									aria-label="Remove favorite"
								>
									<Star size={18} weight="fill" />
								</button>
							</form>
						</a>
					{/each}
				</div>
			{/each}
		</section>
	{/if}

	{#if notificationGroups.length > 0}
		<section class="space-y-3">
			<SectionLabel label="Notifications" icon={BellRinging} />
			{#each notificationGroups as group (group.label)}
				<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
					{#each group.items as item (item.id)}
						<a
							href={entityHref(item.targetType, item.targetId)}
							class="flex items-center justify-between px-4 py-3 transition-colors hover:bg-accent"
						>
							<div class="flex min-w-0 items-center gap-3">
								<group.icon size={18} class="shrink-0 text-muted-foreground" />
								<span class="truncate font-semibold">{item.targetName}</span>
							</div>
							<form method="POST" action="?/removeNotification" use:enhance onclick={(e) => e.stopPropagation()}>
								<input type="hidden" name="notifyId" value={item.id} />
								<button
									type="submit"
									class="flex items-center justify-center rounded-full p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
									aria-label="Remove notification"
								>
									<BellRinging size={18} weight="fill" />
								</button>
							</form>
						</a>
					{/each}
				</div>
			{/each}
		</section>
	{/if}

	<section class="space-y-3">
		<SectionLabel label="Appearance" icon={PaintBrushHousehold} />
		<button
			onclick={() => theme.toggle()}
			class="flex w-full items-center justify-between rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
		>
			<span class="font-semibold">{theme.dark ? 'Dark mode' : 'Light mode'}</span>
			{#if theme.dark}
				<Moon class="h-5 w-5 text-muted-foreground" />
			{:else}
				<Sun class="h-5 w-5 text-muted-foreground" />
			{/if}
		</button>
	</section>

	<Button variant="destructive" onclick={signOut} class="w-full">Sign out</Button>
</div>
