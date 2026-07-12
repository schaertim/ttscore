<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { HeadToHead } from '$lib/api';
	import * as Drawer from '$lib/components/ui/drawer/index.js';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import { formatShortName } from '$lib/utils';

	interface Props {
		h2h: HeadToHead;
		/** Display classifications (live where available), left/right. */
		clsA: string | null;
		clsB: string | null;
	}

	let { h2h, clsA, clsB }: Props = $props();
</script>

<Drawer.Header class="border-b px-5 pt-2 pb-5 text-left">
	<div class="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
		<a href="/players/{h2h.playerA.id}" class="min-w-0 text-left">
			<p class="truncate text-xs font-medium tracking-wide text-muted-foreground">
				{h2h.playerA.currentClubName ?? ''}
			</p>
			<p class="mt-1 truncate text-lg font-semibold hover:underline">
				{formatShortName(h2h.playerA.fullName)}
			</p>
			<div class="mt-1"><ClassBadge classification={clsA} /></div>
		</a>

		<div class="flex flex-col items-center gap-0.5 px-3">
			<div class="flex items-baseline gap-1.5 leading-none">
				<span class="font-mono text-3xl font-black text-win tabular-nums">{h2h.record.aWins}</span>
				<span class="text-3xl font-normal text-muted-foreground/40">–</span>
				<span class="font-mono text-3xl font-black text-loss tabular-nums">{h2h.record.bWins}</span>
			</div>
			<p class="text-xs text-muted-foreground">
				{h2h.record.games}
				{$_('h2h.duels')}
			</p>
		</div>

		<a href="/players/{h2h.playerB.id}" class="min-w-0 text-right">
			<p class="truncate text-xs font-medium tracking-wide text-muted-foreground">
				{h2h.playerB.currentClubName ?? ''}
			</p>
			<p class="mt-1 truncate text-lg font-semibold hover:underline">
				{formatShortName(h2h.playerB.fullName)}
			</p>
			<div class="mt-1 flex justify-end"><ClassBadge classification={clsB} /></div>
		</a>
	</div>
</Drawer.Header>
