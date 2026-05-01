<script lang="ts">
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { api } from '$lib/api';
	import type { Player, PagedResponse } from '$lib/api';
	import { CaretLeft, CaretRight } from 'phosphor-svelte';
	import { klassColors } from '$lib/utils';

	let searchQuery = $state('');
	let isSearching  = $state(false);
	let currentPage  = $state(0);
	let response     = $state<PagedResponse<Player> | null>(null);

	const PAGE_SIZE = 20;

	let timer: ReturnType<typeof setTimeout>;

	async function search(page = 0) {
		if (searchQuery.length < 3) { response = null; return; }
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

	function initials(name: string) {
		return name.split(' ').filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join('');
	}

	const totalPages = $derived(response ? Math.ceil(response.total / PAGE_SIZE) : 0);
</script>

<div class="py-4 space-y-6 pb-20">
	<div class="px-1">
		<p class="text-xs font-bold uppercase tracking-widest text-muted-foreground">Players & Clubs</p>
		<h1 class="text-3xl font-extrabold tracking-tight">Search</h1>
	</div>

	<Input
		bind:value={searchQuery}
		class="w-full pl-4 py-5 text-base"
		placeholder="Search players..."
	/>

	{#if searchQuery.length >= 3}
		<section class="space-y-4">
			<h2 class="text-xs font-bold uppercase tracking-[0.1em] text-muted-foreground px-1">
				Results for "{searchQuery}"
				{#if response}
					<span class="ml-1 normal-case tracking-normal font-normal text-muted-foreground/60">
						({response.total} found)
					</span>
				{/if}
			</h2>

			{#if isSearching}
				<div class="space-y-2">
					{#each [1, 2, 3] as i (i)}
						<div class="flex items-center gap-3 px-4 py-3 rounded-xl border border-border bg-card">
							<Skeleton class="w-9 h-9 rounded-full shrink-0" />
							<div class="flex-1 space-y-1.5">
								<Skeleton class="h-3.5 w-36" />
								<Skeleton class="h-3 w-24" />
							</div>
							<Skeleton class="h-4 w-12" />
						</div>
					{/each}
				</div>
			{:else if !response || response.items.length === 0}
				<p class="text-center text-sm text-muted-foreground py-12">No players found.</p>
			{:else}
				<div class="space-y-2">
					{#each response.items as player (player.id)}
						<a
							href="/players/{player.id}"
							class="flex items-center gap-3 px-4 py-3 rounded-xl border border-border bg-card hover:bg-accent transition-colors group"
						>
							<div class="shrink-0 w-9 h-9 rounded-full bg-muted flex items-center justify-center
							            text-[11px] font-black text-muted-foreground tracking-tight">
								{initials(player.fullName)}
							</div>

							<div class="flex-1 min-w-0">
								<p class="text-sm font-semibold truncate">{player.fullName}</p>
								<p class="text-[10px] text-muted-foreground uppercase tracking-wide truncate">
									{player.currentClubName ?? '—'}
								</p>
							</div>

							<div class="shrink-0 flex flex-col items-end gap-1">
								{#if player.klass}
									<span class="text-[10px] font-black px-1.5 py-0.5 rounded
									             tracking-wide {klassColors(player.klass)}">
										{player.klass}
									</span>
								{/if}
								{#if player.currentElo}
									<span class="text-sm font-black tabular-nums">{player.currentElo}</span>
								{/if}
							</div>
						</a>
					{/each}
				</div>

				<!-- Pagination -->
				{#if totalPages > 1}
					<div class="flex items-center justify-center gap-2 pt-2">
						<Button
							variant="outline"
							size="sm"
							disabled={currentPage === 0}
							onclick={() => search(currentPage - 1)}
						>
							<CaretLeft class="w-4 h-4" />
						</Button>

						<span class="text-sm text-muted-foreground tabular-nums px-2">
							{currentPage + 1} / {totalPages}
						</span>

						<Button
							variant="outline"
							size="sm"
							disabled={currentPage >= totalPages - 1}
							onclick={() => search(currentPage + 1)}
						>
							<CaretRight class="w-4 h-4" />
						</Button>
					</div>
				{/if}
			{/if}
		</section>
	{/if}
</div>
