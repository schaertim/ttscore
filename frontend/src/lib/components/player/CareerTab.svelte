<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { Career } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import ClassArc from './ClassArc.svelte';
	import CareerClubs from './CareerClubs.svelte';
	import CareerRivals from './CareerRivals.svelte';
	import { formatName } from '$lib/utils';
	import {
		ChartLineIcon,
		PingPongIcon,
		MedalIcon,
		TrophyIcon,
		ScalesIcon
	} from 'phosphor-svelte';

	let { career }: { career: Career } = $props();

	const t = $derived(career.totals);
	const m = $derived(career.milestones);
	const winPct = $derived(t.matches > 0 ? Math.round((t.wins / t.matches) * 100) : 0);
	const hasClassArc = $derived(career.classProgression.length >= 2);
	const isEmpty = $derived(
		t.matches === 0 && career.classProgression.length === 0 && career.seasons.length === 0
	);
</script>

{#if isEmpty}
	<p class="py-12 text-center text-sm text-muted-foreground">{$_('career.no_data')}</p>
{:else}
	<div class="space-y-6">
		{#if hasClassArc}
			<section class="space-y-3">
				<SectionLabel label={$_('career.class_progression')} icon={ChartLineIcon} />
				<Card.Root class="border-border/50 p-4">
					<div class="h-56">
						<ClassArc progression={career.classProgression} />
					</div>
				</Card.Root>
			</section>
		{/if}

		{#if t.matches > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('career.at_a_glance')} icon={PingPongIcon} />
				<div class="grid grid-cols-3 gap-3">
					<StatTile label={$_('career.matches')} labelPosition="bottom" align="center" value={t.matches} />
					<StatTile label={$_('stats.record')} labelPosition="bottom" align="center">
						<ScoreLine
							segments={[
								{ value: t.wins, tone: 'win' },
								{ value: t.losses, tone: 'loss' }
							]}
						/>
					</StatTile>
					<StatTile label={$_('stats.win_rate')} labelPosition="bottom" align="center" value={`${winPct}%`} />
				</div>
				<div class="grid grid-cols-3 gap-3">
					<StatTile label={$_('career.seasons')} labelPosition="bottom" align="center" value={t.seasonsPlayed} />
					<StatTile label={$_('career.opponents')} labelPosition="bottom" align="center" value={t.opponentsFaced} />
					<StatTile label={$_('career.clubs')} labelPosition="bottom" align="center" value={t.clubsCount} />
				</div>
				{#if t.firstYear && t.lastYear}
					<p class="text-center text-xs text-muted-foreground">
						{$_('career.active_range', { values: { from: t.firstYear, to: t.lastYear } })}
					</p>
				{/if}
			</section>
		{/if}

		<section class="space-y-3">
			<SectionLabel label={$_('career.milestones')} icon={MedalIcon} />
			<div class="space-y-3">
				<div class="grid grid-cols-2 gap-3">
					{#if m.peakClass}
						<StatTile label={$_('career.peak_class')}>
							<div class="flex items-center gap-2">
								<ClassBadge classification={m.peakClass} size="lg" />
								{#if m.peakClassSeason}
									<span class="text-xs text-muted-foreground">{m.peakClassSeason}</span>
								{/if}
							</div>
						</StatTile>
					{/if}
					<StatTile label={$_('career.longest_streak')}>
						<ScoreLine segments={[{ value: m.longestWinStreak, tone: 'win' }]} />
					</StatTile>
				</div>

				{#if m.bestWinOpponentName}
					<StatTile label={$_('career.best_win')}>
						<p class="flex items-center gap-1.5 text-base font-semibold">
							<span class="truncate">{formatName(m.bestWinOpponentName)}</span>
							<ClassBadge classification={m.bestWinOpponentClass} />
						</p>
					</StatTile>
				{/if}

				<div class="grid grid-cols-2 gap-3">
					{#if m.bestSeasonName}
						<StatTile label={$_('career.best_season')}>
							<div class="flex items-baseline gap-2">
								<span class="text-base font-semibold">{m.bestSeasonName}</span>
								<ScoreLine
									class="text-sm"
									segments={[
										{ value: m.bestSeasonWins, tone: 'win' },
										{ value: m.bestSeasonGames - m.bestSeasonWins, tone: 'loss' }
									]}
								/>
							</div>
						</StatTile>
					{/if}
					{#if m.debutSeason}
						<StatTile label={$_('career.debut')} value={m.debutSeason} />
					{/if}
				</div>
			</div>
		</section>

		{#if career.seasons.length > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('career.clubs_leagues')} icon={TrophyIcon} />
				<CareerClubs seasons={career.seasons} />
			</section>
		{/if}

		{#if career.rivalries.length > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('career.rivalries')} icon={ScalesIcon} />
				<CareerRivals rivals={career.rivalries} />
			</section>
		{/if}
	</div>
{/if}
