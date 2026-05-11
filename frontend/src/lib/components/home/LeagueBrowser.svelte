<script lang="ts">
	import type { Group, Season, SeasonStats, Federation } from '$lib/api';
	import { api } from '$lib/api';
	import * as Select from '$lib/components/ui/select/index.js';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import {
		CaretDown,
		CaretRight,
		Globe,
		MapTrifold,
		TrendUp,
		Clock,
		Trophy
	} from 'phosphor-svelte';
	import StatCard from '$lib/components/StatCard.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	interface Props {
		seasons: Season[];
		federations: Federation[];
	}

	let { seasons, federations }: Props = $props();

	let selectedSeasonId = $state(seasons[0]?.id ?? '');

	const selectedSeason = $derived(seasons.find((s: Season) => s.id === selectedSeasonId));

	let groups = $state<Group[]>([]);
	let loadingGroups = $state(false);
	let stats = $state<SeasonStats | null>(null);
	let loadingStats = $state(false);
	let expandedFederations = $state<string[]>([]);

	async function loadGroups() {
		loadingGroups = true;
		expandedFederations = [];
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

	const groupsByFederation = $derived(
		federations
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

<div class="space-y-6">
	<header class="flex items-end justify-between px-1">
		<div>
			<p class="text-xs font-medium tracking-widest text-muted-foreground uppercase">
				League Browser
			</p>
			<h1 class="text-3xl font-black tracking-tight">Leagues</h1>
		</div>
		<Select.Root
			type="single"
			value={selectedSeasonId}
			onValueChange={(v) => {
				if (v) selectedSeasonId = v;
			}}
		>
			<Select.Trigger class="w-32">
				{selectedSeason?.name ?? 'Season'}
			</Select.Trigger>
			<Select.Content>
				{#each seasons as season (season.id)}
					<Select.Item value={season.id}>{season.name}</Select.Item>
				{/each}
			</Select.Content>
		</Select.Root>
	</header>

	<section class="space-y-4">
		<SectionLabel label="Regions" icon={Trophy} />

		{#if loadingGroups}
			<div class="space-y-2">
				{#each [1, 2, 3] as i (i)}
					<Skeleton class="h-16 w-full rounded-xl" />
				{/each}
			</div>
		{:else if groupsByFederation.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">No groups found for this season</p>
		{:else}
			<Accordion.Root type="multiple" bind:value={expandedFederations} class="space-y-2">
				{#each groupsByFederation as fed (fed.id)}
					{@const isExpanded = expandedFederations.includes(fed.id)}
					{@const isNational = fed.name === 'STT'}
					<Accordion.Item
						value={fed.id}
						class="overflow-hidden rounded-xl bg-card shadow-sm ring-1 ring-border not-last:border-b-0"
					>
						<Accordion.Trigger
							class="w-full items-center px-4 py-4 transition-colors hover:bg-accent hover:no-underline
							{isExpanded ? 'bg-accent/50' : ''} [&_[data-slot=accordion-trigger-icon]]:hidden"
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
						</Accordion.Trigger>
						<Accordion.Content class="p-0">
							<div class="divide-y divide-border/40 bg-background/30">
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
							</div>
						</Accordion.Content>
					</Accordion.Item>
				{/each}
			</Accordion.Root>
		{/if}
	</section>

	<section class="grid grid-cols-2 gap-4">
		<StatCard
			label="Players Registered"
			value={loadingStats || !stats ? null : stats.registeredPlayers.toLocaleString()}
		>
			{#snippet footer()}
				<TrendUp size={16} />
				<span class="text-[10px] font-bold">{selectedSeason?.name ?? ''}</span>
			{/snippet}
		</StatCard>

		<StatCard
			label="Matches Played"
			value={loadingStats || !stats ? null : stats.matchesLast24h}
		>
			{#snippet footer()}
				<Clock size={16} />
				<span class="text-[10px] font-bold">Last 24 hours</span>
			{/snippet}
		</StatCard>
	</section>
</div>
