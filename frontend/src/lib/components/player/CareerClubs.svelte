<script lang="ts">
	import type { CareerSeasonEntry } from '$lib/api';

	let { seasons }: { seasons: CareerSeasonEntry[] } = $props();

	type Stint = {
		clubName: string;
		firstSeason: string;
		lastSeason: string;
		seasonCount: number;
		leagues: string[];
	};

	// Collapse consecutive same-club seasons into a single tenure, newest first.
	const stints = $derived.by<Stint[]>(() => {
		const out: Stint[] = [];
		for (const s of seasons) {
			const club = s.clubName ?? '—';
			const last = out.at(-1);
			if (last && last.clubName === club) {
				last.lastSeason = s.seasonName;
				last.seasonCount++;
				if (s.leagueName && !last.leagues.includes(s.leagueName)) last.leagues.push(s.leagueName);
			} else {
				out.push({
					clubName: club,
					firstSeason: s.seasonName,
					lastSeason: s.seasonName,
					seasonCount: 1,
					leagues: s.leagueName ? [s.leagueName] : []
				});
			}
		}
		return out.reverse();
	});

	// "2003/2004" → start year "2003"; end year is the part after the slash.
	function yearSpan(stint: Stint): string {
		const start = stint.firstSeason.slice(0, 4);
		const end = stint.lastSeason.slice(5) || stint.lastSeason;
		return start === end.slice(0, 4) ? start : `${start}–${end}`;
	}
</script>

<div class="space-y-0">
	{#each stints as stint, i (stint.firstSeason + stint.clubName)}
		<div class="flex gap-3">
			<!-- Timeline rail -->
			<div class="flex flex-col items-center">
				<div class="mt-1.5 size-2.5 shrink-0 rounded-full bg-primary"></div>
				{#if i < stints.length - 1}
					<div class="w-px flex-1 bg-border"></div>
				{/if}
			</div>

			<div class="min-w-0 flex-1 pb-5">
				<div class="flex items-baseline justify-between gap-2">
					<p class="truncate font-semibold">{stint.clubName}</p>
					<span class="shrink-0 font-mono text-xs text-muted-foreground tabular-nums">
						{yearSpan(stint)}
					</span>
				</div>
				{#if stint.leagues.length > 0}
					<div class="mt-1.5 flex flex-wrap gap-1">
						{#each stint.leagues as league (league)}
							<span
								class="rounded-full bg-muted px-2 py-0.5 text-2xs font-medium text-muted-foreground"
							>
								{league}
							</span>
						{/each}
					</div>
				{/if}
			</div>
		</div>
	{/each}
</div>
