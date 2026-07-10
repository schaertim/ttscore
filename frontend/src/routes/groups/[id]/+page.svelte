<script lang="ts">
	import type { PageData } from './$types';
	import type { Match } from '$lib/api';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import * as Table from '$lib/components/ui/table/index.js';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import StatCard from '$lib/components/StatCard.svelte';
	import FollowButton from '$lib/components/FollowButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import { page } from '$app/state';
	import { replaceState } from '$app/navigation';
	import { _, locale } from 'svelte-i18n';

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
	let following = $state(false);
	let followId = $state<string | null>(null);
	let notify = $state(false);

	$effect.pre(() => {
		following = data.following;
		followId = data.followId;
		notify = data.notify;
	});

	const sorted = $derived([...data.standings].sort((a, b) => a.position - b.position));

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

	function zone(pos: number): 'promotion' | 'relegation' | null {
		const { promotionSpots, relegationSpots } = data.group;
		if (promotionSpots && pos <= promotionSpots) return 'promotion';
		if (relegationSpots && pos > sorted.length - relegationSpots) return 'relegation';
		return null;
	}

	function diff(won: number, lost: number): string {
		const d = won - lost;
		return d > 0 ? `+${d}` : `${d}`;
	}

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

	function formatDate(dateStr: string | null): string {
		if (!dateStr) return $_('group.tbd');
		return new Date(dateStr).toLocaleDateString($locale ?? 'de', {
			day: '2-digit',
			month: '2-digit',
			year: '2-digit'
		});
	}
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<div class="flex items-center justify-between">
			<BackButton class="" />
			<div class="flex items-center">
				<FollowButton bind:following bind:followId bind:notify targetType="division_group" targetId={data.group.id} authenticated={!!data.user} />
				<NotifyButton {following} {followId} bind:notify authenticated={!!data.user} />
			</div>
		</div>
		<div class="min-w-0">
			<PageTitle class="mb-1">{data.group.name}</PageTitle>
			<p class="text-sm text-muted-foreground">
				{data.group.season}
				{#if data.group.totalRounds > 0}
					· <span class="font-semibold">{$_('leagues.round', { values: { played: data.group.roundsPlayed, total: data.group.totalRounds } })}</span>
				{/if}
			</p>
		</div>
	</header>

<Tabs.Root value={activeTab} onValueChange={setTab}>
	<Tabs.List class="w-full">
		<Tabs.Trigger value="standings" class="flex-1">{$_("group.standings")}</Tabs.Trigger>
		<Tabs.Trigger value="results" class="flex-1">{$_("group.results")}</Tabs.Trigger>
		<Tabs.Trigger value="schedule" class="flex-1">{$_("group.schedule")}</Tabs.Trigger>
	</Tabs.List>

	<Tabs.Content value="standings" class="mt-4 space-y-3">
		<div class="overflow-hidden rounded-xl border border-border">
			<Table.Root>
				<Table.Header>
					<Table.Row class="border-border hover:bg-transparent">
						<Table.Head class="w-8 pl-4 text-xs">{$_("group.pos")}</Table.Head>
						<Table.Head class="text-xs">{$_("group.team")}</Table.Head>
						<Table.Head class="w-10 text-center text-xs">{$_("group.played")}</Table.Head>
						<Table.Head class="w-10 text-center text-xs">{$_("group.points")}</Table.Head>
						<Table.Head class="w-12 pr-4 text-right text-xs">{$_("group.diff")}</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each sorted as row (row.teamId)}
						{@const z = zone(row.position)}
						<Table.Row class="border-border">
							<Table.Cell
								class="border-l-2 pl-4 font-mono font-semibold tabular-nums
								{z === 'promotion'
									? 'border-l-win'
									: z === 'relegation'
										? 'border-l-loss'
										: 'border-l-transparent'}"
							>
								{row.position}
							</Table.Cell>

							<Table.Cell class="text-sm font-semibold">
								<a href="/teams/{row.teamId}" class="hover:underline">
									{row.team}
								</a>
							</Table.Cell>

							<Table.Cell class="text-center font-mono tabular-nums">
								{row.played}
							</Table.Cell>

							<Table.Cell class="text-center font-mono font-semibold tabular-nums">
								{row.points}
							</Table.Cell>

							<Table.Cell
								class="pr-4 text-right font-mono font-semibold tabular-nums
                       {row.gamesWon - row.gamesLost > 0
									? 'text-win'
									: row.gamesWon - row.gamesLost < 0
										? 'text-loss'
										: 'text-muted-foreground'}"
							>
								{diff(row.gamesWon, row.gamesLost)}
							</Table.Cell>
						</Table.Row>
					{/each}
				</Table.Body>
			</Table.Root>
		</div>

		{#if completedMatches.length > 0}
			<div class="grid grid-cols-2 gap-3 pt-1">
				<StatCard label={$_("group.home_advantage")} value={homeWinPct} />
				<StatCard label={$_("group.draw_rate")} value={drawPct} />
			</div>
		{/if}
	</Tabs.Content>

	<Tabs.Content value="results" class="mt-4 space-y-3">
		{#if completedMatches.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">{$_("group.no_results")}</p>
		{:else}
			{#each completedMatches as match (match.id)}
				<MatchCard {match} />
			{/each}
		{/if}
	</Tabs.Content>

	<Tabs.Content value="schedule" class="mt-4 space-y-3">
		{#if scheduledMatches.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">{$_("group.no_schedule")}</p>
		{:else}
			{#each scheduledMatches as match (match.id)}
				<div
					class="flex items-center justify-between rounded-xl border border-border
                 bg-card px-4 py-3"
				>
					<div class="flex min-w-0 flex-col gap-1">
						<span class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
							{$_('group.round_label', { values: { round: match.round } })} · {formatDate(match.playedAt)}
						</span>
						<div class="flex min-w-0 items-center gap-2 text-sm">
							<span class="truncate font-semibold">{match.homeTeam}</span>
							<span class="shrink-0 text-muted-foreground">vs</span>
							<span class="truncate font-semibold">{match.awayTeam}</span>
						</div>
					</div>
					<span
						class="ml-3 shrink-0 rounded-full border border-border px-2 py-1 text-xs text-muted-foreground"
					>
						{formatDate(match.playedAt)}
					</span>
				</div>
			{/each}
		{/if}
	</Tabs.Content>
</Tabs.Root>
</div>
