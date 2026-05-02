<script lang="ts">
	import { klassColors } from '$lib/utils';

	let { klass }: { klass: string | null | undefined } = $props();
</script>

{#if klass}
	<span
		class="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-black tracking-wide {klassColors(klass)}"
	>
		{klass}
	</span>
{/if}
