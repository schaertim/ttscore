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

<div class="space-y-8 py-4">
	<div class="flex flex-col gap-4">
		<div class="flex items-end justify-between px-1">
			<div>
				<p class="text-xs font-bold tracking-widest text-muted-foreground uppercase">
					League Browser
				</p>
				<h1 class="text-3xl font-extrabold tracking-tight">Leagues</h1>
			</div>
			<Select.Root
				type="single"
				value={selectedSeasonId}
				onValueChange={(v) => {
					if (v) selectedSeasonId = v;
				}}
			>
				<Select.Trigger
					class="w-32 border-b border-border bg-card text-xs font-bold transition-all hover:border-primary"
				>
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
			<h3
				class="text-[10px] font-black tracking-[0.2em] whitespace-nowrap text-muted-foreground uppercase"
			>
				Browse Regions
			</h3>
			<Separator class="ml-4 flex-1 bg-border/60" />
		</div>

		{#if loadingGroups}
			<div class="space-y-2">
				{#each [1, 2, 3] as i (i)}
					<Skeleton class="h-16 w-full rounded-xl" />
				{/each}
			</div>
		{:else if groupsByFederation.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">No groups found for this season</p>
		{:else}
			<div class="space-y-2">
				{#each groupsByFederation as fed (fed.id)}
					{@const isExpanded = expandedFederations.has(fed.id)}
					{@const isNational = fed.name === 'STT'}
					<Collapsible.Root
						open={isExpanded}
						onOpenChange={() => toggleFederation(fed.id)}
						class="overflow-hidden rounded-xl bg-card shadow-sm ring-1 ring-border"
					>
						<Collapsible.Trigger
							class="flex w-full items-center justify-between px-4 py-4 text-left transition-colors hover:bg-accent
							{isExpanded ? 'bg-accent/50' : ''}"
						>
							<div class="flex items-center gap-3">
								{#if isNational}
									<Globe class="h-5 w-5 text-muted-foreground" />
								{:else}
									<MapTrifold class="h-5 w-5 text-muted-foreground" />
								{/if}
								<span class="font-bold">{fed.name}</span>
							</div>
							<CaretDown
								class="h-5 w-5 text-muted-foreground transition-transform duration-200
								{isExpanded ? 'rotate-180' : ''}"
							/>
						</Collapsible.Trigger>

						<Collapsible.Content class="divide-y divide-border/40 bg-background/30">
							{#each fed.groups as group (group.id)}
								<a
									href="/groups/{group.id}"
									class="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-accent"
								>
									<div class="min-w-0 flex-1">
										<p class="truncate text-sm font-semibold">{group.name}</p>
										{#if group.teamCount > 0 || group.totalRounds > 0}
											<p
												class="mt-0.5 text-[10px] font-bold tracking-wide text-muted-foreground uppercase"
											>
												{#if group.teamCount > 0}{group.teamCount} Teams{/if}
												{#if group.teamCount > 0 && group.totalRounds > 0}
													&nbsp;·&nbsp;
												{/if}
												{#if group.totalRounds > 0}RD {group.roundsPlayed}/{group.totalRounds}{/if}
											</p>
										{/if}
									</div>
									<CaretRight
										class="h-4 w-4 shrink-0 text-muted-foreground transition-colors group-hover:text-foreground"
									/>
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
				<TrendUp class="h-4 w-4" />
				<span class="text-[10px] font-bold">{selectedSeason?.name ?? ''}</span>
			{/snippet}
		</StatCard>

		<StatCard
			label="Matches Played"
			value={loadingStats || !stats ? null : stats.matchesLast24h}
			class="p-6"
		>
			{#snippet footer()}
				<Clock class="h-4 w-4" />
				<span class="text-[10px] font-bold">Last 24 hours</span>
			{/snippet}
		</StatCard>
	</section>
</div>
