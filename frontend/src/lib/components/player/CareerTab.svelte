<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { Career } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import BestWinTile from '$lib/components/BestWinTile.svelte';
	import RecordOverview from '$lib/components/RecordOverview.svelte';
	import ClassArc from './ClassArc.svelte';
	import CareerClubs from './CareerClubs.svelte';
	import CareerRivals from './CareerRivals.svelte';
	import {
		ChartLineIcon,
		MedalIcon,
		ScalesIcon,
		ArrowRightIcon,
		FlagBannerFoldIcon,
		PresentationChartIcon
	} from 'phosphor-svelte';

	interface Props {
		career: Career;
		playerId: string;
	}

	let { career, playerId }: Props = $props();

	const t = $derived(career.totals);
	const m = $derived(career.milestones);
	const hasClassArc = $derived(career.classProgression.length >= 2);
	const isEmpty = $derived(
		t.matches === 0 && career.classProgression.length === 0 && career.seasons.length === 0
	);
</script>

{#if isEmpty}
	<p class="py-12 text-center text-sm text-muted-foreground">{$_('career.no_data')}</p>
{:else}
	<div class="space-y-6">
		{#if t.matches > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('career.at_a_glance')} icon={PresentationChartIcon} />
				<RecordOverview
					totalLabel={$_('career.matches')}
					total={t.matches}
					wins={t.wins}
					losses={t.losses}
				/>
				<div class="grid grid-cols-3 gap-3">
					<StatTile
						label={$_('career.seasons')}
						labelPosition="bottom"
						align="center"
						value={t.seasonsPlayed}
					/>
					<StatTile
						label={$_('career.opponents')}
						labelPosition="bottom"
						align="center"
						value={t.opponentsFaced}
					/>
					<StatTile
						label={$_('career.clubs')}
						labelPosition="bottom"
						align="center"
						value={t.clubsCount}
					/>
				</div>
			</section>
		{/if}

		{#if hasClassArc}
			<section class="space-y-3">
				<SectionLabel label={$_('career.class_progression')} icon={ChartLineIcon} />
				<Card.Root class="border-border/50 p-4">
					<div class="h-52">
						<ClassArc progression={career.classProgression} />
					</div>
				</Card.Root>
			</section>
		{/if}

		{#if career.seasons.length > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('career.clubs_leagues')} icon={FlagBannerFoldIcon} />
				<Card.Root class="border-border/50 p-4">
					<CareerClubs seasons={career.seasons} />
				</Card.Root>
			</section>
		{/if}

		{#if career.rivalries.length > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('career.rivalries')} icon={ScalesIcon} />
				<CareerRivals rivals={career.rivalries} {playerId} />
			</section>
		{/if}

		<section class="space-y-3">
			<SectionLabel label={$_('career.milestones')} icon={MedalIcon} />
			<div class="space-y-3">
				<div class="grid grid-cols-2 gap-3">
					{#if m.peakClass}
						<StatTile label={$_('career.peak_class')}>
							<div class="space-y-1">
								<div class="flex items-center">
									<ClassBadge classification={m.peakClass} size="md" />
								</div>
								{#if m.peakClassSeason}
									<p class="text-xs text-muted-foreground">{m.peakClassSeason}</p>
								{/if}
							</div>
						</StatTile>
					{/if}
					<StatTile label={$_('career.biggest_jump')}>
						{#if m.biggestJumpFrom && m.biggestJumpTo}
							<div class="space-y-1">
								<div class="flex items-center gap-1.5">
									<ClassBadge classification={m.biggestJumpFrom} size="md" />
									<ArrowRightIcon size={16} class="shrink-0 text-muted-foreground" />
									<ClassBadge classification={m.biggestJumpTo} size="md" />
								</div>
								{#if m.biggestJumpSeason}
									<p class="text-xs text-muted-foreground">{m.biggestJumpSeason}</p>
								{/if}
							</div>
						{:else}
							<span class="text-sm text-muted-foreground">{$_('career.no_jumps')}</span>
						{/if}
					</StatTile>
				</div>

				{#if m.bestWinOpponentName}
					<BestWinTile
						label={$_('career.best_win')}
						opponentId={m.bestWinOpponentId}
						opponentName={m.bestWinOpponentName}
						opponentClass={m.bestWinOpponentClass}
					/>
				{/if}

				<div class="grid grid-cols-2 gap-3">
					<StatTile label={$_('career.longest_streak')}>
						<ScoreLine segments={[{ value: m.longestWinStreak, tone: 'win' }]} />
					</StatTile>
					{#if m.bestSeasonName}
						<StatTile label={$_('career.best_season')} value={m.bestSeasonName} />
					{/if}
				</div>
			</div>
		</section>
	</div>
{/if}
