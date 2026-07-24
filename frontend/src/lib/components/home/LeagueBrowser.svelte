<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { Group, Season, SeasonStats, Federation } from '$lib/api';
	import { api } from '$lib/api';
	import SeasonSelect from '$lib/components/SeasonSelect.svelte';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import FederationAccordionItem from '$lib/components/home/FederationAccordionItem.svelte';
	import { TrendUpIcon, ClockIcon, TrophyIcon } from 'phosphor-svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { _ } from 'svelte-i18n';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { categorizeGroupName, naturalCompare } from '$lib/groupCategory';

	interface Props {
		seasons: Season[];
		federations: Federation[];
		/** Optional callout (e.g. sign-in / set-player banner) rendered under the header. */
		banner?: Snippet;
	}

	let { seasons, federations, banner }: Props = $props();

	// Persist the selected season in the URL (?season=…) so it survives back navigation
	// (e.g. selecting a past season, opening a group, then navigating back here).
	// We use `goto(..., { replaceState: true })` rather than the standalone `replaceState()`
	// from `$app/navigation`: that helper writes a stale URL into the history entry's state
	// (it captures `page.url` *before* updating it), which then misdirects SvelteKit's
	// popstate handler on back-navigation — it prefers that stale stored URL over the
	// browser's actual (correct) address bar URL. A real (if shallow) `goto` navigation
	// doesn't have this problem. The actual selection still lives in `$state` (seeded once
	// from `page.url` on initial mount / on remount after back-navigation), since `goto`'s
	// reactive `page.url` update happens too late in the render cycle to drive the fetch
	// effect below directly.
	const paramSeasonId = page.url.searchParams.get('season');
	// svelte-ignore state_referenced_locally
	let selectedSeasonId = $state(
		seasons.some((s) => s.id === paramSeasonId) ? paramSeasonId! : (seasons[0]?.id ?? '')
	);

	function setSelectedSeasonId(value: string) {
		selectedSeasonId = value;
		const url = new URL(page.url);
		if (value === seasons[0]?.id) url.searchParams.delete('season');
		else url.searchParams.set('season', value);
		goto(url, { replaceState: true, noScroll: true, keepFocus: true });
	}

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

	// A region with few groups doesn't need a second accordion level at all. Beyond that floor,
	// whether to actually nest isn't about the *count* — it's about whether categorising helps:
	// if every group lands in its own one-item category (nothing merged, e.g. names the categoriser
	// can't parse), nesting only adds a redundant click per group for no benefit, so we only nest
	// when it genuinely collapses the list (see groupCategory.ts for the categorisation itself).
	const MIN_GROUPS_TO_CONSIDER_CATEGORIES = 6;

	type GroupCategory = { name: string; groups: Group[] };

	const groupsByFederation = $derived(
		federations
			.map((fed) => {
				const fedGroups = groups
					.filter((g) => g.federation === fed.name)
					.sort((a, b) => naturalCompare(a.name, b.name));

				let categories: GroupCategory[] | null = null;
				if (fedGroups.length >= MIN_GROUPS_TO_CONSIDER_CATEGORIES) {
					const byCategory = new Map<string, Group[]>();
					for (const group of fedGroups) {
						const category = categorizeGroupName(group.name);
						const bucket = byCategory.get(category);
						if (bucket) bucket.push(group);
						else byCategory.set(category, [group]);
					}
					if (byCategory.size < fedGroups.length) {
						categories = Array.from(byCategory, ([name, catGroups]) => ({
							name,
							groups: catGroups
						})).sort((a, b) => naturalCompare(a.name, b.name));
					}
				}

				return { ...fed, groups: fedGroups, categories };
			})
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
			value={selectedSeasonId}
			onChange={setSelectedSeasonId}
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
					<FederationAccordionItem {fed} isExpanded={expandedFederations.includes(fed.id)} />
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
