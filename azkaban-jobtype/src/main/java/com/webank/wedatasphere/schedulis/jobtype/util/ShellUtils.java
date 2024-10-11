/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.jobtype.util;

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
	
	public static void main(String[] args) {
		ShellUtils.getPid();
		String ppid = ShellUtils.getPPid(getPid());
		System.out.println(ppid);
	}

}
