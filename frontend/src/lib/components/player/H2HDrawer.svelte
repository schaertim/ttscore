<script lang="ts">
	import { _, locale } from 'svelte-i18n';
	import { api, type HeadToHead, type H2HGame } from '$lib/api';
	import * as Drawer from '$lib/components/ui/drawer/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import PlayerRadar from './PlayerRadar.svelte';
	import { classColorVar, formatName, timeAgo } from '$lib/utils';
	import { TargetIcon, ClockCounterClockwiseIcon } from 'phosphor-svelte';

	interface Props {
		open: boolean;
		/** Always the signed-in user's player — shown on the left. */
		homePlayerId: string;
		/** The opponent to compare against. Changing this triggers a re-fetch. */
		opponentId: string | null;
	}

	let { open = $bindable(), homePlayerId, opponentId }: Props = $props();

	let data = $state<HeadToHead | null>(null);
	let loading = $state(false);
	let error = $state(false);

	let loadedKey = $state('');
	$effect(() => {
		if (!open || !opponentId) return;
		const key = `${homePlayerId}:${opponentId}`;
		if (key === loadedKey) return;
		loadedKey = key;
		loading = true;
		error = false;
		data = null;
		api.players
			.headToHead(homePlayerId, opponentId)
			.then((res) => (data = res))
			.catch(() => (error = true))
			.finally(() => (loading = false));
	});

	// Home player is always playerA (left). Opponent is always playerB (right).
	const clsA = $derived(data?.playerA.liveClassification ?? data?.playerA.classification ?? null);
	const clsB = $derived(data?.playerB.liveClassification ?? data?.playerB.classification ?? null);

	const colors = $derived<[string, string]>([classColorVar(clsA), 'var(--color-primary)']);

	const radarPlayers = $derived(
		data
			? [
					{ stats: data.statsA, label: formatName(data.playerA.fullName), color: colors[0] },
					{ stats: data.statsB, label: formatName(data.playerB.fullName), color: colors[1] }
				]
			: []
	);

	const setsRecord = $derived.by(() => {
		if (!data) return null;
		let left = 0,
			right = 0;
		for (const g of data.games) {
			left += g.aSets ?? 0;
			right += g.bSets ?? 0;
		}
		return { left, right };
	});

	const pointsRecord = $derived.by(() => {
		if (!data) return null;
		let left = 0,
			right = 0;
		for (const g of data.games) {
			for (const s of g.sets) {
				left += s.homePoints;
				right += s.awayPoints;
			}
		}
		return { left, right };
	});

	function fmtDate(iso: string | null): string {
		return iso ? timeAgo(iso, $locale ?? 'de') : '';
	}
</script>

