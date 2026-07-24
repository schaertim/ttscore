/**
 * Groups are scraped from two different sources (click-tt and knob.ch) whose naming
 * conventions differ — including for the same federation across seasons, once it migrated
 * from one scraper to the other. This extracts a normalised competition/tier label
 * ("category") from a group name, so the league browser can bucket the same tier together
 * regardless of which scraper produced it, e.g.:
 *
 *   "HE 1. Liga Gr. 1"        (click-tt)  -> "1. Liga"
 *   "1. Liga - Gruppe 1"      (knob)      -> "1. Liga"
 *   "DA 1. Liga Gr. 1"        (click-tt)  -> "1. Liga Damen"
 *   "1. Liga Damen - Gruppe 1"(knob)      -> "1. Liga Damen"
 *   "O40 3. Liga Gr. 1"       (click-tt)  -> "O40"
 *   "O40 - Gruppe F"          (knob)      -> "O40"
 *
 * Age-group divisions (O40, U18, ...) collapse to just the age code, ignoring the tier —
 * unlike the open categories, age groups tend to have few enough teams overall that splitting
 * them further by tier just recreates the clutter this is meant to solve (e.g. "O40 1. Liga",
 * "O40 2. Liga" and "O40 3. Liga" all become one "O40" category).
 *
 * The two sources disagree on how gender is marked (click-tt: leading "HE"/"DA"; knob:
 * trailing "Damen") and how the stage/sub-group is marked (a mix of "Gr."/"Gruppe"/"Final"/
 * "Playoff"/"Auf-/Abstiegsspiele", with or without a leading dash) — this strips both in a
 * source-agnostic way and reassembles a canonical label in a fixed order.
 *
 * Names with no recognisable stage suffix (e.g. "STTL Men", "1° Divisione", ANJTT's compact
 * "L1P1" codes) fall back to using the whole name as its own one-off category — those tend to
 * belong to federations with few enough groups that this doesn't matter (see `groupCategoryThreshold`
 * usage at the call site, which only buckets federations with many groups in the first place).
 */

const AGE_PREFIX = /^(O\d{2}|U\d{2})\b\.?\s*/i;
const GENDER_PREFIX = /^(HE|DA)\b\.?\s*/i;
// "Gr"/"Ph" need their own digit-lookahead alternative rather than relying on a trailing \b:
// some sources glue the group number directly on with no separator at all (AVVF's "Gr1", no
// dot/space) — "r" and "1" are both word characters, so no \b exists between them and the
// plain `\bGr\.?\b` alternative silently fails to match, leaving the whole name uncategorised.
const STAGE_MARKER =
	/\bGr\.?(?=\d)|\bGr\.?\b|\bGruppe\b|\bPh\.?(?=\d)|\bPh\.?\b|\bFinal\b|\bPlayoff\b|\bAuf(?:stiegsspiele)?\b|\bAbstiegsspiele\b|\bEntscheidung\b/i;
const DAMEN_SUFFIX = /\bDamen\b/i;
// Normalises inconsistent spacing around the tier number's period ("5 . Liga", "1.Liga" -> "1. Liga")
// so the same tier doesn't get split into separate categories just from scraper formatting noise.
const TIER_PERIOD_SPACING = /(\d+)\s*\.\s*(?=[A-Za-zÀ-ÿ])/g;

export function categorizeGroupName(rawName: string): string {
	let s = rawName.trim();

	let agePrefix = '';
	const ageMatch = s.match(AGE_PREFIX);
	if (ageMatch) {
		agePrefix = ageMatch[1].toUpperCase();
		// Some sources join the age code directly to the stage with a dash, colon, or no
		// separator at all (e.g. "O40-G1", "U13:MTTV") rather than the usual " - " — strip
		// any leftover separator too.
		s = s.slice(ageMatch[0].length).replace(/^[\s:-]+/, '');
	}

	let isWomen = false;
	const genderMatch = s.match(GENDER_PREFIX);
	if (genderMatch) {
		isWomen = genderMatch[1].toUpperCase() === 'DA';
		s = s.slice(genderMatch[0].length);
	}

	s = s.replace(TIER_PERIOD_SPACING, '$1. ');

	const stageIdx = s.search(STAGE_MARKER);
	let tier = (stageIdx >= 0 ? s.slice(0, stageIdx) : s).trim();

	// knob marks women's divisions with a trailing "Damen" inside the tier text itself,
	// rather than a leading marker — treat it as the same signal as click-tt's "DA" prefix.
	const damenMatch = tier.match(DAMEN_SUFFIX);
	if (damenMatch) {
		isWomen = true;
		tier = tier.slice(0, damenMatch.index).trim();
	}

	// Drop a leftover trailing separator from stripping the stage suffix (e.g. "1. Liga - ").
	tier = tier.replace(/[\s,/-]+$/, '').trim();

	// Age-group divisions collapse to just the age code — the tier is dropped rather than
	// appended, so "O40 1. Liga", "O40 2. Liga", etc. all bucket together under "O40".
	const parts = agePrefix
		? [agePrefix, isWomen ? 'Damen' : ''].filter((p) => p.length > 0)
		: [tier, isWomen ? 'Damen' : ''].filter((p) => p.length > 0);
	return parts.length > 0 ? parts.join(' ') : rawName.trim();
}

/** Numeric-aware comparator: "Gruppe 2" sorts before "Gruppe 10", "1. Liga" before "10. Liga". */
export function naturalCompare(a: string, b: string): number {
	const chunks = (s: string) => s.match(/\d+|\D+/g) ?? [];
	const ac = chunks(a);
	const bc = chunks(b);
	const len = Math.max(ac.length, bc.length);

	for (let i = 0; i < len; i++) {
		const x = ac[i] ?? '';
		const y = bc[i] ?? '';
		if (x === y) continue;

		const xNum = /^\d+$/.test(x) ? Number(x) : null;
		const yNum = /^\d+$/.test(y) ? Number(y) : null;
		if (xNum !== null && yNum !== null) {
			if (xNum !== yNum) return xNum - yNum;
			continue;
		}
		return x < y ? -1 : 1;
	}
	return 0;
}
