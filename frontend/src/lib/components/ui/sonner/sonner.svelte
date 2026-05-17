<script lang="ts">
	import { Toaster as Sonner, type ToasterProps as SonnerProps } from 'svelte-sonner';
	import { mode } from 'mode-watcher';
	import SpinnerIcon from 'phosphor-svelte/lib/SpinnerIcon';
	import CheckCircleIcon from 'phosphor-svelte/lib/CheckCircleIcon';
	import XCircleIcon from 'phosphor-svelte/lib/XCircleIcon';
	import InfoIcon from 'phosphor-svelte/lib/InfoIcon';
	import WarningIcon from 'phosphor-svelte/lib/WarningIcon';

	let { ...restProps }: SonnerProps = $props();
</script>

<Sonner
	theme={mode.current}
	class="toaster group"
	style="--normal-bg: var(--color-popover); --normal-text: var(--color-popover-foreground); --normal-border: var(--color-border);"
	{...restProps}
>
	{#snippet loadingIcon()}
		<SpinnerIcon size="16" class="animate-spin" />
	{/snippet}
	{#snippet successIcon()}
		<CheckCircleIcon size="16" />
	{/snippet}
	{#snippet errorIcon()}
		<XCircleIcon size="16" />
	{/snippet}
	{#snippet infoIcon()}
		<InfoIcon size="16" />
	{/snippet}
	{#snippet warningIcon()}
		<WarningIcon size="16" />
	{/snippet}
</Sonner>
