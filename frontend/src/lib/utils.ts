import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}

const KLASS_CLASSES: Record<string, string> = {
	A: 'text-klass-a bg-klass-a/15',
	B: 'text-klass-b bg-klass-b/15',
	C: 'text-klass-c bg-klass-c/15',
	D: 'text-klass-d bg-klass-d/15',
	E: 'text-klass-e bg-klass-e/15'
};

export function klassColors(klass: string | null | undefined): string {
	if (!klass) return 'text-muted-foreground bg-muted';
	const letter = klass[0].toUpperCase();
	return KLASS_CLASSES[letter] ?? 'text-muted-foreground bg-muted';
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
