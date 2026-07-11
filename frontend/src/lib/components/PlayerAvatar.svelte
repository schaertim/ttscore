<script lang="ts">
	import { formatName, classificationColors } from '$lib/utils';

	interface Props {
		fullName: string;
		classification?: string | null;
		size?: 'sm' | 'md' | 'lg';
	}

	let { fullName, classification, size = 'md' }: Props = $props();

	// Format from storage format before computing initials so they read "F L" not "L F"
	const initials = $derived(
		formatName(fullName)
			.split(' ')
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0].toUpperCase())
			.join('')
	);

	const sizeClasses = {
		sm: 'h-8 w-8 text-2xs',
		md: 'h-9 w-9 text-xs',
		lg: 'h-12 w-12 text-sm'
	};

	// When a classification is given, tint the avatar with its class color
	// (muted background, vivid text/ring) instead of the neutral default.
	const colorClass = $derived(
		classification ? classificationColors(classification) : 'bg-muted text-muted-foreground'
	);
</script>

<div
	class="m-1.5 flex shrink-0 items-center justify-center rounded-full leading-none font-black
	       tracking-tight ring-1 ring-current/30 {colorClass} {sizeClasses[size]}"
>
	{initials}
</div>
