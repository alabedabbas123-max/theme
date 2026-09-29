package com.almlk.swiftkey.ime;

/**
 * Tiny manifest entry point kept deliberately simple for old AIDE packagers. The implementation
 * lives in AlmlkImeServiceCore so this required component never contains the large
 * anonymous-listener graph that triggered D8 failure.
 */
public final class AlmlkImeService extends AlmlkImeServiceCore {}
