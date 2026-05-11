<script lang="ts">
	import type { Snippet } from 'svelte';

	type Variant = 'default' | 'win' | 'loss' | 'neutral';

	type Props = {
		variant?: Variant;
		size?: 'sm' | 'md' | 'lg';
		class?: string;
		children: Snippet;
	};

	let { variant = 'default', size = 'md', class: className = '', children }: Props = $props();

	const variantClasses: Record<Variant, string> = {
		default: 'bg-card text-foreground ring-1 ring-border',
		win: 'bg-win/15 text-win ring-1 ring-win/30',
		loss: 'bg-loss/15 text-loss ring-1 ring-loss/30',
		neutral: 'bg-card text-muted-foreground ring-1 ring-border'
	};

	const sizeClasses: Record<string, string> = {
		sm: 'px-2   py-1   text-[10px] rounded-sm',
		md: 'px-2.5 py-1.5 text-xs     rounded-md',
		lg: 'px-3   py-2   text-sm     rounded-md'
	};
</script>

<span
	class="inline-flex items-center justify-center leading-none font-bold tabular-nums
         {variantClasses[variant]} {sizeClasses[size]} {className}"
>
	{@render children()}
</span>
