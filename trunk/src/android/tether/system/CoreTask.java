/**
 *  This program is free software; you can redistribute it and/or modify it under 
 *  the terms of the GNU General Public License as published by the Free Software 
 *  Foundation; either version 3 of the License, or (at your option) any later 
 *  version.
 *  You should have received a copy of the GNU General Public License along with 
 *  this program; if not, see <http://www.gnu.org/licenses/>. 
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Seth Lemons.
 */

package android.tether.system;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import android.tether.data.ClientData;
import android.util.Log;

public class CoreTask {

	public static final String MSG_TAG = "TETHER -> CoreTask";
	
	public String DATA_FILE_PATH;
	
	private static final String FILESET_VERSION = "10";
	private static final String defaultDNS1 = "208.67.220.220";
	private static final String defaultDNS2 = "208.67.222.222";
	
	public void setPath(String path){
		this.DATA_FILE_PATH = path;
	}

    public boolean whitelistExists() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	if (file.exists() && file.canRead()) {
    		return true;
    	}
    	return false;
    }
    
    public boolean removeWhitelist() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	if (file.exists()) {
	    	return file.delete();
    	}
    	return false;
    }

    public void touchWhitelist() throws IOException {
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	file.createNewFile();
    }
    
    public void saveWhitelist(ArrayList<String> whitelist) throws Exception {
    	FileOutputStream fos = null;
    	File file = new File(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    	try {
			fos = new FileOutputStream(file);
			for (String mac : whitelist) {
				fos.write((mac+"\n").getBytes());
			}
		} 
		finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					// nothing
				}
			}
		}
    }
    
    public ArrayList<String> getWhitelist() throws Exception {
    	return readLinesFromFile(this.DATA_FILE_PATH+"/conf/whitelist_mac.conf");
    }    
    
    public boolean wpaSupplicantExists() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
    	if (file.exists() && file.canRead()) {
    		return true;
    	}
    	return false;
    }
 
    public boolean removeWpaSupplicant() {
    	File file = new File(this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
    	if (file.exists()) {
	    	return file.delete();
    	}
    	return false;
    }

    
    public Hashtable<String,ClientData> getLeases() throws Exception {
        Hashtable<String,ClientData> returnHash = new Hashtable<String,ClientData>();
        
        ClientData clientData;
        
        ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/var/dnsmasq.leases");
        
        for (String line : lines) {
			clientData = new ClientData();
			String[] data = line.split(" ");
			Date connectTime = new Date(Long.parseLong(data[0] + "000"));
			String macAddress = data[1];
			String ipAddress = data[2];
			String clientName = data[3];
			clientData.setConnectTime(connectTime);
			clientData.setClientName(clientName);
			clientData.setIpAddress(ipAddress);
			clientData.setMacAddress(macAddress);
			clientData.setConnected(true);
			returnHash.put(macAddress, clientData);
		}
    	return returnHash;
    }
 
    public void chmodBin(List<String> filenames) throws Exception {
        Process process = null;
		process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
    	for (String tmpFilename : filenames) {
    		os.writeBytes("chmod 0755 "+this.DATA_FILE_PATH+"/bin/"+tmpFilename+"\n");
    	}
    	os.writeBytes("exit\n");
        os.flush();
        os.close();
        process.waitFor();
    }   

    public synchronized ArrayList<String> readLinesFromCmd(String command) {
    	Process process = null;
    	BufferedReader in = null;
    	ArrayList<String> lines = new ArrayList<String>();
    	Log.d(MSG_TAG, "Reading lines from command: " + command);
    	try {
    		process = Runtime.getRuntime().exec(command);
    		InputStreamHandler inputStreamHandler = new InputStreamHandler(process.getInputStream());
    		inputStreamHandler.start();
    		inputStreamHandler.join(2000);
            if (inputStreamHandler.isAlive()) {
            	Log.d(MSG_TAG, "TIMEOUT! Running command '"+command+"'!");
            	inputStreamHandler.destroy();
            }            
    		process.waitFor();
    		lines = inputStreamHandler.getLines();
    		Log.d(MSG_TAG, "Command-output: "+lines.toString());
    		
    	} catch (Exception e) {
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
			try {
				if (in != null) {
					in.close();
				}
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
    	}
    	return lines;
    }
    
    public ArrayList<String> readLinesFromFile(String filename) {
    	String line = null;
    	BufferedReader br = null;
    	ArrayList<String> lines = new ArrayList<String>();
    	Log.d(MSG_TAG, "Reading lines from file: " + filename);
    	try {
    		br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename))));
    		while((line = br.readLine())!=null) {
    			lines.add(line.trim());
    		}
    	} catch (Exception e) {
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
    		try {
    			br.close();
    		} catch (Exception e) {
    			// Nothing.
    		}
    	}
    	return lines;
    }
    
    public boolean writeLinesToFile(String filename, String lines) {
		OutputStream out = null;
		boolean returnStatus = false;
		Log.d(MSG_TAG, "Writing " + lines.length() + " bytes to file: " + filename);
		try {
			out = new FileOutputStream(filename);
        	out.write(lines.getBytes());
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
        	try {
        		if (out != null)
        			out.close();
        		returnStatus = true;
			} catch (IOException e) {
				returnStatus = false;
			}
		}
		return returnStatus;
    }
    
    public boolean isNatEnabled() {
    	ArrayList<String> lines = readLinesFromFile("/proc/sys/net/ipv4/ip_forward");
    	return lines.contains("1");
    }
    
    public boolean isProcessRunning(String processName) throws Exception {
    	
    	ArrayList<String> lines = readLinesFromCmd("ps");
    	for (String proc : lines) {
    		if (proc.contains(processName))
    			return true;
    	}
    	return false;
    }

    public boolean hasRootPermission() {
    	Process process = null;
    	DataOutputStream os = null;
    	boolean rooted = true;
		try {
			process = Runtime.getRuntime().exec("su");
	        os = new DataOutputStream(process.getOutputStream());
	        os.writeBytes("exit\n");
	        os.flush();	        
	        process.waitFor();
	        Log.d(MSG_TAG, "Exit-Value ==> "+process.exitValue());
	        if (process.exitValue() != 0) {
	        	rooted = false;
	        }
		} catch (Exception e) {
			Log.d(MSG_TAG, "Can't obtain root - Here is what I know: "+e.getMessage());
			rooted = false;
		}
		finally {
			if (os != null) {
				try {
					os.close();
					process.destroy();
				} catch (Exception e) {
					// nothing
				}
			}
		}
		return rooted;
    }
    
    public boolean runRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
	        os = new DataOutputStream(process.getOutputStream());
	        Log.d(MSG_TAG, "Execute command: "+command);
	        os.writeBytes(command+"\n");
	        os.writeBytes("exit\n");
	        os.flush();
	        process.waitFor();
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
			return false;
		}
		finally {
			try {
				if (os != null) {
					os.close();
				}
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}
		return true;
    }

    /*
    public String getProp(String property) {
    	ArrayList<String> lines = readLinesFromCmd("getprop " + property);
    	if (lines.size() > 0)
    		return lines.get(0);
    	return "";
    }*/

    
    public synchronized void updateDnsmasqFilepath() {
    	String dnsmasqConf = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	String newDnsmasq = new String();
    	boolean writeconfig = false;
    	
    	ArrayList<String> lines = readLinesFromFile(dnsmasqConf);
    	
    	for (String line : lines) {
    		if (line.contains("dhcp-leasefile=") && !line.contains(CoreTask.this.DATA_FILE_PATH)){
    			line = "dhcp-leasefile="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.leases";
    			writeconfig = true;
    		}
    		else if (line.contains("pid-file=") && !line.contains(CoreTask.this.DATA_FILE_PATH)){
    			line = "pid-file="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.pid";
    			writeconfig = true;
    		}
    		newDnsmasq += line+"\n";
    	}

    	if (writeconfig == true)
    		writeLinesToFile(dnsmasqConf, newDnsmasq);
    }
    
    public synchronized void updateDnsmasqConf() {
    	String dnsmasqConf = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	String newDnsmasq = new String();
    	// Getting dns-servers
    	String dns[] = new String[2];
    	//dns[0] = getProp("net.dns1");
    	//dns[1] = getProp("net.dns2");
    	if (dns[0] == null || dns[0].length() <= 0) {
    		dns[0] = defaultDNS1;
    	}
    	if (dns[1] == null || dns[1].length() <= 0) {
    		dns[1] = defaultDNS2;
    	}
    	boolean writeconfig = false;
    	ArrayList<String> lines = readLinesFromFile(dnsmasqConf);
    	
    	int servercount = 0;
	    for (String s : lines) {
    		if (s.contains("server")) { 
    			if (s.contains(dns[servercount]) == false){
    				s = "server="+dns[servercount];
    				writeconfig = true;
    			}
    			servercount++;
    		}
    		newDnsmasq += s+"\n";
		}

    	if (writeconfig == true) {
			Log.d(MSG_TAG, "Writing new DNS-Servers: "+dns[0]+","+dns[1]);
    		writeLinesToFile(dnsmasqConf, newDnsmasq);
    	}
    	else {
			Log.d(MSG_TAG, "No need to update DNS-Servers: "+dns[0]+","+dns[1]);
    	}
    }
    
    public boolean filesetOutdated(){
    	boolean outdated = true;
    	
    	File inFile = new File(this.DATA_FILE_PATH+"/bin/tether");
    	if (inFile.exists() == false) {
    		return false;
    	}
    	ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/bin/tether");

    	int linecount = 0;
    	for (String line : lines) {
    		if (line.contains("@Version")){
    			String instVersion = line.split("=")[1];
    			if (instVersion != null && FILESET_VERSION.equals(instVersion.trim()) == true) {
    				outdated = false;
    			}
    			break;
    		}
    		if (linecount++ > 2)
    			break;
    	}
    	return outdated;
    }
    

    public Hashtable<String,String> getWpaSupplicantConf() {
    	File inFile = new File(this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf");
    	if (inFile.exists() == false) {
    		return null;
    	}
    	Hashtable<String,String> tiWlanConf = new Hashtable<String,String>();
    	ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf");

    	for (String line : lines) {
    		if (line.contains("=")) {
	    		String[] pair = line.split("=");
	    		if (pair[0] != null && pair[1] != null && pair[0].length() > 0 && pair[1].length() > 0) {
	    			tiWlanConf.put(pair[0].trim(), pair[1].trim());
	    		}
    		}
    	}
    	return tiWlanConf;
    }   
    
    public synchronized boolean writeWpaSupplicantConf(Hashtable<String,String> values) {
    	String filename = this.DATA_FILE_PATH+"/conf/wpa_supplicant.conf";
    	String fileString = "";
    	
    	ArrayList<String>inputLines = readLinesFromFile(filename);
    	for (String line : inputLines) {
    		if (line.contains("=")) {
    			String key = line.split("=")[0];
    			if (values.containsKey(key)) {
    				line = key+"="+values.get(key);
    			}
    		}
    		line+="\n";
    		fileString += line;
    	}
    	return writeLinesToFile(filename, fileString);	
    }
    
    public Hashtable<String,String> getTiWlanConf() {
    	Hashtable<String,String> tiWlanConf = new Hashtable<String,String>();
    	ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/conf/tiwlan.ini");

    	for (String line : lines) {
    		String[] pair = line.split("=");
    		if (pair[0] != null && pair[1] != null && pair[0].length() > 0 && pair[1].length() > 0) {
    			tiWlanConf.put(pair[0].trim(), pair[1].trim());
    		}
    	}
    	return tiWlanConf;
    }
 
    public synchronized boolean writeTiWlanConf(String name, String value) {
    	Hashtable<String, String> table = new Hashtable<String, String>();
    	table.put(name, value);
    	return writeTiWlanConf(table);
    }
    
    public synchronized boolean writeTiWlanConf(Hashtable<String,String> values) {
    	String filename = this.DATA_FILE_PATH+"/conf/tiwlan.ini";
    	ArrayList<String> valueNames = Collections.list(values.keys());

    	String fileString = "";
    	
    	ArrayList<String> inputLines = readLinesFromFile(filename);
    	for (String line : inputLines) {
    		for (String name : valueNames) {
        		if (line.contains(name)){
	    			line = name+" = "+values.get(name);
	    			break;
	    		}
    		}
    		line+="\n";
    		fileString += line;
    	}
    	return writeLinesToFile(filename, fileString); 	
    }
    
    public long getModifiedDate(String filename) {
    	File file = new File(filename);
    	if (file.exists() == false) {
    		return -1;
    	}
    	return file.lastModified();
    }
}

class InputStreamHandler extends Thread {
	InputStream is;
	ArrayList<String> lines;

	InputStreamHandler(InputStream is) {
		this.is = is;
	}

	public ArrayList<String> getLines() {
		return this.lines;
	}
	
	public void run() {
		try {
			this.lines = new ArrayList<String>();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			while ((line = br.readLine()) != null) {
				Log.d(">>>>>>>>>>>", ">>> LOOP ==> "+line);
				this.lines.add(line);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
}
