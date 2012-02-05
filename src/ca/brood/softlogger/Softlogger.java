package ca.brood.softlogger;

import java.util.ArrayList;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;


import java.io.File;


public class Softlogger {
	private Logger log;
	private boolean configValid = false;
	
	private String loggerName = "Unnamed Logger";
	private int defaultDevicePoll = 0;
	private String logFilePath = "log/";
	private String tableFilePath = "lut/";
	private String dataFilePath = "data/";
	private String configFilePath = "";
	
	private DataServer server;
	private ArrayList<Channel> channels;
	
	public Softlogger() {
		log = Logger.getLogger(Softlogger.class);
		PropertyConfigurator.configure("logger.config");
		configValid = false;
	}
	public void configure(String configFile) {
		log.info("");
		log.info("");
		log.info("******************************");
		log.info("SOFTLOGGER IS STARTING");
		log.info("******************************");
		log.info("Softlogger using config file: "+configFile);
		configFilePath = configFile;
		if (!loadConfig(configFilePath)) {
			log.fatal("Error loading config file.");
		}
	}
	public static void main(String[] args) {
		Softlogger s = new Softlogger();
		s.configure("config.xml");
		s.run();
		try {
			Thread.sleep(10000); //Run for 10 seconds
		} catch (InterruptedException e) {
		}
		s.stop();
		s.log.info("All done");
	}
	public void kill() {
		log.info("Softlogger killing channels");
		for (int i=0; i<channels.size(); i++) {
			channels.get(i).kill();
		}
	}
	public void stop() {
		log.info("Softlogger stopping channels");
		for (int i=0; i<channels.size(); i++) {
			channels.get(i).stop();
		}
	}
	
	public void run() {
		//Start all the channels, which will in turn start all the devices
		for (int i=0; i<channels.size(); i++) {
			channels.get(i).run();
		}
	}

	private boolean loadConfig(String filename) {
		File xmlFile = new File(filename);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		Document doc;
		try {
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(xmlFile);
			doc.getDocumentElement().normalize();
		} catch (Exception e) {
			log.fatal("Exception while trying to load config file: "+filename);
			return false;
		}
		Node currentConfigNode = doc.getDocumentElement();
		NodeList loggerConfigNodes = currentConfigNode.getChildNodes();
		log.debug("Configuring Logger...");
		for (int i=0; i<loggerConfigNodes.getLength(); i++) {
			Node configNode = loggerConfigNodes.item(i);
			if ("name".compareToIgnoreCase(configNode.getNodeName())==0){
				log.debug("Logger Name: "+configNode.getFirstChild().getNodeValue());
				this.loggerName = configNode.getFirstChild().getNodeValue();
			} else if ("poll".compareToIgnoreCase(configNode.getNodeName())==0){
				log.debug("Default poll period: "+configNode.getFirstChild().getNodeValue());
				try {
					this.defaultDevicePoll = Integer.parseInt(configNode.getFirstChild().getNodeValue());
				} catch (NumberFormatException e) {
					log.error("Invalid device poll: "+configNode.getFirstChild().getNodeValue());
					this.defaultDevicePoll = 0;
				}
			} else if ("tableFilePath".compareToIgnoreCase(configNode.getNodeName())==0){
				log.debug("Lookup table path: "+configNode.getFirstChild().getNodeValue());
				this.tableFilePath = configNode.getFirstChild().getNodeValue();
			} else if ("dataFilePath".compareToIgnoreCase(configNode.getNodeName())==0){
				log.debug("Data file path: "+configNode.getFirstChild().getNodeValue());
				this.dataFilePath = configNode.getFirstChild().getNodeValue();
			} else if (("server".compareToIgnoreCase(configNode.getNodeName())==0) || 
			("channel".compareToIgnoreCase(configNode.getNodeName())==0)|| 
			("#text".compareToIgnoreCase(configNode.getNodeName())==0))	{
				continue;
			} else {
				log.warn("Got unknown node in config: "+configNode.getNodeName());
			}
		}
		if (loggerName.equals("")) {
			log.warn("Softlogger name is blank");
		}
		if (defaultDevicePoll < 0) {
			log.warn("Softlogger default device poll rate is invalid.  Using default of 60.");
			defaultDevicePoll = 60;
		}
		if (tableFilePath.equals("")) {
			log.warn("Softlogger lookup table file path is invalid.  Using default of lut/");
			tableFilePath = "lut/";
		}
		if (dataFilePath.equals("")) {
			log.warn("Softlogger data file path is invalid.  Using default of data/");
			dataFilePath = "data/";
		}
		
		//Load the data server
		loggerConfigNodes = doc.getElementsByTagName("server");
		if (loggerConfigNodes.getLength() == 0) {
			log.fatal("Could not find a server defined in the config file.");
			return false;
		}
		if (loggerConfigNodes.getLength() > 1) {
			log.fatal("Too many servers are defined in the config file");
			return false;
		}
		currentConfigNode = loggerConfigNodes.item(0);
		server = new DataServer();
		if (!server.configure(currentConfigNode)) {
			return false;
		}
		
		//Load the channels
		loggerConfigNodes = doc.getElementsByTagName("channel");
		channels = new ArrayList<Channel>();
		boolean workingChannel = false;
		for (int i=0; i<loggerConfigNodes.getLength(); i++) {
			currentConfigNode = loggerConfigNodes.item(i);
			Channel mc = new Channel();
			if (mc.configure(currentConfigNode)) {
				workingChannel = true;
				channels.add(mc);
			}
		}
		
		if (!workingChannel) {
			log.fatal("No usable channels configured");
			return false;
		}
		
		for (int i=0; i<channels.size(); i++) {
			channels.get(i).setDefaultPoll(this.defaultDevicePoll);
		}
		
		return true;
	}
}