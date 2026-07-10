<script lang="ts">
	import type { PageData } from './$types';
	import type { PlayerGame } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import SeasonSelect from '$lib/components/SeasonSelect.svelte';
	import CaretDownIcon from 'phosphor-svelte/lib/CaretDownIcon';
	import CaretUpIcon from 'phosphor-svelte/lib/CaretUpIcon';
	import TrendUpIcon from 'phosphor-svelte/lib/TrendUpIcon';
	import TrendDownIcon from 'phosphor-svelte/lib/TrendDownIcon';
	import ArrowRightIcon from 'phosphor-svelte/lib/ArrowRightIcon';
	import { locale, _ } from 'svelte-i18n';
	import { page } from '$app/state';
	import { replaceState } from '$app/navigation';
	import { formatName, seasonNameOf } from '$lib/utils';

	let { data }: { data: PageData } = $props();

	// Persist the active tab in the URL (?tab=…) so it survives reloads and back navigation.
	const TABS = ['history', 'upcoming'];
	const activeTab = $derived(
		TABS.includes(page.url.searchParams.get('tab') ?? '')
			? page.url.searchParams.get('tab')!
			: 'history'
	);

	function setTab(value: string) {
		const url = new URL(page.url);
		if (value === 'history') url.searchParams.delete('tab');
		else url.searchParams.set('tab', value);
		replaceState(url, page.state);
	}

	// Sentinel season for games with no recorded date (rare, mostly non-league games).
	const UNDATED = 'unknown';

	type MonthGroup = {
		key: string;
		label: string;
		games: PlayerGame[];
		wins: number;
		totalElo: number;
		/** Unrounded sum, used to break the sign tie when totalElo rounds to 0. */
		rawElo: number;
		/** False when no played game this month has a known eloDelta (e.g. no longer on click-tt). */
		hasEloData: boolean;
	};

	function groupByMonth(games: PlayerGame[]): MonthGroup[] {
		const map = new Map<string, PlayerGame[]>();
		for (const game of games) {
			const date = game.playedAt ? new Date(game.playedAt) : null;
			const key = date
				? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
				: 'unknown';
			if (!map.has(key)) map.set(key, []);
			map.get(key)!.push(game);
		}
		return Array.from(map.entries()).map(([key, monthGames]) => {
			const [year, month] = key.split('-');
			const label = new Date(Number(year), Number(month) - 1).toLocaleDateString($locale ?? 'de', {
				month: 'long',
				year: 'numeric'
			});
			const played = monthGames.filter((g) => g.result !== 'NOT_PLAYED');
			const wins = played.filter(
				(g) =>
					(g.result === 'HOME' && g.playerSide === 'home') ||
					(g.result === 'AWAY' && g.playerSide === 'away')
			).length;
			const totalElo = played.reduce((sum, g) => sum + Math.round(g.eloDelta ?? 0), 0);
			const rawElo = played.reduce((sum, g) => sum + (g.eloDelta ?? 0), 0);
			const hasEloData = played.some((g) => g.eloDelta != null);
			return { key, label, games: monthGames, wins, totalElo, rawElo, hasEloData };
		});
	}

	let matches = $state<PlayerGame[] | null>(null);
	let selectedSeason = $state<string>('');
	let expandedMonths = $state<string[]>([]);

	// Resolve the streamed history; reset when navigating to another player.
	$effect(() => {
		const pending = data.streamed.matches;
		matches = null;
		selectedSeason = '';
		pending.then((m) => {
			matches = m;
		});
	});

	// Seasons the player actually has games in, newest first, with an "undated" bucket if needed.
	const seasonOptions = $derived.by(() => {
		if (!matches) return [];
		const names = new Set<string>();
		let hasUndated = false;
		for (const game of matches) {
			const name = seasonNameOf(game.playedAt);
			if (name) names.add(name);
			else hasUndated = true;
		}
		const options = Array.from(names)
			.sort((a, b) => b.localeCompare(a))
			.map((name) => ({ value: name, label: name }));
		if (hasUndated) options.push({ value: UNDATED, label: $_('player.undated_games') });
		return options;
	});

	// Default to the newest available season once options are known (or after a player change).
	// Computes the initial expandedMonths inline (rather than leaving it to the groups-watching
	// effect below) so the very first render of the accordion already has the right value —
	// otherwise it mounts collapsed for one tick and the animated open never fully plays,
	// leaving the content visually collapsed even though the chevron flips. Only the current
	// (newest) season auto-expands its most recent month; past seasons default to all-collapsed.
	$effect(() => {
		if (seasonOptions.length > 0 && !seasonOptions.some((o) => o.value === selectedSeason)) {
			const newSeason = seasonOptions[0].value;
			selectedSeason = newSeason;
			const games = (matches ?? []).filter(
				(g) => (seasonNameOf(g.playedAt) ?? UNDATED) === newSeason
			);
			const newGroups = groupByMonth(games);
			expandedMonths = newGroups.length > 0 ? [newGroups[0].key] : [];
		}
	});

	const visibleGames = $derived(
		(matches ?? []).filter((g) => (seasonNameOf(g.playedAt) ?? UNDATED) === selectedSeason)
	);

	const groups = $derived(groupByMonth(visibleGames));

	// Expand the most recent month whenever the visible season changes — but only for the
	// current (newest) season. Past seasons default to all-collapsed.
	$effect(() => {
		const isCurrentSeason = seasonOptions.length > 0 && selectedSeason === seasonOptions[0].value;
		expandedMonths = isCurrentSeason && groups.length > 0 ? [groups[0].key] : [];
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />
		<div class="flex items-start justify-between gap-4">
			<div class="min-w-0">
				<PageTitle class="mb-1">{$_('player.games_title')}</PageTitle>
				<p class="text-sm text-muted-foreground">{formatName(data.player.fullName)}</p>
			</div>
			{#if activeTab === 'history' && seasonOptions.length > 0}
				<SeasonSelect bind:value={selectedSeason} seasons={seasonOptions} />
			{/if}
		</div>
	</header>

	<Tabs.Root value={activeTab} onValueChange={setTab}>
		<Tabs.List class="w-full">
			<Tabs.Trigger value="history" class="flex-1">{$_('player.tab_history')}</Tabs.Trigger>
			<Tabs.Trigger value="upcoming" class="flex-1">{$_('player.tab_upcoming')}</Tabs.Trigger>
		</Tabs.List>

		<Tabs.Content value="history" class="mt-4">
	{#if matches === null}
		<div class="space-y-3">
			{#each [1, 2, 3] as n (n)}
				<Skeleton class="h-16 w-full rounded-2xl" />
			{/each}
		</div>
	{:else if groups.length === 0}
		<p class="py-12 text-center text-sm text-muted-foreground">{$_('player.no_games')}</p>
	{:else}
		<Accordion.Root type="multiple" bind:value={expandedMonths} class="space-y-3">
			{#each groups as group (group.key)}
				{@const expanded = expandedMonths.includes(group.key)}
				{@const isPositive = group.totalElo > 0 || (group.totalElo === 0 && group.rawElo > 0)}
				{@const isNegative = group.totalElo < 0 || (group.totalElo === 0 && group.rawElo < 0)}
				{@const eloLabel = isPositive
					? `+${Math.abs(group.totalElo)}`
					: isNegative
						? `-${Math.abs(group.totalElo)}`
						: '±0'}
				<Accordion.Item
					value={group.key}
					class="overflow-hidden rounded-2xl border border-border/50 bg-card not-last:border-b-0"
				>
					<Accordion.Trigger
						class="w-full items-center rounded-none px-4 py-4 transition-colors hover:bg-accent hover:no-underline [&_[data-slot=accordion-trigger-icon]]:hidden"
					>
						<div class="min-w-0 flex-1 text-left">
							<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
								{$_('player.games_count', { values: { count: group.games.length } })}
							</p>
							<p class="mt-1 text-xl font-semibold tracking-tight">{group.label}</p>
						</div>
						<div class="flex shrink-0 items-center gap-3">
							{#if group.hasEloData}
								<span
									class="flex items-center gap-1 rounded-md border px-2 py-1 font-mono text-2xs font-semibold tabular-nums {isPositive
										? 'border-win text-win'
										: isNegative
											? 'border-loss text-loss'
											: 'border-current text-muted-foreground'}"
								>
									{eloLabel} ELO
									{#if isPositive}
										<TrendUpIcon size="10" weight="bold" />
									{:else if isNegative}
										<TrendDownIcon size="10" weight="bold" />
									{:else}
										<ArrowRightIcon size="10" weight="bold" />
									{/if}
								</span>
							{/if}
							{#if expanded}
								<CaretUpIcon size="20" class="text-muted-foreground" />
							{:else}
								<CaretDownIcon size="20" class="text-muted-foreground" />
							{/if}
						</div>
					</Accordion.Trigger>
					<Accordion.Content class="p-0">
						<div class="space-y-3 bg-background/50 px-4 py-4">
							{#each group.games as game (game.gameId)}
								<GameCard mode="player" {game} />
							{/each}
						</div>
					</Accordion.Content>
				</Accordion.Item>
			{/each}
		</Accordion.Root>
	{/if}
		</Tabs.Content>

		<Tabs.Content value="upcoming" class="mt-4">
			{#await data.streamed.upcoming}
				<div class="space-y-3">
					{#each [1, 2, 3] as n (n)}
						<Skeleton class="h-16 w-full rounded-2xl" />
					{/each}
				</div>
			{:then upcoming}
				{#if !upcoming || upcoming.matches.length === 0}
					<p class="py-12 text-center text-sm text-muted-foreground">
						{$_('player.no_upcoming')}
					</p>
				{:else}
					<div class="space-y-3">
						{#each upcoming.matches as match (match.id)}
							<MatchCard
								{match}
								perspectiveTeam={upcoming.teamName}
								href="/players/{data.player.id}/preview/{match.id}"
							/>
						{/each}
					</div>
				{/if}
			{/await}
		</Tabs.Content>
	</Tabs.Root>
</div>
