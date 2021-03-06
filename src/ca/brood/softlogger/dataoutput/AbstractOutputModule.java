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

package ca.brood.softlogger.dataoutput;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ca.brood.softlogger.modbus.register.RegisterCollection;

public abstract class AbstractOutputModule 
		implements OutputModule {
	
	protected OutputableDevice m_OutputDevice;
	protected RegisterCollection m_Registers;
	
	public AbstractOutputModule() {
		m_OutputDevice = null;
		m_Registers = new RegisterCollection();
	}
	
	public AbstractOutputModule(AbstractOutputModule other) {
		m_Registers = new RegisterCollection(other.m_Registers);
		m_OutputDevice = other.m_OutputDevice;
	}
	
	protected abstract void setConfigValue(String name, String value) throws Exception;
	
	@Override
	public boolean configure(Node rootNode) {
		NodeList configNodes = rootNode.getChildNodes();
		try {
			for (int i=0; i<configNodes.getLength(); i++) {
				Node configNode = configNodes.item(i);
				if (("#text".compareToIgnoreCase(configNode.getNodeName())==0) || 
						("#comment".compareToIgnoreCase(configNode.getNodeName())==0))	{
					continue;
				} else if (("configValue".compareToIgnoreCase(configNode.getNodeName())==0)) {
					String name = configNode.getAttributes().getNamedItem("name").getNodeValue();
					String value = configNode.getFirstChild().getNodeValue();
					setConfigValue(name, value);
				}
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	@Override
	abstract public AbstractOutputModule clone();

	@Override
	public RegisterCollection getRegisterCollection() {
		return m_Registers;
	}
	
	@Override
	public void setRegisterCollection(RegisterCollection reg) {
		m_Registers = reg;
	}
	
	@Override
	public void setOutputableDevice(OutputableDevice dev) {
		this.m_OutputDevice = dev;
	}
	
	public void resetRegisterSamplings() {
		m_Registers.resetRegisterSamplings();
	}

	public boolean useRegisterSampling() {
		return true;
	}
}
