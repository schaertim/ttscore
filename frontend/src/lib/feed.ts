import type { FavoriteResponse, Match } from '$lib/api';
import type { FeedItem } from '$lib/components/home/feed-types';
import { api } from '$lib/api';

// ── Types ─────────────────────────────────────────────────────────────────────

export type ResolvedEvent = {
	key: string;
	entityType: 'player' | 'team' | 'division_group';
	entityName: string;
	entityHref: string;
	item: FeedItem;
	sortKey: string; // ISO date string; '9999' for undated (class changes)
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function klassRank(klass: string): number {
	return parseInt(klass.slice(1)) || 0;
}

// ── Per-entity event fetchers ─────────────────────────────────────────────────

async function fetchPlayerEvents(fav: FavoriteResponse): Promise<ResolvedEvent[]> {
	const [games, history] = await Promise.all([
		api.players.matches(fav.targetId).catch(() => []),
		api.players.classHistory(fav.targetId).catch(() => [])
	]);

	const events: ResolvedEvent[] = [];

	// Class change (placed at top of feed, no specific date)
	if (history.length >= 2 && history[0].klass !== history[1].klass) {
		const direction = klassRank(history[0].klass) > klassRank(history[1].klass) ? 'UP' : 'DOWN';
		events.push({
			key: `${fav.id}-class`,
			entityType: 'player',
			entityName: fav.targetName,
			entityHref: `/players/${fav.targetId}`,
			item: { kind: 'class_change', direction, from: history[1].klass, to: history[0].klass },
			sortKey: '9999'
		});
	}

	// Group completed games by matchId, then count personal wins per team match
	const byMatch = new Map<string, typeof games>();
	for (const game of games) {
		if (game.status === 'SCHEDULED') continue;
		if (!byMatch.has(game.matchId)) byMatch.set(game.matchId, []);
		byMatch.get(game.matchId)!.push(game);
	}

	for (const [matchId, matchGames] of byMatch) {
		const first = matchGames[0];
		const myWins = matchGames.filter(
			(g) =>
				(g.result === 'HOME' && g.playerSide === 'home') ||
				(g.result === 'AWAY' && g.playerSide === 'away')
		).length;
		const oppWins = matchGames.filter(
			(g) =>
				(g.result === 'HOME' && g.playerSide === 'away') ||
				(g.result === 'AWAY' && g.playerSide === 'home')
		).length;
		const result = myWins > oppWins ? 'WIN' : myWins < oppWins ? 'LOSS' : 'DRAW';

		events.push({
			key: `${fav.id}-match-${matchId}`,
			entityType: 'player',
			entityName: fav.targetName,
			entityHref: `/matches/${matchId}`,
			item: {
				kind: 'player_match',
				result,
				opponentTeam: first.playerSide === 'home' ? first.awayTeam : first.homeTeam,
				matchScore: `${myWins}–${oppWins}`,
				playedAt: first.playedAt
			},
			sortKey: first.playedAt ?? ''
		});
	}

	return events;
}

async function fetchTeamEvents(fav: FavoriteResponse): Promise<ResolvedEvent[]> {
	const matches = await api.teams.matches(fav.targetId).catch((): Match[] => []);
	return matches
		.filter((m) => m.status !== 'SCHEDULED')
		.map((m) => {
			const isHome = m.homeTeamId === fav.targetId;
			const myScore = isHome ? m.homeScore : m.awayScore;
			const oppScore = isHome ? m.awayScore : m.homeScore;
			const result =
				myScore != null && oppScore != null
					? myScore > oppScore
						? 'WIN'
						: myScore < oppScore
							? 'LOSS'
							: 'DRAW'
					: 'DRAW';
			return {
				key: `${fav.id}-match-${m.id}`,
				entityType: 'team' as const,
				entityName: fav.targetName,
				entityHref: `/matches/${m.id}`,
				item: {
					kind: 'team_match' as const,
					result,
					opponent: isHome ? m.awayTeam : m.homeTeam,
					score: myScore != null && oppScore != null ? `${myScore}–${oppScore}` : '?–?',
					playedAt: m.playedAt
				},
				sortKey: m.playedAt ?? ''
			};
		});
}

async function fetchGroupEvents(fav: FavoriteResponse): Promise<ResolvedEvent[]> {
	const matches = await api.groups.matches(fav.targetId).catch((): Match[] => []);
	return matches
		.filter((m) => m.status !== 'SCHEDULED' && m.homeScore != null && m.awayScore != null)
		.map((m) => ({
			key: `${fav.id}-match-${m.id}`,
			entityType: 'division_group' as const,
			entityName: fav.targetName,
			entityHref: `/matches/${m.id}`,
			item: {
				kind: 'group_match' as const,
				homeTeam: m.homeTeam,
				awayTeam: m.awayTeam,
				score: `${m.homeScore}–${m.awayScore}`,
				playedAt: m.playedAt
			},
			sortKey: m.playedAt ?? ''
		}));
}

// ── Public API ────────────────────────────────────────────────────────────────

export async function resolveFeed(favorites: FavoriteResponse[]): Promise<ResolvedEvent[]> {
	if (favorites.length === 0) return [];
	const arrays = await Promise.all(
		favorites.map((fav) => {
			if (fav.targetType === 'player') return fetchPlayerEvents(fav);
			if (fav.targetType === 'team') return fetchTeamEvents(fav);
			return fetchGroupEvents(fav);
		})
	);
	return arrays.flat().sort((a, b) => b.sortKey.localeCompare(a.sortKey));
}
