<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats, RadarStats } from '$lib/api';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import { LineChart } from 'layerchart';
	import { curveLinearClosed } from 'd3-shape';
	import { scaleBand } from 'd3-scale';
	import { radarMetrics } from '$lib/utils';

	type RadarPlayer = {
		stats: PlayerSeasonStats | RadarStats;
		label: string;
		color: string;
	};

	interface Props {
		players: RadarPlayer[];
	}

	let { players }: Props = $props();

	const single = $derived(players.length === 1);

	// One data row per metric, with a score column per player (p0, p1, …).
	const chartData = $derived.by(() => {
		const metricsByPlayer = players.map((p) => radarMetrics(p.stats));
		const axes: { key: keyof ReturnType<typeof radarMetrics>; label: string }[] = [
			{ key: 'form', label: $_('stats.radar_form') },
			{ key: 'clutch', label: $_('stats.radar_clutch') },
			{ key: 'grit', label: $_('stats.radar_grit') },
			{ key: 'punch', label: $_('stats.radar_punch') },
			{ key: 'resilience', label: $_('stats.radar_resilience') },
			{ key: 'consistency', label: $_('stats.radar_consistency') }
		];
		return axes.map((axis) => {
			const row: Record<string, string | number> = { metric: axis.label };
			metricsByPlayer.forEach((m, i) => (row[`p${i}`] = m[axis.key]));
			return row;
		});
	});

	const series = $derived(
		players.map((p, i) => ({
			key: `p${i}`,
			label: p.label,
			color: p.color,
			props: { class: 'stroke-[1.5]' }
		}))
	);

	const chartConfig = $derived(
		Object.fromEntries(
			players.map((p, i) => [`p${i}`, { label: p.label, color: p.color }])
		) satisfies Chart.ChartConfig
	);
</script>

<Chart.Container config={chartConfig} class="mx-auto aspect-square max-h-[250px]">
	<LineChart
		data={chartData}
		{series}
		radial
		x="metric"
		xScale={scaleBand()}
		points={{ r: 3, stroke: 'transparent' }}
		padding={12}
		props={{
			spline: {
				curve: curveLinearClosed,
				fillOpacity: single ? 0.15 : 0,
				motion: 'tween'
			},
			xAxis: { tickLength: 0 },
			yAxis: { format: () => '' },
			grid: { radialY: 'linear' },
			tooltip: { context: { mode: 'voronoi' } },
			highlight: { lines: false, points: false }
		}}
	>
		{#snippet tooltip()}
			<Chart.Tooltip />
		{/snippet}
	</LineChart>
</Chart.Container>
