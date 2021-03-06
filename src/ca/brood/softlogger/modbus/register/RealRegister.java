/*******************************************************************************
 * Copyright (c) 2013-2016 Charles Hache <chache@cygnustech.ca>.  
 * All rights reserved. 
 * 
 * This file is part of the softlogger project.
 * softlogger is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * softlogger is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with softlogger.  If not, see <https://www.gnu.org/licenses/gpl-3.0.en.html>.
 * 
 * Contributors:
 *     Charles Hache <chache@cygnustech.ca> - initial API and implementation
 ******************************************************************************/

package ca.brood.softlogger.modbus.register;

import ca.brood.softlogger.datafunction.DataFunction;
import ca.brood.softlogger.util.*;
import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ReadCoilsRequest;
import net.wimpi.modbus.msg.ReadInputDiscretesRequest;
import net.wimpi.modbus.msg.ReadInputRegistersRequest;
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest;

import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.*;

public class RealRegister extends Register implements Comparable<RealRegister>{
	protected int address = Integer.MAX_VALUE;
	protected int size = 0;
	protected int device = 0;
	protected int scanRate = 0;
	protected RegisterType regType;
	protected Sampling sampling;
	protected double samplingValue = 0;
	protected int samplingCount = 0;
	protected RegisterSizeType sizeType;
	protected boolean reverseBytes;
	protected Class<? extends DataFunction> functionClass;
	protected String dataFunctionArgument;
	
	protected RealRegister(int device) {
		super();
		this.device = device;
		log = LogManager.getLogger(RealRegister.class + " device: "+device);
		sampling = Sampling.MEAN;
		sizeType = RegisterSizeType.UNSIGNED;
		reverseBytes = false;
		functionClass = null;
		dataFunctionArgument = "";
	}
	protected RealRegister(RealRegister r) {
		super(r);
		address = r.address;
		size = r.size;
		device = r.device;
		scanRate = r.scanRate;
		regType = r.regType;
		sampling = r.sampling;
		samplingValue = r.samplingValue;
		samplingCount = r.samplingCount;
		sizeType = r.sizeType;
		reverseBytes = r.reverseBytes;
		functionClass = r.functionClass;
		dataFunctionArgument = r.dataFunctionArgument;
	}
	public RealRegister clone() {
		return new RealRegister(this);
	}
	public String getDataFunctionArgument() {
		return dataFunctionArgument;
	}
	public void setDataFunctionArgument(String arg) {
		dataFunctionArgument = arg;
	}
	public Class<? extends DataFunction> getFunctionClass() {
		return functionClass;
	}
	public void setFunctionClass(Class<? extends DataFunction> func) {
		functionClass = func;
	}
	public RegisterSizeType getSizeType() {
		return this.sizeType;
	}
	public boolean getReverseBytes() {
		return this.reverseBytes;
	}
	public void setNull() {
		this.registerData.setNull();
	}
	public void resetSampling() {
		samplingValue = 0;
		samplingCount = 0;
		//Reset all latch-on and latch-off coils to null
		if (this.getRegisterType() == RegisterType.INPUT_COIL || this.getRegisterType() == RegisterType.OUTPUT_COIL) {
			if (this.sampling == Sampling.LATCHOFF || this.sampling == Sampling.LATCHON) {
				this.registerData.setNull();
			}
		}
	}
	public void setDataWithSampling(RegisterData temp) {
		switch (sampling) {
		case LATEST:
			this.setData(temp);
			break;
		case SUM:
			//Only for registers
			if (!temp.isNull()) {
				samplingValue += temp.getFloat();
				samplingCount ++;
				this.setData((float)samplingValue, temp.getTimestamp());
			} else {
				this.setNull();
			}
			break;
		case MEAN:
			samplingCount ++;
			if (this.getRegisterType() == RegisterType.INPUT_COIL || this.getRegisterType() == RegisterType.OUTPUT_COIL) {
				if (!temp.isNull()) {
					if (temp.getBool())
						samplingValue ++;
					if ((samplingValue/samplingCount) >0.5f) {
						this.setData(true, temp.getTimestamp());
					} else {
						this.setData(false, temp.getTimestamp());
					}
				} else {
					this.setNull();
				}
			} else {
				if (!temp.isNull()) {
					samplingValue += temp.getFloat();
					this.setData((float)(samplingValue/samplingCount), temp.getTimestamp());
				} else {
					this.setNull();
				}
			}
			break;
		case LATCHON:
			//only for coils
			if (temp.getBool() || this.isNull())
				this.setData(temp.getBool(), temp.getTimestamp());
			break;
		case LATCHOFF:
			//only for coils
			if (!temp.getBool() || this.isNull())
				this.setData(temp.getBool(), temp.getTimestamp());
			break;
		}
	}
	public void setDefaultScanRate(int rate) {
		if (scanRate == 0) {
			scanRate = rate;
			log.debug("Updating scan rate to "+scanRate);
		}
	}
	public RegisterType getRegisterType() {
		return regType;
	}
	public int getAddress() {
		return address;
	}
	public int getLongAddress() {
		return RegisterType.getLongAddress(address, regType);
	}
	public int getSize() {
		return size;
	}
	public int getScanRate() {
		return this.scanRate;
	}
	
