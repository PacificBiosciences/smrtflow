/**
 * 
 */
package com.pacbio.secondary.common.sys.linux;

import java.util.HashMap;
import java.util.Map;



/**
 * Singleton that provides cmd implementation for linux cmds such as "df"
 * @author jmiller
 */
public class LinuxCmdFactory {
	
	public enum Command { Df }

	private static LinuxCmdFactory instance;
	
	
	private Map<Command, Class<?>> commandMap;

	private LinuxCmdFactory() {
		//add the default implementations of the cmd
		commandMap = new HashMap<Command, Class<?>>();
		commandMap.put(Command.Df, Df.class);
	}
	
	public static LinuxCmdFactory instance() {
		if( instance == null )
			instance = new LinuxCmdFactory();
		return instance;
	}

	/**
	 * Provide a mechanism to use non-default implementations of cmds. Useful for mocks.
	 * @param df
	 * @param clazz
	 */
	public void setCommandClass(Command df, Class<?> clazz) {
		this.commandMap.put(df, clazz);
	}

	
	/**
	 * 
	 * @return
	 * @throws Exception
	 */
	public IDf getDf() throws Exception {
		return (IDf) (commandMap.get(Command.Df)).newInstance();
	}

}
