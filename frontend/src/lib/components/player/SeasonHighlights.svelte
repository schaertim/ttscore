<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { PlayerSeasonStats } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import Overline from '$lib/components/Overline.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import { formatName } from '$lib/utils';

	interface Props {
		stats: PlayerSeasonStats;
	}

	let { stats }: Props = $props();
</script>

<div class="space-y-3">
	{#if stats.bestWinOpponentName}
		<Card.Root class="gap-1 p-4">
			<Overline>{$_('stats.best_win')}</Overline>
			<p class="mt-1 flex items-center gap-1.5 truncate text-base font-semibold">
				<span class="truncate">{formatName(stats.bestWinOpponentName)}</span>
				<ClassBadge classification={stats.bestWinOpponentClass} />
			</p>
		</Card.Root>
	{/if}

	<div class="grid grid-cols-2 gap-3">
		<Card.Root class="gap-1 p-4">
			<Overline>{$_('stats.longest_streak')}</Overline>
			<p class="text-2xl leading-none font-black text-win tabular-nums">{stats.longestWinStreak}</p>
		</Card.Root>
		<Card.Root class="gap-1 p-4">
			<Overline>{$_('stats.current_streak')}</Overline>
			<p class="text-2xl leading-none font-black tabular-nums">{stats.currentWinStreak}</p>
		</Card.Root>
	</div>
</div>
