package com.devewm.pwdstrength;

import static com.devewm.pwdstrength.PasswordCharacterRange.CharacterBlock.BASIC_LATIN_LETTERS_LOWER_CASE;
import static com.devewm.pwdstrength.PasswordCharacterRange.CharacterBlock.BASIC_LATIN_LETTERS_UPPER_CASE;
import static com.devewm.pwdstrength.PasswordCharacterRange.CharacterBlock.BASIC_LATIN_NUMERICAL_DIGITS;
import static com.devewm.pwdstrength.PasswordCharacterRange.CharacterBlock.BASIC_LATIN_SYMBOLS;

import java.math.BigInteger;

/**
 * Contains values for commonly-used password types.
 *
 */
public enum PasswordStrengthClass implements Comparable<PasswordStrengthClass> {
	LENGTH_8_LOWER_CASE(8, BASIC_LATIN_LETTERS_LOWER_CASE), LENGTH_8_MIXED_CASE(8, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE), LENGTH_8_MIXED_CASE_WITH_NUMBER(8, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS), LENGTH_8_MIXED_CASE_WITH_NUMBER_AND_SYMBOL(8, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS, BASIC_LATIN_SYMBOLS),

	LENGTH_10_LOWER_CASE(10, BASIC_LATIN_LETTERS_LOWER_CASE), LENGTH_10_MIXED_CASE(10, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE), LENGTH_10_MIXED_CASE_WITH_NUMBER(10, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS), LENGTH_10_MIXED_CASE_WITH_NUMBER_AND_SYMBOL(10, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS, BASIC_LATIN_SYMBOLS),

	LENGTH_12_LOWER_CASE(12, BASIC_LATIN_LETTERS_LOWER_CASE), LENGTH_12_MIXED_CASE(12, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE), LENGTH_12_MIXED_CASE_WITH_NUMBER(12, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS), LENGTH_12_MIXED_CASE_WITH_NUMBER_AND_SYMBOL(12, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS, BASIC_LATIN_SYMBOLS),

	LENGTH_16_LOWER_CASE(16, BASIC_LATIN_LETTERS_LOWER_CASE), LENGTH_16_MIXED_CASE(16, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE), LENGTH_16_MIXED_CASE_WITH_NUMBER(16, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS), LENGTH_16_MIXED_CASE_WITH_NUMBER_AND_SYMBOL(16, BASIC_LATIN_LETTERS_LOWER_CASE, BASIC_LATIN_LETTERS_UPPER_CASE, BASIC_LATIN_NUMERICAL_DIGITS, BASIC_LATIN_SYMBOLS);

	private BigInteger iterationCount;

	private PasswordStrengthClass(final int length, final PasswordCharacterRange.CharacterBlock... blocks) {
		final StringBuffer basePassword = new StringBuffer();

		final char[] initialChars = Character.toChars(blocks[0].getRanges().iterator().next().getLowerBound());
		for (int i = 0; i < ((length - blocks.length) + 1); i++) {
			basePassword.append(initialChars);
		}

		for (int i = 1; i < blocks.length; i++) {
			final char[] nextChars = Character.toChars(blocks[i].getRanges().iterator().next().getLowerBound());
			basePassword.append(nextChars);
		}

		final PasswordStrengthMeter passwordStrengthMeter = PasswordStrengthMeter.getInstance();
		this.iterationCount = passwordStrengthMeter.iterationCount(basePassword.toString());
	}

	/**
	 * Find the minimum iteration count for a password of this type
	 *
	 * @return iteration count for the simplest password matching this description
	 */
	public BigInteger getIterations() {
		return this.iterationCount;
	}
}