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
	groupId: string;
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

export type TeamBasic = {
	id: string;
	name: string;
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
	/** STT age/eligibility category ("Aktive", "O50", "U17", …), null if unknown. */
	category: string | null;
	classification: string | null;
	liveClassification: string | null;
	currentElo: number | null;
	liveElo: number | null;
	isSyncing: boolean;
	/** False for knob-only players (no click-tt link): they never sync and have no ELO, so the
	 *  client hides ELO-specific UI like the ELO history graph. */
	clickttLinked: boolean;
	/** How the click-tt link was established (see backend PlayerService.MatchMethod), or null for
	 *  knob-only players. `*_NEAR` values are lower-confidence matches. */
	matchMethod: string | null;
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

/** Radar-chart source counts, over a rolling 1-year window rather than the current season. */
export type RadarSource = {
	opponentBuckets: OpponentBucket[];
	deuceSetsWon: number;
	deuceSetsTotal: number;
	tightGameWins: number;
	tightGames: number;
	comeFromBehindWins: number;
	comeFromBehindGames: number;
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
	bestWinOpponentId: string | null;
	bestWinOpponentName: string | null;
	bestWinOpponentClass: string | null;
	competitions: CompetitionStat[];
	radar: RadarSource;
};

/** Just the H2H radar's two inputs — see the backend's `RadarStatsResponse`. */
export type RadarStats = {
	/** Last 10 decided games (rolling 1-year window, same scope as `radar`), oldest → newest. */
	recentForm: boolean[];
	radar: RadarSource;
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
	statsA: RadarStats;
	statsB: RadarStats;
	record: H2HRecord;
	games: H2HGame[];
};

// ── Match preview (Pro) ──

export type PreviewPlayer = {
	id: string;
	fullName: string;
	classification: string | null;
	elo: number | null;
	wins: number;
	losses: number;
};

export type PreviewTeam = {
	teamId: string;
	teamName: string;
	position: number;
	played: number;
	points: number;
	/** Games won minus games lost across the season. */
	gamesDiff: number;
	/** "W-D-L" match record. */
	record: string;
	/** Last five decided matches, newest first: "W" | "L" | "D". */
	form: string[];
	/** Full roster, strongest first. */
	roster: PreviewPlayer[];
};

export type PreviewPriorMeeting = {
	matchId: string;
	homeTeam: string;
	awayTeam: string;
	homeScore: number | null;
	awayScore: number | null;
	playedAt: string | null;
	round: string | null;
};

export type PreviewMatchup = {
	homePlayer: PreviewPlayer;
	awayPlayer: PreviewPlayer;
	/** All-time direct record, oriented so homeWins belongs to homePlayer. */
	homeWins: number;
	awayWins: number;
	meetings: number;
	lastPlayedAt: string | null;
	/** ELO-implied probability that homePlayer wins; null when either ELO is unknown. */
	homeWinProbability: number | null;
	/** Every past duel result from homePlayer's perspective, newest first: "W" | "L". */
	results: string[];
};

/** Shared fixture-header shape rendered by PreviewHeader (team + player previews). */
export type PreviewFixture = {
	matchId: string;
	groupId: string;
	groupName: string;
	round: string | null;
	playedAt: string | null;
	status: string;
	home: PreviewTeam;
	away: PreviewTeam;
};

export type MatchPreview = PreviewFixture & {
	previousMeeting: PreviewPriorMeeting | null;
	keyMatchups: PreviewMatchup[];
};

/** Player-centric preview of one fixture; duels always have the focus player as homePlayer. */
export type PlayerMatchPreview = PreviewFixture & {
	/** True when the focus player's team is the home side. */
	isHome: boolean;
	player: PreviewPlayer;
	/** One duel per opponent-roster player, strongest opponent first. */
	duels: PreviewMatchup[];
};

export type PlayerUpcoming = {
	teamId: string;
	teamName: string;
	matches: Match[];
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
	topClass: string | null;
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
	bestWinOpponentId: string | null;
	bestWinOpponentName: string | null;
	bestWinOpponentClass: string | null;
	bestSeasonName: string | null;
	bestSeasonWins: number;
	bestSeasonGames: number;
	biggestJumpSeason: string | null;
	biggestJumpFrom: string | null;
	biggestJumpTo: string | null;
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

export type ClassHistoryEntry = {
	classification: string;
	seasonName: string;
	effectiveDate: string;
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
		teams: (groupId: string) => get<TeamBasic[]>(`/groups/${groupId}/teams`),
		matches: (groupId: string) => get<Match[]>(`/groups/${groupId}/matches`)
	},

	teams: {
		get: (teamId: string) => get<TeamSummary>(`/teams/${teamId}`),
		roster: (teamId: string) => get<TeamPlayer[]>(`/teams/${teamId}/roster`),
		matches: (teamId: string) => get<Match[]>(`/teams/${teamId}/matches`)
	},

	matches: {
		detail: (matchId: string) => get<MatchDetail>(`/matches/${matchId}`),
		/** Pro-gated neutral preview for a (typically scheduled) fixture. */
		preview: (matchId: string, accessToken: string) =>
			getAuthed<MatchPreview>(`/matches/${matchId}/preview`, accessToken)
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
		/** Triggers an on-demand click-tt sync and resolves once it has finished (or been skipped). */
		sync: async (playerId: string): Promise<void> => {
			await fetch(`${BASE}/players/${playerId}/sync`).catch(() => {});
		},
		elo: (playerId: string) => get<EloEntry[]>(`/players/${playerId}/elo`),
		/** `limit` omitted → full match history; otherwise just the `limit` most recent games. */
		matches: (playerId: string, limit?: number) =>
			get<PlayerGame[]>(`/players/${playerId}/matches${limit ? `?limit=${limit}` : ''}`),
		upcoming: (playerId: string) => get<PlayerUpcoming>(`/players/${playerId}/upcoming`),
		/** Pro-gated player-centric preview of one of the player's team fixtures. */
		matchPreview: (playerId: string, matchId: string, accessToken: string) =>
			getAuthed<PlayerMatchPreview>(`/players/${playerId}/preview/${matchId}`, accessToken),
		seasonStats: (playerId: string) => get<PlayerSeasonStats>(`/players/${playerId}/stats`),
		career: (playerId: string) => get<Career>(`/players/${playerId}/career`),
		headToHead: (playerId: string, opponentId: string, accessToken: string) =>
			getAuthed<HeadToHead>(`/players/${playerId}/h2h/${opponentId}`, accessToken),
		leagueContext: (playerId: string) => get<LeagueContext>(`/players/${playerId}/league-context`),
		classHistory: (playerId: string) =>
			get<ClassHistoryEntry[]>(`/players/${playerId}/class-history`)
	}
};
