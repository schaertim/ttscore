import type { PlayerGame } from '$lib/api';

/** Sentinel month key for games with no recorded date (rare, mostly non-league games). */
export const UNDATED = 'unknown';

export type MonthGroup = {
	key: string;
	/** First day of the month, or null for the undated bucket. */
	month: Date | null;
	games: PlayerGame[];
	wins: number;
	totalElo: number;
	/** Unrounded sum, used to break the sign tie when totalElo rounds to 0. */
	rawElo: number;
	/** False when no played game this month has a known eloDelta (e.g. no longer on click-tt). */
	hasEloData: boolean;
};

/** Buckets a player's games by calendar month (newest-first input order is preserved). */
export function groupByMonth(games: PlayerGame[]): MonthGroup[] {
	const map = new Map<string, PlayerGame[]>();
	for (const game of games) {
		const date = game.playedAt ? new Date(game.playedAt) : null;
		const key = date
			? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
			: UNDATED;
		if (!map.has(key)) map.set(key, []);
		map.get(key)!.push(game);
	}
	return Array.from(map.entries()).map(([key, monthGames]) => {
		const [year, month] = key.split('-');
		const monthDate = key === UNDATED ? null : new Date(Number(year), Number(month) - 1);
		const played = monthGames.filter((g) => g.result !== 'NOT_PLAYED');
		const wins = played.filter(
			(g) =>
				(g.result === 'HOME' && g.playerSide === 'home') ||
				(g.result === 'AWAY' && g.playerSide === 'away')
		).length;
		const totalElo = played.reduce((sum, g) => sum + Math.round(g.eloDelta ?? 0), 0);
		const rawElo = played.reduce((sum, g) => sum + (g.eloDelta ?? 0), 0);
		const hasEloData = played.some((g) => g.eloDelta != null);
		return { key, month: monthDate, games: monthGames, wins, totalElo, rawElo, hasEloData };
	});
}
