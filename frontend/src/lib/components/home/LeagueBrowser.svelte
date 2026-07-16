<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { Group, Season, SeasonStats, Federation } from '$lib/api';
	import { api } from '$lib/api';
	import SeasonSelect from '$lib/components/SeasonSelect.svelte';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import {
		CaretDownIcon,
		CaretRightIcon,
		GlobeIcon,
		MapTrifoldIcon,
		TrendUpIcon,
		ClockIcon,
		TrophyIcon
	} from 'phosphor-svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { _ } from 'svelte-i18n';

	interface Props {
		seasons: Season[];
		federations: Federation[];
		/** Optional callout (e.g. sign-in / set-player banner) rendered under the header. */
		banner?: Snippet;
	}

	let { seasons, federations, banner }: Props = $props();

	// Deliberate initial-value capture: default to the newest season once, then the user owns it.
	// svelte-ignore state_referenced_locally
	let selectedSeasonId = $state(seasons[0]?.id ?? '');

	const selectedSeason = $derived(seasons.find((s: Season) => s.id === selectedSeasonId));

	let groups = $state<Group[]>([]);
	let loadingGroups = $state(true);
	let stats = $state<SeasonStats | null>(null);
	let loadingStats = $state(true);
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
			<p class="mb-1 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
				{$_('leagues.browser_label')}
			</p>
			<PageTitle>{$_('leagues.title')}</PageTitle>
		</div>
		<SeasonSelect
			bind:value={selectedSeasonId}
			seasons={seasons.map((s) => ({ value: s.id, label: s.name }))}
		/>
	</header>

	{@render banner?.()}

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
			<Accordion.Root type="multiple" bind:value={expandedFederations} class="space-y-3">
				{#each groupsByFederation as fed (fed.id)}
					{@const isExpanded = expandedFederations.includes(fed.id)}
					{@const isNational = fed.name === 'STT'}
					<Accordion.Item
						value={fed.id}
						class="overflow-hidden rounded-xl border border-border bg-card"
					>
						<Accordion.Trigger
							class="w-full items-center rounded-none px-4 py-4 transition-colors hover:bg-accent hover:no-underline [&_[data-slot=accordion-trigger-icon]]:hidden"
						>
							<div class="flex items-center gap-3">
								{#if isNational}
									<GlobeIcon size={20} class="text-muted-foreground" />
								{:else}
									<MapTrifoldIcon size={20} class="text-muted-foreground" />
								{/if}
								<span class="font-semibold">{fed.name}</span>
							</div>
							<CaretDownIcon
								size={20}
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
												<div
													class="mt-1 flex items-center gap-1.5 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
												>
													{#if group.teamCount > 0}
														<span
															>{$_('leagues.teams', {
																values: { count: group.teamCount }
															})}</span
														>
													{/if}
													{#if group.teamCount > 0 && group.totalRounds > 0}
														<Separator
															orientation="vertical"
															class="bg-muted-foreground/40 data-[orientation=vertical]:h-2.5"
														/>
													{/if}
													{#if group.totalRounds > 0}
														<span
															>{$_('leagues.round', {
																values: { played: group.roundsPlayed, total: group.totalRounds }
															})}</span
														>
													{/if}
												</div>
											{/if}
										</div>
										<CaretRightIcon
											size={16}
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

	<section class="grid grid-cols-2 gap-3">
		<StatTile
			label={$_('leagues.players_active')}
			value={loadingStats || !stats ? null : stats.activePlayers.toLocaleString()}
		>
			{#snippet footer()}
				<TrendUpIcon size={16} />
				<span class="text-2xs font-semibold">{selectedSeason?.name ?? ''}</span>
			{/snippet}
		</StatTile>

		<StatTile
			label={$_('leagues.matches_played')}
			value={loadingStats || !stats ? null : stats.matchesLast24h}
		>
			{#snippet footer()}
				<ClockIcon size={16} />
				<span class="text-2xs font-semibold">{$_('leagues.last_24h')}</span>
			{/snippet}
		</StatTile>
	</section>
</div>
