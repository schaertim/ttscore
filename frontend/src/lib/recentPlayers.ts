const STORAGE_KEY = 'ttscore:recent-players';
const MAX_ENTRIES = 10;

export interface RecentPlayer {
	id: string;
	fullName: string;
	classification: string | null;
	currentClubName: string | null;
}

export function getRecentPlayers(): RecentPlayer[] {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (!raw) return [];
		return JSON.parse(raw) as RecentPlayer[];
	} catch {
		return [];
	}
}

export function addRecentPlayer(player: RecentPlayer): void {
	try {
		const current = getRecentPlayers().filter((p) => p.id !== player.id);
		const updated = [player, ...current].slice(0, MAX_ENTRIES);
		localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
	} catch {
		// private browsing or storage full — silently ignore
	}
}

export function removeRecentPlayer(id: string): void {
	try {
		const updated = getRecentPlayers().filter((p) => p.id !== id);
		localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
	} catch {
		// ignore
	}
}

export function clearRecentPlayers(): void {
	try {
		localStorage.removeItem(STORAGE_KEY);
	} catch {
		// ignore
	}
}
