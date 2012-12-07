package thredds.server.ncSubset.format;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import thredds.server.config.Netcdf4AvailabilityChecker;
import ucar.nc2.jni.netcdf.Nc4Iosp;

public enum SupportedFormat{
	
		
	CSV("CSV", true, "text/plain", "text/csv", "csv"  ),
	XML("XML", true, "application/xml", "text/xml", "xml"),
	NETCDF3("NETCDF3", true, "application/x-netcdf","netcdf"),	
	NETCDF4("NETCDF4", Netcdf4AvailabilityChecker.isNetcdf4Available() ,  "application/x-netcdf4" , "netcdf4");

	
	/*
	 * First alias is used as content-type in the http headers
	 */
	private final List<String> aliases;
	private final String formatName;
	private final boolean available;
	//private final List<SupportedOperation> operations;
	
	SupportedFormat(String formatName, boolean available, String...aliases ){
		this.formatName=formatName;
		this.available = available;
		//this.operations = operations;
		 
		
		List<String> aliasesList = new ArrayList<String>();
		for(String alias : aliases){
			aliasesList.add(alias);
		}
		this.aliases = Collections.unmodifiableList(aliasesList);
	}
	
	public String getFormatName(){
		return formatName;
	}
	
	public boolean isAvailable(){
		return available;
	}
	
	public List<String> getAliases(){
		return aliases;
	}
	
	/*List<SupportedOperation> getOperations(){
		return operations;
	}*/
	
	//The first item in the aliases is the content type for the responses
	public String getResponseContentType(){
		return aliases.get(0);
	}

	
		
}
