export type PlayerMatchItem = {
	kind: 'player_match';
	result: 'WIN' | 'LOSS' | 'DRAW';
	opponentTeam: string;
	matchScore: string;
	playedAt: string | null;
};

export type ClassChangeItem = {
	kind: 'class_change';
	direction: 'UP' | 'DOWN';
	from: string;
	to: string;
};

export type TeamMatchItem = {
	kind: 'team_match';
	result: 'WIN' | 'LOSS' | 'DRAW';
	opponent: string;
	score: string;
	playedAt: string | null;
};

export type GroupMatchItem = {
	kind: 'group_match';
	homeTeam: string;
	awayTeam: string;
	score: string;
	playedAt: string | null;
};

export type UpcomingMatchItem = {
	kind: 'upcoming_match';
	homeTeam: string;
	awayTeam: string;
	playedAt: string | null;
};

export type FeedItem =
	| PlayerMatchItem
	| ClassChangeItem
	| TeamMatchItem
	| GroupMatchItem
	| UpcomingMatchItem;
