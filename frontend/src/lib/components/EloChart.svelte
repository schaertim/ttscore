<script lang="ts">
	import { AnnotationLine, LineChart, Spline } from 'layerchart';
	import { scaleLinear, scaleUtc } from 'd3-scale';
	import { curveMonotoneX } from 'd3-shape';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import { ELO_THRESHOLDS } from '$lib/utils';
	import { _, locale } from 'svelte-i18n';
	import type { EloEntry } from '$lib/api';

	interface EloChartSeries {
		label: string;
		/** Stroke colour (CSS var or value). */
		color: string;
		history: EloEntry[];
	}

	let { series }: { series: EloChartSeries[] } = $props();

	const PAD = 30;

	const parsed = $derived(
		series.map((s) => ({
			label: s.label,
			color: s.color,
			points: s.history
				.map((e) => ({ date: new Date(e.recordedAt), value: e.eloValue }))
				.sort((a, b) => a.date.getTime() - b.date.getTime())
		}))
	);

	const drawable = $derived(parsed.filter((s) => s.points.length >= 2));

	const allValues = $derived(parsed.flatMap((s) => s.points.map((p) => p.value)));
	const yMin = $derived(allValues.length ? Math.min(...allValues) - PAD : 0);
	const yMax = $derived(allValues.length ? Math.max(...allValues) + PAD : 0);

	// Union of all recorded timestamps, ascending.
	const dates = $derived.by(() => {
		const seen = new Map<number, Date>();
		for (const s of parsed) for (const p of s.points) seen.set(p.date.getTime(), p.date);
		return [...seen.values()].sort((a, b) => a.getTime() - b.getTime());
	});

	// One row per union date; each series carries its last known ELO forward (ELO is
	// constant between games), and is null before that player's first record.
	const rows = $derived.by(() =>
		dates.map((date) => {
			const row: Record<string, number | Date | null> = { date };
			parsed.forEach((s, i) => {
				let v: number | null = null;
				for (const p of s.points) {
					if (p.date.getTime() <= date.getTime()) v = p.value;
					else break;
				}
				row[`p${i}`] = v;
			});
			return row;
		})
	);

	const chartSeries = $derived(
		parsed.map((s, i) => ({ key: `p${i}`, label: s.label, color: s.color }))
	);
	const chartConfig = $derived(
		Object.fromEntries(parsed.map((s, i) => [`p${i}`, { label: s.label, color: s.color }])) as Chart.ChartConfig
	);

	const visibleThresholds = $derived(ELO_THRESHOLDS.filter(([elo]) => elo > yMin && elo < yMax));
</script>

{#if drawable.length === 0}
	<div class="flex h-full items-center justify-center">
		<p class="text-sm text-muted-foreground">{$_('player.no_elo_data')}</p>
	</div>
{:else}
	<Chart.Container config={chartConfig} class="aspect-auto h-full">
		<LineChart
			data={rows}
			x="date"
			xScale={scaleUtc()}
			yScale={scaleLinear()}
			yDomain={[yMin, yMax]}
			axis="x"
			padding={{ right: 20, bottom: 20, left: 10 }}
			grid={false}
			rule={false}
			series={chartSeries}
			props={{
				xAxis: {
					format: (v: Date) =>
						v.toLocaleDateString($locale ?? 'de', { month: 'short', year: '2-digit' })
				},
				highlight: { points: { r: 4 } }
			}}
		>
			{#snippet marks({ context })}
				{#each context.series.visibleSeries as s (s.key)}
					<Spline
						seriesKey={s.key}
						strokeWidth={2}
						curve={curveMonotoneX}
						defined={(d: Record<string, number | Date | null>) => d[s.key] != null}
					/>
				{/each}
				{#each visibleThresholds as [elo, label] (elo)}
					<AnnotationLine
						y={elo}
						{label}
						labelPlacement="right"
						props={{
							line: { style: 'stroke: var(--foreground); stroke-opacity: 0.3', 'stroke-dasharray': '4 3' },
							label: { class: 'fill-foreground/30 text-2xs' }
						}}
					/>
				{/each}
			{/snippet}
			{#snippet tooltip()}
				<Chart.Tooltip hideLabel />
			{/snippet}
		</LineChart>
	</Chart.Container>
{/if}