	@SuppressWarnings("unchecked")
	public boolean configure(Node registerNode) {
		if (!super.configure(registerNode)) {
			return false;
		}
		NodeList configNodes = registerNode.getChildNodes();
		for (int i=0; i<configNodes.getLength(); i++) {
			Node configNode = configNodes.item(i);
			if (("#text".compareToIgnoreCase(configNode.getNodeName())==0) || 
					("#comment".compareToIgnoreCase(configNode.getNodeName())==0))	{
				continue;
			} else if (("registerAddress".compareToIgnoreCase(configNode.getNodeName())==0))	{
				int addy = 0;
				try {
					addy = Util.parseInt(configNode.getFirstChild().getNodeValue());
				} catch (NumberFormatException e) {
					log.error("Couldn't parse register Address to integer from: "+configNode.getFirstChild().getNodeValue());
					return false;
				}
				try {
					this.regType = RegisterType.fromAddress(addy);
					this.address = RegisterType.getAddress(addy);
					registerNode.removeChild(configNode);
				} catch (NumberFormatException e) {
					log.error("Error converting modbuss address to register type: "+addy);
					return false;
				}
			} else if (("size".compareToIgnoreCase(configNode.getNodeName())==0))	{
				try {
					this.size = Util.parseInt(configNode.getFirstChild().getNodeValue());
					this.sizeType = RegisterSizeType.fromString(configNode.getAttributes().getNamedItem("type").getTextContent());
					String rv = configNode.getAttributes().getNamedItem("reverse").getTextContent();
					if (rv.equals("t") || rv.equals("true")) {
						this.reverseBytes = true;
					} else if (rv.equals("f") || rv.equals("false")) {
						this.reverseBytes = false;
					} else {
						log.error("Invalid attribute for size.  Reverse must be in [t,f,true,false]");
						return false;
					}
					registerNode.removeChild(configNode);
				} catch (NumberFormatException e) {
					log.error("Couldn't parse size to integer from: "+configNode.getFirstChild().getNodeValue());
				}
			} else if (("dataFunction".compareToIgnoreCase(configNode.getNodeName())==0))	{
				String functionType = configNode.getAttributes().item(0).getTextContent();
				String functionArg = configNode.getFirstChild().getNodeValue();
				try {
					this.setFunctionClass((Class<? extends DataFunction>) Class.forName(functionType));
					this.setDataFunctionArgument(functionArg);
					registerNode.removeChild(configNode);
				} catch (ClassNotFoundException e) {
					log.error("Couldn't load data function: "+functionType);
				}
				
			} else if ("scanRate".compareToIgnoreCase(configNode.getNodeName())==0){
				try {
					if (configNode.getFirstChild() != null)
						this.scanRate = Integer.parseInt(configNode.getFirstChild().getNodeValue());
					this.sampling = Sampling.fromString(configNode.getAttributes().item(0).getTextContent());
					registerNode.removeChild(configNode);
				} catch (NumberFormatException e) {
					log.error("Invalid scan rate: "+configNode.getFirstChild().getNodeValue());
					this.scanRate = 0;
				}
			}
		}
		if (this.address < 0 || this.address > 65535) {
			log.error("Parsed invalid address: "+this.address);
			return false;
		}
		switch (this.regType) {
		case INPUT_COIL:
		case OUTPUT_COIL:
			if (this.size != 1) {
				if (this.size != 0)
					log.warn("Got invalid size for an input or output coil.  Changing size to 1 from: "+this.size);
				this.size = 1;
			}
			if (sampling == Sampling.SUM) {
				log.warn("SUM sampling not allowed for coils.  Using default of MEAN.");
				sampling = Sampling.MEAN;
			}
			break;
		case INPUT_REGISTER:
		case OUTPUT_REGISTER:
			if (this.size != 1 && this.size != 2) {
				if (this.size != 0)
					log.warn("Got invalid size for an input or output register.  Changing size to 2 from: "+this.size);
				this.size = 1;
			}
			if (sampling == Sampling.LATCHOFF || sampling == Sampling.LATCHON) {
				log.warn("LATCHON / LATCHOFF samplings not allowed for non-coil registers. Using default of MEAN.");
				sampling = Sampling.MEAN;
			}
			break;
		}
		
		switch (this.sizeType) {
		case SIGNED:
			if (this.regType == RegisterType.INPUT_COIL || this.regType == RegisterType.OUTPUT_COIL) {
				log.warn("SIGNED size type is ignored for input and output coils.");
			}
			break;
		case UNSIGNED:
			break;
		case FLOAT:
			if (this.regType == RegisterType.INPUT_COIL || this.regType == RegisterType.OUTPUT_COIL) {
				log.error("FLOAT size type is ignored for input and output coils.");
			} else {
				if (this.size != 2) {
					log.error("FLOAT size type can only be used with double word (size=2) registers.  Changing to unsigned integer type.");
					this.sizeType = RegisterSizeType.UNSIGNED;
				}
			}
			break;
		}
		return true;
	}
	
