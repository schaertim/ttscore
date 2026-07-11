/**
 * Debounce [fn] by [delay] ms. The returned function postpones the call on every
 * invocation; `cancel()` drops a pending call (e.g. when the input is cleared or the
 * component unmounts).
 */
export function debounce<Args extends unknown[]>(fn: (...args: Args) => void, delay = 300) {
	let timer: ReturnType<typeof setTimeout> | undefined;
	const run = (...args: Args) => {
		clearTimeout(timer);
		timer = setTimeout(() => fn(...args), delay);
	};
	run.cancel = () => clearTimeout(timer);
	return run;
}
