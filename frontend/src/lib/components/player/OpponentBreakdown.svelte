<script lang="ts">
	import type { OpponentBucket } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import { cn } from '$lib/utils';

	interface Props {
		buckets: OpponentBucket[];
	}

	let { buckets }: Props = $props();

	function pct(b: OpponentBucket): number {
		return b.games > 0 ? Math.round((b.wins / b.games) * 100) : 0;
	}

	function barTone(p: number): string {
		return p >= 50 ? 'bg-win' : p >= 33 ? 'bg-foreground/40' : 'bg-loss';
	}
</script>

<Card.Root class="gap-3.5 p-5">
	{#each buckets as bucket (bucket.label)}
		{@const p = pct(bucket)}
		{@const isAggregate = bucket.label === 'HIGHER' || bucket.label === 'LOWER'}
		<div class="flex items-center gap-1">
			<span class="flex shrink-0 items-center gap-0.5">
				<span class="w-3.5 text-center text-xs font-semibold text-muted-foreground">
					{#if isAggregate}{bucket.label === 'HIGHER' ? '≥' : '≤'}{/if}
				</span>
				<span class="inline-flex w-10 items-center">
					<ClassBadge classification={isAggregate ? bucket.nearClass : bucket.label} />
				</span>
			</span>
			<div class="h-2 flex-1 overflow-hidden rounded-full bg-muted">
				<div class={cn('h-full rounded-full', barTone(p))} style="width: {p}%"></div>
			</div>
			<span class="w-9 shrink-0 text-right font-mono font-semibold text-sm tabular-nums">{p}%</span>
		</div>
	{/each}
</Card.Root>
