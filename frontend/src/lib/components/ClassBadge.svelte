<script lang="ts">
	import { classificationColors } from '$lib/utils';

	let { classification }: { classification: string | null | undefined } = $props();
</script>

{#if classification}
	<span
		class="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-black tracking-wide ring-1 ring-current/30 {classificationColors(
			classification
		)}"
	>
		{classification}
	</span>
{/if}
