/*******************************************************************************
 * Copyright 2010 - 2011, Qualcomm Innovation Center, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 ******************************************************************************/

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Collections;
import java.util.Comparator;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.net.URL;

/**
 * This class generates a C++ code template from a given DBus introspection XML
 * file
 */
public class ParseAJXML {
        
    private static InterfaceDescription inter = new InterfaceDescription();
    private String currentNodeName;
    private String currentObjectPath;
    private static boolean isUnnamedRoot = false;
    private CodeGenConfig config;
    private AJNErrorHandler errorHandler;
    public static boolean useFullNames = false;
    public static boolean useFullPath = false;
    private HashMap<String, InterfaceDescription> interfaceNames = new HashMap<String, InterfaceDescription>();
    private HashMap<String, Boolean> shortNames = new HashMap<String, Boolean>();

    public ParseAJXML(){
        config = CodeGenConfig.getInstance();
        errorHandler = config.errorHandler;
    }

    /**
     * Parse the XML file with a SAX parser and store all the interface
     * information in the data structure.
     * @param XMLClassDef The input XML file
     * @throws Exception 
     */
    public void parseXML(String XMLClassDef) throws Exception {
        // Parse the XML so we can process it
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document document;
        //create a schemaFactory to create a new schema
        URL url = this.getClass().getResource("src/config/introspect.xsd");
        SchemaFactory schemaFactory =
            SchemaFactory.newInstance( XMLConstants.W3C_XML_SCHEMA_NS_URI );
        Schema s = schemaFactory.newSchema(url);

        //set the DocumentBuilderFactory's schema to introspect.xsd
        factory.setSchema(s);

        builder = factory.newDocumentBuilder();

        /*
         * Overwrite the builder's entity resolver so it doesn't try to look up
         * external introspect.dtd
         */
        AJNEntityResolver resolver = new AJNEntityResolver();
        builder.setEntityResolver(resolver);

        //set the parser's error handler to the custom error handler
        builder.setErrorHandler(errorHandler);

        document =
            builder.parse(new InputSource(new StringReader(XMLClassDef)));
        document.getDocumentElement().normalize();

        Element root = document.getDocumentElement();

        String nodeName = root.getNodeName();
        currentNodeName = root.getAttribute("name");
                
        /*
         * check to make sure the class names are consistent, exits the tool if
         * it's not consistent
         */
        checkObjectPath(currentNodeName);
        checkNodeName(currentNodeName);

        parseNode(root, false);

        //If there has been a name collision, update each object's name
        if(useFullPath) {
            for(ObjectData obj : config.objects) {
                obj.updateObjectName();
            }
        }
    }

    private void parseNode(Element root, boolean isNested) throws Exception {
        String nodeName = root.getNodeName();
        currentNodeName = root.getAttribute("name");

        ObjectData newNode = new ObjectData();
        if(isNested) {
            verifyNestedNodeName(currentNodeName);
            newNode.objName = currentNodeName;
            currentObjectPath = currentObjectPath + "/" + newNode.objName;
            newNode.objPath = currentObjectPath;
        }
        else {
            newNode.objName = config.className;
            if(isUnnamedRoot){
               currentObjectPath = config.objPath;
               newNode.objPath = "/";
            }
            else {
                currentObjectPath = config.objPath + "/" + config.className;
                newNode.objPath = currentObjectPath;
            }
        }

        NodeList children = root.getChildNodes();

        /* 
         * Holds the current list of interfaces for the given node.  After the node has been parsed,
         * the interfaces are combined, if necessary, to create a new interface which inherits from
         * the interfaces in the list.
         */
        ArrayList<InterfaceDescription> tempInterfaces = new ArrayList<InterfaceDescription>();

        /*
         * This builds the array list that represents the interface(s)
         * description(s) that are used to generate the code (i.e. the
         * ArrayList containing the ArrayLists for methods, signals and
         * properties
         */
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);

            //skip the element nodes since they have no data
            if (Node.ELEMENT_NODE != node.getNodeType()) {
                continue;
            }

