<script lang="ts">
	import { _ } from 'svelte-i18n';
	import { api, type HeadToHead, type H2HGame, type PlayerGame, type EloEntry } from '$lib/api';
	import GameCard from '$lib/components/GameCard.svelte';
	import EloChart from '$lib/components/EloChart.svelte';
	import * as Drawer from '$lib/components/ui/drawer/index.js';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import PlayerRadar from './PlayerRadar.svelte';
	import PaywallTeaser from '$lib/components/PaywallTeaser.svelte';
	import { classColorVar, formatName, formatShortName } from '$lib/utils';
	import { TargetIcon, ClockCounterClockwiseIcon, ChartLineIcon } from 'phosphor-svelte';

	interface Props {
		open: boolean;
		/** Always the signed-in user's player — shown on the left. */
		homePlayerId: string;
		/** The opponent to compare against. Changing this triggers a re-fetch. */
		opponentId: string | null;
		/** H2H is a Pro feature — non-Pro users see a paywall instead of the data. */
		isPro: boolean;
	}

	let { open = $bindable(), homePlayerId, opponentId, isPro }: Props = $props();

	let data = $state<HeadToHead | null>(null);
	let loading = $state(false);
	let error = $state(false);
	// ELO histories load independently of the H2H payload; failures leave them empty
	// rather than breaking the whole drawer.
	let eloA = $state<EloEntry[]>([]);
	let eloB = $state<EloEntry[]>([]);

	let loadedKey = $state('');
	$effect(() => {
		if (!open || !opponentId || !isPro) return;
		const key = `${homePlayerId}:${opponentId}`;
		if (key === loadedKey) return;
		loadedKey = key;
		loading = true;
		error = false;
		data = null;
		eloA = [];
		eloB = [];
		const opp = opponentId;
		api.players
			.headToHead(homePlayerId, opp)
			.then((res) => (data = res))
			.catch(() => (error = true))
			.finally(() => (loading = false));
		api.players
			.elo(homePlayerId)
			.then((res) => (eloA = res))
			.catch(() => {});
		api.players
			.elo(opp)
			.then((res) => (eloB = res))
			.catch(() => {});
	});

	// Home player is always playerA (left). Opponent is always playerB (right).
	const clsA = $derived(data?.playerA.liveClassification ?? data?.playerA.classification ?? null);
	const clsB = $derived(data?.playerB.liveClassification ?? data?.playerB.classification ?? null);

	// "You" gets the vivid class colour; the opponent gets a faded version of the same
	// hue — matching how ClassBadge tints its background (pure hue, blended with
	// transparent rather than desaturated toward grey). The contrast alone conveys who
	// is who, so the charts need no legend.
	const youColor = $derived(classColorVar(clsA));
	const colors = $derived<[string, string]>([
		youColor,
		`color-mix(in srgb, ${youColor} 40%, transparent)`
	]);

	const radarPlayers = $derived(
		data
			? [
					{ stats: data.statsA, label: formatName(data.playerA.fullName), color: colors[0] },
					{ stats: data.statsB, label: formatName(data.playerB.fullName), color: colors[1] }
				]
			: []
	);

	const hasElo = $derived(eloA.length >= 2 || eloB.length >= 2);

	const eloSeries = $derived(
		data
			? [
					{ label: formatName(data.playerA.fullName), color: colors[0], history: eloA },
					{ label: formatName(data.playerB.fullName), color: colors[1], history: eloB }
				]
			: []
	);

	const setsRecord = $derived.by(() => {
		if (!data) return null;
		let left = 0,
			right = 0;
		for (const g of data.games) {
			left += g.aSets ?? 0;
			right += g.bSets ?? 0;
		}
		return { left, right };
	});

	const pointsRecord = $derived.by(() => {
		if (!data) return null;
		let left = 0,
			right = 0;
		for (const g of data.games) {
			for (const s of g.sets) {
				left += s.homePoints;
				right += s.awayPoints;
			}
		}
		return { left, right };
	});

	// Green when this side leads, red when it trails, muted when tied — matching the
	// win/loss coloring used across the app.
	function sideTone(value: number, other: number): 'win' | 'loss' | 'neutral' {
		if (value > other) return 'win';
		if (value < other) return 'loss';
		return 'neutral';
	}

	// Map each H2H game to a PlayerGame from player A's ("you") perspective so we can
	// reuse GameCard. Sets are oriented to A (homePoints = A), so playerSide is 'home'.
	const encounterGames = $derived.by<PlayerGame[]>(() => {
		const d = data;
		if (!d) return [];
		return d.games.map((g: H2HGame) => ({
			matchId: g.matchId,
			gameId: g.gameId,
			playedAt: g.playedAt,
			homeTeam: null,
			awayTeam: null,
			homeScore: null,
			awayScore: null,
			round: null,
			status: null,
			competitionName: g.competitionName,
			playerSide: 'home',
			opponentId: d.playerB.id,
			opponentName: d.playerB.fullName,
			opponentClassification: clsB,
			homeSets: g.aSets,
			awaySets: g.bSets,
			result: g.aWon ? 'HOME' : 'AWAY',
			eloDelta: null,
			eloDeltaProvisional: false,
			sets: g.sets
		}));
	});
