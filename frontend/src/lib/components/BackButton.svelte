<script lang="ts">
	import { cn } from '$lib/utils';
	import { CaretLeft } from 'phosphor-svelte';

	interface Props {
		class?: string;
	}

	let { class: className = 'mb-6' }: Props = $props();
</script>

<button
	onclick={() => history.back()}
	class={cn(
		'flex items-center gap-1 text-muted-foreground transition-colors hover:text-foreground',
		className
	)}
>
	<CaretLeft class="h-4 w-4" />
	<span class="text-xs font-medium tracking-widest uppercase">Back</span>
</button>
