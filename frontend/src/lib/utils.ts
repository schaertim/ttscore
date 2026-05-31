import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
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
		lang ??
		(typeof navigator !== 'undefined' ? navigator.language : undefined) ??
		'de';

	const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });

	if (minutes < 60) return rtf.format(-minutes, 'minute');
	if (hours < 24) return rtf.format(-hours, 'hour');
	if (days < 7) return rtf.format(-days, 'day');
	if (weeks < 5) return rtf.format(-weeks, 'week');
	return rtf.format(-months, 'month');
}

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