<Drawer.Root bind:open>
	<Drawer.Content class="max-h-[88vh]">
		{#if loading}
			<div class="space-y-4 p-5">
				<Skeleton class="h-10 w-full rounded-lg" />
				<Skeleton class="h-16 w-full rounded-lg" />
				<Skeleton class="aspect-square max-h-[240px] w-full rounded-lg" />
			</div>
		{:else if error || !data}
			<div class="p-10 text-center text-sm text-muted-foreground">
				{$_('player.no_matches')}
			</div>
		{:else}
			<!-- Versus header -->
			<Drawer.Header class="border-b px-5 pt-2 pb-5">
				<div class="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
					<div class="min-w-0">
						<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
							{$_('h2h.you')}
						</p>
						<p class="mt-1 truncate text-sm font-semibold">{formatName(data.playerA.fullName)}</p>
						<div class="mt-1.5"><ClassBadge classification={clsA} /></div>
					</div>

					<div class="flex flex-col items-center gap-0.5 px-3">
						<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
							H2H
						</p>
						<div class="flex items-baseline gap-1.5 leading-none">
							<span class="text-2xl font-black tabular-nums text-win">{data.record.aWins}</span>
							<span class="text-base font-normal text-muted-foreground/40">–</span>
							<span class="text-2xl font-black tabular-nums text-loss">{data.record.bWins}</span>
						</div>
						<p class="text-2xs text-muted-foreground">
							{data.record.games}
							{$_('h2h.duels')}
						</p>
					</div>

					<div class="min-w-0 text-right">
						<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
							{$_('h2h.opponent')}
						</p>
						<p class="mt-1 truncate text-sm font-semibold">{formatName(data.playerB.fullName)}</p>
						<div class="mt-1.5 flex justify-end"><ClassBadge classification={clsB} /></div>
					</div>
				</div>
			</Drawer.Header>

			<div class="space-y-5 overflow-y-auto px-5 pt-4 pb-8">

				<!-- Sets & points record -->
				{#if setsRecord && (setsRecord.left > 0 || setsRecord.right > 0)}
					<section class="grid grid-cols-2 gap-3">
						<div class="rounded-lg bg-muted/40 px-4 py-3 text-center">
							<p class="text-2xs tracking-widest text-muted-foreground uppercase">
								{$_('h2h.sets')}
							</p>
							<div class="mt-1 flex items-baseline justify-center gap-1.5 tabular-nums">
								<span class="text-lg font-black" style="color:{colors[0]}">{setsRecord.left}</span>
								<span class="text-sm text-muted-foreground/50">–</span>
								<span class="text-lg font-black" style="color:{colors[1]}">{setsRecord.right}</span>
							</div>
						</div>
						{#if pointsRecord && (pointsRecord.left > 0 || pointsRecord.right > 0)}
							<div class="rounded-lg bg-muted/40 px-4 py-3 text-center">
								<p class="text-2xs tracking-widest text-muted-foreground uppercase">
									{$_('h2h.points')}
								</p>
								<div class="mt-1 flex items-baseline justify-center gap-1.5 tabular-nums">
									<span class="text-lg font-black" style="color:{colors[0]}"
										>{pointsRecord.left}</span
									>
									<span class="text-sm text-muted-foreground/50">–</span>
									<span class="text-lg font-black" style="color:{colors[1]}"
										>{pointsRecord.right}</span
									>
								</div>
							</div>
						{/if}
					</section>
				{/if}

				<!-- Radar overlay -->
				<section class="space-y-2">
					<SectionLabel label={$_('h2h.strengths')} icon={TargetIcon} />
					<PlayerRadar players={radarPlayers} />
					<div class="flex justify-center gap-4">
						<div class="flex items-center gap-1.5">
							<span class="size-2 rounded-full" style="background:{colors[0]}"></span>
							<span class="text-xs text-muted-foreground">{formatName(data.playerA.fullName)}</span>
						</div>
						<div class="flex items-center gap-1.5">
							<span class="size-2 rounded-full" style="background:{colors[1]}"></span>
							<span class="text-xs text-muted-foreground">{formatName(data.playerB.fullName)}</span>
						</div>
					</div>
				</section>

				<!-- Recent encounters -->
				{#if data.games.length > 0}
					<section class="space-y-2">
						<SectionLabel label={$_('h2h.recent_encounters')} icon={ClockCounterClockwiseIcon} />
						<div class="space-y-1.5">
							{#snippet gameRow(game: H2HGame)}
								<div class="min-w-0 flex-1">
									<p class="truncate text-xs font-medium">
										{game.competitionName ?? '—'}
									</p>
									<p class="text-2xs text-muted-foreground">{fmtDate(game.playedAt)}</p>
								</div>
								<div class="shrink-0 text-sm font-bold tabular-nums">
									<span style="color:{game.aWon ? colors[0] : 'var(--color-muted-foreground)'}">
										{game.aSets ?? '–'}
									</span>
									<span class="text-muted-foreground/40">:</span>
									<span style="color:{!game.aWon ? colors[1] : 'var(--color-muted-foreground)'}">
										{game.bSets ?? '–'}
									</span>
								</div>
							{/snippet}
							{#each data.games.slice(0, 6) as game (game.gameId)}
								{#if game.matchId}
									<a
										href="/matches/{game.matchId}"
										class="flex items-center gap-3 rounded-lg bg-muted/30 px-3 py-2 transition-colors hover:bg-muted/60"
									>
										{@render gameRow(game)}
									</a>
								{:else}
									<div class="flex items-center gap-3 rounded-lg bg-muted/30 px-3 py-2">
										{@render gameRow(game)}
									</div>
								{/if}
							{/each}
						</div>
					</section>
				{/if}
			</div>
		{/if}
	</Drawer.Content>
</Drawer.Root>