</script>

<Drawer.Root bind:open>
	<Drawer.Content class="max-h-[88vh]">
		{#if !isPro}
			<div class="p-5">
				<PaywallTeaser title={$_('pro.h2h_title')} description={$_('pro.h2h_desc')} />
			</div>
		{:else if loading}
			<div class="space-y-4 p-5">
				<Skeleton class="h-10 w-full rounded-lg" />
				<Skeleton class="h-16 w-full rounded-lg" />
				<Skeleton class="aspect-square max-h-[240px] w-full rounded-lg" />
			</div>
		{:else if error || !data}
			<div class="p-10 text-center text-sm text-muted-foreground">
				{$_('player.no_matches')}
			</div>
		{:else}
			<div class="min-h-0 flex-1 overflow-y-auto">
				<!-- Versus header -->
				<Drawer.Header class="border-b px-5 pt-2 pb-5 text-left">
					<div class="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
						<div class="min-w-0 text-left">
							<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
								{$_('h2h.you')}
							</p>
							<p class="mt-1 truncate text-lg font-semibold">
								{formatShortName(data.playerA.fullName)}
							</p>
							<div class="mt-1"><ClassBadge classification={clsA} /></div>
						</div>

						<div class="flex flex-col items-center gap-0.5 px-3">
							<div class="flex items-baseline gap-1.5 leading-none">
								<span class="font-mono text-3xl font-black text-win tabular-nums">{data.record.aWins}</span>
								<span class="text-3xl font-normal text-muted-foreground/40">–</span>
								<span class="font-mono text-3xl font-black text-loss tabular-nums">{data.record.bWins}</span>
							</div>
							<p class="text-xs text-muted-foreground">
								{data.record.games}
								{$_('h2h.duels')}
							</p>
						</div>

						<div class="min-w-0 text-right">
							<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
								{$_('h2h.opponent')}
							</p>
							<p class="mt-1 truncate text-lg font-semibold">
								{formatShortName(data.playerB.fullName)}
							</p>
							<div class="mt-1 flex justify-end"><ClassBadge classification={clsB} /></div>
						</div>
					</div>
				</Drawer.Header>

				<div class="space-y-5 px-5 pt-4 pb-8">
					<!-- Sets & points record -->
					{#if setsRecord && (setsRecord.left > 0 || setsRecord.right > 0)}
						<section class="grid grid-cols-2 gap-3">
							<StatTile label={$_('h2h.sets')} labelPosition="bottom" align="center">
								<ScoreLine
									segments={[
										{ value: setsRecord.left, tone: sideTone(setsRecord.left, setsRecord.right) },
										{ value: setsRecord.right, tone: sideTone(setsRecord.right, setsRecord.left) }
									]}
								/>
							</StatTile>
							{#if pointsRecord && (pointsRecord.left > 0 || pointsRecord.right > 0)}
								<StatTile label={$_('h2h.points')} labelPosition="bottom" align="center">
									<ScoreLine
										segments={[
											{
												value: pointsRecord.left,
												tone: sideTone(pointsRecord.left, pointsRecord.right)
											},
											{
												value: pointsRecord.right,
												tone: sideTone(pointsRecord.right, pointsRecord.left)
											}
										]}
									/>
								</StatTile>
							{/if}
						</section>
					{/if}

					<div class={hasElo ? 'grid gap-5 md:grid-cols-2 md:items-stretch md:gap-3' : ''}>
						<!-- ELO history -->
						{#if hasElo}
							<section class="flex flex-col space-y-2">
								<SectionLabel label={$_('player.elo_history')} icon={ChartLineIcon} />
								<Card.Root class="flex-1 p-4">
									<div class="h-full min-h-52">
										<EloChart series={eloSeries} />
									</div>
								</Card.Root>
							</section>
						{/if}

						<!-- Radar overlay -->
						<section class="flex flex-col space-y-2">
							<SectionLabel label={$_('h2h.strengths')} icon={TargetIcon} />
							<Card.Root class="flex-1 justify-center p-4">
								<div>
									<PlayerRadar players={radarPlayers} />
								</div>
							</Card.Root>
						</section>
					</div>

					<!-- Recent encounters -->
					{#if data.games.length > 0}
						<section class="space-y-2">
							<SectionLabel label={$_('h2h.recent_encounters')} icon={ClockCounterClockwiseIcon} />
							<div class="space-y-2">
								{#each encounterGames.slice(0, 6) as game (game.gameId)}
									<GameCard mode="player" {game} />
								{/each}
							</div>
						</section>
					{/if}
				</div>
			</div>
		{/if}
	</Drawer.Content>
</Drawer.Root>
