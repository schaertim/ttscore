<script lang="ts">
	import type { Player, PlayerGame } from '$lib/api';
	import { ArrowUp, ArrowDown } from 'phosphor-svelte';

	interface Props {
		player: Player;
		recentMatches: Promise<PlayerGame[]>;
	}

	let { player, recentMatches }: Props = $props();

	const klassLetter = $derived((player.klass?.[0] ?? '').toLowerCase());
	const klassVar = $derived(klassLetter ? `var(--klass-${klassLetter})` : 'var(--muted-foreground)');

	// ── Sparkline ─────────────────────────────────────────────────────────────────

	function buildSparklinePoints(matches: PlayerGame[], currentElo: number): number[] {
		const games = matches
			.filter((g) => g.eloDelta != null && g.status !== 'SCHEDULED')
			.slice(0, 12);
		if (games.length === 0) return [currentElo];
		const pts: number[] = new Array(games.length + 1);
		pts[games.length] = currentElo;
		for (let i = games.length - 1; i >= 0; i--) {
			pts[i] = pts[i + 1] - (games[i].eloDelta ?? 0);
		}
		return pts;
	}

	function buildLinePath(pts: number[], w = 300, h = 56): string {
		if (pts.length < 2) return '';
		const min = Math.min(...pts);
		const max = Math.max(...pts);
		const range = max - min || 10;
		return pts
			.map((p, i) => {
				const x = (i / (pts.length - 1)) * w;
				const y = h - ((p - min) / range) * (h - 8) - 4;
				return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
			})
			.join(' ');
	}

	function buildFillPath(pts: number[], w = 300, h = 56): string {
		const line = buildLinePath(pts, w, h);
		return line ? `${line} L${w},${h} L0,${h} Z` : '';
	}

	function endDotY(pts: number[], h = 56): number {
		const min = Math.min(...pts);
		const max = Math.max(...pts);
		const range = max - min || 10;
		return h - ((pts[pts.length - 1] - min) / range) * (h - 8) - 4;
	}

	// ── Month delta ───────────────────────────────────────────────────────────────

	function monthDelta(matches: PlayerGame[]): number {
		const now = new Date();
		return matches
			.filter((g) => {
				if (!g.playedAt || g.eloDelta == null) return false;
				const d = new Date(g.playedAt);
				return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
			})
			.reduce((sum, g) => sum + (g.eloDelta ?? 0), 0);
	}
</script>

<a href="/players/{player.id}" class="block py-4">
	<div class="relative overflow-hidden rounded-2xl bg-card border border-border/50 p-5">

		<!-- Top row: name/club + glowing class badge -->
		<div class="flex items-start justify-between gap-4">
			<div class="min-w-0">
				<h2 class="text-xl font-black leading-tight tracking-tight">
					{player.fullName}
				</h2>
				{#if player.currentClubName}
					<p class="mt-0.5 text-xs text-muted-foreground">{player.currentClubName}</p>
				{/if}
			</div>

			{#if player.klass}
				<!-- Class badge with colour glow -->
				<div
					class="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl text-lg font-black text-white"
					style="
						background: {klassVar};
						box-shadow: 0 0 18px color-mix(in srgb, {klassVar} 70%, transparent),
						            0 0 40px color-mix(in srgb, {klassVar} 35%, transparent);
					"
				>
					{player.klass}
				</div>
			{/if}
		</div>

		<!-- ELO number + label -->
		<div class="mt-4">
			<p class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">ELO Rating</p>
			<p class="mt-0.5 text-5xl font-black tabular-nums leading-none">
				{player.currentElo ?? '—'}
			</p>
		</div>

		<!-- Month delta (async) -->
		{#await recentMatches then matches}
			{@const delta = monthDelta(matches)}
			{@const rounded = Math.round(delta)}
			{#if rounded !== 0}
				<p class="mt-2 flex items-center gap-1 text-sm font-bold {delta > 0 ? 'text-emerald-500' : 'text-red-500'}">
					{#if delta > 0}
						<ArrowUp size={14} weight="bold" />+{rounded} this month
					{:else}
						<ArrowDown size={14} weight="bold" />{rounded} this month
					{/if}
				</p>
			{/if}
		{/await}

		<!-- Sparkline (async) -->
		{#if player.currentElo}
			{#await recentMatches then matches}
				{@const pts = buildSparklinePoints(matches, player.currentElo)}
				{#if pts.length >= 2}
					<div class="mt-5 h-14 w-full">
						<svg
							viewBox="0 0 300 56"
							preserveAspectRatio="none"
							class="h-full w-full"
							aria-hidden="true"
						>
							<defs>
								<linearGradient id="hero-fill-{player.klass}" x1="0" y1="0" x2="0" y2="1">
									<stop offset="0%" style="stop-color:{klassVar}; stop-opacity:0.4" />
									<stop offset="100%" style="stop-color:{klassVar}; stop-opacity:0" />
								</linearGradient>
							</defs>
							<!-- Fill area -->
							<path d={buildFillPath(pts)} fill="url(#hero-fill-{player.klass})" />
							<!-- Line -->
							<path
								d={buildLinePath(pts)}
								fill="none"
								stroke={klassVar}
								stroke-width="2.5"
								stroke-linecap="round"
								stroke-linejoin="round"
							/>
							<!-- End dot -->
							<circle cx="300" cy={endDotY(pts)} r="3.5" fill={klassVar} />
							<circle
								cx="300"
								cy={endDotY(pts)}
								r="6"
								fill={klassVar}
								opacity="0.25"
							/>
						</svg>
					</div>
				{/if}
			{/await}
		{/if}
	</div>
</a>
