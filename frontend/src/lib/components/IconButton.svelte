<script lang="ts" module>
	import { tv, type VariantProps } from 'tailwind-variants';

	export const iconButtonVariants = tv({
		base: 'flex items-center justify-center rounded-full p-2 transition-colors outline-none hover:bg-muted focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50',
		variants: {
			tone: {
				/** Low-emphasis default: muted icon that darkens on hover. */
				muted: 'text-muted-foreground hover:text-foreground',
				/** Active/engaged state (e.g. already following): full foreground colour. */
				foreground: 'text-foreground'
			}
		},
		defaultVariants: { tone: 'muted' }
	});

	export type IconButtonTone = VariantProps<typeof iconButtonVariants>['tone'];
</script>

<script lang="ts">
	import type { Snippet } from 'svelte';
	import { cn } from '$lib/utils';

	interface Props {
		tone?: IconButtonTone;
		type?: 'button' | 'submit';
		title?: string;
		ariaLabel?: string;
		disabled?: boolean;
		onclick?: (e: MouseEvent) => void;
		class?: string;
		children: Snippet;
	}

	let {
		tone = 'muted',
		type = 'button',
		title,
		ariaLabel,
		disabled = false,
		onclick,
		class: className,
		children
	}: Props = $props();
</script>

<button
	{type}
	{title}
	aria-label={ariaLabel}
	{disabled}
	{onclick}
	class={cn(iconButtonVariants({ tone }), className)}
>
	{@render children()}
</button>
