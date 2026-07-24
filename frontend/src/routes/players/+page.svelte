<script lang="ts">
	import type { PageData } from './$types';
	import { onMount } from 'svelte';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { api } from '$lib/api';
	import type { Player, PagedResponse } from '$lib/api';
	import {
		CaretLeftIcon,
		CaretRightIcon,
		ClockIcon,
		MagnifyingGlassIcon,
		StarIcon,
		ScalesIcon
	} from 'phosphor-svelte';
	import { compareWithMe } from '$lib/h2h.svelte';
	import { debounce } from '$lib/debounce';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import IconButton from '$lib/components/IconButton.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import FollowPlayerCard from '$lib/components/FollowPlayerCard.svelte';
	import RecentPlayerCard from '$lib/components/RecentPlayerCard.svelte';
	import PlayerAvatar from '$lib/components/PlayerAvatar.svelte';
	import * as Carousel from '$lib/components/ui/carousel/index.js';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { _ } from 'svelte-i18n';
	import { formatName } from '$lib/utils';
	import {
		getRecentPlayers,
		addRecentPlayer,
		updateRecentPlayer,
		type RecentPlayer
	} from '$lib/recentPlayers';

	let { data }: { data: PageData } = $props();

	let searchQuery = $state('');
	let isSearching = $state(false);
	let currentPage = $state(0);
	let response = $state<PagedResponse<Player> | null>(null);
	// Deliberate local copy: optimistic removal on unfollow without waiting for the server.
	// svelte-ignore state_referenced_locally
	let favoritePlayers = $state([...data.favoritePlayers]);
	let recentPlayers = $state<RecentPlayer[]>([]);

	onMount(() => {
		recentPlayers = getRecentPlayers();
		refreshRecentClassifications();
	});

	// The recents list is a point-in-time snapshot captured when each player was clicked, so a
	// classification change since then (season rollover, live in-season bump/drop) would otherwise
	// show a stale avatar color indefinitely. Re-fetch each one's current data in the background —
	// updating in place (not via addRecentPlayer, which would reorder the list) — same as the
	// favourites list already does by always reading fresh server data.
	async function refreshRecentClassifications() {
		await Promise.all(
			recentPlayers.map(async (recent) => {
				try {
					const fresh = await api.players.get(recent.id);
					const classification = fresh.liveClassification ?? fresh.classification ?? null;
					const patch = {
						fullName: fresh.fullName,
						classification,
						currentClubName: fresh.currentClubName ?? null
					};
					updateRecentPlayer(recent.id, patch);
					recentPlayers = recentPlayers.map((p) => (p.id === recent.id ? { ...p, ...patch } : p));
				} catch {
					// player fetch failed (deleted/offline) — leave the stale entry as-is
				}
			})
		);
	}

	const PAGE_SIZE = 20;

	const runSearch = debounce(async (page: number) => {
		try {
			response = await api.players.search(searchQuery, page, PAGE_SIZE);
			currentPage = page;
		} catch {
			response = null;
		} finally {
			isSearching = false;
		}
	});

	function search(page = 0) {
		if (searchQuery.length < 3) {
			response = null;
			return;
		}
		isSearching = true;
		runSearch(page);
	}

	$effect(() => {
		if (searchQuery.length >= 3) {
			currentPage = 0;
			search(0);
		} else {
			response = null;
			isSearching = false;
		}
		return () => runSearch.cancel();
	});

	const totalPages = $derived(response ? Math.ceil(response.total / PAGE_SIZE) : 0);
	const favoriteIds = $derived(new Set(favoritePlayers.map((p) => p.id)));
	const filteredRecents = $derived(recentPlayers.filter((p) => !favoriteIds.has(p.id)));
	const showFavorites = $derived(favoritePlayers.length > 0 && searchQuery.length < 3);
	const showRecents = $derived(filteredRecents.length > 0 && searchQuery.length < 3);
</script>

