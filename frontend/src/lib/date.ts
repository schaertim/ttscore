// Canonical date/time formatting. Every user-facing date in the app goes through one of these
// helpers so the format stays consistent per context (dense list vs. header vs. relative vs.
// chart axis). Calendar helpers return `null` for a missing/invalid date — the caller supplies
// the context-appropriate fallback (a fixture reads `common.tbd`, most others an em dash).
//
// Locale resolves to the passed value, then the browser, then 'de' (the app's primary locale).
// Pass the svelte-i18n `$locale` store value straight through.

type DateInput = string | Date | null | undefined;

function resolveLocale(lang?: string | null): string {
	return lang ?? (typeof navigator !== 'undefined' ? navigator.language : undefined) ?? 'de';
}

function parse(input: DateInput): Date | null {
	if (input == null) return null;
	const d = input instanceof Date ? input : new Date(input);
	return Number.isNaN(d.getTime()) ? null : d;
}

/**
 * Relative time, past or future, from "vor 3 Minuten" up to "vor 2 Jahren" / "in 5 Std.".
 * Returns '' for a missing date. Direction follows the sign of the difference, so the same
 * helper covers both "3 days ago" feed stamps and "in 5 hours" countdowns.
 */
export function relativeTime(input: DateInput, lang?: string | null): string {
	const d = parse(input);
	if (!d) return '';
	const diffMs = d.getTime() - Date.now();
	const rtf = new Intl.RelativeTimeFormat(resolveLocale(lang), { numeric: 'auto' });

	const minutes = Math.round(diffMs / 60_000);
	const hours = Math.round(diffMs / 3_600_000);
	const days = Math.round(diffMs / 86_400_000);
	const weeks = Math.round(diffMs / (7 * 86_400_000));
	const months = Math.round(diffMs / (30 * 86_400_000));
	const years = Math.round(diffMs / (365 * 86_400_000));

	if (Math.abs(minutes) < 60) return rtf.format(minutes, 'minute');
	if (Math.abs(hours) < 24) return rtf.format(hours, 'hour');
	if (Math.abs(days) < 7) return rtf.format(days, 'day');
	if (Math.abs(weeks) < 5) return rtf.format(weeks, 'week');
	if (Math.abs(months) < 12) return rtf.format(months, 'month');
	return rtf.format(years, 'year');
}

/** Compact numeric date with year — dense list rows (match cards). `05.07.26` */
export function dateNumeric(input: DateInput, lang?: string | null): string | null {
	const d = parse(input);
	if (!d) return null;
	return d.toLocaleDateString(resolveLocale(lang), {
		day: '2-digit',
		month: '2-digit',
		year: '2-digit'
	});
}

/** Day + month, no year — only for rows sitting under a `monthYear` header. `05.07.` */
export function dayMonth(input: DateInput, lang?: string | null): string | null {
	const d = parse(input);
	if (!d) return null;
	return d.toLocaleDateString(resolveLocale(lang), { day: '2-digit', month: '2-digit' });
}

/** Readable date with year — prominent single dates & section headers. `5. Juli 2026` */
export function dateLong(input: DateInput, lang?: string | null): string | null {
	const d = parse(input);
	if (!d) return null;
	return d.toLocaleDateString(resolveLocale(lang), {
		day: 'numeric',
		month: 'long',
		year: 'numeric'
	});
}

/** Weekday-led, no year — imminent fixtures where the day of week matters. `Sa., 5. Juli` */
export function dateWeekday(input: DateInput, lang?: string | null): string | null {
	const d = parse(input);
	if (!d) return null;
	return d.toLocaleDateString(resolveLocale(lang), {
		weekday: 'short',
		day: 'numeric',
		month: 'short'
	});
}

/** Month + year — month group headers. `Juli 2026` */
export function monthYear(input: DateInput, lang?: string | null): string | null {
	const d = parse(input);
	if (!d) return null;
	return d.toLocaleDateString(resolveLocale(lang), { month: 'long', year: 'numeric' });
}
