<script lang="ts">
	import { AnnotationLine, Highlight, LinearGradient, LineChart, Spline } from 'layerchart';
	import { scaleLinear, scaleUtc } from 'd3-scale';
	import { curveLinear } from 'd3-shape';
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

	// Fallback stroke colour (the gradient overrides it): the latest class.
	const color = $derived(classColorVar(points.at(-1) ? classLabelForRank(points.at(-1)!.rank) : null));

	const ranks = $derived(points.map((p) => p.rank));
	const yMin = $derived(ranks.length ? Math.max(1, Math.min(...ranks) - 1) : 1);
	const yMax = $derived(ranks.length ? Math.min(22, Math.max(...ranks) + 1) : 22);

	// Class-letter band boundaries on the ladder (D≤5, C 6–10, B 11–15, A≥16). The line is
	// stroked with a vertical gradient that switches colour hard at each boundary, so its
	// colour always reflects the class band at that height.
	const BAND_BOUNDARIES = [15.5, 10.5, 5.5];
	const bandColor = (rank: number) => classColorVar(classLabelForRank(Math.round(rank)));

	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	function gradientStops(context: any): [number, string][] {
		const total = context.height + context.padding.top + context.padding.bottom;
		const offset = (rank: number) => context.yScale(rank) / total;
		const stops: [number, string][] = [];
		// Top of the domain downward; higher rank sits nearer the top (offset 0).
		let bandAbove = bandColor(yMax);
		stops.push([0, bandAbove]);
		for (const boundary of BAND_BOUNDARIES) {
			if (boundary <= yMin || boundary >= yMax) continue;
			const o = offset(boundary);
			const bandBelow = bandColor(boundary - 0.5);
			stops.push([o, bandAbove], [o, bandBelow]); // duplicate offset = hard edge
			bandAbove = bandBelow;
		}
		stops.push([1, bandAbove]);
		return stops;
	}

	// Dashed reference lines, labelled on the right — mirrors the ELO chart's threshold
	// annotations. Only classes the player actually held are shown, to avoid clutter.
	const classLines = $derived.by(() => {
		const uniqueRanks = [...new Set(ranks)].sort((a, b) => a - b);
		return uniqueRanks.map((rank) => ({ rank, label: classLabelForRank(rank) }));
	});

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
			axis="x"
			padding={{ right: 20, bottom: 20, left: 10 }}
			grid={false}
			rule={false}
			series={[{ key: 'rank', label: $_('career.class'), color }]}
			props={{
				xAxis: {
					format: (v: Date) => v.toLocaleDateString($locale ?? 'de', { year: 'numeric' })
				}
			}}
		>
			{#snippet marks({ context })}
				<LinearGradient stops={gradientStops(context)} units="userSpaceOnUse" vertical>
					{#snippet children({ gradient })}
						<Spline seriesKey="rank" stroke={gradient} strokeWidth={2} curve={curveLinear} />
					{/snippet}
				</LinearGradient>
				{#each classLines as line (line.rank)}
					<AnnotationLine
						y={line.rank}
						label={line.label}
						labelPlacement="right"
						props={{
							line: {
								style: 'stroke: var(--foreground); stroke-opacity: 0.3',
								'stroke-dasharray': '4 3'
							},
							label: { class: 'fill-foreground/30 text-2xs' }
						}}
					/>
				{/each}
			{/snippet}

			{#snippet highlight({ context })}
				{#if context.tooltip.data}
					<Highlight lines points={{ fill: bandColor(context.y(context.tooltip.data)) }} />
				{/if}
			{/snippet}

			{#snippet tooltip({ context })}
				{@const rank = context.tooltip.data ? context.y(context.tooltip.data) : 0}
				<Chart.Tooltip
					color={bandColor(rank)}
					labelFormatter={(v: Date) => v.toLocaleDateString($locale ?? 'de', { year: 'numeric' })}
				>
					{#snippet formatter({ value })}
						<div class="flex flex-1 items-center gap-2">
							<div
								class="size-2.5 shrink-0 rounded-[2px]"
								style="background-color: {bandColor(Number(value))}"
							></div>
							<span class="text-muted-foreground">{$_('career.class')}</span>
							<span class="ml-auto font-mono font-medium text-foreground">
								{classLabelForRank(Number(value))}
							</span>
						</div>
					{/snippet}
				</Chart.Tooltip>
			{/snippet}
		</LineChart>
	</Chart.Container>
{/if}
