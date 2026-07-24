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

	// Class changes — one event per consecutive pair in the history where the
	// classification actually changed, each sorted by its own effective date, not just
	// the single most recent transition.
	for (let i = 0; i < history.length - 1; i++) {
		const current = history[i];
		const previous = history[i + 1];
		if (current.classification === previous.classification) continue;
		const direction =
			classificationRank(current.classification) > classificationRank(previous.classification)
				? 'UP'
				: 'DOWN';
		events.push({
			key: `${fav.id}-class-${current.effectiveDate}`,
			entityType: 'player',
			entityName: formatName(fav.targetName),
			entityHref: `/players/${fav.targetId}`,
			item: {
				kind: 'class_change',
				direction,
				from: previous.classification,
				to: current.classification,
				effectiveDate: current.effectiveDate
			},
			sortKey: current.effectiveDate
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

// ── Backend-aggregated preview feed (home dashboard only) ──────────────────────
//
// The full per-entity resolution above (resolveFeed + fetchers) stays in use for the standalone
// /feed page, which is low-traffic and genuinely needs the complete picture. The home dashboard
// only ever shows a handful of items, so it calls a dedicated backend endpoint that computes the
// true most-recent events across all followed entities server-side (see FeedService.kt) instead
// of fetching every followed entity's entire match + classification history client-side just to
// throw most of it away.

/** Flat wire shape from `GET /follows/feed` — mirrors the backend's `FeedEventResponse`. */
export type FeedEvent = {
	key: string;
	entityType: 'player' | 'team' | 'division_group';
	entityName: string;
	entityHref: string;
	kind: FeedItem['kind'];
	sortKey: string;
	result?: 'WIN' | 'LOSS' | 'DRAW';
	opponentTeam?: string;
	opponent?: string;
	matchScore?: string;
	score?: string;
	playedAt?: string | null;
	direction?: 'UP' | 'DOWN';
	fromClass?: string;
	toClass?: string;
	effectiveDate?: string;
	homeTeam?: string;
	awayTeam?: string;
};

/** Adapts a flat backend feed event into the `ResolvedEvent` shape `FeedItemCard` renders. */
export function toResolvedEvent(e: FeedEvent): ResolvedEvent {
	const entityName = e.entityType === 'player' ? formatName(e.entityName) : e.entityName;
	const playedAt = e.playedAt ?? null;

	let item: FeedItem;
	switch (e.kind) {
		case 'player_match':
			item = {
				kind: 'player_match',
				result: e.result!,
				opponentTeam: e.opponentTeam!,
				matchScore: e.matchScore!,
				playedAt
			};
			break;
		case 'class_change':
			item = {
				kind: 'class_change',
				direction: e.direction!,
				from: e.fromClass!,
				to: e.toClass!,
				effectiveDate: e.effectiveDate!
			};
			break;
		case 'team_match':
			item = { kind: 'team_match', result: e.result!, opponent: e.opponent!, score: e.score!, playedAt };
			break;
		case 'group_match':
			item = { kind: 'group_match', homeTeam: e.homeTeam!, awayTeam: e.awayTeam!, score: e.score!, playedAt };
			break;
		case 'upcoming_match':
			item = { kind: 'upcoming_match', homeTeam: e.homeTeam!, awayTeam: e.awayTeam!, playedAt };
			break;
	}

	return {
		key: e.key,
		entityType: e.entityType,
		entityName,
		entityHref: e.entityHref,
		item,
		sortKey: e.sortKey
	};
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
