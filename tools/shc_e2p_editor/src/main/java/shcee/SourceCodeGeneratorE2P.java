/*
* This file is part of smarthomatic, http://www.smarthomatic.org.
* Copyright (c) 2013 Uwe Freese
*
* smarthomatic is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License, or (at your
* option) any later version.
*
* smarthomatic is distributed in the hope that it will be useful, but
* WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
* Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with smarthomatic. If not, see <http://www.gnu.org/licenses/>.
*/

package shcee;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javax.swing.JOptionPane;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xpath.internal.XPathAPI;

/**
 * This is a very simple generator for EEPROM layout files used by the SHC device firmwares.
 * The positions, length and ranges are saved to a *.h file per DeviceType.
 * This generator assumes that EEPROM blocks have a DeviceType as restriction (which
 * is the case for SHC) and creates one file per DeviceType.
 * @author uwe
 */
public class SourceCodeGeneratorE2P
{
	public SourceCodeGeneratorE2P() throws TransformerException, IOException
	{
		String errMsg = Util.conformsToSchema(SHCEEMain.EEPROM_LAYOUT_XML, SHCEEMain.EEPROM_METAMODEL_XSD);
		
		if (null != errMsg)
		{
			JOptionPane.showMessageDialog(SHCEEMain.mySHCEEMain, SHCEEMain.EEPROM_LAYOUT_XML + " does not conform to " +
					SHCEEMain.EEPROM_METAMODEL_XSD + ".\nError message:\n" + errMsg , "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		Node xmlRoot;
		
		try
		{
			xmlRoot = Util.readXML(new File(SHCEEMain.EEPROM_LAYOUT_XML));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			JOptionPane.showMessageDialog(SHCEEMain.mySHCEEMain, SHCEEMain.EEPROM_LAYOUT_XML + " could not be loaded", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		xmlRoot = Util.getChildNode(xmlRoot, "E2P");
		
		// for all DeviceTypes
		NodeList devTypeNodes = XPathAPI.selectNodeList(xmlRoot, "//EnumValue[ID='DeviceType']/Element");
		
		for (int d = 0; d < devTypeNodes.getLength(); d++)
		{
			int offset = 0;
			
			Node devTypeNode = devTypeNodes.item(d);
			
			String devTypeID = Util.getChildNodeValue(devTypeNode, "Value");

			String filename = Util.getChildNodeValue(devTypeNode, "Name").toLowerCase().replace(' ', '_');
			PrintWriter out = new PrintWriter(new FileWriter("../../firmware/src_common/e2p_" + filename + ".h"));

			out.println("/*");
			out.println("* This file is part of smarthomatic, http://www.smarthomatic.org.");
			out.println("* Copyright (c) 2013 Uwe Freese");
			out.println("*");
			out.println("* smarthomatic is free software: you can redistribute it and/or modify it");
			out.println("* under the terms of the GNU General Public License as published by the");
			out.println("* Free Software Foundation, either version 3 of the License, or (at your");
			out.println("* option) any later version.");
			out.println("*");
			out.println("* smarthomatic is distributed in the hope that it will be useful, but");
			out.println("* WITHOUT ANY WARRANTY; without even the implied warranty of");
			out.println("* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General");
			out.println("* Public License for more details.");
			out.println("*");
			out.println("* You should have received a copy of the GNU General Public License along");
			out.println("* with smarthomatic. If not, see <http://www.gnu.org/licenses/>.");
			out.println("*");
			out.println("* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			out.println("* ! WARNING: This file is generated by the SHC EEPROM editor and should !");
			out.println("* ! never be modified manually.                                         !");
			out.println("* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			out.println("*/");
			out.println("");
			 
			// for all blocks
			NodeList blocks = XPathAPI.selectNodeList(xmlRoot, "//Block");

			for (int b = 0; b < blocks.getLength(); b++)
			{
				Node block = blocks.item(b);

				Node restrictionValueNode = XPathAPI.selectSingleNode(block, "Restriction[RefID='DeviceType']/Value");
				
				if (null != restrictionValueNode)
				{
					if (!restrictionValueNode.getFirstChild().getNodeValue().equals(devTypeID))
					{
						continue;
					}
				}
				
				String blockName = Util.getChildNodeValue(block, "Name");
				NodeList elements = block.getChildNodes();

				out.println("");
				out.println("// ---------- " + blockName + " ----------");
				out.println("");

				for (int e = 0; e < elements.getLength(); e++)
				{
					Node element = elements.item(e);
					
					if (element.getNodeName().equals("EnumValue"))
					{
						String ID1 = Util.getChildNodeValue(element, "ID");
						out.println("// EnumValue " + ID1);
						out.println("");
						String ID = ID1.toUpperCase();
						
						NodeList enumElements = XPathAPI.selectNodeList(element, "Element");
						
						out.println("typedef enum {");

						for (int ee = 0; ee < enumElements.getLength(); ee++)
						{
							Node enumElement = enumElements.item(ee);
							String value = Util.getChildNodeValue(enumElement, "Value");
							String name = ID + "_" + Util.getChildNodeValue(enumElement, "Name").toUpperCase().replace(' ', '_');
							
							String suffix = ee == enumElements.getLength() - 1 ? "" : ","; 
							
							out.println("  " + name + " = " + value + suffix);
						}

						out.println("} " + ID1 + "Enum;");
						out.println("");
						
						out.println("#define EEPROM_" + ID + "_BYTE " + (offset / 8));
						out.println("#define EEPROM_" + ID + "_BIT " + (offset % 8));
						out.println("#define EEPROM_" + ID + "_LENGTH_BITS 8");
						out.println("");
						offset += 8;
					}
					else if (element.getNodeName().equals("UIntValue"))
					{
						String ID = Util.getChildNodeValue(element, "ID");
						out.println("// UIntValue " + ID);
						out.println("");
						ID = ID.toUpperCase();
						String bits = Util.getChildNodeValue(element, "Bits");
						String minVal = Util.getChildNodeValue(element, "MinVal");
						String maxVal = Util.getChildNodeValue(element, "MaxVal");
						out.println("#define EEPROM_" + ID + "_BYTE " + (offset / 8));
						out.println("#define EEPROM_" + ID + "_BIT " + (offset % 8));
						out.println("#define EEPROM_" + ID + "_LENGTH_BITS " + bits);
						out.println("#define EEPROM_" + ID + "_MINVAL " + minVal);
						out.println("#define EEPROM_" + ID + "_MAXVAL " + maxVal);
						out.println("");
						offset += Integer.parseInt(bits);
					}
					else if (element.getNodeName().equals("ByteArray"))
					{
						String ID = Util.getChildNodeValue(element, "ID");
						out.println("// ByteArray " + ID);
						out.println("");
						ID = ID.toUpperCase();
						String bytes = Util.getChildNodeValue(element, "Bytes");
						out.println("#define EEPROM_" + ID + "_BYTE " + (offset / 8));
						out.println("#define EEPROM_" + ID + "_BIT " + (offset % 8));
						out.println("#define EEPROM_" + ID + "_LENGTH_BYTES " + bytes);
						out.println("");
						offset += Integer.parseInt(bytes) * 8;
					}
					else if (element.getNodeName().equals("Reserved"))
					{
						String bits = Util.getChildNodeValue(element, "Bits");
						out.println("// Reserved area with " + bits + " bits");
						out.println("");
						offset += Integer.parseInt(bits);
					}
				}
			}
			
			out.println("// overall length: " + offset + " bits");
			out.println("");
			
			out.close();
		}
	}
}
