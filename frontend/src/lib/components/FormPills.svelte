<script lang="ts">
	import { CheckCircleIcon, XCircleIcon, MinusCircleIcon } from 'phosphor-svelte';

	interface Props {
		/** Results newest first ("W" | "L" | "D"); rendered oldest → newest. */
		form: string[];
		size?: number;
		class?: string;
	}

	let { form, size = 16, class: className = '' }: Props = $props();
</script>

{#if form.length > 0}
	<div class="flex {className}">
		{#each [...form].reverse() as result, i (i)}
			{#if result === 'W'}
				<CheckCircleIcon weight="fill" {size} class="text-win" />
			{:else if result === 'L'}
				<XCircleIcon weight="fill" {size} class="text-loss" />
			{:else}
				<MinusCircleIcon weight="fill" {size} class="text-muted-foreground/50" />
			{/if}
		{/each}
	</div>
{/if}
