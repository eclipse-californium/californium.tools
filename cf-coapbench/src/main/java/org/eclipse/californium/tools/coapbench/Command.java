/*******************************************************************************
 * Copyright (c) 2014 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and initial implementation
 ******************************************************************************/
package org.eclipse.californium.tools.coapbench;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Wrapper class for commands.
 * <pre>
 * command = [at] [body]
 * at = @id
 * body = options* parameters*
 * options = -name value
 * parameter = value
 * </pre>
 */
public class Command {

	public static final int ALL = -1;
	
	private final String complete;
	
	private Map<String, String> options;
	
	private List<String> parameters;
	
	public Command(String command) {
		this.complete = command;
		this.parameters = new LinkedList<String>();
		this.options = new HashMap<String, String>();
		String[] parts = command.split(" ");
		int ptr = 1;
		
		while (ptr < parts.length) {
			String option = parts[ptr];
			if (option.startsWith("-")) {
				if (ptr+1 < parts.length && !parts[ptr+1].startsWith("-")) {
					options.put(option, parts[ptr+1]);
					ptr += 2;
					continue;
				} else {
					options.put(option, "");
				}
			} else {
				parameters.add(option);
			}
			ptr++;
		}
	}
	
	public int getAt() {
		if (complete.startsWith("@")) { // e.g.: "@3 do -whatever"
			return Integer.parseInt(complete.split(" ")[0].substring(1));
		} else {
			return ALL;
		}
	}
	
	public String getBody() {
		if (complete.startsWith("@")) { // e.g.: "@3 do -whatever"
			return complete.substring(complete.indexOf(" ")).trim();
		} else {
			return complete;
		}
	}
	
	public List<String> getParameters() {
		return parameters;
	}
	
	public boolean has(String option) {
		return options.containsKey(option);
	}
	
	public int getInt(String option) {
		if (!has(option))
			throw new CommandException("Command has no option "+option);
		return Integer.parseInt(options.get(option));
	}
	
	public String getString(String option) {
		if (!has(option))
			throw new CommandException("Command has no option "+option);
		return options.get(option);
	}
	
	@Override
	public String toString() {
		return complete + " => " + options+", "+parameters;
	}
	
	public static class CommandException extends RuntimeException {
		private static final long serialVersionUID = 8136391656127005355L;

		public CommandException(String message) {
			super(message);
		}
	}
}
