package com.emc.mongoose.common.generator;

public class FormattingGenerator extends SimpleFormattingGenerator {

	/**
	 * Special characters
	 */
	public static final char PATTERN_SYMBOL = '%';
	public static final char[] RANGE_SYMBOLS = {'[',']'};
	public static final char RANGE_DELIMITER = '-';

	/**
	 * Segments (parts) of the input string that do not require changes
	 */
	private String[] segments;

	/**
	 * Generators of values that should be inserted instead of special characters (see above)
	 */
	private ValueGenerator<String>[] generators;

	public FormattingGenerator(final String pattern) {
		this(pattern, StringGeneratorFactory.generatorFactory());
	}

	public FormattingGenerator(final String pattern, final GeneratorFactory<String> generatorFactory) {
		super(pattern, generatorFactory);
	}

	@Override
	@SuppressWarnings("unchecked") // AsyncStringGeneratorFactory always returns ValueGenerator<String> values for generators[]
	protected void initialize(String pattern) {
		final int patternSymbolsNum = countPatternSymbols(pattern);
		generators = new ValueGenerator[patternSymbolsNum];
		segments = new String[patternSymbolsNum + 1];
		StringBuilder segmentsBuilder = new StringBuilder();
		StringBuilder patternBuilder = new StringBuilder(pattern);
		int segmentCounter = 0;
		for (int j = 0; j < patternSymbolsNum; j++) {
			int i = 0;
			while (patternBuilder.charAt(i) != PATTERN_SYMBOL) {
				segmentsBuilder.append(patternBuilder.charAt(i)); // building of the segment by character
				i++;
			}
			segments[segmentCounter] = segmentsBuilder.toString();// adding of the segment in 'segments' filed
			segmentsBuilder.setLength(0);
			patternBuilder.delete(0, i + 1); // cutting the segment of the input string
			addExpressionParams(patternBuilder, segmentCounter);
			segmentCounter++;
		}
		segments[patternSymbolsNum] = patternBuilder.toString();
	}

	/**
	 *
	 * @param pattern - input pattern string
	 * @return a number of PATTERN_SYMBOLs in the input pattern string
	 */
	public static int countPatternSymbols(final String pattern) {
		int counter = 0;
		int lastPatternIndex = pattern.length() - 1;
		if (pattern.charAt(lastPatternIndex) == PATTERN_SYMBOL) {
			throw new IllegalArgumentException();
		}
		char[] patternChars = pattern.toCharArray();
		for (int i = 0; i < lastPatternIndex; i++) {
			if (patternChars[i] == PATTERN_SYMBOL) {
				counter++;
				if (patternChars[i + 1] == PATTERN_SYMBOL) {
					throw new IllegalArgumentException();
				}
			}
		}
		return counter;
	}

	/**
	 * In this method is used to fill the 'generators' field with value generators
	 * in accordance with the specified expression parameters
	 * @param expression - a string which follows PATTERN_SYMBOL
	 * @param index of current empty position in generators' array ('generators' field)
	 */
	private void addExpressionParams(final StringBuilder expression, final int index) {
		final char type = expression.charAt(0);
		String format = initParameter(expression, FORMAT_SYMBOLS);
		String range = initParameter(expression, RANGE_SYMBOLS);
		expression.delete(0, 1);
		generators[index] = generatorFactory().createGenerator(type, format, range);
	}

	/**
	 * This method can be used for debug
	 * @return a string with fields' content
	 */
	@Override
	public String toString() {
		final StringBuilder result = new StringBuilder();
		result.append("Generators: ");
		for (final ValueGenerator generator : generators) {
			result.append(generator.getClass().getName()).append(";");
		}
		result.append("\n");
		result.append("Segments: ");
		for (final String segment: segments) {
			result.append(segment).append(";");
		}
		result.append("\n");
		return result.toString();
	}

	/**
	 *
	 * @param result - a parameter to create an opportunity of StringBuilder reusing
	 *                  (StringBuilder instance must be new or cleared with setLength(0))
	 * @return string with PATTERN_SYMBOLs replaced by suitable values
	 */
	@Override
	protected final String format(StringBuilder result) {
		for (int i = 0; i < segments.length - 1; i++) {
			result.append(segments[i]);
			if (generators[i] != null) {
				result.append(generators[i].get());
			}
		}
		result.append(segments[segments.length - 1]);
		return result.toString();
	}
}