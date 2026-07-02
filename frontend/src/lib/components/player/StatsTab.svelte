<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats } from '$lib/api';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import OpponentBreakdown from './OpponentBreakdown.svelte';
	import SetDistributionRadial from './SetDistributionRadial.svelte';
	import MonthlyForm from './MonthlyForm.svelte';
	import SeasonHighlights from './SeasonHighlights.svelte';
	import {
		UsersThreeIcon,
		PingPongIcon,
		CalendarBlankIcon,
		MedalIcon,
		ChartDonutIcon,
		CalendarDotsIcon
	} from 'phosphor-svelte';

	interface Props {
		stats: PlayerSeasonStats;
	}

	let { stats }: Props = $props();

	const losses = $derived(stats.overall.games - stats.overall.wins);
	const winPct = $derived(
		stats.overall.games > 0 ? Math.round((stats.overall.wins / stats.overall.games) * 100) : 0
	);
</script>

{#if stats.totalGames === 0}
	<p class="py-12 text-center text-sm text-muted-foreground">{$_('stats.no_data')}</p>
{:else}
	<div class="space-y-6">
		<div class="grid grid-cols-3 gap-3">
			<StatTile label={$_('stats.games')} labelPosition="bottom" align="center" value={stats.totalGames} />
			<StatTile label={$_('stats.record')} labelPosition="bottom" align="center">
				<ScoreLine
					segments={[
						{ value: stats.overall.wins, tone: 'win' },
						{ value: losses, tone: 'loss' }
					]}
				/>
			</StatTile>
			<StatTile label={$_('stats.win_rate')} labelPosition="bottom" align="center" value={`${winPct}%`} />
		</div>

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
