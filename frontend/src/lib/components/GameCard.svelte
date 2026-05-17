<script lang="ts">
	import type { Game, PlayerGame } from '$lib/api';
	import { cn } from '$lib/utils';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import * as Card from '$lib/components/ui/card/index.js';

	let {
		mode = 'match',
		game
	}: {
		mode?: 'match' | 'player';
		game: Game | PlayerGame;
	} = $props();

	function toPlayerGame(g: Game | PlayerGame): PlayerGame { return g as PlayerGame; }
	function toGame(g: Game | PlayerGame): Game { return g as Game; }

	function lastName(name: string | null): string {
		if (!name) return '—';
		return name.split(' ').at(-1) ?? name;
	}

	function formatDate(dateStr: string | null | undefined): string {
		if (!dateStr) return '—';
		return new Date(dateStr).toLocaleDateString('de-CH', { day: '2-digit', month: '2-digit' });
	}

	function formatDelta(delta: number | null): string {
		if (delta == null) return '';
		const rounded = Math.round(delta);
		return rounded >= 0 ? `+${rounded}` : String(rounded);
	}

	function isWin(g: PlayerGame): boolean {
		return (
			(g.result === 'HOME' && g.playerSide === 'home') ||
			(g.result === 'AWAY' && g.playerSide === 'away')
		);
	}

	function playerSets(g: PlayerGame): number {
		return g.playerSide === 'home' ? (g.homeSets ?? 0) : (g.awaySets ?? 0);
	}

	function opponentSets(g: PlayerGame): number {
		return g.playerSide === 'home' ? (g.awaySets ?? 0) : (g.homeSets ?? 0);
	}
</script>

