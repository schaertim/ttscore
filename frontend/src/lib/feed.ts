import type { FollowResponse, Match, PlayerGame } from '$lib/api';
import type { FeedItem } from '$lib/components/home/feed-types';
import { api } from '$lib/api';
import { classificationRank, formatName } from '$lib/utils';

// ── Types ─────────────────────────────────────────────────────────────────────

export type ResolvedEvent = {
	key: string;
	entityType: 'player' | 'team' | 'division_group';
	entityName: string;
	entityHref: string;
	item: FeedItem;
	sortKey: string; // ISO date string (class changes use their reclassification date)
};

// Upcoming matches surface in the feed starting one day before throw-off.
const UPCOMING_WINDOW_MS = 24 * 60 * 60 * 1000;

function isUpcomingSoon(playedAt: string | null): boolean {
	if (!playedAt) return false;
	const t = new Date(playedAt).getTime();
	if (Number.isNaN(t)) return false;
	const now = Date.now();
	return t > now && t - now <= UPCOMING_WINDOW_MS;
}

// ── Per-entity event fetchers ─────────────────────────────────────────────────

async function fetchPlayerEvents(fav: FollowResponse): Promise<ResolvedEvent[]> {
	const [games, history] = await Promise.all([
		api.players.matches(fav.targetId).catch(() => []),
		api.players.classHistory(fav.targetId).catch(() => [])
	]);

	const events: ResolvedEvent[] = [];

	// Class change (placed at top of feed, no specific date)
	if (history.length >= 2 && history[0].classification !== history[1].classification) {
		const direction =
			classificationRank(history[0].classification) > classificationRank(history[1].classification)
				? 'UP'
				: 'DOWN';
		events.push({
			key: `${fav.id}-class`,
			entityType: 'player',
			entityName: formatName(fav.targetName),
			entityHref: `/players/${fav.targetId}`,
			item: {
				kind: 'class_change',
				direction,
				from: history[1].classification,
				to: history[0].classification,
				effectiveDate: history[0].effectiveDate
			},
			sortKey: history[0].effectiveDate
		});
	}

	// Group completed league games by matchId, then count personal wins per team match
	const byMatch = new Map<string, PlayerGame[]>();
	for (const game of games) {
		if (!game.matchId) continue;
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
			entityName: formatName(fav.targetName),
			entityHref: `/matches/${matchId}`,
			item: {
				kind: 'player_match',
				result,
				opponentTeam: (first.playerSide === 'home' ? first.awayTeam : first.homeTeam) ?? '—',
				matchScore: `${myWins}–${oppWins}`,
				playedAt: first.playedAt
			},
			sortKey: first.playedAt ?? ''
		});
	}

	return events;
}

async function fetchTeamEvents(fav: FollowResponse): Promise<ResolvedEvent[]> {
	const matches = await api.teams.matches(fav.targetId).catch((): Match[] => []);
	const events: ResolvedEvent[] = [];

	for (const m of matches) {
		// Upcoming fixture (within a day of throw-off) → a look-ahead card.
		if (m.status === 'SCHEDULED') {
			if (isUpcomingSoon(m.playedAt)) {
				events.push({
					key: `${fav.id}-upcoming-${m.id}`,
					entityType: 'team',
					entityName: fav.targetName,
					entityHref: `/matches/${m.id}`,
					item: {
						kind: 'upcoming_match',
						homeTeam: m.homeTeam,
						awayTeam: m.awayTeam,
						playedAt: m.playedAt
					},
					sortKey: m.playedAt ?? ''
				});
			}
			continue;
		}

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
		events.push({
			key: `${fav.id}-match-${m.id}`,
			entityType: 'team',
			entityName: fav.targetName,
			entityHref: `/matches/${m.id}`,
			item: {
				kind: 'team_match',
				result,
				opponent: isHome ? m.awayTeam : m.homeTeam,
				score: myScore != null && oppScore != null ? `${myScore}–${oppScore}` : '?–?',
				playedAt: m.playedAt
			},
			sortKey: m.playedAt ?? ''
		});
	}

	return events;
}

async function fetchGroupEvents(fav: FollowResponse): Promise<ResolvedEvent[]> {
	const matches = await api.groups.matches(fav.targetId).catch((): Match[] => []);
	const events: ResolvedEvent[] = [];

	for (const m of matches) {
		if (m.status === 'SCHEDULED') {
			if (isUpcomingSoon(m.playedAt)) {
				events.push({
					key: `${fav.id}-upcoming-${m.id}`,
					entityType: 'division_group',
					entityName: fav.targetName,
					entityHref: `/matches/${m.id}`,
					item: {
						kind: 'upcoming_match',
						homeTeam: m.homeTeam,
						awayTeam: m.awayTeam,
						playedAt: m.playedAt
					},
					sortKey: m.playedAt ?? ''
				});
			}
			continue;
		}

		if (m.homeScore == null || m.awayScore == null) continue;
		events.push({
			key: `${fav.id}-match-${m.id}`,
			entityType: 'division_group',
			entityName: fav.targetName,
			entityHref: `/matches/${m.id}`,
			item: {
				kind: 'group_match',
				homeTeam: m.homeTeam,
				awayTeam: m.awayTeam,
				score: `${m.homeScore}–${m.awayScore}`,
				playedAt: m.playedAt
			},
			sortKey: m.playedAt ?? ''
		});
	}

	return events;
}

// ── Public API ────────────────────────────────────────────────────────────────

export async function resolveFeed(follows: FollowResponse[]): Promise<ResolvedEvent[]> {
	if (follows.length === 0) return [];
	const arrays = await Promise.all(
		follows.map((fav) => {
			if (fav.targetType === 'player') return fetchPlayerEvents(fav);
			if (fav.targetType === 'team') return fetchTeamEvents(fav);
			return fetchGroupEvents(fav);
		})
	);
	return arrays.flat().sort((a, b) => b.sortKey.localeCompare(a.sortKey));
}
