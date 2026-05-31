<script lang="ts">
	import { formatName } from '$lib/utils';

	interface Props {
		fullName: string;
		size?: 'sm' | 'md' | 'lg';
	}

	let { fullName, size = 'md' }: Props = $props();

	// Format from storage format before computing initials so they read "F L" not "L F"
	const initials = $derived(formatName(fullName).split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join(''));

	const sizeClasses = {
		sm: 'h-8 w-8 text-[10px]',
		md: 'h-9 w-9 text-[11px]',
		lg: 'h-12 w-12 text-sm'
	};
</script>

<div
	class="flex shrink-0 items-center justify-center rounded-full bg-muted font-black tracking-tight
	       text-muted-foreground ring-1 ring-border/50 {sizeClasses[size]}"
>
	{initials}
</div>
