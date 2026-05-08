<script lang="ts">
	import type { PageData } from './$types';
	import type { Match } from '$lib/api';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import * as Table from '$lib/components/ui/table/index.js';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import StatCard from '$lib/components/StatCard.svelte';
	import FavoriteButton from '$lib/components/FavoriteButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';

	let { data }: { data: PageData } = $props();
	let favorited = $state(data.favorited);
	let favoriteId = $state(data.favoriteId);
	let notifying = $state(data.notifying);
	let notifyId = $state(data.notifyId);

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
		if (!dateStr) return 'TBD';
		return new Date(dateStr).toLocaleDateString('de-CH', {
			day: '2-digit',
			month: '2-digit',
			year: '2-digit'
		});
	}
</script>

<div class="space-y-1 px-1 py-4">
	<div class="flex items-center justify-between mb-4">
		<BackButton class="mb-0" />
		{#if data.user}
			<div class="flex items-center gap-0.5">
				<FavoriteButton
					bind:favorited
					bind:favoriteId
					targetType="division_group"
					targetId={data.group.id}
				/>
				<NotifyButton
					bind:notifying
					bind:notifyId
					targetType="division_group"
					targetId={data.group.id}
				/>
			</div>
		{/if}
	</div>
	<div class="min-w-0 space-y-1">
		<h1 class="text-3xl font-extrabold tracking-tight">{data.group.name}</h1>
		<p class="text-sm text-muted-foreground">
			{data.group.season}
			{#if data.group.totalRounds > 0}
				· <span class="font-medium">Rd {data.group.roundsPlayed}/{data.group.totalRounds}</span>
			{/if}
		</p>
	</div>
</div>

<Tabs.Root value="standings">
	<Tabs.List class="w-full">
		<Tabs.Trigger value="standings" class="flex-1">Standings</Tabs.Trigger>
		<Tabs.Trigger value="results" class="flex-1">Results</Tabs.Trigger>
		<Tabs.Trigger value="schedule" class="flex-1">Schedule</Tabs.Trigger>
	</Tabs.List>

	<Tabs.Content value="standings" class="mt-4 space-y-3">
		<div class="overflow-hidden rounded-xl border border-border">
			<Table.Root>
				<Table.Header>
					<Table.Row class="border-border hover:bg-transparent">
						<Table.Head class="w-8 pl-5 text-xs">Pos</Table.Head>
						<Table.Head class="text-xs">Team</Table.Head>
						<Table.Head class="w-10 text-center text-xs">Pld</Table.Head>
						<Table.Head class="w-10 text-center text-xs">Pts</Table.Head>
						<Table.Head class="w-12 pr-4 text-right text-xs">+/-</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each sorted as row (row.teamId)}
						{@const z = zone(row.position)}
						<Table.Row class="border-border">
							<Table.Cell
								class="border-l-2 pl-4 font-bold tabular-nums
								{z === 'promotion'
									? 'border-l-win'
									: z === 'relegation'
										? 'border-l-loss'
										: 'border-l-transparent'}"
							>
								{row.position}
							</Table.Cell>

							<Table.Cell class="text-sm font-medium">
								<a href="/teams/{row.teamId}" class="hover:underline">
									{row.team}
								</a>
							</Table.Cell>

							<Table.Cell class="text-center tabular-nums">
								{row.played}
							</Table.Cell>

							<Table.Cell class="text-center font-bold tabular-nums">
								{row.points}
							</Table.Cell>

							<Table.Cell
								class="pr-4 text-right font-medium tabular-nums
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
				<StatCard label="Home Advantage" value={homeWinPct} />
				<StatCard label="Draw Rate" value={drawPct} />
			</div>
		{/if}
	</Tabs.Content>

	<Tabs.Content value="results" class="mt-4 space-y-2">
		{#if completedMatches.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">No results yet</p>
		{:else}
			{#each completedMatches as match (match.id)}
				<MatchCard {match} />
			{/each}
		{/if}
	</Tabs.Content>

	<Tabs.Content value="schedule" class="mt-4 space-y-2">
		{#if scheduledMatches.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">No upcoming matches</p>
		{:else}
			{#each scheduledMatches as match (match.id)}
				<div
					class="flex items-center justify-between rounded-xl border border-border
                 bg-card px-4 py-3"
				>
					<div class="flex min-w-0 flex-col gap-0.5">
						<span class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">
							Rd {match.round} · {formatDate(match.playedAt)}
						</span>
						<div class="flex min-w-0 items-center gap-1.5 text-sm">
							<span class="truncate font-medium">{match.homeTeam}</span>
							<span class="flex-shrink-0 text-muted-foreground">vs</span>
							<span class="truncate font-medium">{match.awayTeam}</span>
						</div>
					</div>
					<span class="ml-3 shrink-0 rounded-full border border-border px-2.5 py-0.5 text-xs text-muted-foreground">
						{formatDate(match.playedAt)}
					</span>
				</div>
			{/each}
		{/if}
	</Tabs.Content>
</Tabs.Root>
