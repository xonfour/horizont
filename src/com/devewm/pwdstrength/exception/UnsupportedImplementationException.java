package com.devewm.pwdstrength.exception;

public class UnsupportedImplementationException extends UnsupportedOperationException {
	private static final long serialVersionUID = -5408905784921295827L;

	private final Class<?> impl;

	public UnsupportedImplementationException(final Class<?> targetClass, final Throwable cause) {
		super.initCause(cause);
		this.impl = targetClass;
	}

	@Override
	public String getMessage() {
		return this.impl.getClass().getName() + " is not a supported implementation.";
	}

}