	@Override
	public int compareTo(RealRegister other) {
		int ret = this.regType.compareTo(other.regType);
		if (ret != 0) {
			return ret;
		}
		ret = this.address - other.address;
		return ret;
	}
	
	@Override
	public String toString() {
		return "RealRegister: fieldname="+this.fieldName+"; address="+this.address+"; size="+this.size+"; data: "+registerData.toString();
	}
	public ModbusRequest getRequest() {
		return getRequest(this.size);
	}
	public ModbusRequest getRequest(int size) {
		ModbusRequest request = null;
		switch (this.regType) {
		case INPUT_COIL:
			request = new ReadInputDiscretesRequest(this.address, size);
			break;
		case OUTPUT_COIL:
			request = new ReadCoilsRequest(this.address, size);
			break;
		case INPUT_REGISTER:
			request = new ReadInputRegistersRequest(this.address, size);
			break;
		case OUTPUT_REGISTER:
			request = new ReadMultipleRegistersRequest(this.address, size);
		}
		
		return request;
	}
	public static class ScanRateComparator implements Comparator<RealRegister> {

		//@Override
		public int compare(RealRegister arg0, RealRegister arg1) {
			int ret = arg0.scanRate - arg1.scanRate;
			if (ret != 0)
				return ret;
			ret = arg0.regType.compareTo(arg1.regType);
			if (ret != 0)
				return ret;
			return arg0.address - arg1.address;
		}
		
	}
}