{#snippet row(player: Player)}
	<PlayerAvatar fullName={player.fullName} size="md" />
	<div class="min-w-0 flex-1">
		<div class="flex items-center gap-2">
			<p class="truncate text-sm font-semibold">{formatName(player.fullName)}</p>
			<ClassBadge classification={player.liveClassification ?? player.classification} />
		</div>
		<p class="truncate text-2xs tracking-wide text-muted-foreground">
			{player.currentClubName ?? '—'}
		</p>
	</div>
{/snippet}

<div class="space-y-6">
	<header>
		<p class="mb-1 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
			{$_('search.subtitle')}
		</p>
		<PageTitle>{$_('search.title')}</PageTitle>
	</header>

	<div class="relative">
		<MagnifyingGlassIcon
			size={16}
			class="absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
		/>
		<Input
			bind:value={searchQuery}
			class="w-full py-4 pl-9 text-base"
			placeholder={$_('search.placeholder')}
		/>
	</div>

	{#if showRecents}
		<section class="space-y-3">
			<SectionLabel label={$_('search.recently_viewed')} icon={ClockIcon} />
			<Carousel.Root opts={{ align: 'start' }} class="w-full">
				<Carousel.Content class="-ms-3">
					{#each filteredRecents as player (player.id)}
						<Carousel.Item class="basis-1/3 ps-3 md:basis-1/5">
							<RecentPlayerCard
								id={player.id}
								fullName={player.fullName}
								classification={player.classification}
								currentClubName={player.currentClubName}
								onRemove={() => {
									recentPlayers = recentPlayers.filter((p) => p.id !== player.id);
								}}
							/>
						</Carousel.Item>
					{/each}
				</Carousel.Content>
			</Carousel.Root>
		</section>
	{/if}

	{#if showFavorites}
		<section class="space-y-3">
			<SectionLabel label={$_('search.favourites')} icon={StarIcon} />
			<div class="grid grid-cols-3 gap-3 md:grid-cols-5">
				{#each favoritePlayers as player (player.id)}
					<FollowPlayerCard
						id={player.id}
						fullName={player.fullName}
						classification={player.liveClassification ?? player.classification}
						currentClubName={player.currentClubName}
						followId={player.followId}
						onUnfollow={() => {
							favoritePlayers = favoritePlayers.filter((p) => p.id !== player.id);
						}}
					/>
				{/each}
			</div>
		</section>
	{/if}

	{#if searchQuery.length >= 3}
		<section class="space-y-3">
			<SectionLabel label={$_('search.results_for', { values: { query: searchQuery } })}>
				{#if response}
					<span class="ml-1 font-normal tracking-normal text-muted-foreground/60 normal-case">
						{$_('search.found', { values: { count: response.total } })}
					</span>
				{/if}
			</SectionLabel>

			{#if isSearching}
				<div class="space-y-3">
					{#each [1, 2, 3] as i (i)}
						<div class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3">
							<Skeleton class="h-9 w-9 shrink-0 rounded-full" />
							<div class="flex-1 space-y-2">
								<Skeleton class="h-3.5 w-36" />
								<Skeleton class="h-3 w-24" />
							</div>
							<Skeleton class="h-4 w-12" />
						</div>
					{/each}
				</div>
			{:else if !response || response.items.length === 0}
				<p class="py-12 text-center text-sm text-muted-foreground">{$_('search.no_results')}</p>
			{:else}
				<div class="space-y-3">
					{#each response.items as player (player.id)}
						<div
							class="group flex items-center gap-1 rounded-xl border border-border bg-card pr-2 transition-colors hover:bg-accent"
						>
							<a
								href="/players/{player.id}"
								onclick={() =>
									addRecentPlayer({
										id: player.id,
										fullName: player.fullName,
										classification: player.liveClassification ?? player.classification ?? null,
										currentClubName: player.currentClubName ?? null
									})}
								class="flex min-w-0 flex-1 items-center gap-3 px-4 py-3"
							>
								{@render row(player)}
							</a>
							{#if data.homePlayerId && player.id !== data.homePlayerId}
								<IconButton
									onclick={() => compareWithMe(player.id)}
									class="shrink-0 rounded-lg hover:text-primary"
									ariaLabel={$_('h2h.compare')}
								>
									<ScalesIcon size={18} />
								</IconButton>
							{/if}
						</div>
					{/each}
				</div>

				{#if totalPages > 1}
					<div class="flex items-center justify-center gap-2 pt-2">
						<Button
							variant="outline"
							size="sm"
							disabled={currentPage === 0}
							onclick={() => search(currentPage - 1)}
						>
							<CaretLeftIcon size={16} />
						</Button>

						<span class="px-2 text-sm text-muted-foreground tabular-nums">
							{currentPage + 1} / {totalPages}
						</span>

						<Button
							variant="outline"
							size="sm"
							disabled={currentPage >= totalPages - 1}
							onclick={() => search(currentPage + 1)}
						>
							<CaretRightIcon size={16} />
						</Button>
					</div>
				{/if}
			{/if}
		</section>
	{/if}
</div>
