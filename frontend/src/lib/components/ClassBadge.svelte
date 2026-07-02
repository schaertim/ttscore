<script lang="ts">
	import { classificationColors } from '$lib/utils';

	let {
		classification,
		size = 'sm',
		class: className = ''
	}: { classification: string | null | undefined; size?: 'sm' | 'lg'; class?: string } = $props();
</script>

{#if classification}
	{#if size === 'lg'}
		<span
			class="shrink-0 rounded-full px-3 py-0.5 text-lg font-black tracking-wide ring-1 ring-current/30 {classificationColors(
				classification
			)} {className}"
		>
			{classification}
		</span>
	{:else}
		<span
			class="shrink-0 rounded-full px-1.5 py-0.5 text-2xs font-black tracking-wide ring-1 ring-current/30 {classificationColors(
				classification
			)} {className}"
		>
			{classification}
		</span>
	{/if}
{/if}
