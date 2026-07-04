<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { CareerRival } from '$lib/api';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import { formatName } from '$lib/utils';
	import { CaretRightIcon } from 'phosphor-svelte';

	let { rivals }: { rivals: CareerRival[] } = $props();
</script>

<div class="divide-y divide-border/50 overflow-hidden rounded-2xl border border-border bg-card">
	{#each rivals as rival (rival.opponentId)}
		<a
			href="/players/{rival.opponentId}"
			class="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-accent"
		>
			<div class="min-w-0 flex-1">
				<div class="flex items-center gap-1.5">
					<span class="truncate text-sm font-semibold">{formatName(rival.opponentName)}</span>
					<ClassBadge classification={rival.opponentClass} />
				</div>
				<p class="text-2xs tracking-wide text-muted-foreground">
					{$_('career.meetings', { values: { count: rival.meetings } })}
				</p>
			</div>

			<ScoreLine
				class="text-base"
				segments={[
					{ value: rival.wins, tone: 'win' },
					{ value: rival.losses, tone: 'loss' }
				]}
			/>
			<CaretRightIcon size="16" class="shrink-0 text-muted-foreground" />
		</a>
	{/each}
</div>
