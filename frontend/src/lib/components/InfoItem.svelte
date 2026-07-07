<script lang="ts" module>
	import { tv, type VariantProps } from 'tailwind-variants';

	export const infoItemVariants = tv({
		base: 'group/info-item flex w-full items-center border text-left transition-colors outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50',
		variants: {
			variant: {
				primary: 'border-primary/20 bg-primary/5 hover:bg-primary/10',
				muted: 'border-border bg-card hover:bg-accent'
			},
			size: {
				default: 'gap-4 rounded-2xl p-4',
				sm: 'gap-2 rounded-xl px-4 py-2.5'
			}
		},
		defaultVariants: {
			variant: 'primary',
			size: 'default'
		}
	});

	export type InfoItemVariant = VariantProps<typeof infoItemVariants>['variant'];
	export type InfoItemSize = VariantProps<typeof infoItemVariants>['size'];
</script>

<script lang="ts">
	import type { Component, Snippet } from 'svelte';
	import { cn } from '$lib/utils';
	import { CaretRightIcon } from 'phosphor-svelte';

	interface Props {
		title: string;
		description?: string;
		/** Leading icon (e.g. a phosphor-svelte icon component). */
		icon?: Component;
		variant?: InfoItemVariant;
		size?: InfoItemSize;
		/** Render as an anchor. Omit to render as a <button>. */
		href?: string;
		type?: 'button' | 'submit';
		onclick?: () => void;
		disabled?: boolean;
		class?: string;
		/** Trailing content. Defaults to a chevron. */
		trailing?: Snippet;
	}

	let {
		title,
		description,
		icon: Icon,
		variant = 'primary',
		size = 'default',
		href,
		type = 'button',
		onclick,
		disabled = false,
		class: className,
		trailing
	}: Props = $props();
</script>

{#snippet body()}
	{#if Icon}
		{#if size === 'default'}
			<div
				class={cn(
					'shrink-0 rounded-full p-2',
					variant === 'primary' ? 'bg-primary/10' : 'bg-muted'
				)}
			>
				<Icon size="20" class="text-primary" />
			</div>
		{:else}
			<Icon size="16" class="shrink-0 text-primary" />
		{/if}
	{/if}

	<div class="min-w-0 flex-1">
		<p class={cn('text-sm', size === 'sm' ? 'font-medium text-muted-foreground' : 'font-semibold')}>
			{title}
		</p>
		{#if description}
			<p class="mt-0.5 text-xs text-muted-foreground">{description}</p>
		{/if}
	</div>

	<div class="shrink-0">
		{#if trailing}
			{@render trailing()}
		{:else}
			<CaretRightIcon size={size === 'sm' ? '14' : '16'} class="text-muted-foreground" />
		{/if}
	</div>
{/snippet}

{#if href}
	<a {href} {onclick} class={cn(infoItemVariants({ variant, size }), className)}>
		{@render body()}
	</a>
{:else}
	<button {type} {onclick} {disabled} class={cn(infoItemVariants({ variant, size }), className)}>
		{@render body()}
	</button>
{/if}
