<script lang="ts">
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
</script>

<!-- Mirrors FeedItemCard: entity icon · name/description lines · event badge. -->
<div class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3">
	<Skeleton class="h-9 w-7 shrink-0 rounded-lg" />
	<div class="flex-1 space-y-2">
		<Skeleton class="h-3.5 w-32 rounded" />
		<Skeleton class="h-3 w-48 rounded" />
	</div>
	<Skeleton class="h-8 w-8 shrink-0 rounded-sm" />
</div>
