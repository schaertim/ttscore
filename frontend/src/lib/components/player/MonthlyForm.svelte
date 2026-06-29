<script lang="ts">
	import { locale, _ } from 'svelte-i18n';
	import type { MonthlyForm } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import { scaleBand } from 'd3-scale';
	import { BarChart, Highlight } from 'layerchart';
	import { cubicInOut } from 'svelte/easing';

	interface Props {
		monthly: MonthlyForm[];
	}

	let { monthly }: Props = $props();

	const chartConfig = {
		wins: { label: 'W', color: 'var(--color-win)' },
		losses: { label: 'L', color: 'var(--color-loss)' }
	} satisfies Chart.ChartConfig;

	const chartData = $derived(
		monthly.map((m) => {
			const [year, mo] = m.month.split('-').map(Number);
			const label = new Date(year, mo - 1).toLocaleDateString($locale ?? 'de', { month: 'short' });
			return { month: label, wins: m.wins, losses: m.losses };
		})
	);
</script>

<Card.Root class="p-5">
	<Chart.Container config={chartConfig} class="h-40 w-full">
		<BarChart
			data={chartData}
			xScale={scaleBand().padding(0.25)}
			x="month"
			axis="x"
			rule={false}
			series={[
				{
					key: 'losses',
					label: $_('stats.losses'),
					color: chartConfig.losses.color,
					props: { rounded: 'bottom' }
				},
				{
					key: 'wins',
					label: $_('stats.wins'),
					color: chartConfig.wins.color,
					props: { rounded: 'top' }
				}
			]}
			seriesLayout="stack"
			legend
			props={{
				bars: { stroke: 'none', motion: { type: 'tween', duration: 400, easing: cubicInOut } },
				highlight: { area: false },
				xAxis: { format: (d: string) => d }
			}}
		>
			{#snippet belowMarks()}
				<Highlight area={{ class: 'fill-muted' }} />
			{/snippet}
			{#snippet tooltip()}
				<Chart.Tooltip />
			{/snippet}
		</BarChart>
	</Chart.Container>
</Card.Root>
