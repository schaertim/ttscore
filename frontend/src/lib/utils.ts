import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}

const KLASS_CLASSES: Record<string, string> = {
	A: 'text-klass-a bg-klass-a-subtle',
	B: 'text-klass-b bg-klass-b-subtle',
	C: 'text-klass-c bg-klass-c-subtle',
	D: 'text-klass-d bg-klass-d-subtle',
	E: 'text-klass-e bg-klass-e-subtle',
};

export function klassColors(klass: string | null | undefined): string {
	if (!klass) return 'text-muted-foreground bg-muted';
	const letter = klass[0].toUpperCase();
	return KLASS_CLASSES[letter] ?? 'text-muted-foreground bg-muted';
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type WithoutChild<T> = T extends { child?: any } ? Omit<T, "child"> : T;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type WithoutChildren<T> = T extends { children?: any } ? Omit<T, "children"> : T;
export type WithoutChildrenOrChild<T> = WithoutChildren<WithoutChild<T>>;
export type WithElementRef<T, U extends HTMLElement = HTMLElement> = T & { ref?: U | null };