            nodeName = node.getNodeName();
            // Handle interface parsing
            if ("interface".equals(nodeName)) {
                /*
                 * ignore those interfaces because they are already implemented
                 * by the API
                 */
                if(parseNameAttr(node).equals("org.freedesktop.DBus.Introspectable") ||
                   parseNameAttr(node).equals("org.freedesktop.DBus.Properties")){
                    continue;
                }
                else{
                    parseInterface(node);
                    InterfaceDescription temp = inter;
                    temp = validateInterface(temp);
                    //Add the interface to the temporary list
                    tempInterfaces.add(temp);
                    inter = new InterfaceDescription();
                }
            }
            // Handle sub node parsing
            else if ("node".equals(nodeName)) {
                parseNode((Element)node, true);
                currentObjectPath = currentObjectPath.substring(0, currentObjectPath.lastIndexOf("/"));
            }
        }
        //If the node contains 1+ interfaces...
        if(!tempInterfaces.isEmpty()) {
            //Make sure that the object name is present
            if(!isNested && isUnnamedRoot && config.objects.isEmpty()) {
                SAXParseException error =
                    new SAXParseException("CodeGen error: Object name not defined. Object name should either be defined in command line (-n) or in the XML.", null);
                throw error;
            }
            checkDuplicateNames(newNode);
            //Merge the interfaces in the temp list to get a combined interface
            InterfaceDescription temp = mergeInterfaces(tempInterfaces);
            temp = validateInterface(temp);
            //Set the object's interface and add the object to the master list.
            newNode.inter = temp;
            config.objects.add(newNode);
        }
    }
  
    /**
     * Given a list of InterfaceDescriptions, this will combine them and return
     * a single interface, which contains all of the methods, signals, and
     * properties of the given interfaces.
     * @param tempInterfaces: the list of interfaces
     * @return derivedInterface: the combined interface
     */
    private InterfaceDescription mergeInterfaces(ArrayList<InterfaceDescription> tempInterfaces) {
        //If there is only one interface, don't make a new one
        if(tempInterfaces.size() == 1) {
            return tempInterfaces.get(0);
        }

        InterfaceDescription derivedInterface = new InterfaceDescription();
        //Sort the interfaces alphabetically.
        Collections.sort(tempInterfaces, new Comparator<InterfaceDescription>() {
            public int compare(InterfaceDescription one, InterfaceDescription two){
                return one.getFullName().compareTo(two.getFullName());
            }
        });

        for(InterfaceDescription parent : tempInterfaces) {
            if(derivedInterface.className.length() != 0) {
                derivedInterface.className += "__";
                derivedInterface.interfaceName += "__";
            }
            derivedInterface.parents.add(parent);
            derivedInterface.className += parent.className;
            derivedInterface.interfaceName += parent.interfaceName;
            derivedInterface.methods.addAll(parent.getMethods());
            derivedInterface.signals.addAll(parent.getSignals());
            derivedInterface.properties.addAll(parent.getProperties());
        }
        derivedInterface.updateMemberNames();
        derivedInterface.interfaceFullName = derivedInterface.className;
        derivedInterface.isDerived = true;

        return derivedInterface;
    }

    /**
     * Given an ObjectData, this verifies that it is unique
     * @param obj: the ObjectData to check
     * @return
     * @throws SAXParseException
     */
    private void checkDuplicateNames(ObjectData obj) throws SAXParseException {
       if(config.objects.contains(obj)) {
            SAXParseException error =
                new SAXParseException("CodeGen error: XML file contains nested objects with the same name.", null);
            throw error;
       }
    }

    /**
     * Given an node name, this verifies that it only contains an object name
     * @param xmlNodeName: the name to check
     * @return
     * @throws SAXParseException
     */
    private void verifyNestedNodeName(String xmlNodeName) throws SAXParseException {
        if(xmlNodeName == null || xmlNodeName.equals("")) {
            SAXParseException error =
                new SAXParseException("CodeGen error: Object name of a nested object must be specified in the XML.", null);
            throw error;
        }
        if(xmlNodeName.contains("/")) {
            SAXParseException error =
                new SAXParseException("CodeGen error: Nested nodes cannot specify object paths.", null);
            throw error;
        }
    }

    /**
     * Check to see if the object path is consistent between cmd line and XML
     * @param xmlNodeName
     * @return
     * @throws SAXParseException
     */
    private void checkObjectPath(String xmlNodeName) throws SAXParseException {
        //If the objPath wasn't specified via -r, and the xmlNodeName is empty or doesn't start with /, throw an error
        if(config.objPath == null || config.objPath.equals("")) {
            if(xmlNodeName.equals("") || xmlNodeName == null || !xmlNodeName.startsWith("/")) {
                return;
            }
            //If the xmlNodeName is just a /, then just set the config.objPath to /
            if(xmlNodeName.length() == 1) {
                return;
            }

            //Otherwise, set the objPath to the substring of xmlNodeName from 0 to the last index of /
            config.objPath = xmlNodeName.substring(0, xmlNodeName.lastIndexOf('/'));
            return;
        }
        //If config.objPath contains a trailing '/'. trim it
        if(config.objPath.endsWith("/") && config.objPath.length() > 1) {
           config.objPath = config.objPath.substring(0, config.objPath.lastIndexOf('/'));
        }

        //If the xmlNodeName starts with /, it means it's specifying an object path
        if(xmlNodeName.startsWith("/")) {
            //objPath is just /, if xmlNodeName contains only one /, then they match
            if(config.objPath.length() == 1 && xmlNodeName.substring(0, xmlNodeName.lastIndexOf('/')+1).length() == 1) {
                return;
            }
            //If the path is defined in both places and they are different, throw an error
            if(!xmlNodeName.substring(0, xmlNodeName.lastIndexOf('/')).equals(config.objPath)) {
                SAXParseException error = new SAXParseException("CodeGen error: Object path mismatch between XML and command line arguments", null);
                throw error;
            }
        }
    }

    /**
     * Check to see if the class name is consistent between cmd line and XML
     * @param xmlNodeName
     * @return
     * @throws SAXParseException 
     */
    private void checkNodeName(String xmlNodeName) throws SAXParseException{
        //if objName not specified, and xmlNodeName is empty, /, or only contains the objPath, throw an error
        if(config.className == null || config.className.equals("")) {
            if(xmlNodeName.equals("") || xmlNodeName.equals(config.objPath) || xmlNodeName.endsWith("/")) {
                isUnnamedRoot = true;
            }
            config.className = xmlNodeName.substring(xmlNodeName.lastIndexOf("/")+1);
        }else if(!xmlNodeName.equals("") || xmlNodeName == null) {
            //if class name is define in both places, make sure they match
            if(!xmlNodeName.substring(xmlNodeName.lastIndexOf("/")+1).equals(config.className) &&
               !xmlNodeName.equals(config.objPath) && !xmlNodeName.endsWith("/")) {
                SAXParseException error = new SAXParseException("CodeGen error: class name mismatch between XML and command line arguments", null);
                throw error;
            }
        }
    }

    /**
     * Given an InterfaceDescription, this verifies that either the name is unique, or an exact 
     * instance of that interface already been parsed.  Then, this checks for a name collision.
     * If there is, then full names will be used for everything.  Finally, the interface gets
     * added to the master list.
     * @param curIface: the InterfaceDescription to be checked
     * @return the validated InterfaceDescription
     * @throws SAXParseException
     */
    private InterfaceDescription validateInterface(InterfaceDescription curIface) throws SAXParseException {
        InterfaceDescription temp = interfaceNames.get(curIface.getFullName());
        if(temp != null) {
            /*
             * Check if the two interfaces are the same.
             * If not, throw an exception.
             */
            if(curIface.equals(temp)) {
                return temp;
            }
            else {
                SAXParseException error = new SAXParseException("CodeGen error: two interfaces with the same name but different signatures", null);
                throw error;
            }
        }
        /*
         * Check if the short name is already in use.
         * If so, use full names for everything.
         */ 
        if(shortNames.containsKey(curIface.getName())) {
            useFullNames = true;
        }
        else {
            shortNames.put(curIface.getName(), true);
        }

        /*
         * If the interface is not present (ie, it hasn't been created),
         * add it to the master list.  Otherwise, get the old instance.
         */
        config.interfaces.add(curIface);
        interfaceNames.put(curIface.getFullName(), curIface);
        return curIface;
    }


    /**
     * Extracts data from the node and populate the interfaceDescription arraylist.
     * @throws SAXParseException 
     */
    private String parseInterface(Node interfaceNode) throws SAXParseException {
        String nodeName;
        int i;

        //Set interface name
        inter.setName(parseNameAttr(interfaceNode));

        NodeList children = interfaceNode.getChildNodes();
        int numChildren = children.getLength();
        for (i = 0; i < numChildren; i++) {
            Node node2 = children.item(i);
            nodeName = node2.getNodeName();
                        
            //skip the element nodes since they have no data
            if (Node.ELEMENT_NODE != node2.getNodeType()) {
                continue;
            }

            if ("method".equals(nodeName)) {
                parseMethod(node2);                                        
            } else if ("signal".equals(nodeName)) {
                parseSignal(node2);                                        
            }else if("property".equals(nodeName)){
                parseProperty(node2);
            }else if("annotation".equals(nodeName)){
                if(node2.getAttributes().getNamedItem("name").getNodeValue().equals("org.alljoyn.Bus.Item.IsSecure") &&
                   node2.getAttributes().getNamedItem("value").getNodeValue().equals("true")){
                    inter.isSecure = true;
                }//So far the "Secure" annotation is the only being checked for interfaces.
            }
        }

        return null;
    }

    /**
     * Parse Name Attribute
     */
    private String parseNameAttr(Node node) {
        String str = "";
        NamedNodeMap attrList = node.getAttributes();
        Node node2 = attrList.getNamedItem("name");
        if (node2 != null) {
            str = node2.getNodeValue();
        }
        return str;
    }

    /**
     * Parse a property
     * @throws SAXParseException 
     */
    private void parseProperty(Node node) throws SAXParseException{
        PropertyDef newProp = new PropertyDef();
        newProp.setName(parseNameAttr(node));

        NamedNodeMap map =  node.getAttributes();

        for(int i = 0 ; i < map.getLength(); i++){
            String text = map.item(i).toString();
            String type = text.substring(0, text.indexOf('='));
            String data = text.substring(text.indexOf('=')+2,
                                         text.lastIndexOf('"'));

            if(type.equals("access")){
                newProp.setAccess(data);
            }else if(type.equals("name")){
                newProp.setName(data);
            }else if(type.equals("type")){
                if(config.runnable && data.contains("v")){
                    data = "au";
                    SAXParseException warning = 
                        new SAXParseException("Variant type not supported in runnable mode.  Treating " + newProp.getName() + " as unsigned int array.", null);
                    errorHandler.warning(warning);
                }
                newProp.setSignature(data);
            }else{
                SAXParseException error=
                    new SAXParseException(("CodeGen error: parsing " +
                                           newProp.getName() + "property."),
                                          null);
                throw error;
            }
        }
        newProp.setInterfaceName(inter.getFullName());
        inter.addNewProperty(newProp);
    }

    /**
     * Parse Method
     * @throws SAXParseException 
     */
    private void parseMethod(Node node) throws SAXParseException {
        int i;
        int count1 = 0; //used to name un-named input args
        int count2 = 0;//used to name un-named output args
        MethodDef newMethod = new MethodDef();
        newMethod.setName(parseNameAttr(node));
        newMethod.setType("alljoyn::MESSAGE_METHOD_CALL");
        String inArgsTypes = "";
        String inArgsNames = "";
        String outArgsTypes = "";
        String outArgsNames = "";

        NodeList children = node.getChildNodes();
        int numChildren = children.getLength();
        for (i = 0; i < numChildren; i++) {
            Node node2 = children.item(i);
            
            //skip the element nodes since they have no data
            if (Node.ELEMENT_NODE != node2.getNodeType()) {
                continue;
            }
            
            String nodeName = node2.getNodeName();
            if ("arg".equals(nodeName)) {
                ArgDef newArg = parseMethodArgList(node2);
                if(newMethod.hasArgName(newArg.getArgName())){
                    SAXParseException error =
                        new SAXParseException(("CodeGen error: " +
                                               newMethod.getName() +
                                               " cannot have multiple args with the same name."),
                                              null);
                    throw error;
                }

                if (newArg.getArgDirection().equals("in")) {
                    //if the arg is not named, check the -l option
                    if (newArg.getArgName().equals("")) {
                        if (config.useStrict) {
                            SAXParseException error =
                                new SAXParseException("CodeGen error: In "
                                                      + newMethod.getName()
                                                      + " method, all arguments must be named. Use the -l option for auto-generated arg names",
                                                      null);
                            throw error;
                        } else {
                            newArg.argName = "inputArg" + count1;
                            count1++;
                        }
                    }
                    
                    newMethod.inArgCount++;
                    inArgsTypes += newArg.getArgType();
                    inArgsNames += newArg.getArgName() + ",";
                } else if (newArg.getArgDirection().equals("out")) {
                    //if arg is not named, check -l option
                    if (newArg.getArgName().equals("")) {
                        if (config.useStrict) {
                            SAXParseException error =
                                new SAXParseException("CodeGen error: In "
                                                      + newMethod.getName()
                                                      + " method, all arguments must be named. Use the -l option for auto-generated arg names",
                                                      null);
                            throw error;
                        } else {
                            newArg.argName = "outputArg" + count2;
                            count2++;
                        }
                    }
                    
                    newMethod.outArgCount++;
                    outArgsTypes += newArg.getArgType();
                    outArgsNames += newArg.getArgName() + ",";
                }
                
                //add the new argument to the method
                newMethod.argList.add(newArg);
                
            }else if("annotation".equals(nodeName)){
                NamedNodeMap attrList = node2.getAttributes();
                if(attrList.getNamedItem("name").getNodeValue().equals("org.freedesktop.DBus.Method.NoReply") &&
                   attrList.getNamedItem("value").getNodeValue().equals("true")){
                    newMethod.noReply = true;
                }else if(attrList.getNamedItem("name").getNodeValue().equals("org.alljoyn.Bus.Item.IsSecure") &&
                         attrList.getNamedItem("value").getNodeValue().equals("true")){
                    newMethod.isSecure = true;
                }
            }
        }

        if (inArgsNames.equals("")) {
            inArgsNames = "NULL";
            inArgsTypes = "NULL";
        } else {
            inArgsNames = inArgsNames.substring(0, inArgsNames.length() - 1);
        }

        if (outArgsNames.equals("")) {
            outArgsNames = "NULL";
            outArgsTypes = "NULL";
        } else {
            outArgsNames = outArgsNames.substring(0, outArgsNames.length() - 1);
        }

        newMethod.setParams(inArgsTypes);
        newMethod.setParamNames(inArgsNames);
        newMethod.setRetType(outArgsTypes);
        newMethod.setRetNames(outArgsNames);
        newMethod.setInterfaceName(inter.getFullName());
        inter.addNewMethod(newMethod);
    }

    /**
     * Parse Method Argument List
     * @throws SAXParseException 
     */
    private ArgDef parseMethodArgList(Node node) throws SAXParseException {
        int numAttr;
        NamedNodeMap attrList;
        Node node2;
        String name = "";
        String direction = "";
        String type = "";
        String variantType = null;

        attrList = node.getAttributes();
        numAttr = attrList.getLength();

        if (numAttr > 0) {
            node2 = attrList.getNamedItem("name");
            if (null != node2) {
                name = node2.getNodeValue();
            }

            node2 = attrList.getNamedItem("direction");
            if (null != node2) {
                if(node2.getNodeValue().equals("unset")){
                    direction = "in";
                }else{                                        
                    direction = node2.getNodeValue();
                }
            }

            node2 = attrList.getNamedItem("type");
            if (null != node2) {
                type = node2.getNodeValue();
                if(config.runnable && type.contains("v")){
                    type = "au";
                    SAXParseException warning =
                        new SAXParseException("Variant type not supported in runnable mode.  Treating " + name + " as unsigned int array.", null);
                    errorHandler.warning(warning);
                }
            }
                        
            node2 = attrList.getNamedItem("annotation");
            if(null != node2){
                if(node2.getAttributes().getNamedItem("name").getNodeValue().equals("org.alljoyn.Bus.Arg.VariantTypes")){
                    variantType =
                        node2.getAttributes().getNamedItem("value").getNodeValue();
                }
            }

        } else {
            return null;
        }
        
        return new ArgDef(name, type, direction, variantType);
    }

    /**
     * Parse Signal for name, arguments, and return type
     * @throws SAXException 
     */
    private void parseSignal(Node node) throws SAXParseException {
        SignalDef newSignal = new SignalDef();
        newSignal.setName(parseNameAttr(node));
        newSignal.setType("alljoyn::MESSAGE_SIGNAL");
        String inArgsTypes = "";
        String inArgsNames = "";
        int count = 0;

        NodeList children = node.getChildNodes();
        int numChildren = children.getLength();
        for (int i = 0; i < numChildren; i++) {
            Node node2 = children.item(i);
            
            //skip the element nodes since they have no data
            if (Node.ELEMENT_NODE != node2.getNodeType()) {
                continue;
            }

            String nodeName = node2.getNodeName();
            if ("arg".equals(nodeName)) {
                ArgDef newArg = parseSignalAttrList(node2);
                if(newSignal.hasArgName(newArg.getArgName())){
                    SAXParseException error =
                        new SAXParseException(("CodeGen error: " +
                                               newSignal.getName() +
                                               " can not have multiple args with the same name."),
                                              null);
                    throw error;
                }

                //if the arg is not named, check the -l option
                if (newArg.getArgName().equals("")) {
                    if (config.useStrict) {
                        SAXParseException error =
                            new SAXParseException("CodeGen error: In "
                                                  + newSignal.getName()
                                                  + "signal, all arguments must be named. Use the -l option for auto-generated arg names",
                                                  null);
                        throw error;
                    } else {
                        newArg.argName = "signalArg" + count;
                        count++;
                    }
                }

                //add the arg to the signal, and add the name and type to inArgsTypes and inArgsNames
                newSignal.argList.add(newArg);
                inArgsTypes += newArg.getArgType();
                inArgsNames += newArg.getArgName() + ",";
            }else if("annotation".equals(nodeName)){
            	//check for the IsSecure annotation
                if(node2.getAttributes().getNamedItem("name").getNodeValue().equals("org.alljoyn.Bus.Item.IsSecure") &&
                   node2.getAttributes().getNamedItem("value").getNodeValue().equals("true")){
                    newSignal.isSecure = true;
                }
            }
        }
        if (!inArgsNames.equals("")) {
            inArgsNames = inArgsNames.substring(0, inArgsNames.length() - 1);
        }
        newSignal.setParams(inArgsTypes);
        newSignal.setParamNames(inArgsNames);
        newSignal.setInterfaceName(inter.getFullName());
        inter.addNewSignal(newSignal);
    }

    /**
     * Parse Signal Attribute List
     * @throws SAXParseException 
     */
    private ArgDef parseSignalAttrList(Node node) throws SAXParseException {
        int i, numAttr;
        NamedNodeMap attrList;
        String nodeName;
        String name = "";
        String type = "";
        String variantType = null;
        boolean hasDirection = false;

        attrList = node.getAttributes();
        numAttr = attrList.getLength();

        if (numAttr > 0) {
            for (i = 0; i < numAttr; i++) {
                nodeName = attrList.item(i).getNodeName();
                if ("name".equals(nodeName)) {
                    name = attrList.item(i).getNodeValue();
                }

                if ("type".equals(nodeName)) {
                    type = attrList.item(i).getNodeValue();
                    if(config.runnable && type.contains("v")){
                        type = "au";
                        SAXParseException warning =
                            new SAXParseException("Variant type not supported in runnable mode.  Treating " + name + " as unsigned int array.", null);
                        errorHandler.warning(warning);
                    }
                }
                                
                if("direction".equals(nodeName) && !attrList.item(i).getNodeValue().equals("unset")){
                    hasDirection = true;
                }

                if("annotation".equals(nodeName)){
                    Node tempNode = attrList.item(i);
                    if(tempNode.getAttributes().getNamedItem("name").getNodeValue().equals("org.alljoyn.Bus.Arg.VariantTypes")){
                        variantType = tempNode.getAttributes().getNamedItem("value").getNodeValue();
                    }
                }
            }
            
        } else {
            return null;
        }

        /*
         * This is done outside the for loop because the signal name might not
         * be parsed when direction is parsed.
         */
        try{
            if(hasDirection){
                SAXParseException warning =
                    new SAXParseException(("CodeGen warning: Signal argument \"" +
                                           name +
                                           "\" should not have a direction field."),
                                          null);
                throw warning;
            }                        
        }catch(SAXParseException e){
            errorHandler.warning(e);
        }
        
        

        return new ArgDef(name, type, "in", variantType);
    }
} // class CodeGenerator


/**
 * 
 * Custom EntityResolver that prevents the SAX parser to look for unnecessary
 * outside sources.
 *
 */
class AJNEntityResolver implements EntityResolver {

    public InputSource resolveEntity(String publicId, String systemId)
        throws SAXException, IOException {
        return new InputSource(new StringReader(""));
    }
}
