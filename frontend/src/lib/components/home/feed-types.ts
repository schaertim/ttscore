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

export type GroupLatestItem = {
	kind: 'group_latest';
	homeTeam: string;
	awayTeam: string;
	score: string;
	playedAt: string | null;
};

export type FeedItem = PlayerMatchItem | ClassChangeItem | TeamMatchItem | GroupLatestItem;
