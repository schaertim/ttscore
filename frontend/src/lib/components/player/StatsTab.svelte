<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
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
			<Card.Root class="items-center gap-0.5 py-4">
				<span class="text-xl font-black tabular-nums">{stats.totalGames}</span>
				<span class="text-2xs tracking-widest text-muted-foreground uppercase"
					>{$_('stats.games')}</span
				>
			</Card.Root>
			<Card.Root class="items-center gap-0.5 py-4">
				<span class="text-xl font-black tabular-nums">
					<span class="text-win">{stats.overall.wins}</span>
					<span class="font-normal text-muted-foreground/40">–</span>
					<span class="text-loss">{losses}</span>
				</span>
				<span class="text-2xs tracking-widest text-muted-foreground uppercase"
					>{$_('stats.record')}</span
				>
			</Card.Root>
			<Card.Root class="items-center gap-0.5 py-4">
				<span class="text-xl font-black tabular-nums">{winPct}%</span>
				<span class="text-2xs tracking-widest text-muted-foreground uppercase"
					>{$_('stats.win_rate')}</span
				>
			</Card.Root>
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
