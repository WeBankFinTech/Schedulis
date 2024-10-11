package azkaban.jobtype.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class ShellUtils {
	
	public static String getPid(){
		// get name representing the running Java virtual machine.  
		String name = ManagementFactory.getRuntimeMXBean().getName();  
		//System.out.println(name);  
		// get pid  
		String pid = name.split("@")[0];  
		return pid;
	}
	
	public static String getPPid(String cpid){
		Process process = null;
	    List<String> processList = new ArrayList<String>();
	    InputStream in = null;
	    String[] cmds = {"/bin/sh", "-c", "ps -ef | grep " + cpid + " | grep -v 'grep' | awk '{print $3}'"};
	    try {
	      process = Runtime.getRuntime().exec(cmds);
	      process.waitFor();
	      in = process.getInputStream();
	      BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	      String line;
	      while ((line = reader.readLine()) != null) {
	        processList.add(line);
	      }
	      reader.close();
	    } catch (IOException e) {
	      e.printStackTrace();
	    } catch (InterruptedException e) {
	      e.printStackTrace();
	    }
	    
	    return processList.get(0);
	}

	public static String getExecutorServerPid() {
		ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c",
				"ps -ef | grep 'AzkabanExecutorServer' | grep -v 'grep' | awk '{print $2}'");
		Process process;
		ArrayList<String> processIds = new ArrayList<>();
		try {
			process = processBuilder.start();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (!line.isEmpty()) {
						processIds.add(line);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (!processIds.isEmpty()) {
			return processIds.get(0);
		}

		return "";
	}
	
	public static void main(String[] args) {
		ShellUtils.getPid();
		String ppid = ShellUtils.getPPid(getPid());
		System.out.println(ppid);
	}

}
