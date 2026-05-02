<script lang="ts">
	type Variant = 'default' | 'win' | 'loss';

	type Props = {
		label: string;
		value: string;
		variant?: Variant;
		size?: 'sm' | 'md' | 'lg';
		class?: string;
	};

	let { label, value, variant = 'default', size = 'md', class: className = '' }: Props = $props();

	const valueClasses: Record<Variant, string> = {
		default: 'text-on-surface',
		win: 'text-win',
		loss: 'text-loss'
	};

	const sizeClasses: Record<string, string> = {
		sm: 'text-xl',
		md: 'text-3xl',
		lg: 'text-4xl'
	};
</script>

<div class={className}>
	<p class="text-label mb-1 text-on-surface-subtle uppercase">
		{label}
	</p>
	<p class="leading-none font-black tabular-nums {sizeClasses[size]} {valueClasses[variant]}">
		{value}
	</p>
</div>
