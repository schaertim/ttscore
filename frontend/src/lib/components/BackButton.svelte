<script lang="ts">
	import { cn } from '$lib/utils';
	import { ArrowLeftIcon, CaretLeftIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';

	interface Props {
		class?: string;
	}

	// No default margin — page headers own the spacing (usually via `space-y-*`).
	let { class: className = '' }: Props = $props();
</script>

<button
	onclick={() => history.back()}
	class={cn(
		'flex h-8 items-center gap-1 text-muted-foreground transition-colors hover:text-foreground',
		className
	)}
>
	<ArrowLeftIcon size={16} />
	<span class="text-xs font-semibold tracking-widest uppercase">{$_('common.back')}</span>
</button>
