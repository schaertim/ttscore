<script lang="ts">
	import type { PageData } from './$types';
	import type { Group, Season, SeasonStats } from '$lib/api';
	import { api } from '$lib/api';
	import * as Select from '$lib/components/ui/select/index.js';
	import * as Collapsible from '$lib/components/ui/collapsible/index.js';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { SvelteSet } from 'svelte/reactivity';
	import { CaretDown, CaretRight, Globe, MapTrifold, TrendUp, Clock } from 'phosphor-svelte';
	import StatCard from '$lib/components/StatCard.svelte';

	let { data }: { data: PageData } = $props();

	let selectedSeasonId = $state(data.seasons[0]?.id ?? '');

	const selectedSeason = $derived(data.seasons.find((s: Season) => s.id === selectedSeasonId));

	let groups = $state<Group[]>([]);
	let loadingGroups = $state(false);
	let stats = $state<SeasonStats | null>(null);
	let loadingStats = $state(false);
	let expandedFederations = $state<Set<string>>(new Set());

	async function loadGroups() {
		loadingGroups = true;
		expandedFederations = new SvelteSet();
		try {
			groups = await api.groups.list({ season: selectedSeason?.name });
		} finally {
			loadingGroups = false;
		}
	}

	async function loadStats() {
		if (!selectedSeason) return;
		loadingStats = true;
		try {
			stats = await api.stats.get(selectedSeason.name);
		} finally {
			loadingStats = false;
		}
	}

	function toggleFederation(federationId: string) {
		if (expandedFederations.has(federationId)) {
			expandedFederations.delete(federationId);
		} else {
			expandedFederations.add(federationId);
		}
		expandedFederations = new SvelteSet(expandedFederations);
	}

	// Groups grouped by federation name
	const groupsByFederation = $derived(
		data.federations
			.map((fed) => ({
				...fed,
				groups: groups.filter((g) => g.federation === fed.name)
			}))
			.filter((fed) => fed.groups.length > 0)
	);

	$effect(() => {
		loadGroups();
		loadStats();
	});
</script>

<div class="py-4 space-y-8">
	<div class="flex flex-col gap-4">
		<div class="flex items-end justify-between px-1">
			<div>
				<p class="text-xs font-bold uppercase tracking-widest text-muted-foreground">
					League Browser
				</p>
				<h1 class="text-3xl font-extrabold tracking-tight">Leagues</h1>
			</div>
			<Select.Root
				type="single"
				value={selectedSeasonId}
				onValueChange={(v) => { if (v) selectedSeasonId = v; }}
			>
				<Select.Trigger class="w-32 text-xs font-bold bg-card border-b border-border hover:border-primary transition-all">
					{selectedSeason?.name ?? 'Season'}
				</Select.Trigger>
				<Select.Content>
					{#each data.seasons as season (season.id)}
						<Select.Item value={season.id}>{season.name}</Select.Item>
					{/each}
				</Select.Content>
			</Select.Root>
		</div>
	</div>

	<section class="space-y-4">
		<div class="flex items-center justify-between px-1">
			<h3 class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground whitespace-nowrap">Browse Regions</h3>
			<Separator class="flex-1 ml-4 bg-border/60" />
		</div>

		{#if loadingGroups}
			<div class="space-y-2">
				{#each [1, 2, 3] as i (i)}
					<Skeleton class="h-16 w-full rounded-xl" />
				{/each}
			</div>
		{:else if groupsByFederation.length === 0}
			<p class="text-center text-sm text-muted-foreground py-12">
				No groups found for this season
			</p>
		{:else}
			<div class="space-y-2">
				{#each groupsByFederation as fed (fed.id)}
					{@const isExpanded = expandedFederations.has(fed.id)}
					{@const isNational = fed.name === 'STT'}
					<Collapsible.Root
						open={isExpanded}
						onOpenChange={() => toggleFederation(fed.id)}
						class="bg-card rounded-xl overflow-hidden ring-1 ring-border shadow-sm"
					>
						<Collapsible.Trigger
							class="w-full flex items-center justify-between px-4 py-4 hover:bg-accent transition-colors text-left
							{isExpanded ? 'bg-accent/50' : ''}"
						>
							<div class="flex items-center gap-3">
								{#if isNational}
									<Globe class="w-5 h-5 text-muted-foreground" />
								{:else}
									<MapTrifold class="w-5 h-5 text-muted-foreground" />
								{/if}
								<span class="font-bold">{fed.name}</span>
							</div>
							<CaretDown
								class="w-5 h-5 text-muted-foreground transition-transform duration-200
								{isExpanded ? 'rotate-180' : ''}"
							/>
						</Collapsible.Trigger>

						<Collapsible.Content class="divide-y divide-border/40 bg-background/30">
							{#each fed.groups as group (group.id)}
								<a
									href="/groups/{group.id}"
									class="flex items-center gap-3 px-4 py-3 hover:bg-accent transition-colors group"
								>
									<div class="flex-1 min-w-0">
										<p class="text-sm font-semibold truncate">{group.name}</p>
										{#if group.teamCount > 0 || group.totalRounds > 0}
											<p class="text-[10px] font-bold uppercase tracking-wide text-muted-foreground mt-0.5">
												{#if group.teamCount > 0}{group.teamCount} Teams{/if}
												{#if group.teamCount > 0 && group.totalRounds > 0} &nbsp;·&nbsp; {/if}
												{#if group.totalRounds > 0}RD {group.roundsPlayed}/{group.totalRounds}{/if}
											</p>
										{/if}
									</div>
									<CaretRight class="shrink-0 w-4 h-4 text-muted-foreground group-hover:text-foreground transition-colors" />
								</a>
							{/each}
						</Collapsible.Content>
					</Collapsible.Root>
				{/each}
			</div>
		{/if}
	</section>

	<section class="grid grid-cols-2 gap-4 pt-4">
		<StatCard
			label="Players Registered"
			value={loadingStats || !stats ? null : stats.registeredPlayers.toLocaleString()}
			class="p-6"
		>
			{#snippet footer()}
				<TrendUp class="w-4 h-4" />
				<span class="text-[10px] font-bold">{selectedSeason?.name ?? ''}</span>
			{/snippet}
		</StatCard>

		<StatCard
			label="Matches Played"
			value={loadingStats || !stats ? null : stats.matchesLast24h}
			class="p-6"
		>
			{#snippet footer()}
				<Clock class="w-4 h-4" />
				<span class="text-[10px] font-bold">Last 24 hours</span>
			{/snippet}
		</StatCard>
	</section>
</div>