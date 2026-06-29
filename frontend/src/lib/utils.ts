import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import type { PlayerSeasonStats } from '$lib/api';

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}

/** CSS color for a classification — the class-letter brand color, or primary as a fallback. */
export function classColorVar(cls: string | null | undefined): string {
	if (!cls) return 'var(--color-primary)';
	const letter = cls[0].toLowerCase();
	return ['a', 'b', 'c', 'd', 'e'].includes(letter)
		? `var(--class-${letter})`
		: 'var(--color-primary)';
}

/** The six 0–100 radar scores derived from a player's season stats. */
export type RadarMetrics = {
	form: number;
	clutch: number;
	fiveSet: number;
	punch: number;
	resilience: number;
	consistency: number;
};

export function radarMetrics(stats: PlayerSeasonStats): RadarMetrics {
	const pct = (w: number, g: number) => (g > 0 ? Math.round((w / g) * 100) : 0);

	const same = stats.opponentBuckets.filter((b) => b.label !== 'HIGHER' && b.label !== 'LOWER');
	const consistency = pct(
		same.reduce((s, b) => s + b.wins, 0),
		same.reduce((s, b) => s + b.games, 0)
	);
	const higher = stats.opponentBuckets.find((b) => b.label === 'HIGHER');

	return {
		form: pct(stats.recentForm.filter(Boolean).length, stats.recentForm.length),
		clutch: pct(stats.deuceSetsWon, stats.deuceSetsTotal),
		fiveSet: pct(stats.tightGameWins, stats.tightGames),
		punch: higher ? pct(higher.wins, higher.games) : 0,
		resilience: pct(stats.comeFromBehindWins, stats.comeFromBehindGames),
		consistency
	};
}

/**
 * Flips a player name from backend storage format ("Lastname Firstname") to
 * display format ("Firstname Lastname"). Handles compound surnames.
 * "Müller Hans"      → "Hans Müller"
 * "De Marco Giovanni" → "Giovanni De Marco"
 * Returns '—' for null/empty inputs.
 */
export function formatName(name: string | null | undefined): string {
	if (!name?.trim()) return '—';
	const parts = name.trim().split(/\s+/);
	if (parts.length === 1) return parts[0];
	const firstName = parts[parts.length - 1];
	const lastName = parts.slice(0, -1).join(' ');
	return `${firstName} ${lastName}`;
}

const CLASSIFICATION_CLASSES: Record<string, string> = {
	A: 'text-class-a bg-class-a/15',
	B: 'text-class-b bg-class-b/15',
	C: 'text-class-c bg-class-c/15',
	D: 'text-class-d bg-class-d/15',
	E: 'text-class-e bg-class-e/15'
};

export function classificationColors(classification: string | null | undefined): string {
	if (!classification) return 'text-muted-foreground bg-muted';
	const letter = classification[0].toUpperCase();
	return CLASSIFICATION_CLASSES[letter] ?? 'text-muted-foreground bg-muted';
}

/** Numeric ladder position of a classification ("B14" → 14, "A22" → 22). Higher = stronger. */
export function classificationRank(classification: string | null | undefined): number {
	if (!classification) return 0;
	return parseInt(classification.slice(1)) || 0;
}

export function timeAgo(dateStr: string | null | undefined, lang?: string): string {
	if (!dateStr) return '';
	const diff = Date.now() - new Date(dateStr).getTime();
	const seconds = Math.floor(diff / 1_000);
	const minutes = Math.floor(seconds / 60);
	const hours = Math.floor(minutes / 60);
	const days = Math.floor(hours / 24);
	const weeks = Math.floor(days / 7);
	const months = Math.floor(days / 30);

	// Resolve locale: prefer explicit arg, then browser, then 'de'
	const locale =
		lang ?? (typeof navigator !== 'undefined' ? navigator.language : undefined) ?? 'de';

	const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });

	if (minutes < 60) return rtf.format(-minutes, 'minute');
	if (hours < 24) return rtf.format(-hours, 'hour');
	if (days < 7) return rtf.format(-days, 'day');
	if (weeks < 5) return rtf.format(-weeks, 'week');
	return rtf.format(-months, 'month');
}

/**
 * Swiss TT men's classification ladder — `[minElo, class]`, descending. Each class covers
 * `[minElo, nextClass.minElo)`. Shared by the ELO chart annotations and the class-progress bar.
 */
export const ELO_THRESHOLDS: [number, string][] = [
	[1990, 'A22'],
	[1890, 'A21'],
	[1790, 'A20'],
	[1680, 'A19'],
	[1565, 'A18'],
	[1490, 'A17'],
	[1435, 'A16'],
	[1360, 'B15'],
	[1320, 'B14'],
	[1280, 'B13'],
	[1240, 'B12'],
	[1200, 'B11'],
	[1150, 'C10'],
	[1100, 'C9'],
	[1050, 'C8'],
	[990, 'C7'],
	[930, 'C6'],
	[860, 'D5'],
	[780, 'D4'],
	[700, 'D3'],
	[630, 'D2']
];

export function ordinal(n: number): string {
	const s = ['th', 'st', 'nd', 'rd'];
	const v = n % 100;
	return n + (s[(v - 20) % 10] ?? s[v] ?? s[0]);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type WithoutChild<T> = T extends { child?: any } ? Omit<T, 'child'> : T;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type WithoutChildren<T> = T extends { children?: any } ? Omit<T, 'children'> : T;
export type WithoutChildrenOrChild<T> = WithoutChildren<WithoutChild<T>>;
export type WithElementRef<T, U extends HTMLElement = HTMLElement> = T & { ref?: U | null };
