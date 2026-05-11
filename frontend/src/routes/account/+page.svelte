<script lang="ts">
	import type { PageData } from './$types';
	import { goto, invalidate } from '$app/navigation';
	import { enhance } from '$app/forms';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import { api, type Player } from '$lib/api';
	import { Star, BellRinging, Sun, Moon, Trash, User, UsersThree, Trophy } from 'phosphor-svelte';
	import { theme } from '$lib/theme.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	import { page } from '$app/state';

	let { data }: { data: PageData } = $props();

	let query = $state('');
	let results = $state<Player[]>([]);
	let searching = $state(false);
	let searchTimeout: ReturnType<typeof setTimeout>;

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
</script>

<div class="flex flex-col gap-6 pt-2">
	<header class="space-y-4">
		<BackButton />
		<div>
			<h1 class="text-3xl leading-none font-black tracking-tighter">Account</h1>
			<p class="mt-1 text-sm text-muted-foreground">{data.user?.email}</p>
		</div>
	</header>

	<section class="flex flex-col gap-3">
		<SectionLabel label="My Player" />

		{#if data.profile.homePlayerId}
			<div class="flex items-center justify-between rounded-xl border border-border px-4 py-3">
				<div class="flex min-w-0 items-center gap-3">
					<User size={18} class="shrink-0 text-muted-foreground" />
					<a
						href="/players/{data.profile.homePlayerId}"
						class="font-semibold transition-colors hover:text-primary"
					>
						{data.profile.homePlayerName ?? 'Unknown player'}
					</a>
				</div>
				<form method="POST" action="?/removeHomePlayer" use:enhance>
					<button
						type="submit"
						class="ml-3 shrink-0 text-muted-foreground transition-colors hover:text-destructive"
						aria-label="Remove home player"
					>
						<Trash size={16} />
					</button>
				</form>
			</div>
		{:else}
			<p class="text-sm text-muted-foreground">No player set yet.</p>
		{/if}

		{#if page.form?.error}
			<p class="text-sm text-destructive">{page.form.error}</p>
		{/if}

		<div class="flex flex-col gap-2">
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
				<ul class="flex flex-col gap-1">
					{#each results as player (player.id)}
						<li>
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
									class="flex w-full items-center justify-between rounded-lg border border-border
									       px-4 py-2.5 text-left transition-colors hover:bg-muted"
								>
									<span class="font-medium">{player.fullName}</span>
									<span class="text-xs text-muted-foreground">{player.currentClubName ?? ''}</span>
								</button>
							</form>
						</li>
					{/each}
				</ul>
			{/if}
		</div>
	</section>

	{#if data.favorites.length > 0}
		<section class="flex flex-col gap-3">
			<SectionLabel label="Favorites" icon={Star} />
			<ul class="flex flex-col gap-1">
				{#each data.favorites as item (item.id)}
					{@const EntityIcon =
						item.targetType === 'player' ? User : item.targetType === 'team' ? UsersThree : Trophy}
					{@const href = `/${item.targetType === 'division_group' ? 'groups' : item.targetType === 'team' ? 'teams' : 'players'}/${item.targetId}`}
					<li class="flex items-center justify-between rounded-xl border border-border px-4 py-3">
						<div class="flex min-w-0 items-center gap-3">
							<EntityIcon size={18} class="shrink-0 text-muted-foreground" />
							<a {href} class="truncate font-semibold transition-colors hover:text-primary">
								{item.targetName}
							</a>
						</div>
						<form method="POST" action="?/removeFavorite" use:enhance>
							<input type="hidden" name="favoriteId" value={item.id} />
							<button
								type="submit"
								class="ml-3 shrink-0 text-muted-foreground transition-colors hover:text-destructive"
								aria-label="Remove favorite"
							>
								<Trash size={16} />
							</button>
						</form>
					</li>
				{/each}
			</ul>
		</section>
	{/if}

	{#if data.notifications.length > 0}
		<section class="flex flex-col gap-3">
			<SectionLabel label="Notifications" icon={BellRinging} />
			<ul class="flex flex-col gap-1">
				{#each data.notifications as item (item.id)}
					{@const EntityIcon =
						item.targetType === 'player' ? User : item.targetType === 'team' ? UsersThree : Trophy}
					{@const href = `/${item.targetType === 'division_group' ? 'groups' : item.targetType === 'team' ? 'teams' : 'players'}/${item.targetId}`}
					<li class="flex items-center justify-between rounded-xl border border-border px-4 py-3">
						<div class="flex min-w-0 items-center gap-3">
							<EntityIcon size={18} class="shrink-0 text-muted-foreground" />
							<a {href} class="truncate font-semibold transition-colors hover:text-primary">
								{item.targetName}
							</a>
						</div>
						<form method="POST" action="?/removeNotification" use:enhance>
							<input type="hidden" name="notifyId" value={item.id} />
							<button
								type="submit"
								class="ml-3 shrink-0 text-muted-foreground transition-colors hover:text-destructive"
								aria-label="Remove notification"
							>
								<Trash size={16} />
							</button>
						</form>
					</li>
				{/each}
			</ul>
		</section>
	{/if}

	<section class="flex flex-col gap-3">
		<SectionLabel label="Appearance" />
		<button
			onclick={() => theme.toggle()}
			class="flex items-center justify-between rounded-xl border border-border px-4 py-3
			       transition-colors hover:bg-accent"
		>
			<span class="text-sm font-semibold">Dark mode</span>
			{#if theme.dark}
				<Sun class="h-5 w-5 text-muted-foreground" />
			{:else}
				<Moon class="h-5 w-5 text-muted-foreground" />
			{/if}
		</button>
	</section>

	<Button variant="destructive" onclick={signOut} class="w-full">Sign out</Button>
</div>
