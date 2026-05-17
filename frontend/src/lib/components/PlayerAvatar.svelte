<script lang="ts">
	interface Props {
		fullName: string;
		size?: 'sm' | 'md' | 'lg';
	}

	let { fullName, size = 'md' }: Props = $props();

	const initials = $derived(fullName.split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join(''));

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
