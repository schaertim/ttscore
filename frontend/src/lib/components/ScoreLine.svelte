<script lang="ts">
	import { cn } from '$lib/utils';

	type Tone = 'win' | 'loss' | 'neutral' | 'default';

	interface Segment {
		value: number | string;
		tone?: Tone;
	}

	let { segments, class: className = '' }: { segments: Segment[]; class?: string } = $props();

	const TONE_CLASS: Record<Tone, string> = {
		win: 'text-win',
		loss: 'text-loss',
		neutral: 'text-muted-foreground',
		default: 'text-foreground'
	};
</script>

<!-- A row of colored, dash-separated numbers (e.g. W–D–L, sets, points).
     Sized to match StatTile's default value so it reads as the card value. -->
<p class={cn('text-xl leading-none font-black tabular-nums', className)}>
	{#each segments as segment, i (i)}
		{#if i > 0}
			<span class="font-normal text-muted-foreground/40">–</span>
		{/if}
		<span class={TONE_CLASS[segment.tone ?? 'default']}>{segment.value}</span>
	{/each}
</p>
