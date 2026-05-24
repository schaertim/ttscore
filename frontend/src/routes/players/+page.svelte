<script lang="ts">
	import type { PageData } from './$types';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { api } from '$lib/api';
	import type { Player, PagedResponse } from '$lib/api';
	import { CaretLeftIcon, CaretRightIcon, MagnifyingGlassIcon, StarIcon } from 'phosphor-svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import FavoritePlayerCard from '$lib/components/FavoritePlayerCard.svelte';
	import PlayerAvatar from '$lib/components/PlayerAvatar.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	let { data }: { data: PageData } = $props();

	let searchQuery = $state('');
	let isSearching = $state(false);
	let currentPage = $state(0);
	let response = $state<PagedResponse<Player> | null>(null);
	let favoritePlayers = $state([...data.favoritePlayers]);

	const PAGE_SIZE = 20;

	let timer: ReturnType<typeof setTimeout>;

	async function search(page = 0) {
		if (searchQuery.length < 3) {
			response = null;
			return;
		}
		isSearching = true;
		clearTimeout(timer);
		timer = setTimeout(async () => {
			try {
				response = await api.players.search(searchQuery, page, PAGE_SIZE);
				currentPage = page;
			} catch {
				response = null;
			} finally {
				isSearching = false;
			}
		}, 300);
	}

	$effect(() => {
		if (searchQuery.length >= 3) {
			currentPage = 0;
			search(0);
		} else {
			response = null;
			isSearching = false;
		}
		return () => clearTimeout(timer);
	});

	const totalPages = $derived(response ? Math.ceil(response.total / PAGE_SIZE) : 0);
	const showFavorites = $derived(favoritePlayers.length > 0 && searchQuery.length < 3);
</script>

<div class="space-y-6">
	<header>
		<p class="text-xs font-medium tracking-widest text-muted-foreground uppercase">
			Players & Clubs
		</p>
		<h1 class="text-3xl font-black tracking-tight">Search</h1>
	</header>

	<div class="relative">
		<MagnifyingGlassIcon
			size="16"
			class="absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
		/>
		<Input
			bind:value={searchQuery}
			class="w-full py-5 pl-9 text-base"
			placeholder="Search players..."
		/>
	</div>

	{#if showFavorites}
		<section class="space-y-3">
			<SectionLabel label="Favourites" icon={StarIcon} />
			<div class="flex gap-3 overflow-x-auto pb-1" style="-webkit-overflow-scrolling: touch;">
				{#each favoritePlayers as player (player.id)}
					<FavoritePlayerCard
						id={player.id}
						fullName={player.fullName}
						klass={player.klass}
						favoriteId={player.favoriteId}
						onunfavorite={() => {
							favoritePlayers = favoritePlayers.filter((p) => p.id !== player.id);
						}}
					/>
				{/each}
			</div>
		</section>
	{/if}

	{#if searchQuery.length >= 3}
		<section class="space-y-4">
			<h2 class="px-1 text-xs font-medium tracking-widest text-muted-foreground uppercase">
				Results for "{searchQuery}"
				{#if response}
					<span class="ml-1 font-normal tracking-normal text-muted-foreground/60 normal-case">
						({response.total} found)
					</span>
				{/if}
			</h2>

			{#if isSearching}
				<div class="space-y-2">
					{#each [1, 2, 3] as i (i)}
						<div class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3">
							<Skeleton class="h-9 w-9 shrink-0 rounded-full" />
							<div class="flex-1 space-y-1.5">
								<Skeleton class="h-3.5 w-36" />
								<Skeleton class="h-3 w-24" />
							</div>
							<Skeleton class="h-4 w-12" />
						</div>
					{/each}
				</div>
			{:else if !response || response.items.length === 0}
				<p class="py-12 text-center text-sm text-muted-foreground">No players found.</p>
			{:else}
				<div class="space-y-2">
					{#each response.items as player (player.id)}
						<a
							href="/players/{player.id}"
							class="group flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
						>
							<PlayerAvatar fullName={player.fullName} size="md" />

							<div class="min-w-0 flex-1">
								<p class="truncate text-sm font-semibold">{player.fullName}</p>
								<p class="truncate text-[10px] tracking-wide text-muted-foreground">
									{player.currentClubName ?? '-'}
								</p>
							</div>

							<ClassBadge klass={player.klass} />
						</a>
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
							<CaretLeftIcon size="16" />
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
							<CaretRightIcon size="16" />
						</Button>
					</div>
				{/if}
			{/if}
		</section>
	{/if}
</div>
