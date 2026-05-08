<script lang="ts">
	import type { FavoriteResponse, PlayerGame, Match } from '$lib/api';
	import { api } from '$lib/api';
	import { timeAgo } from '$lib/utils';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { Star } from 'phosphor-svelte';

	interface Props {
		favorites: Promise<FavoriteResponse[]>;
	}

	let { favorites }: Props = $props();

	function entityHref(fav: FavoriteResponse): string {
		if (fav.targetType === 'division_group') return `/groups/${fav.targetId}`;
		if (fav.targetType === 'team') return `/teams/${fav.targetId}`;
		return `/players/${fav.targetId}`;
	}

	function playerGameSummary(game: PlayerGame): string {
		const isHome = game.playerSide === 'home';
		const won =
			(isHome && game.result === 'HOME') || (!isHome && game.result === 'AWAY');
		const lost =
			(isHome && game.result === 'AWAY') || (!isHome && game.result === 'HOME');
		const outcome = won ? 'W' : lost ? 'L' : '–';
		const mySets = isHome ? game.homeSets : game.awaySets;
		const oppSets = isHome ? game.awaySets : game.homeSets;
		const sets = `${mySets ?? '?'}–${oppSets ?? '?'}`;
		const opp = game.opponentName ?? 'Unknown';
		const delta =
			game.eloDelta != null
				? ` · ${game.eloDelta > 0 ? '+' : ''}${Math.round(game.eloDelta)}`
				: '';
		return `${outcome} ${sets} vs ${opp}${delta}`;
	}

	function matchSummary(match: Match): string {
		if (match.homeScore != null && match.awayScore != null) {
			return `${match.homeTeam} ${match.homeScore}–${match.awayScore} ${match.awayTeam}`;
		}
		return `${match.homeTeam} vs ${match.awayTeam}`;
	}

	function loadActivity(
		fav: FavoriteResponse
	): Promise<{ summary: string; playedAt: string | null } | null> {
		if (fav.targetType === 'player') {
			return api.players
				.matches(fav.targetId)
				.then((games) => {
					const last = games.find((g) => g.status !== 'SCHEDULED');
					if (!last) return null;
					return { summary: playerGameSummary(last), playedAt: last.playedAt };
				})
				.catch(() => null);
		}
		const matchesFn =
			fav.targetType === 'team' ? api.teams.matches : api.groups.matches;
		return matchesFn(fav.targetId)
			.then((matches) => {
				const last = matches.find((m) => m.status !== 'SCHEDULED');
				if (!last) return null;
				return { summary: matchSummary(last), playedAt: last.playedAt };
			})
			.catch(() => null);
	}
</script>

{#await favorites then favs}
	{#if favs.length > 0}
		<section class="space-y-3">
			<SectionLabel label="Favorites" icon={Star} class="px-1" />
			<div class="divide-y divide-border rounded-xl border border-border bg-card overflow-hidden">
				{#each favs as fav (fav.id)}
					{@const activityPromise = loadActivity(fav)}
					<a
						href={entityHref(fav)}
						class="flex items-center justify-between gap-3 px-4 py-3 hover:bg-accent"
					>
						<div class="min-w-0">
							<p class="text-sm font-semibold truncate">{fav.targetName}</p>
							{#await activityPromise}
								<Skeleton class="mt-1 h-3 w-40 rounded" />
							{:then activity}
								{#if activity}
									<p class="text-xs text-muted-foreground truncate">
										{activity.summary}
										{#if activity.playedAt}
											<span class="ml-1">&middot; {timeAgo(activity.playedAt)}</span>
										{/if}
									</p>
								{:else}
									<p class="text-xs text-muted-foreground">No recent activity</p>
								{/if}
							{/await}
						</div>
					</a>
				{/each}
			</div>
		</section>
	{/if}
{/await}
