<script lang="ts">
	import { classificationColors } from '$lib/utils';

	let {
		classification,
		size = 'sm',
		class: className = ''
	}: {
		classification: string | null | undefined;
		size?: 'sm' | 'md' | 'lg';
		class?: string;
	} = $props();

	const sizeClasses = {
		sm: 'px-1.5 py-0.5 text-2xs',
		md: 'px-2.5 py-0.5 text-sm',
		lg: 'px-3 py-0.5 text-lg'
	};
</script>

{#if classification}
	<span
		class="shrink-0 rounded-full font-black tracking-wide ring-1 ring-current/30 {sizeClasses[
			size
		]} {classificationColors(classification)} {className}"
	>
		{classification}
	</span>
{/if}
