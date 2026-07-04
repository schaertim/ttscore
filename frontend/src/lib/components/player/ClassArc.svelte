<script lang="ts">
	import { LineChart, Spline } from 'layerchart';
	import { scaleLinear, scaleUtc } from 'd3-scale';
	import { curveStepAfter } from 'd3-shape';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import { classColorVar, classificationRank, classLabelForRank } from '$lib/utils';
	import { _, locale } from 'svelte-i18n';
	import type { CareerClassPoint } from '$lib/api';

	let { progression }: { progression: CareerClassPoint[] } = $props();

	// Each (season, half) maps to a representative date: first half ≈ Oct (season start year),
	// second half ≈ Mar (following year). y is the class ladder rank (1–22).
	const points = $derived(
		progression
			.map((p) => {
				const startYear = parseInt(p.seasonName.slice(0, 4));
				const date =
					p.half === 'first'
						? new Date(Date.UTC(startYear, 9, 1))
						: new Date(Date.UTC(startYear + 1, 2, 1));
				return { date, rank: classificationRank(p.classification) };
			})
			.filter((p) => p.rank > 0)
			.sort((a, b) => a.date.getTime() - b.date.getTime())
	);

	const latestClass = $derived(progression.at(-1)?.classification);
	const color = $derived(classColorVar(latestClass));

	const ranks = $derived(points.map((p) => p.rank));
	const yMin = $derived(ranks.length ? Math.max(1, Math.min(...ranks) - 1) : 1);
	const yMax = $derived(ranks.length ? Math.min(22, Math.max(...ranks) + 1) : 22);

	const chartConfig = $derived({
		rank: { label: $_('career.class'), color }
	} as Chart.ChartConfig);
</script>

{#if points.length < 2}
	<div class="flex h-full items-center justify-center">
		<p class="text-sm text-muted-foreground">{$_('career.no_class_data')}</p>
	</div>
{:else}
	<Chart.Container config={chartConfig} class="aspect-auto h-full">
		<LineChart
			data={points}
			x="date"
			xScale={scaleUtc()}
			yScale={scaleLinear()}
			yDomain={[yMin, yMax]}
			axis={true}
			padding={{ right: 16, bottom: 20, left: 36 }}
			grid={false}
			rule={false}
			series={[{ key: 'rank', label: $_('career.class'), color }]}
			props={{
				xAxis: {
					format: (v: Date) => v.toLocaleDateString($locale ?? 'de', { year: 'numeric' })
				},
				yAxis: { format: (v: number) => classLabelForRank(v) }
			}}
		>
			{#snippet marks()}
				<Spline seriesKey="rank" strokeWidth={2} curve={curveStepAfter} />
			{/snippet}
		</LineChart>
	</Chart.Container>
{/if}
