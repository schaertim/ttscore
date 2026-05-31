<script lang="ts">
	import type { Group, Season, SeasonStats, Federation } from '$lib/api';
	import { api } from '$lib/api';
	import * as Select from '$lib/components/ui/select/index.js';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import {
		CaretDownIcon,
		CaretRightIcon,
		GlobeIcon,
		MapTrifoldIcon,
		TrendUpIcon,
		ClockIcon,
		TrophyIcon
	} from 'phosphor-svelte';
	import StatCard from '$lib/components/StatCard.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { _ } from 'svelte-i18n';

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
				{$_('leagues.browser_label')}
			</p>
			<h1 class="text-3xl font-black tracking-tight">{$_('leagues.title')}</h1>
		</div>
		<Select.Root
			type="single"
			value={selectedSeasonId}
			onValueChange={(v) => {
				if (v) selectedSeasonId = v;
			}}
		>
			<Select.Trigger class="w-32">
				{selectedSeason?.name ?? $_('leagues.season')}
			</Select.Trigger>
			<Select.Content>
				{#each seasons as season (season.id)}
					<Select.Item value={season.id}>{season.name}</Select.Item>
				{/each}
			</Select.Content>
		</Select.Root>
	</header>

	<section class="space-y-4">
		<SectionLabel label={$_('leagues.regions')} icon={TrophyIcon} />

		{#if loadingGroups}
			<div class="space-y-2">
				{#each [1, 2, 3] as i (i)}
					<Skeleton class="h-16 w-full rounded-xl" />
				{/each}
			</div>
		{:else if groupsByFederation.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">{$_('leagues.no_groups')}</p>
		{:else}
			<Accordion.Root type="multiple" bind:value={expandedFederations} class="space-y-3.5">
				{#each groupsByFederation as fed (fed.id)}
					{@const isExpanded = expandedFederations.includes(fed.id)}
					{@const isNational = fed.name === 'STT'}
					<Accordion.Item
						value={fed.id}
						class="overflow-hidden rounded-xl border border-border/50 bg-card not-last:border-b-0"
					>
						<Accordion.Trigger
							class="w-full items-center rounded-none px-4 py-4 transition-colors hover:bg-accent hover:no-underline [&_[data-slot=accordion-trigger-icon]]:hidden"
						>
							<div class="flex items-center gap-3">
								{#if isNational}
									<GlobeIcon size="20" class="text-muted-foreground" />
								{:else}
									<MapTrifoldIcon size="20" class="text-muted-foreground" />
								{/if}
								<span class="font-bold">{fed.name}</span>
							</div>
							<CaretDownIcon
								size="20"
								class="text-muted-foreground transition-transform duration-200
								{isExpanded ? 'rotate-180' : ''}"
							/>
						</Accordion.Trigger>
						<Accordion.Content class="p-0">
							<div class="divide-y divide-border/40 bg-background/50">
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
													{#if group.teamCount > 0}{$_('leagues.teams', { values: { count: group.teamCount } })}{/if}
													{#if group.teamCount > 0 && group.totalRounds > 0}
														&nbsp;·&nbsp;
													{/if}
													{#if group.totalRounds > 0}{$_('leagues.round', { values: { played: group.roundsPlayed, total: group.totalRounds } })}{/if}
												</p>
											{/if}
										</div>
										<CaretRightIcon
											size="16"
											class="shrink-0 text-muted-foreground transition-colors group-hover:text-foreground"
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

	<section class="grid grid-cols-2 gap-3.5">
		<StatCard
			label={$_('leagues.players_registered')}
			value={loadingStats || !stats ? null : stats.registeredPlayers.toLocaleString()}
		>
			{#snippet footer()}
				<TrendUpIcon size="16" />
				<span class="text-[10px] font-bold">{selectedSeason?.name ?? ''}</span>
			{/snippet}
		</StatCard>

		<StatCard
			label={$_('leagues.matches_played')}
			value={loadingStats || !stats ? null : stats.matchesLast24h}
		>
			{#snippet footer()}
				<ClockIcon size="16" />
				<span class="text-[10px] font-bold">{$_('leagues.last_24h')}</span>
			{/snippet}
		</StatCard>
	</section>
</div>
