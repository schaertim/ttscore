<script lang="ts">
	import type { PageData } from './$types';
	import type { Match } from '$lib/api';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import FollowButton from '$lib/components/FollowButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import StandingsTable from '$lib/components/group/StandingsTable.svelte';
	import ScheduledMatchCard from '$lib/components/group/ScheduledMatchCard.svelte';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { page } from '$app/state';
	import { replaceState } from '$app/navigation';
	import { _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();

	// Persist the active tab in the URL (?tab=…) so it survives reloads and back navigation.
	const TABS = ['standings', 'results', 'schedule'];
	const activeTab = $derived(
		TABS.includes(page.url.searchParams.get('tab') ?? '')
			? page.url.searchParams.get('tab')!
			: 'standings'
	);

	function setTab(value: string) {
		const url = new URL(page.url);
		if (value === 'standings') url.searchParams.delete('tab');
		else url.searchParams.set('tab', value);
		replaceState(url, page.state);
	}

	// Synced in an effect (not just initialised) so client-side navigation between
	// groups — which reuses this component — picks up the new group's follow state.
	let following = $state(false);
	let followId = $state<string | null>(null);
	let notify = $state(false);

	$effect.pre(() => {
		following = data.following;
		followId = data.followId;
		notify = data.notify;
	});

	const completedMatches = $derived(
		data.matches
			.filter((m: Match) => m.status === 'COMPLETED')
			.sort((a: Match, b: Match) =>
				(b.round ?? '0').localeCompare(a.round ?? '0', undefined, { numeric: true })
			)
	);

	const scheduledMatches = $derived(
		data.matches
			.filter((m: Match) => m.status === 'SCHEDULED')
			.sort((a: Match, b: Match) =>
				(a.round ?? '0').localeCompare(b.round ?? '0', undefined, { numeric: true })
			)
	);

	const homeWinPct = $derived.by(() => {
		if (completedMatches.length === 0) return null;
		const homeGames = completedMatches.reduce((s: number, m: Match) => s + (m.homeScore ?? 0), 0);
		const awayGames = completedMatches.reduce((s: number, m: Match) => s + (m.awayScore ?? 0), 0);
		const total = homeGames + awayGames;
		if (total === 0) return null;
		return `${Math.round((homeGames / total) * 100)}%`;
	});

	const drawPct = $derived.by(() => {
		if (completedMatches.length === 0) return null;
		const draws = completedMatches.filter((m: Match) => m.homeScore === m.awayScore).length;
		return `${Math.round((draws / completedMatches.length) * 100)}%`;
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<div class="flex items-center justify-between">
			<BackButton />
			<div class="flex items-center">
				<FollowButton
					bind:following
					bind:followId
					bind:notify
					targetType="division_group"
					targetId={data.group.id}
					authenticated={!!data.user}
				/>
				<NotifyButton {following} {followId} bind:notify authenticated={!!data.user} />
			</div>
		</div>
		<div class="min-w-0">
			<PageTitle class="mb-1">{data.group.name}</PageTitle>
			<div class="flex items-center gap-1.5 text-sm text-muted-foreground">
				<span>{data.group.season}</span>
				{#if data.group.totalRounds > 0}
					<Separator
						orientation="vertical"
						class="bg-muted-foreground/40 data-[orientation=vertical]:h-3.5"
					/>
					<span>{$_('leagues.round', {
							values: { played: data.group.roundsPlayed, total: data.group.totalRounds }
						})}</span
					>
				{/if}
			</div>
		</div>
	</header>

	<Tabs.Root value={activeTab} onValueChange={setTab}>
		<Tabs.List class="w-full">
			<Tabs.Trigger value="standings" class="flex-1">{$_('group.standings')}</Tabs.Trigger>
			<Tabs.Trigger value="results" class="flex-1">{$_('group.results')}</Tabs.Trigger>
			<Tabs.Trigger value="schedule" class="flex-1">{$_('group.schedule')}</Tabs.Trigger>
		</Tabs.List>

		<Tabs.Content value="standings" class="mt-4 space-y-3">
			<StandingsTable standings={data.standings} group={data.group} />

			{#if completedMatches.length > 0}
				<div class="grid grid-cols-2 gap-3 pt-1">
					<StatTile label={$_('group.home_advantage')} value={homeWinPct} />
					<StatTile label={$_('group.draw_rate')} value={drawPct} />
				</div>
			{/if}
		</Tabs.Content>

		<Tabs.Content value="results" class="mt-4 space-y-3">
			{#if completedMatches.length === 0}
				<p class="py-12 text-center text-sm text-muted-foreground">{$_('group.no_results')}</p>
			{:else}
				{#each completedMatches as match (match.id)}
					<MatchCard {match} />
				{/each}
			{/if}
		</Tabs.Content>

		<Tabs.Content value="schedule" class="mt-4 space-y-3">
			{#if scheduledMatches.length === 0}
				<p class="py-12 text-center text-sm text-muted-foreground">{$_('group.no_schedule')}</p>
			{:else}
				{#each scheduledMatches as match (match.id)}
					<ScheduledMatchCard {match} />
				{/each}
			{/if}
		</Tabs.Content>
	</Tabs.Root>
</div>
