import { PUBLIC_API_URL } from '$env/static/public';

const BASE = PUBLIC_API_URL + '/api/v1';

async function get<T>(path: string): Promise<T> {
	const res = await fetch(`${BASE}${path}`);
	if (!res.ok) throw new Error(`API error ${res.status}: ${path}`);
	return res.json();
}

/** Like {@link get}, but forwards a Supabase access token — required for Pro-gated endpoints. */
async function getAuthed<T>(path: string, accessToken: string): Promise<T> {
	const res = await fetch(`${BASE}${path}`, {
		headers: { Authorization: `Bearer ${accessToken}` }
	});
	if (!res.ok) throw new Error(`API error ${res.status}: ${path}`);
	return res.json();
}

// ── Types ────────────────────────────────────────────────────

export type Season = {
	id: string;
	name: string;
};

export type Federation = {
	id: string;
	name: string;
};

export type Group = {
	id: string;
	name: string;
	federation: string;
	season: string;
	promotionSpots: number | null;
	relegationSpots: number | null;
	teamCount: number;
	roundsPlayed: number;
	totalRounds: number;
};

export type TeamSummary = {
	id: string;
	name: string;
	groupName: string;
	position: number;
	record: string;
	points: number;
	lastResults: string[];
};

export type TeamPlayer = {
	id: string;
	fullName: string;
	licenceNr: string;
	classification: string | null;
	wins: number;
	losses: number;
};

export type Standing = {
	teamId: string;
	team: string;
	position: number;
	played: number;
	won: number;
	drawn: number;
	lost: number;
	gamesWon: number;
	gamesLost: number;
	points: number;
};

export type Match = {
	id: string;
	homeTeamId: string;
	awayTeamId: string;
	homeTeam: string;
	awayTeam: string;
	homeScore: number | null;
	awayScore: number | null;
	round: string | null;
	playedAt: string | null;
	status: string;
};

export type SetScore = {
	setNumber: number;
	homePoints: number;
	awayPoints: number;
};

export type Game = {
	id: string;
	orderInMatch: number;
	gameType: string;
	homePlayerId: string | null;
	homePlayer2Id: string | null;
	awayPlayerId: string | null;
	awayPlayer2Id: string | null;
	homePlayerName: string | null;
	homePlayer2Name: string | null;
	awayPlayerName: string | null;
	awayPlayer2Name: string | null;
	homePlayerClassification: string | null;
	homePlayer2Classification: string | null;
	awayPlayerClassification: string | null;
	awayPlayer2Classification: string | null;
	homeSets: number | null;
	awaySets: number | null;
	result: string;
	sets: SetScore[];
};

export type MatchDetail = Match & {
	games: Game[];
};

export type Player = {
	id: string;
	fullName: string;
	licenceNr: string;
	currentClubName: string | null;
	classification: string | null;
	liveClassification: string | null;
	currentElo: number | null;
	liveElo: number | null;
	isSyncing: boolean;
};

export type EloEntry = {
	eloValue: number;
	recordedAt: string;
};

export type PlayerGame = {
	matchId: string | null;
	gameId: string;
	playedAt: string | null;
	homeTeam: string | null;
	awayTeam: string | null;
	homeScore: number | null;
	awayScore: number | null;
	round: string | null;
	status: string | null;
	competitionName: string | null;
	playerSide: 'home' | 'away';
	opponentId: string | null;
	opponentName: string | null;
	opponentClassification: string | null;
	homeSets: number | null;
	awaySets: number | null;
	result: 'HOME' | 'AWAY' | 'NOT_PLAYED';
	eloDelta: number | null;
	eloDeltaProvisional: boolean;
	sets: SetScore[];
};

export type SeasonStats = {
	activePlayers: number;
	matchesLast24h: number;
};

export type WinRate = {
	wins: number;
	games: number;
};

export type SetScoreBucket = {
	playerSets: number;
	opponentSets: number;
	count: number;
};

export type OpponentBucket = {
	/** Exact class label (e.g. "B12"), or the sentinel "HIGHER" / "LOWER" for aggregated tiers. */
	label: string;
	wins: number;
	games: number;
	nearClass?: string;
	farClass?: string;
};

export type MonthlyForm = {
	month: string;
	wins: number;
	losses: number;
};

export type CompetitionStat = {
	name: string;
	wins: number;
	games: number;
	isTournament: boolean;
};

export type PlayerSeasonStats = {
	seasonName: string;
	totalGames: number;
	overall: WinRate;
	/** Last 10 decided games, oldest → newest; true = win. */
	recentForm: boolean[];
	opponentBuckets: OpponentBucket[];
	setDistribution: SetScoreBucket[];
	setsWon: number;
	setsLost: number;
	deuceSetsWon: number;
	deuceSetsTotal: number;
	tightGameWins: number;
	tightGames: number;
	comebackWins: number;
	comeFromBehindGames: number;
	comeFromBehindWins: number;
	monthly: MonthlyForm[];
	longestWinStreak: number;
	currentWinStreak: number;
	bestWinOpponentName: string | null;
	bestWinOpponentClass: string | null;
	competitions: CompetitionStat[];
};

