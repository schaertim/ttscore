<script lang="ts">
	import type { PageData } from './$types';
	import type { PlayerGame } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import SeasonSelect from '$lib/components/SeasonSelect.svelte';
	import MonthAccordion from '$lib/components/player/MonthAccordion.svelte';
	import { groupByMonth, UNDATED } from '$lib/components/player/monthGroups';
	import { _ } from 'svelte-i18n';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { formatName, seasonNameOf } from '$lib/utils';

	let { data }: { data: PageData } = $props();

	// Persist the active tab in the URL (?tab=…) so it survives reloads and back navigation.
	// We use `goto(..., { replaceState: true })` rather than the standalone `replaceState()`
	// from `$app/navigation`: that helper writes a stale URL into the history entry's state
	// (it captures `page.url` *before* updating it), which then misdirects SvelteKit's
	// popstate handler on back-navigation — it prefers that stale stored URL over the
	// browser's actual (correct) address bar URL. A real (if shallow) `goto` navigation
	// doesn't have this problem. The actual selection still lives in `$state` (seeded from
	// the URL on mount and resynced below), since `goto`'s reactive `page.url` update happens
	// too late in the render cycle to drive the tab UI directly.
	const TABS = ['history', 'upcoming'];
	function tabFromUrl() {
		const t = page.url.searchParams.get('tab');
		return TABS.includes(t ?? '') ? t! : 'history';
	}
	let activeTab = $state(tabFromUrl());

	function setTab(value: string) {
		activeTab = value;
		const url = new URL(page.url);
		if (value === 'history') url.searchParams.delete('tab');
		else url.searchParams.set('tab', value);
		goto(url, { replaceState: true, noScroll: true, keepFocus: true });
	}

	let matches = $state<PlayerGame[] | null>(null);
	let selectedSeason = $state<string>('');
	let expandedMonths = $state<string[]>([]);

	// Resolve the streamed history; reset when navigating to another player.
	$effect(() => {
		const pending = data.streamed.matches;
		matches = null;
		selectedSeason = '';
		activeTab = tabFromUrl();
		pending.then((m) => {
			matches = m;
		});
	});

	// Seasons the player actually has games in, newest first, with an "undated" bucket if needed.
	const seasonOptions = $derived.by(() => {
		if (!matches) return [];
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- local temp, discarded after the computation
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
				<MonthAccordion {groups} bind:expandedMonths />
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
