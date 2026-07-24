import { STORAGE_KEYS } from '$lib/storageKeys';

const STORAGE_KEY = STORAGE_KEYS.recentPlayers;
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

/**
 * Updates a stored entry's fields in place (e.g. a refreshed classification) without moving its
 * position in the list — unlike `addRecentPlayer`, which reorders to the front on every view.
 */
export function updateRecentPlayer(id: string, patch: Partial<Omit<RecentPlayer, 'id'>>): void {
	try {
		const current = getRecentPlayers();
		const index = current.findIndex((p) => p.id === id);
		if (index === -1) return;
		current[index] = { ...current[index], ...patch };
		localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
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
