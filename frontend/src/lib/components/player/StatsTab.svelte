<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats } from '$lib/api';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import RecordOverview from '$lib/components/RecordOverview.svelte';
	import OpponentBreakdown from './OpponentBreakdown.svelte';
	import SetDistributionRadial from './SetDistributionRadial.svelte';
	import MonthlyForm from './MonthlyForm.svelte';
	import SeasonHighlights from './SeasonHighlights.svelte';
	import {
		UsersThreeIcon,
		MedalIcon,
		ChartDonutIcon,
		CalendarDotsIcon,
		PresentationChartIcon
	} from 'phosphor-svelte';

	interface Props {
		stats: PlayerSeasonStats;
	}

	let { stats }: Props = $props();

	const losses = $derived(stats.overall.games - stats.overall.wins);
</script>

{#if stats.totalGames === 0}
	<p class="py-12 text-center text-sm text-muted-foreground">{$_('stats.no_data')}</p>
{:else}
	<div class="space-y-6">
		<section class="space-y-3">
			<SectionLabel label={$_('stats.overview')} icon={PresentationChartIcon} />
			<RecordOverview
				totalLabel={$_('stats.games')}
				total={stats.totalGames}
				wins={stats.overall.wins}
				{losses}
			/>
		</section>

		{#if stats.setDistribution.length > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('stats.how_games_end')} icon={ChartDonutIcon} />
				<SetDistributionRadial buckets={stats.setDistribution} />
			</section>
		{/if}

		{#if stats.opponentBuckets.length > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('stats.by_opponent')} icon={UsersThreeIcon} />
				<OpponentBreakdown buckets={stats.opponentBuckets} />
			</section>
		{/if}

		{#if stats.monthly.length > 0}
			<section class="space-y-3">
				<SectionLabel label={$_('stats.by_month')} icon={CalendarDotsIcon} />
				<MonthlyForm monthly={stats.monthly} />
			</section>
		{/if}

		<section class="space-y-3">
			<SectionLabel label={$_('stats.highlights')} icon={MedalIcon} />
			<SeasonHighlights {stats} />
		</section>
	</div>
{/if}