export type H2HRecord = {
	aWins: number;
	bWins: number;
	games: number;
};

export type H2HGame = {
	gameId: string;
	matchId: string | null;
	playedAt: string | null;
	competitionName: string | null;
	/** Sets won, from player A's perspective. */
	aSets: number | null;
	bSets: number | null;
	aWon: boolean;
	/** Set points oriented to A (homePoints = A's points). */
	sets: SetScore[];
};

export type HeadToHead = {
	playerA: Player;
	playerB: Player;
	statsA: PlayerSeasonStats;
	statsB: PlayerSeasonStats;
	record: H2HRecord;
	games: H2HGame[];
};

// ── Career tab (Pro) ──

export type CareerClassPoint = {
	seasonName: string;
	half: 'first' | 'second';
	classification: string;
};

export type CareerSeasonEntry = {
	seasonName: string;
	clubName: string | null;
	leagueName: string | null;
};

export type CareerTotals = {
	matches: number;
	wins: number;
	losses: number;
	seasonsPlayed: number;
	firstYear: number | null;
	lastYear: number | null;
	opponentsFaced: number;
	clubsCount: number;
};

export type CareerMilestones = {
	debutSeason: string | null;
	debutOpponentName: string | null;
	peakClass: string | null;
	peakClassSeason: string | null;
	longestWinStreak: number;
	bestWinOpponentName: string | null;
	bestWinOpponentClass: string | null;
	bestSeasonName: string | null;
	bestSeasonWins: number;
	bestSeasonGames: number;
};

export type CareerRival = {
	opponentId: string;
	opponentName: string;
	opponentClass: string | null;
	meetings: number;
	wins: number;
	losses: number;
};

export type Career = {
	classProgression: CareerClassPoint[];
	seasons: CareerSeasonEntry[];
	totals: CareerTotals;
	milestones: CareerMilestones;
	rivalries: CareerRival[];
};

export type PagedResponse<T> = {
	items: T[];
	page: number;
	size: number;
	total: number;
};

export type FollowResponse = {
	id: string;
	targetType: string;
	targetId: string;
	targetName: string;
	/** Whether push notifications (the bell) are on for this follow. */
	notify: boolean;
};

export type NextMatch = {
	matchId: string;
	homeTeam: string;
	awayTeam: string;
	playerTeamId: string;
	playerTeamName: string;
	playedAt: string | null;
	round: string | null;
	groupId: string;
	groupName: string;
};

export type ClassHistoryEntry = {
	classification: string;
	seasonName: string;
};

export type LeagueContext = {
	teamId: string;
	teamName: string;
	groupId: string;
	groupName: string;
	position: number;
	won: number;
	drawn: number;
	lost: number;
	scheduledMatchCount: number;
};

// ── API functions ────────────────────────────────────────────

export const api = {
	seasons: {
		list: () => get<Season[]>('/seasons')
	},

	federations: {
		list: () => get<Federation[]>('/federations')
	},

	groups: {
		list: (params?: { league?: string; season?: string }) => {
			const qs = new URLSearchParams();
			if (params?.league) qs.set('league', params.league);
			if (params?.season) qs.set('season', params.season);
			const query = qs.toString();
			return get<Group[]>(`/groups${query ? '?' + query : ''}`);
		},
		get: (groupId: string) => get<Group>(`/groups/${groupId}`),
		standings: (groupId: string) => get<Standing[]>(`/groups/${groupId}/standings`),
		matches: (groupId: string) => get<Match[]>(`/groups/${groupId}/matches`)
	},

	teams: {
		get: (teamId: string) => get<TeamSummary>(`/teams/${teamId}`),
		roster: (teamId: string) => get<TeamPlayer[]>(`/teams/${teamId}/roster`),
		matches: (teamId: string) => get<Match[]>(`/teams/${teamId}/matches`)
	},

	matches: {
		detail: (matchId: string) => get<MatchDetail>(`/matches/${matchId}`)
	},

	stats: {
		get: (seasonName: string) => get<SeasonStats>(`/stats?season=${encodeURIComponent(seasonName)}`)
	},

	players: {
		search: (name: string, page = 0, size = 20) =>
			get<PagedResponse<Player>>(
				`/players/search?name=${encodeURIComponent(name)}&page=${page}&size=${size}`
			),
		get: (playerId: string) => get<Player>(`/players/${playerId}`),
		elo: (playerId: string) => get<EloEntry[]>(`/players/${playerId}/elo`),
		matches: (playerId: string) => get<PlayerGame[]>(`/players/${playerId}/matches`),
		nextMatch: (playerId: string) => get<NextMatch>(`/players/${playerId}/next-match`),
		seasonStats: (playerId: string) => get<PlayerSeasonStats>(`/players/${playerId}/stats`),
		headToHead: (playerId: string, opponentId: string, accessToken: string) =>
			getAuthed<HeadToHead>(`/players/${playerId}/h2h/${opponentId}`, accessToken),
		leagueContext: (playerId: string) => get<LeagueContext>(`/players/${playerId}/league-context`),
		classHistory: (playerId: string) =>
			get<ClassHistoryEntry[]>(`/players/${playerId}/class-history`)
	}
};
