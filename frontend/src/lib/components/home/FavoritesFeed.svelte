<script lang="ts">
	import type { FavoriteResponse } from '$lib/api';
	import type { FeedItem } from './feed-types';
	import { api } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import FeedItemCard from '$lib/components/home/FeedItemCard.svelte';
	import { Rss } from 'phosphor-svelte';

	interface Props {
		favorites: Promise<FavoriteResponse[]>;
	}

	let { favorites }: Props = $props();

	// ── Class rank helpers ────────────────────────────────────────────────────────
	// Klass format: "D1"–"D5", "C6"–"C10", "B11"–"B15", "A16"–"A22"
	// The numeric suffix directly encodes rank — higher = better.
	function klassRank(klass: string): number {
		return parseInt(klass.slice(1)) || 0;
	}

	// ── Feed item resolution ──────────────────────────────────────────────────────

	function resolvePlayerItem(fav: FavoriteResponse): Promise<FeedItem | null> {
		// Run match history + class history in parallel, return the most recent event
		const matchP = api.players
			.matches(fav.targetId)
			.then((games): FeedItem | null => {
				// Dedup by matchId — take the first occurrence per match (newest-first list)
				const seen = new Set<string>();
				const latestMatch = games.find((g) => {
					if (g.status === 'SCHEDULED' || seen.has(g.matchId)) return false;
					seen.add(g.matchId);
					return true;
				});
				if (!latestMatch) return null;

				const isHome = latestMatch.playerSide === 'home';
				const myScore = isHome ? latestMatch.homeScore : latestMatch.awayScore;
				const oppScore = isHome ? latestMatch.awayScore : latestMatch.homeScore;
				const result =
					myScore != null && oppScore != null
						? myScore > oppScore
							? 'WIN'
							: myScore < oppScore
								? 'LOSS'
								: 'DRAW'
						: 'DRAW';
				const opponentTeam = isHome ? latestMatch.awayTeam : latestMatch.homeTeam;
				const matchScore =
					myScore != null && oppScore != null ? `${myScore}–${oppScore}` : '?–?';

				return {
					kind: 'player_match',
					result,
					opponentTeam,
					matchScore,
					playedAt: latestMatch.playedAt
				} satisfies FeedItem;
			})
			.catch(() => null);

		const classP = api.players
			.classHistory(fav.targetId)
			.then((history): FeedItem | null => {
				if (history.length < 2) return null;
				const [current, previous] = history;
				if (current.klass === previous.klass) return null;
				const direction = klassRank(current.klass) > klassRank(previous.klass) ? 'UP' : 'DOWN';
				return {
					kind: 'class_change',
					direction,
					from: previous.klass,
					to: current.klass
				} satisfies FeedItem;
			})
			.catch(() => null);

		// Pick class change if it exists (season-level event takes precedence as most newsworthy),
		// otherwise fall back to latest match result.
		return Promise.all([matchP, classP]).then(([matchItem, classItem]) => classItem ?? matchItem);
	}

	function resolveTeamItem(fav: FavoriteResponse): Promise<FeedItem | null> {
		return api.teams
			.matches(fav.targetId)
			.then((matches): FeedItem | null => {
				const last = matches.find((m) => m.status !== 'SCHEDULED');
				if (!last) return null;

				const isHome = last.homeTeamId === fav.targetId;
				const myScore = isHome ? last.homeScore : last.awayScore;
				const oppScore = isHome ? last.awayScore : last.homeScore;
				const result =
					myScore != null && oppScore != null
						? myScore > oppScore
							? 'WIN'
							: myScore < oppScore
								? 'LOSS'
								: 'DRAW'
						: 'DRAW';
				const opponent = isHome ? last.awayTeam : last.homeTeam;
				const score =
					myScore != null && oppScore != null ? `${myScore}–${oppScore}` : '?–?';

				return {
					kind: 'team_match',
					result,
					opponent,
					score,
					playedAt: last.playedAt
				} satisfies FeedItem;
			})
			.catch(() => null);
	}

	function resolveGroupItem(fav: FavoriteResponse): Promise<FeedItem | null> {
		return api.groups
			.matches(fav.targetId)
			.then((matches): FeedItem | null => {
				const last = matches.find(
					(m) => m.status !== 'SCHEDULED' && m.homeScore != null && m.awayScore != null
				);
				if (!last) return null;

				return {
					kind: 'group_latest',
					homeTeam: last.homeTeam,
					awayTeam: last.awayTeam,
					score: `${last.homeScore}–${last.awayScore}`,
					playedAt: last.playedAt
				} satisfies FeedItem;
			})
			.catch(() => null);
	}

	function resolveFeedItem(fav: FavoriteResponse): Promise<FeedItem | null> {
		if (fav.targetType === 'player') return resolvePlayerItem(fav);
		if (fav.targetType === 'team') return resolveTeamItem(fav);
		return resolveGroupItem(fav);
	}

	function entityHref(fav: FavoriteResponse): string {
		if (fav.targetType === 'division_group') return `/groups/${fav.targetId}`;
		if (fav.targetType === 'team') return `/teams/${fav.targetId}`;
		return `/players/${fav.targetId}`;
	}

	function entityType(fav: FavoriteResponse): 'player' | 'team' | 'division_group' {
		if (fav.targetType === 'player') return 'player';
		if (fav.targetType === 'team') return 'team';
		return 'division_group';
	}
</script>

{#await favorites then favs}
	{#if favs.length > 0}
		{@const visible = favs.slice(0, 3)}
		<section class="space-y-3">
			<div class="flex items-center justify-between px-1">
				<SectionLabel label="Feed" icon={Rss} />
				{#if favs.length > 3}
					<a href="/account" class="text-xs font-bold tracking-widest text-muted-foreground uppercase hover:text-foreground">
						View All
					</a>
				{/if}
			</div>
			<div class="overflow-hidden rounded-xl border border-border bg-card divide-y divide-border">
				{#each visible as fav (fav.id)}
					{@const itemPromise = resolveFeedItem(fav)}
					{#await itemPromise}
						<div class="flex items-center gap-3 px-4 py-3.5">
							<Skeleton class="h-10 w-10 shrink-0 rounded-xl" />
							<div class="flex-1 space-y-1.5">
								<Skeleton class="h-3.5 w-32 rounded" />
								<Skeleton class="h-3 w-48 rounded" />
							</div>
							<Skeleton class="h-9 w-9 shrink-0 rounded-xl" />
						</div>
					{:then item}
						{#if item}
							<FeedItemCard
								entityType={entityType(fav)}
								entityName={fav.targetName}
								entityHref={entityHref(fav)}
								{item}
							/>
						{:else}
							<div class="flex items-center gap-3 px-4 py-3.5">
								<div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-muted">
								</div>
								<div class="min-w-0 flex-1">
									<p class="text-sm font-semibold">{fav.targetName}</p>
									<p class="text-xs text-muted-foreground">No recent activity</p>
								</div>
							</div>
						{/if}
					{/await}
				{/each}
			</div>
		</section>
	{/if}
{/await}