{#if mode === 'player'}
	{@const pg = toPlayerGame(game)}
	{@const won = isWin(pg)}
	{@const notPlayed = pg.result === 'NOT_PLAYED'}
	{@const scoreColor = won ? 'text-win' : notPlayed ? 'text-muted-foreground' : 'text-loss'}
	<a href="/matches/{pg.matchId}" class="block">
		<Card.Root class="py-4 transition-colors hover:bg-accent">
			<div class="space-y-2 px-6">
				<p class="truncate text-[10px] font-medium tracking-widest text-muted-foreground uppercase">
					{formatDate(pg.playedAt)} · {pg.homeTeam} vs {pg.awayTeam}
				</p>

				<div class="flex items-baseline gap-4">
					<div class="flex min-w-0 flex-1 items-center gap-1.5">
						{#if pg.opponentId}
							<a
								href="/players/{pg.opponentId}"
								class={cn('min-w-0 truncate text-lg font-normal hover:underline')}
								onclick={(e) => e.stopPropagation()}
							>{pg.opponentName ?? '—'}</a>
						{:else}
							<span class="min-w-0 truncate text-lg font-normal">{pg.opponentName ?? '—'}</span>
						{/if}
						<ClassBadge klass={pg.opponentKlass} />
					</div>

					<div class="flex shrink-0 flex-col items-end gap-0.5">
						<p class={cn('text-3xl font-black tabular-nums', scoreColor)}>
							{playerSets(pg)}:{opponentSets(pg)}
						</p>
						{#if pg.eloDelta != null}
							<span class="text-xs font-black text-muted-foreground">
								{formatDelta(pg.eloDelta)} ELO
							</span>
						{/if}
					</div>
				</div>

				{#if pg.sets.length > 0}
					<div class="flex flex-wrap gap-1.5">
						{#each pg.sets as set, i (i)}
							{@const playerWonSet =
								pg.playerSide === 'home'
									? set.homePoints > set.awayPoints
									: set.awayPoints > set.homePoints}
							<span
								class={cn(
									'rounded px-2.5 py-1 text-xs font-medium tabular-nums',
									playerWonSet ? 'bg-win/15 text-win' : 'bg-loss/15 text-loss'
								)}
							>
								{pg.playerSide === 'home'
									? `${set.homePoints}:${set.awayPoints}`
									: `${set.awayPoints}:${set.homePoints}`}
							</span>
						{/each}
					</div>
				{/if}
			</div>
		</Card.Root>
	</a>
{:else}
	{@const mg = toGame(game)}
	{@const homeWon = mg.result === 'HOME'}
	{@const awayWon = mg.result === 'AWAY'}
	<Card.Root class="py-4">
		<div class="space-y-3 px-6">
			<p class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase">
				Game #{mg.orderInMatch} · {mg.gameType}
			</p>

			<div class="flex items-center gap-4">
				<div class="min-w-0 flex-1 space-y-1.5">
					<div class="flex min-w-0 items-center gap-1.5">
						{#if mg.homePlayer2Name}
							<div class="flex min-w-0 shrink items-center gap-1">
								{#if mg.homePlayerId}
									<a
										href="/players/{mg.homePlayerId}"
										class={cn('truncate', homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground', 'hover:underline')}
									>{lastName(mg.homePlayerName)}</a>
								{:else}
									<span class={cn('truncate', homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.homePlayerName)}</span>
								{/if}
								<ClassBadge klass={mg.homePlayerKlass} />
							</div>
							<span class="shrink-0 text-muted-foreground/40">/</span>
							<div class="flex min-w-0 shrink items-center gap-1">
								{#if mg.homePlayer2Id}
									<a
										href="/players/{mg.homePlayer2Id}"
										class={cn('truncate', homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground', 'hover:underline')}
									>{lastName(mg.homePlayer2Name)}</a>
								{:else}
									<span class={cn('truncate', homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.homePlayer2Name)}</span>
								{/if}
								<ClassBadge klass={mg.homePlayer2Klass} />
							</div>
						{:else}
							{#if mg.homePlayerId}
								<a
									href="/players/{mg.homePlayerId}"
									class={cn('min-w-0 truncate', homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground', 'hover:underline')}
								>{mg.homePlayerName ?? '—'}</a>
							{:else}
								<span class={cn('min-w-0 truncate', homeWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{mg.homePlayerName ?? '—'}</span>
							{/if}
							<ClassBadge klass={mg.homePlayerKlass} />
						{/if}
					</div>
					<div class="flex min-w-0 items-center gap-1.5">
						{#if mg.awayPlayer2Name}
							<div class="flex min-w-0 shrink items-center gap-1">
								{#if mg.awayPlayerId}
									<a
										href="/players/{mg.awayPlayerId}"
										class={cn('truncate', awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground', 'hover:underline')}
									>{lastName(mg.awayPlayerName)}</a>
								{:else}
									<span class={cn('truncate', awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.awayPlayerName)}</span>
								{/if}
								<ClassBadge klass={mg.awayPlayerKlass} />
							</div>
							<span class="shrink-0 text-muted-foreground/40">/</span>
							<div class="flex min-w-0 shrink items-center gap-1">
								{#if mg.awayPlayer2Id}
									<a
										href="/players/{mg.awayPlayer2Id}"
										class={cn('truncate', awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground', 'hover:underline')}
									>{lastName(mg.awayPlayer2Name)}</a>
								{:else}
									<span class={cn('truncate', awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.awayPlayer2Name)}</span>
								{/if}
								<ClassBadge klass={mg.awayPlayer2Klass} />
							</div>
						{:else}
							{#if mg.awayPlayerId}
								<a
									href="/players/{mg.awayPlayerId}"
									class={cn('min-w-0 truncate', awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground', 'hover:underline')}
								>{mg.awayPlayerName ?? '—'}</a>
							{:else}
								<span class={cn('min-w-0 truncate', awayWon ? 'text-lg font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{mg.awayPlayerName ?? '—'}</span>
							{/if}
							<ClassBadge klass={mg.awayPlayerKlass} />
						{/if}
					</div>
				</div>

				<p class="shrink-0 text-3xl font-black text-muted-foreground tabular-nums">
					{mg.homeSets ?? 0}:{mg.awaySets ?? 0}
				</p>
			</div>

			{#if mg.sets && mg.sets.length > 0}
				<div class="flex flex-wrap gap-1.5">
					{#each mg.sets as set, i (i)}
						<span
							class="rounded bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground tabular-nums"
						>
							{set.homePoints}:{set.awayPoints}
						</span>
					{/each}
				</div>
			{/if}
		</div>
	</Card.Root>
{/if}
